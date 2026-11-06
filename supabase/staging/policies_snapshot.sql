-- Read-only. Run in the SQL editor of BOTH the live and the staging project
-- and compare the two results: staging is only a fair test if they match.
-- Lists every table's RLS switch, every policy, every view's security mode
-- and every security definer function in the public schema.
select 'rls' as kind, c.relname as name, c.relrowsecurity::text as detail
from pg_class c join pg_namespace n on n.oid = c.relnamespace
where n.nspname = 'public' and c.relkind = 'r'
union all
select 'policy', tablename || '.' || policyname,
       cmd || ' ' || array_to_string(roles, ',') || ' using(' || coalesce(qual, '') ||
       ') check(' || coalesce(with_check, '') || ')'
from pg_policies where schemaname = 'public'
union all
select 'view', c.relname, coalesce(array_to_string(c.reloptions, ','), 'security_invoker off')
from pg_class c join pg_namespace n on n.oid = c.relnamespace
where n.nspname = 'public' and c.relkind = 'v'
union all
select 'definer', p.proname, md5(pg_get_functiondef(p.oid))
from pg_proc p join pg_namespace n on n.oid = p.pronamespace
where n.nspname = 'public' and p.prosecdef
order by 1, 2;
