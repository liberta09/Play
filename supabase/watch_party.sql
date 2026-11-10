-- ============================================================================
-- Play 1.3.0 - Watch Party Supabase Schema & RPCs
-- ============================================================================
--
-- GERİ ALMA / ROLLBACK (Gerektiğinde tüm nesneleri temizlemek için çalıştırılabilir):
-- /*
-- DROP POLICY IF EXISTS "Members can access party messages" ON realtime.messages;
-- DROP FUNCTION IF EXISTS public.wp_update_content(uuid, text, text, integer, integer, integer);
-- DROP FUNCTION IF EXISTS public.wp_leave_room(uuid);
-- DROP FUNCTION IF EXISTS public.wp_join_room(text);
-- DROP FUNCTION IF EXISTS public.wp_create_room(text, text, integer, integer, integer);
-- DROP FUNCTION IF EXISTS public.wp_is_member(uuid);
-- DROP TRIGGER IF EXISTS set_watch_party_updated_at ON public.watch_party_rooms;
-- DROP FUNCTION IF EXISTS public.handle_watch_party_updated_at();
-- DROP TABLE IF EXISTS public.watch_party_members CASCADE;
-- DROP TABLE IF EXISTS public.watch_party_rooms CASCADE;
-- */
-- ============================================================================

-- 1) TABLOLAR

-- Odalar tablosu
create table if not exists public.watch_party_rooms (
    id uuid primary key default gen_random_uuid(),
    code text not null unique constraint check_watch_party_code check (code ~ '^[2-9A-HJ-NP-Z]{6}$'),
    host_user_id uuid not null references auth.users(id) on delete cascade,
    title text not null,
    media_type text not null constraint check_watch_party_media_type check (media_type in ('movie', 'tv')),
    tmdb_id integer not null,
    season integer,
    episode integer,
    is_active boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

-- Üyeler tablosu
create table if not exists public.watch_party_members (
    room_id uuid not null references public.watch_party_rooms(id) on delete cascade,
    user_id uuid not null references auth.users(id) on delete cascade,
    role text not null constraint check_watch_party_role check (role in ('host', 'guest')),
    joined_at timestamptz not null default now(),
    primary key (room_id, user_id)
);

-- İndeksler
create index if not exists idx_wp_rooms_code on public.watch_party_rooms(code);
create index if not exists idx_wp_rooms_host on public.watch_party_rooms(host_user_id);
create index if not exists idx_wp_members_user on public.watch_party_members(user_id);

-- updated_at otomatik tetikleyicisi
create or replace function public.handle_watch_party_updated_at()
returns trigger as $$
begin
    new.updated_at = now();
    return new;
end;
$$ language plpgsql;

drop trigger if exists set_watch_party_updated_at on public.watch_party_rooms;
create trigger set_watch_party_updated_at
    before update on public.watch_party_rooms
    for each row
    execute function public.handle_watch_party_updated_at();

-- 2) YARDIMCI GÜVENLİK İŞLEVİ (RLS İçin)

create or replace function public.wp_is_member(p_room_id uuid)
returns boolean
language sql
security definer
set search_path = public
stable
as $$
    select exists (
        select 1
        from public.watch_party_members
        where room_id = p_room_id
          and user_id = auth.uid()
    );
$$;

-- 3) RLS İLKELERİ

alter table public.watch_party_rooms enable row level security;
alter table public.watch_party_members enable row level security;

-- Odalar: Yalnızca oda üyesi olan kullanıcılar odayı görebilir (oda pasif olsa dahi salt okunur kalır)
drop policy if exists "Members can view room" on public.watch_party_rooms;
create policy "Members can view room"
    on public.watch_party_rooms
    for select
    using (public.wp_is_member(id));

-- Üyeler: Odanın üyeleri diğer üyeleri listeleyebilir
drop policy if exists "Members can view members" on public.watch_party_members;
create policy "Members can view members"
    on public.watch_party_members
    for select
    using (public.wp_is_member(room_id));

-- Doğrudan INSERT / UPDATE / DELETE authenticated veya anon için kısıtlıdır;
-- Tüm veri manipülasyonu security definer saklı yordamlar (RPC) üzerinden yürütülür.

-- 4) SAKLI YORDAMLAR (RPC)

-- wp_create_room: Yeni oda açar, benzersiz 6 haneli kod üretir ve çağıranı 'host' yapar.
create or replace function public.wp_create_room(
    p_title text,
    p_media_type text,
    p_tmdb_id integer,
    p_season integer default null,
    p_episode integer default null
) returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_user_id uuid := auth.uid();
    v_alphabet text := '23456789ABCDEFGHJKLMNPQRSTUVWXYZ';
    v_code text;
    v_room public.watch_party_rooms%rowtype;
    v_attempts integer := 0;
    i integer;
begin
    if v_user_id is null then
        raise exception 'NOT_AUTHENTICATED';
    end if;

    if p_media_type not in ('movie', 'tv') then
        raise exception 'INVALID_MEDIA_TYPE';
    end if;

    loop
        v_attempts := v_attempts + 1;
        v_code := '';
        for i in 1..6 loop
            v_code := v_code || substr(v_alphabet, floor(random() * length(v_alphabet) + 1)::integer, 1);
        end loop;

        exit when not exists (select 1 from public.watch_party_rooms where code = v_code);

        if v_attempts >= 5 then
            raise exception 'ROOM_CODE_COLLISION';
        end if;
    end loop;

    insert into public.watch_party_rooms (
        code, host_user_id, title, media_type, tmdb_id, season, episode, is_active
    ) values (
        v_code, v_user_id, p_title, p_media_type, p_tmdb_id, p_season, p_episode, true
    ) returning * into v_room;

    insert into public.watch_party_members (
        room_id, user_id, role
    ) values (
        v_room.id, v_user_id, 'host'
    );

    return to_jsonb(v_room);
end;
$$;

-- wp_join_room: Odaya kod ile katılır (büyük/küçük harf duyarsız).
create or replace function public.wp_join_room(
    p_code text
) returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_user_id uuid := auth.uid();
    v_clean_code text := upper(trim(p_code));
    v_room public.watch_party_rooms%rowtype;
begin
    if v_user_id is null then
        raise exception 'NOT_AUTHENTICATED';
    end if;

    select * into v_room
    from public.watch_party_rooms
    where code = v_clean_code;

    if not found then
        raise exception 'ROOM_NOT_FOUND';
    end if;

    if not v_room.is_active then
        raise exception 'ROOM_INACTIVE';
    end if;

    insert into public.watch_party_members (room_id, user_id, role)
    values (v_room.id, v_user_id, 'guest')
    on conflict (room_id, user_id) do nothing;

    return to_jsonb(v_room);
end;
$$;

-- wp_leave_room: Host ise odayı kapatır; guest ise üyelikten çıkar.
create or replace function public.wp_leave_room(
    p_room_id uuid
) returns void
language plpgsql
security definer
set search_path = public
as $$
declare
    v_user_id uuid := auth.uid();
    v_role text;
begin
    if v_user_id is null then
        raise exception 'NOT_AUTHENTICATED';
    end if;

    select role into v_role
    from public.watch_party_members
    where room_id = p_room_id and user_id = v_user_id;

    if v_role = 'host' then
        update public.watch_party_rooms
        set is_active = false, updated_at = now()
        where id = p_room_id;
    elsif v_role = 'guest' then
        delete from public.watch_party_members
        where room_id = p_room_id and user_id = v_user_id;
    end if;
end;
$$;

-- wp_update_content: Sadece host çağırabilir; dizi bölümü veya içerik değiştiğinde odayı günceller.
create or replace function public.wp_update_content(
    p_room_id uuid,
    p_title text,
    p_media_type text,
    p_tmdb_id integer,
    p_season integer default null,
    p_episode integer default null
) returns void
language plpgsql
security definer
set search_path = public
as $$
declare
    v_user_id uuid := auth.uid();
    v_is_host boolean;
begin
    if v_user_id is null then
        raise exception 'NOT_AUTHENTICATED';
    end if;

    select exists (
        select 1
        from public.watch_party_rooms
        where id = p_room_id and host_user_id = v_user_id and is_active = true
    ) into v_is_host;

    if not v_is_host then
        raise exception 'NOT_AUTHORIZED';
    end if;

    if p_media_type not in ('movie', 'tv') then
        raise exception 'INVALID_MEDIA_TYPE';
    end if;

    update public.watch_party_rooms
    set title = p_title,
        media_type = p_media_type,
        tmdb_id = p_tmdb_id,
        season = p_season,
        episode = p_episode,
        updated_at = now()
    where id = p_room_id;
end;
$$;

-- 5) REALTIME AUTHORIZATION (Supabase Broadcast)

-- Kullanıcı yalnızca üyesi olduğu aktif odanın konusuna ('wp:' || room_id::text)
-- mesaj gönderebilir ve dinleyebilir.
drop policy if exists "Members can access party messages" on realtime.messages;
create policy "Members can access party messages"
    on realtime.messages
    for all
    to authenticated
    using (
        exists (
            select 1
            from public.watch_party_members m
            join public.watch_party_rooms r on r.id = m.room_id
            where ('wp:' || m.room_id::text) = realtime.messages.topic
              and m.user_id = auth.uid()
              and r.is_active = true
        )
    )
    with check (
        exists (
            select 1
            from public.watch_party_members m
            join public.watch_party_rooms r on r.id = m.room_id
            where ('wp:' || m.room_id::text) = realtime.messages.topic
              and m.user_id = auth.uid()
              and r.is_active = true
        )
    );
