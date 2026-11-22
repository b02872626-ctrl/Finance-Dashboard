-- =============================================================================
-- Commitments and split payments (web dashboard, Habits tab). Widens
-- i_review_decisions.kind again; no new table, same owner-only policies.
--   'commitment' — something paid off in parts to one person
--                  ref = a short id ("laptop"), decision 'yes',
--                  detail = { "name": "Laptop", "key": payee key, "total": 150000 | null }
--   'tx_split'   — what one payment was, in up to 4 parts that add up to it
--                  ref = the transaction's id (i_transactions.id), decision 'yes',
--                  detail = { "parts": [ { "what": "instalment", "amount": 20000,
--                             "commitment": "laptop" }, { "what": "loan", "amount": 18000 } ] }
--                  what: instalment | normal | loan | refund | passed
-- Removing a row undoes it. Safe to run more than once. Runs after
-- add_payee_rules.sql.
-- =============================================================================

alter table public.i_review_decisions
  drop constraint if exists i_review_decisions_kind_check;

alter table public.i_review_decisions
  add constraint i_review_decisions_kind_check
  check (kind in ('pass_through', 'salary', 'payee_kind', 'ai_consent', 'payee_rule', 'commitment', 'tx_split'));
