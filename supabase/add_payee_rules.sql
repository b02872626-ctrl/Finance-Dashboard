-- =============================================================================
-- Per-person rules (web dashboard, Habits tab). Widens i_review_decisions.kind
-- again; no new table, same owner-only policies.
--   'payee_rule'  — ref = the payee's key (first word + 3 letters of the second)
--                   decision 'yes', detail = the rule:
--                     { "type": "mine", "n": 1, "usual": 1300 }
--                       only n payments a month are the user's own (the n
--                       closest to usual); the rest is "paid for others"
--                     { "type": "loan", "above": 1000 }
--                       this much or more either way is a loan, less a bill share
--                   decision 'no' = the user turned down Kash's suggestion.
--                   Removing the row takes the rule away.
-- Safe to run more than once. Runs after add_payee_kinds.sql.
-- =============================================================================

alter table public.i_review_decisions
  drop constraint if exists i_review_decisions_kind_check;

alter table public.i_review_decisions
  add constraint i_review_decisions_kind_check
  check (kind in ('pass_through', 'salary', 'payee_kind', 'ai_consent', 'payee_rule'));
