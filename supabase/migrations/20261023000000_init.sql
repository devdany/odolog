-- 오도로그 초기 스키마: 조직 / 차량 / 운전자 / 운행
-- 관리자(웹)는 Supabase Auth + RLS(조직 멤버)로, 차량 앱은 anon key + security definer RPC + device token으로 접근한다.

create extension if not exists pgcrypto with schema extensions;

-- ---------- 조직 ----------
create table public.organizations (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  created_at timestamptz not null default now()
);

create table public.org_members (
  org_id uuid not null references public.organizations on delete cascade,
  user_id uuid not null references auth.users on delete cascade,
  role text not null default 'admin',
  created_at timestamptz not null default now(),
  primary key (org_id, user_id)
);

create or replace function public.is_org_member(p_org uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select exists (select 1 from org_members where org_id = p_org and user_id = auth.uid());
$$;

-- ---------- 차량 ----------
create table public.vehicles (
  id uuid primary key default gen_random_uuid(),
  org_id uuid not null references public.organizations on delete cascade,
  plate_no text not null,
  model text,
  ownership text not null default 'owned' check (ownership in ('owned', 'lease', 'rent')),
  pairing_code text unique,
  pairing_expires_at timestamptz,
  device_token_hash text unique,
  paired_at timestamptz,
  last_odo_km numeric(10, 1),
  last_seen_at timestamptz,
  created_at timestamptz not null default now()
);
create index on public.vehicles (org_id);

-- ---------- 운전자 ----------
create table public.drivers (
  id uuid primary key default gen_random_uuid(),
  org_id uuid not null references public.organizations on delete cascade,
  name text not null,
  dept text,
  active boolean not null default true,
  created_at timestamptz not null default now()
);
create index on public.drivers (org_id);

-- ---------- 운행 ----------
create type public.trip_purpose as enum ('COMMUTE', 'BUSINESS', 'PERSONAL', 'UNCONFIRMED');
create type public.trip_source as enum ('AUTO', 'GAP');

create table public.trips (
  id uuid primary key default gen_random_uuid(),
  vehicle_id uuid not null references public.vehicles on delete cascade,
  local_id text not null,                       -- 차량 앱 Room PK (멱등 upsert 키)
  driver_id uuid references public.drivers on delete set null,
  driver_name text,                             -- 앱에서 선택한 운전자 이름(서버 운전자와 매칭 안 돼도 보존)
  start_at timestamptz not null,
  end_at timestamptz,
  start_odo_km numeric(10, 1) not null,
  end_odo_km numeric(10, 1),
  distance_km numeric(10, 1) generated always as (greatest(coalesce(end_odo_km, start_odo_km) - start_odo_km, 0)) stored,
  purpose public.trip_purpose not null default 'UNCONFIRMED',
  start_addr text,
  end_addr text,
  dest_name text,
  toll_won integer,
  note text,
  source public.trip_source not null default 'AUTO',
  confirmed_at timestamptz,
  updated_at timestamptz not null default now(),
  unique (vehicle_id, local_id)
);
create index on public.trips (vehicle_id, start_at);

-- 개인 용도 운행은 위치를 남기지 않는다 (앱에서도 지우지만 서버에서 한 번 더 보장)
create or replace function public.trips_privacy() returns trigger language plpgsql as $$
begin
  if new.purpose = 'PERSONAL' then
    new.start_addr := null; new.end_addr := null; new.dest_name := null;
  end if;
  new.updated_at := now();
  return new;
end $$;
create trigger trips_privacy before insert or update on public.trips
  for each row execute function public.trips_privacy();

-- ---------- RLS ----------
alter table public.organizations enable row level security;
alter table public.org_members enable row level security;
alter table public.vehicles enable row level security;
alter table public.drivers enable row level security;
alter table public.trips enable row level security;

create policy org_read on public.organizations for select using (public.is_org_member(id));
create policy org_update on public.organizations for update using (public.is_org_member(id));
create policy members_read on public.org_members for select using (user_id = auth.uid() or public.is_org_member(org_id));

create policy vehicles_all on public.vehicles for all
  using (public.is_org_member(org_id)) with check (public.is_org_member(org_id));
create policy drivers_all on public.drivers for all
  using (public.is_org_member(org_id)) with check (public.is_org_member(org_id));
create policy trips_all on public.trips for all
  using (exists (select 1 from vehicles v where v.id = vehicle_id and public.is_org_member(v.org_id)))
  with check (exists (select 1 from vehicles v where v.id = vehicle_id and public.is_org_member(v.org_id)));

-- ---------- 관리자 RPC ----------
-- 첫 로그인 시 조직 생성 + 본인을 관리자로 등록
create or replace function public.create_organization(p_name text)
returns uuid language plpgsql security definer set search_path = public as $$
declare v_org uuid;
begin
  if auth.uid() is null then raise exception 'not authenticated'; end if;
  insert into organizations (name) values (p_name) returning id into v_org;
  insert into org_members (org_id, user_id) values (v_org, auth.uid());
  return v_org;
end $$;

-- 차량 페어링 코드(6자리, 24시간) 발급
create or replace function public.issue_pairing_code(p_vehicle uuid)
returns text language plpgsql security definer set search_path = public, extensions as $$
declare v_code text; v_org uuid;
begin
  select org_id into v_org from vehicles where id = p_vehicle;
  if v_org is null or not is_org_member(v_org) then raise exception 'forbidden'; end if;
  loop
    v_code := lpad((floor(random() * 1000000))::int::text, 6, '0');
    exit when not exists (select 1 from vehicles where pairing_code = v_code);
  end loop;
  update vehicles set pairing_code = v_code, pairing_expires_at = now() + interval '24 hours' where id = p_vehicle;
  return v_code;
end $$;

-- ---------- 차량 앱 RPC (anon) ----------
create or replace function public.pair_vehicle(p_code text)
returns json language plpgsql security definer set search_path = public, extensions as $$
declare v vehicles; v_token text; v_org_name text;
begin
  select * into v from vehicles where pairing_code = p_code and pairing_expires_at > now();
  if v.id is null then raise exception 'invalid or expired pairing code'; end if;
  v_token := encode(gen_random_bytes(24), 'hex');
  update vehicles set device_token_hash = encode(digest(v_token, 'sha256'), 'hex'),
    paired_at = now(), pairing_code = null, pairing_expires_at = null where id = v.id;
  select name into v_org_name from organizations where id = v.org_id;
  return json_build_object('vehicle_id', v.id, 'device_token', v_token,
    'plate_no', v.plate_no, 'model', v.model, 'org_name', v_org_name);
end $$;

-- 운행 upsert + 최신 운전자 목록 반환
create or replace function public.vehicle_sync(p_token text, p_trips jsonb default '[]', p_odo_km numeric default null)
returns json language plpgsql security definer set search_path = public, extensions as $$
declare v vehicles; t jsonb; v_driver uuid;
begin
  select * into v from vehicles where device_token_hash = encode(digest(p_token, 'sha256'), 'hex');
  if v.id is null then raise exception 'unknown device'; end if;

  for t in select * from jsonb_array_elements(coalesce(p_trips, '[]'::jsonb)) loop
    v_driver := nullif(t->>'driver_id', '')::uuid;
    if v_driver is not null and not exists (select 1 from drivers where id = v_driver and org_id = v.org_id) then
      v_driver := null;
    end if;
    insert into trips (vehicle_id, local_id, driver_id, driver_name, start_at, end_at, start_odo_km, end_odo_km,
                       purpose, start_addr, end_addr, dest_name, toll_won, note, source, confirmed_at)
    values (v.id, t->>'local_id', v_driver, t->>'driver_name', (t->>'start_at')::timestamptz, (t->>'end_at')::timestamptz,
            (t->>'start_odo_km')::numeric, (t->>'end_odo_km')::numeric,
            coalesce(t->>'purpose', 'UNCONFIRMED')::trip_purpose, t->>'start_addr', t->>'end_addr', t->>'dest_name',
            (t->>'toll_won')::int, t->>'note', coalesce(t->>'source', 'AUTO')::trip_source, (t->>'confirmed_at')::timestamptz)
    on conflict (vehicle_id, local_id) do update set
      driver_id = excluded.driver_id, driver_name = excluded.driver_name, end_at = excluded.end_at, end_odo_km = excluded.end_odo_km,
      purpose = excluded.purpose, start_addr = excluded.start_addr, end_addr = excluded.end_addr,
      dest_name = excluded.dest_name, toll_won = excluded.toll_won, note = excluded.note,
      confirmed_at = excluded.confirmed_at;
  end loop;

  update vehicles set last_seen_at = now(), last_odo_km = coalesce(p_odo_km, last_odo_km) where id = v.id;

  return json_build_object(
    'vehicle', json_build_object('id', v.id, 'plate_no', v.plate_no, 'model', v.model),
    'drivers', coalesce((select json_agg(json_build_object('id', d.id, 'name', d.name, 'dept', d.dept) order by d.name)
                         from drivers d where d.org_id = v.org_id and d.active), '[]'::json));
end $$;

revoke all on function public.pair_vehicle(text) from public;
revoke all on function public.vehicle_sync(text, jsonb, numeric) from public;
grant execute on function public.pair_vehicle(text) to anon, authenticated;
grant execute on function public.vehicle_sync(text, jsonb, numeric) to anon, authenticated;
grant execute on function public.create_organization(text) to authenticated;
grant execute on function public.issue_pairing_code(uuid) to authenticated;
