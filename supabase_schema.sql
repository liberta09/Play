-- Play Cloud State Schema for Supabase
-- Tablo: play_cloud_state (Profiller, Favoriler ve İzleme Geçmişi JSON transfer yükü)

create table if not exists public.play_cloud_state (
    user_id uuid primary key references auth.users(id) on delete cascade,
    payload jsonb not null default '{}'::jsonb,
    updated_at timestamptz not null default timezone('utc'::text, now())
);

-- RLS (Row Level Security) - Kullanıcılar yalnızca kendi verilerini okuyup yazabilir
alter table public.play_cloud_state enable row level security;

create policy "Users can view own cloud state"
    on public.play_cloud_state
    for select
    using (auth.uid() = user_id);

create policy "Users can insert own cloud state"
    on public.play_cloud_state
    for insert
    with check (auth.uid() = user_id);

create policy "Users can update own cloud state"
    on public.play_cloud_state
    for update
    using (auth.uid() = user_id);

-- Otomatik updated_at güncelleme tetikleyicisi
create or replace function public.handle_play_cloud_updated_at()
returns trigger as $$
begin
    new.updated_at = timezone('utc'::text, now());
    return new;
end;
$$ language plpgsql;

create trigger set_play_cloud_updated_at
    before update on public.play_cloud_state
    for each row
    execute function public.handle_play_cloud_updated_at();
