begin;

alter table public.i_users
add column if not exists name text,
add column if not exists email text,
add column if not exists account_created_at timestamptz,
add column if not exists last_sign_in_at timestamptz,
add column if not exists updated_at timestamptz not null default now();

create or replace function public.sync_auth_user_to_i_users()
returns trigger
language plpgsql
security definer
set search_path = public, auth
as $$
begin
  insert into public.i_users (
    id,
    name,
    email,
    account_created_at,
    last_sign_in_at,
    updated_at
  )
  values (
    new.id,
    coalesce(
      nullif(new.raw_user_meta_data ->> 'name', ''),
      nullif(new.raw_user_meta_data ->> 'full_name', '')
    ),
    new.email,
    new.created_at,
    new.last_sign_in_at,
    now()
  )
  on conflict (id) do update
  set
    name = excluded.name,
    email = excluded.email,
    account_created_at = excluded.account_created_at,
    last_sign_in_at = excluded.last_sign_in_at,
    updated_at = now();

  return new;
end;
$$;

drop trigger if exists on_auth_user_changed_sync_i_users on auth.users;

create trigger on_auth_user_changed_sync_i_users
after insert or update of email, raw_user_meta_data, last_sign_in_at
on auth.users
for each row
execute function public.sync_auth_user_to_i_users();

insert into public.i_users (
  id,
  name,
  email,
  account_created_at,
  last_sign_in_at,
  updated_at
)
select
  u.id,
  coalesce(
    nullif(u.raw_user_meta_data ->> 'name', ''),
    nullif(u.raw_user_meta_data ->> 'full_name', '')
  ) as name,
  u.email,
  u.created_at as account_created_at,
  u.last_sign_in_at,
  now()
from auth.users u
on conflict (id) do update
set
  name = excluded.name,
  email = excluded.email,
  account_created_at = excluded.account_created_at,
  last_sign_in_at = excluded.last_sign_in_at,
  updated_at = now();

commit;
