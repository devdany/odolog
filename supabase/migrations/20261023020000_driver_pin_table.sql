-- PIN 해시를 drivers에서 분리: 테이블 단위 SELECT 권한 때문에 컬럼 revoke가 효과가 없으므로,
-- 정책이 없는(=API로 접근 불가) 별도 테이블에 두고 security definer 함수만 접근한다.

create table public.driver_pins (
  driver_id uuid primary key references public.drivers on delete cascade,
  pin_hash text not null,
  failed_count int not null default 0,
  locked_until timestamptz,
  updated_at timestamptz not null default now()
);
alter table public.driver_pins enable row level security;
revoke all on public.driver_pins from anon, authenticated;

insert into public.driver_pins (driver_id, pin_hash, failed_count, locked_until)
  select id, pin_hash, pin_failed_count, pin_locked_until from public.drivers where pin_hash is not null;

alter table public.drivers drop column pin_hash, drop column pin_failed_count, drop column pin_locked_until;

-- 웹에서 PIN 설정 여부만 보여주기 위한 뷰 대용 함수
create or replace function public.drivers_with_pin_status()
returns table (id uuid, name text, dept text, active boolean, has_pin boolean)
language sql stable security definer set search_path = public as $$
  select d.id, d.name, d.dept, d.active, exists (select 1 from driver_pins p where p.driver_id = d.id)
  from drivers d where is_org_member(d.org_id) order by d.name;
$$;

create or replace function public.set_driver_pin(p_driver uuid, p_pin text)
returns void language plpgsql security definer set search_path = public, extensions as $$
declare v_org uuid;
begin
  select org_id into v_org from drivers where id = p_driver;
  if v_org is null or not is_org_member(v_org) then raise exception 'forbidden'; end if;
  if p_pin !~ '^[0-9]{4,6}$' then raise exception 'PIN은 4~6자리 숫자여야 합니다'; end if;
  insert into driver_pins (driver_id, pin_hash) values (p_driver, crypt(p_pin, gen_salt('bf')))
  on conflict (driver_id) do update set pin_hash = excluded.pin_hash, failed_count = 0, locked_until = null, updated_at = now();
end $$;

create or replace function public.verify_driver_pin(p_token text, p_driver uuid, p_pin text)
returns json language plpgsql security definer set search_path = public, extensions as $$
declare v vehicles; d drivers; p driver_pins; v_checkin uuid;
begin
  select * into v from vehicles where device_token_hash = encode(digest(p_token, 'sha256'), 'hex');
  if v.id is null then raise exception 'unknown device'; end if;
  select * into d from drivers where id = p_driver and org_id = v.org_id and active;
  if d.id is null then return json_build_object('ok', false, 'reason', 'unknown_driver'); end if;
  select * into p from driver_pins where driver_id = d.id;
  if p.driver_id is null then return json_build_object('ok', false, 'reason', 'no_pin'); end if;
  if p.locked_until is not null and p.locked_until > now() then
    return json_build_object('ok', false, 'reason', 'locked', 'locked_until', p.locked_until);
  end if;
  if crypt(p_pin, p.pin_hash) <> p.pin_hash then
    update driver_pins set failed_count = failed_count + 1,
      locked_until = case when failed_count + 1 >= 5 then now() + interval '5 minutes' else null end
    where driver_id = d.id;
    return json_build_object('ok', false, 'reason', 'wrong_pin', 'remaining', greatest(0, 4 - p.failed_count));
  end if;
  update driver_pins set failed_count = 0, locked_until = null where driver_id = d.id;
  insert into driver_checkins (vehicle_id, driver_id) values (v.id, d.id) returning id into v_checkin;
  return json_build_object('ok', true, 'checkin_id', v_checkin, 'driver_id', d.id, 'name', d.name, 'at', now());
end $$;

-- vehicle_sync의 has_pin을 새 테이블 기준으로
create or replace function public.vehicle_sync(p_token text, p_trips jsonb default '[]', p_odo_km numeric default null)
returns json language plpgsql security definer set search_path = public, extensions as $$
declare v vehicles; t jsonb; v_driver uuid; c driver_checkins;
begin
  select * into v from vehicles where device_token_hash = encode(digest(p_token, 'sha256'), 'hex');
  if v.id is null then raise exception 'unknown device'; end if;

  for t in select * from jsonb_array_elements(coalesce(p_trips, '[]'::jsonb)) loop
    v_driver := nullif(t->>'driver_id', '')::uuid;
    if v_driver is not null and not exists (select 1 from drivers where id = v_driver and org_id = v.org_id) then
      v_driver := null;
    end if;
    c := null;
    if nullif(t->>'checkin_id', '') is not null then
      select * into c from driver_checkins
        where id = (t->>'checkin_id')::uuid and vehicle_id = v.id and driver_id = v_driver;
    end if;
    insert into trips (vehicle_id, local_id, driver_id, driver_name, start_at, end_at, start_odo_km, end_odo_km,
                       purpose, start_addr, end_addr, dest_name, toll_won, note, source, confirmed_at,
                       checkin_id, driver_verified, driver_verified_at)
    values (v.id, t->>'local_id', v_driver, t->>'driver_name', (t->>'start_at')::timestamptz, (t->>'end_at')::timestamptz,
            (t->>'start_odo_km')::numeric, (t->>'end_odo_km')::numeric,
            coalesce(t->>'purpose', 'UNCONFIRMED')::trip_purpose, t->>'start_addr', t->>'end_addr', t->>'dest_name',
            (t->>'toll_won')::int, t->>'note', coalesce(t->>'source', 'AUTO')::trip_source, (t->>'confirmed_at')::timestamptz,
            c.id, c.id is not null, c.created_at)
    on conflict (vehicle_id, local_id) do update set
      driver_id = excluded.driver_id, driver_name = excluded.driver_name, end_at = excluded.end_at, end_odo_km = excluded.end_odo_km,
      purpose = excluded.purpose, start_addr = excluded.start_addr, end_addr = excluded.end_addr,
      dest_name = excluded.dest_name, toll_won = excluded.toll_won, note = excluded.note,
      confirmed_at = excluded.confirmed_at, checkin_id = excluded.checkin_id,
      driver_verified = excluded.driver_verified, driver_verified_at = excluded.driver_verified_at;
  end loop;

  update vehicles set last_seen_at = now(), last_odo_km = coalesce(p_odo_km, last_odo_km) where id = v.id;

  return json_build_object(
    'vehicle', json_build_object('id', v.id, 'plate_no', v.plate_no, 'model', v.model),
    'drivers', coalesce((select json_agg(json_build_object('id', d.id, 'name', d.name, 'dept', d.dept,
                           'has_pin', exists (select 1 from driver_pins p where p.driver_id = d.id)) order by d.name)
                         from drivers d where d.org_id = v.org_id and d.active), '[]'::json));
end $$;

grant execute on function public.drivers_with_pin_status() to authenticated;
