-- 운전자 본인 확인 (PIN 체크인)
-- 차량에서 운전자가 PIN을 입력하면 서버가 검증하고 체크인을 남긴다.
-- 운행의 "본인 확인" 여부는 차량이 보낸 값이 아니라 서버의 체크인 기록으로 판정한다.

alter table public.drivers
  add column pin_hash text,
  add column pin_failed_count int not null default 0,
  add column pin_locked_until timestamptz;

create table public.driver_checkins (
  id uuid primary key default gen_random_uuid(),
  vehicle_id uuid not null references public.vehicles on delete cascade,
  driver_id uuid not null references public.drivers on delete cascade,
  method text not null default 'PIN',
  created_at timestamptz not null default now()
);
create index on public.driver_checkins (vehicle_id, created_at);
alter table public.driver_checkins enable row level security;
create policy checkins_read on public.driver_checkins for select
  using (exists (select 1 from vehicles v where v.id = vehicle_id and public.is_org_member(v.org_id)));

alter table public.trips
  add column checkin_id uuid references public.driver_checkins on delete set null,
  add column driver_verified boolean not null default false,
  add column driver_verified_at timestamptz;

-- 관리자: PIN 설정 (4~6자리 숫자)
create or replace function public.set_driver_pin(p_driver uuid, p_pin text)
returns void language plpgsql security definer set search_path = public, extensions as $$
declare v_org uuid;
begin
  select org_id into v_org from drivers where id = p_driver;
  if v_org is null or not is_org_member(v_org) then raise exception 'forbidden'; end if;
  if p_pin !~ '^[0-9]{4,6}$' then raise exception 'PIN은 4~6자리 숫자여야 합니다'; end if;
  update drivers set pin_hash = crypt(p_pin, gen_salt('bf')), pin_failed_count = 0, pin_locked_until = null where id = p_driver;
end $$;

-- 차량: PIN 확인 → 체크인 발급
create or replace function public.verify_driver_pin(p_token text, p_driver uuid, p_pin text)
returns json language plpgsql security definer set search_path = public, extensions as $$
declare v vehicles; d drivers; v_checkin uuid;
begin
  select * into v from vehicles where device_token_hash = encode(digest(p_token, 'sha256'), 'hex');
  if v.id is null then raise exception 'unknown device'; end if;
  select * into d from drivers where id = p_driver and org_id = v.org_id and active;
  if d.id is null then return json_build_object('ok', false, 'reason', 'unknown_driver'); end if;
  if d.pin_hash is null then return json_build_object('ok', false, 'reason', 'no_pin'); end if;
  if d.pin_locked_until is not null and d.pin_locked_until > now() then
    return json_build_object('ok', false, 'reason', 'locked', 'locked_until', d.pin_locked_until);
  end if;
  if crypt(p_pin, d.pin_hash) <> d.pin_hash then
    update drivers set pin_failed_count = pin_failed_count + 1,
      pin_locked_until = case when pin_failed_count + 1 >= 5 then now() + interval '5 minutes' else null end
    where id = d.id;
    return json_build_object('ok', false, 'reason', 'wrong_pin', 'remaining', greatest(0, 4 - d.pin_failed_count));
  end if;
  update drivers set pin_failed_count = 0, pin_locked_until = null where id = d.id;
  insert into driver_checkins (vehicle_id, driver_id) values (v.id, d.id) returning id into v_checkin;
  return json_build_object('ok', true, 'checkin_id', v_checkin, 'driver_id', d.id, 'name', d.name, 'at', now());
end $$;

-- vehicle_sync: checkin_id를 받아 서버 기록으로 본인 확인 판정, 운전자 목록에 PIN 설정 여부 포함
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
    'drivers', coalesce((select json_agg(json_build_object('id', d.id, 'name', d.name, 'dept', d.dept, 'has_pin', d.pin_hash is not null) order by d.name)
                         from drivers d where d.org_id = v.org_id and d.active), '[]'::json));
end $$;

revoke all on function public.verify_driver_pin(text, uuid, text) from public;
grant execute on function public.verify_driver_pin(text, uuid, text) to anon, authenticated;
grant execute on function public.set_driver_pin(uuid, text) to authenticated;
-- PIN 해시는 API로 노출하지 않는다
revoke select (pin_hash) on public.drivers from anon, authenticated;
