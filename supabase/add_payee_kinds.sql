-- =============================================================================
-- Payee kinds and the Gemini opt-in (web dashboard, Habits tab).
-- Widens i_review_decisions.kind; no new table, same owner-only policies.
--   'payee_kind'  — the user put a payee in a kind ("Where your money goes")
--                   ref = the payee's key (first word + 3 letters of the second)
--                   decision 'yes'; detail = { "kind": "people" | "shops" | ...,
--                   "from": "you" | "gemini" }. Removing the row undoes it.
--   'ai_consent'  — ref = 'gemini'; decision 'yes' lets the kash-gemini Edge
--                   Function send this user's payee names to Gemini; 'no' or no
--                   row means it refuses. Only the user can set it (RLS).
-- Safe to run more than once.
-- =============================================================================

alter table public.i_review_decisions
  drop constraint if exists i_review_decisions_kind_check;

alter table public.i_review_decisions
  add constraint i_review_decisions_kind_check
  check (kind in ('pass_through', 'salary', 'payee_kind', 'ai_consent'));
