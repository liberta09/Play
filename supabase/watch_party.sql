-- ============================================================================
-- Play Watch Party — Supabase şeması (1.3.0)
-- Gözden geçiren: Claude, 2026-11-11. Tekrar çalıştırılabilir (idempotent).
--
-- ÇALIŞTIRMADAN ÖNCE (Supabase paneli):
--   Project Settings > Realtime > "Allow public access" KAPALI olsun
--   (yalnızca private kanallar). Play başka yerde Realtime kullanmıyor.
--
-- Ne yapar:
--   * 2 tablo: watch_party_rooms, watch_party_members (RLS açık).
--   * İstemci tablolara doğrudan YAZAMAZ; yalnızca aşağıdaki RPC'ler yazar.
--   * Bir kullanıcı yalnızca ÜYESİ olduğu açık odayı ve üyelerini görür.
--   * Realtime kanalı 'wp:<oda-id>': yalnızca üyeler dinler/gönderir;
--     'control', 'content', 'closed' olaylarını YALNIZCA oda sahibi gönderir.
--   * Oda 12 saat açık kalır, en çok 8 kişi.
-- Mevcut tablolara (play_cloud_state vb.) DOKUNMAZ.
-- ============================================================================

create extension if not exists pgcrypto with schema extensions;

-- ---------------------------------------------------------------------------
-- Tablolar
-- ---------------------------------------------------------------------------
create table if not exists public.watch_party_rooms (
  id            uuid primary key default gen_random_uuid(),
  code          text not null unique check (code ~ '^[A-HJ-NP-Z2-9]{6}$'),
  host_user_id  uuid not null references auth.users(id) on delete cascade,
  tmdb_id       integer not null check (tmdb_id > 0),
  media_type    text not null check (media_type in ('movie', 'tv')),
  season        integer check (season is null or season >= 0),
  episode       integer check (episode is null or episode > 0),
  title         text not null check (char_length(title) between 1 and 80),
  created_at    timestamptz not null default now(),
  expires_at    timestamptz not null default now() + interval '12 hours',
  closed_at     timestamptz
);
create index if not exists watch_party_rooms_host_idx on public.watch_party_rooms (host_user_id);

create table if not exists public.watch_party_members (
  room_id       uuid not null references public.watch_party_rooms(id) on delete cascade,
  user_id       uuid not null references auth.users(id) on delete cascade,
  display_name  text not null check (char_length(display_name) between 1 and 40),
  role          text not null check (role in ('host', 'guest')),
  joined_at     timestamptz not null default now(),
  primary key (room_id, user_id)
);
create index if not exists watch_party_members_user_idx on public.watch_party_members (user_id);

alter table public.watch_party_rooms   enable row level security;
alter table public.watch_party_members enable row level security;

revoke all on public.watch_party_rooms   from anon, authenticated;
revoke all on public.watch_party_members from anon, authenticated;
grant select on public.watch_party_rooms   to authenticated;
grant select on public.watch_party_members to authenticated;

-- ---------------------------------------------------------------------------
-- Yardımcılar (SECURITY DEFINER: politika içinden çağrılınca özyineleme olmaz)
-- ---------------------------------------------------------------------------
create or replace function public.wp_topic_room(p_topic text)
returns uuid
language sql
immutable
set search_path = public
as $$
  select case
    when p_topic ~ '^wp:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
      then substr(p_topic, 4)::uuid
    else null
  end;
$$;

create or replace function public.wp_is_open_member(p_room uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select p_room is not null and exists (
    select 1
    from public.watch_party_members m
    join public.watch_party_rooms r on r.id = m.room_id
    where m.room_id = p_room
      and m.user_id = auth.uid()
      and r.closed_at is null
      and r.expires_at > now()
  );
$$;

create or replace function public.wp_is_open_host(p_room uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select p_room is not null and exists (
    select 1
    from public.watch_party_rooms r
    where r.id = p_room
      and r.host_user_id = auth.uid()
      and r.closed_at is null
      and r.expires_at > now()
  );
$$;

-- ---------------------------------------------------------------------------
-- Okuma politikaları (yalnızca üyesi olduğun açık oda)
-- ---------------------------------------------------------------------------
drop policy if exists wp_rooms_select   on public.watch_party_rooms;
create policy wp_rooms_select on public.watch_party_rooms
  for select to authenticated
  using (public.wp_is_open_member(id));

drop policy if exists wp_members_select on public.watch_party_members;
create policy wp_members_select on public.watch_party_members
  for select to authenticated
  using (public.wp_is_open_member(room_id));

-- ---------------------------------------------------------------------------
-- RPC: oda kur
-- ---------------------------------------------------------------------------
create or replace function public.wp_create_room(
  p_tmdb_id      integer,
  p_media_type   text,
  p_season       integer,
  p_episode      integer,
  p_title        text,
  p_display_name text
)
returns table (room_id uuid, code text)
language plpgsql
security definer
set search_path = public, extensions
as $$
#variable_conflict use_column
declare
  v_uid    uuid := auth.uid();
  v_alpha  constant text := '23456789ABCDEFGHJKLMNPQRSTUVWXYZ';  -- 32 harf, 0/O/1/I yok
  v_title  text := left(btrim(coalesce(p_title, '')), 80);
  v_name   text := left(btrim(coalesce(p_display_name, '')), 40);
  v_code   text;
  v_bytes  bytea;
  v_id     uuid;
  v_try    integer := 0;
  i        integer;
begin
  if v_uid is null then
    raise exception 'not_authenticated' using errcode = '28000';
  end if;
  if p_tmdb_id is null or p_tmdb_id <= 0
     or p_media_type not in ('movie', 'tv')
     or (p_media_type = 'tv' and (p_season is null or p_season < 0 or p_episode is null or p_episode <= 0))
     or v_title = '' or v_name = '' then
    raise exception 'invalid_input' using errcode = '22023';
  end if;

  -- Eski kayıtları temizle (1 günden eski kapalı/süresi dolmuş odalar).
  delete from public.watch_party_rooms
  where (closed_at is not null and closed_at < now() - interval '1 day')
     or expires_at < now() - interval '1 day';

  -- Kullanıcının açık odası varsa kapat (aynı anda tek oda).
  update public.watch_party_rooms
  set closed_at = now()
  where host_user_id = v_uid and closed_at is null;

  loop
    v_try := v_try + 1;
    if v_try > 10 then
      raise exception 'code_generation_failed' using errcode = 'P0001';
    end if;
    v_bytes := extensions.gen_random_bytes(6);
    v_code := '';
    for i in 0..5 loop
      v_code := v_code || substr(v_alpha, (get_byte(v_bytes, i) % 32) + 1, 1);
    end loop;
    begin
      insert into public.watch_party_rooms (code, host_user_id, tmdb_id, media_type, season, episode, title)
      values (
        v_code, v_uid, p_tmdb_id, p_media_type,
        case when p_media_type = 'tv' then p_season end,
        case when p_media_type = 'tv' then p_episode end,
        v_title
      )
      returning id into v_id;
      exit;
    exception when unique_violation then
      -- kod çakıştı, yeniden üret
    end;
  end loop;

  insert into public.watch_party_members (room_id, user_id, display_name, role)
  values (v_id, v_uid, v_name, 'host');

  return query select v_id, v_code;
end;
$$;

-- ---------------------------------------------------------------------------
-- RPC: koda katıl
-- ---------------------------------------------------------------------------
create or replace function public.wp_join_room(p_code text, p_display_name text)
returns table (
  room_id      uuid,
  code         text,
  tmdb_id      integer,
  media_type   text,
  season       integer,
  episode      integer,
  title        text,
  expires_at   timestamptz,
  role         text
)
language plpgsql
security definer
set search_path = public
as $$
#variable_conflict use_column
declare
  v_uid   uuid := auth.uid();
  v_code  text := upper(btrim(coalesce(p_code, '')));
  v_name  text := left(btrim(coalesce(p_display_name, '')), 40);
  v_room  public.watch_party_rooms%rowtype;
  v_role  text;
  v_count integer;
begin
  if v_uid is null then
    raise exception 'not_authenticated' using errcode = '28000';
  end if;
  if v_code !~ '^[A-HJ-NP-Z2-9]{6}$' or v_name = '' then
    raise exception 'invalid_input' using errcode = '22023';
  end if;

  select * into v_room
  from public.watch_party_rooms r
  where r.code = v_code and r.closed_at is null and r.expires_at > now()
  for update;
  if not found then
    raise exception 'room_not_found' using errcode = 'P0002';
  end if;

  v_role := case when v_room.host_user_id = v_uid then 'host' else 'guest' end;

  if not exists (
    select 1 from public.watch_party_members m
    where m.room_id = v_room.id and m.user_id = v_uid
  ) then
    select count(*) into v_count from public.watch_party_members m where m.room_id = v_room.id;
    if v_count >= 8 then
      raise exception 'room_full' using errcode = 'P0001';
    end if;
  end if;

  insert into public.watch_party_members (room_id, user_id, display_name, role)
  values (v_room.id, v_uid, v_name, v_role)
  on conflict (room_id, user_id) do update set display_name = excluded.display_name;

  return query select v_room.id, v_room.code, v_room.tmdb_id, v_room.media_type,
                      v_room.season, v_room.episode, v_room.title, v_room.expires_at, v_role;
end;
$$;

-- ---------------------------------------------------------------------------
-- RPC: odadan ayrıl (oda sahibi ayrılırsa oda kapanır)
-- ---------------------------------------------------------------------------
create or replace function public.wp_leave_room(p_room_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
begin
  if v_uid is null then
    raise exception 'not_authenticated' using errcode = '28000';
  end if;
  update public.watch_party_rooms
  set closed_at = now()
  where id = p_room_id and host_user_id = v_uid and closed_at is null;

  delete from public.watch_party_members
  where room_id = p_room_id and user_id = v_uid;
end;
$$;

-- ---------------------------------------------------------------------------
-- RPC: oda içeriğini güncelle (yalnızca oda sahibi; ör. sonraki bölüm)
-- ---------------------------------------------------------------------------
create or replace function public.wp_update_content(
  p_room_id    uuid,
  p_tmdb_id    integer,
  p_media_type text,
  p_season     integer,
  p_episode    integer,
  p_title      text
)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid   uuid := auth.uid();
  v_title text := left(btrim(coalesce(p_title, '')), 80);
begin
  if v_uid is null then
    raise exception 'not_authenticated' using errcode = '28000';
  end if;
  if p_tmdb_id is null or p_tmdb_id <= 0
     or p_media_type not in ('movie', 'tv')
     or (p_media_type = 'tv' and (p_season is null or p_season < 0 or p_episode is null or p_episode <= 0))
     or v_title = '' then
    raise exception 'invalid_input' using errcode = '22023';
  end if;
  update public.watch_party_rooms
  set tmdb_id = p_tmdb_id,
      media_type = p_media_type,
      season = case when p_media_type = 'tv' then p_season end,
      episode = case when p_media_type = 'tv' then p_episode end,
      title = v_title
  where id = p_room_id and host_user_id = v_uid and closed_at is null and expires_at > now();
  if not found then
    raise exception 'not_host_or_closed' using errcode = '42501';
  end if;
end;
$$;

-- ---------------------------------------------------------------------------
-- Yetkiler: yalnızca giriş yapmış kullanıcı çağırabilir
-- ---------------------------------------------------------------------------
revoke all on function public.wp_topic_room(text)          from public, anon;
revoke all on function public.wp_is_open_member(uuid)      from public, anon;
revoke all on function public.wp_is_open_host(uuid)        from public, anon;
revoke all on function public.wp_create_room(integer, text, integer, integer, text, text) from public, anon;
revoke all on function public.wp_join_room(text, text)     from public, anon;
revoke all on function public.wp_leave_room(uuid)          from public, anon;
revoke all on function public.wp_update_content(uuid, integer, text, integer, integer, text) from public, anon;

grant execute on function public.wp_topic_room(text)       to authenticated;
grant execute on function public.wp_is_open_member(uuid)   to authenticated;
grant execute on function public.wp_is_open_host(uuid)     to authenticated;
grant execute on function public.wp_create_room(integer, text, integer, integer, text, text) to authenticated;
grant execute on function public.wp_join_room(text, text)  to authenticated;
grant execute on function public.wp_leave_room(uuid)       to authenticated;
grant execute on function public.wp_update_content(uuid, integer, text, integer, integer, text) to authenticated;

-- ---------------------------------------------------------------------------
-- Realtime yetkilendirme (private kanal 'wp:<oda-id>')
-- ---------------------------------------------------------------------------
drop policy if exists wp_realtime_read  on realtime.messages;
create policy wp_realtime_read on realtime.messages
  for select to authenticated
  using (
    realtime.messages.extension in ('broadcast', 'presence')
    and public.wp_is_open_member(public.wp_topic_room((select realtime.topic())))
  );

drop policy if exists wp_realtime_write on realtime.messages;
create policy wp_realtime_write on realtime.messages
  for insert to authenticated
  with check (
    realtime.messages.extension in ('broadcast', 'presence')
    and public.wp_is_open_member(public.wp_topic_room((select realtime.topic())))
    and (
      realtime.messages.extension = 'presence'
      or coalesce(realtime.messages.event, '') not in ('control', 'content', 'closed')
      or public.wp_is_open_host(public.wp_topic_room((select realtime.topic())))
    )
  );

-- ============================================================================
-- GERİ ALMA (gerekirse bu bloğu ayrı çalıştır)
-- ============================================================================
/*
drop policy if exists wp_realtime_write on realtime.messages;
drop policy if exists wp_realtime_read  on realtime.messages;
drop function if exists public.wp_update_content(uuid, integer, text, integer, integer, text);
drop function if exists public.wp_leave_room(uuid);
drop function if exists public.wp_join_room(text, text);
drop function if exists public.wp_create_room(integer, text, integer, integer, text, text);
drop table if exists public.watch_party_members;
drop table if exists public.watch_party_rooms;
drop function if exists public.wp_is_open_host(uuid);
drop function if exists public.wp_is_open_member(uuid);
drop function if exists public.wp_topic_room(text);
*/
