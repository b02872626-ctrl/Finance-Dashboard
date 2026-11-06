#!/usr/bin/env python3
"""Cross-user isolation test for the STAGING Supabase project.

Proves that one Kash user can never read, change, delete or create another
user's rows, that admin-only data stays hidden from normal users and signed-out
visitors, and that deleting an account deletes only that account.

Each run signs up two throwaway users (A and B), gives both a row in every
per-user table, then has B (and a signed-out visitor) attack A's rows. Both
accounts are deleted at the end.

Settings, from the environment or local.properties:
  STAGING_SUPABASE_URL       / staging.supabase.url
  STAGING_SUPABASE_ANON_KEY  / staging.supabase.anonKey
Exits 0 with "skipped" when they are missing. Refuses to run against the live
project (supabase.url in local.properties). Staging needs "Confirm email" OFF.

Run:  python3 supabase/staging/isolation_test.py
Standard library only. See supabase/staging/README.md.
"""
import base64
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))

BANK = "Kash Isolation Test Bank"
TAMPERED = "tampered-by-other-user"
FORGED = "forged-by-other-user"
RUN = uuid.uuid4().hex[:8]
MARK_A = "isolation-%s-a" % RUN
MARK_B = "isolation-%s-b" % RUN

ADMIN_ONLY_TABLES = ["i_account_deletions", "i_downloads", "i_installs"]
ADMIN_VIEWS = [
    "admin_raw_sms", "admin_unparsed_sms", "admin_parse_rate_7d",
    "admin_capture_only_senders", "admin_money_senders", "admin_txn_quality",
    "admin_prediction_accuracy", "admin_category_hotspots",
    "admin_categorization_summary", "admin_user_health",
]


def T(table, tamper, row, owner="user_id"):
    return {"table": table, "tamper": tamper, "row": row, "owner": owner}


# Every table a user owns rows in, parents before children. row(owner_id,
# marker, refs) builds a row owned by owner_id; refs holds that user's parent
# row ids. row=None: the row already exists (i_users is filled by a trigger).
TABLES = [
    T("i_users", "name", None, owner="id"),
    T("i_transactions", "category", lambda o, m, r: {
        "user_id": o, "id": 1, "bank_name": BANK, "occurred_at": "2026-11-05T10:00:00Z",
        "sender": "CBE", "type": "DEBIT", "amount": 1, "raw_body": m}),
    T("i_raw_data", "sender_name", lambda o, m, r: {
        "user_id": o, "bank_name": BANK, "sender_name": "CBE", "raw_message": m}),
    T("i_sessions", "user_agent", lambda o, m, r: {
        "user_id": o, "client": "android", "user_agent": m}),
    T("i_events", "name", lambda o, m, r: {
        "user_id": o, "name": "isolation_test", "props": {"m": m}}),
    T("i_device_status", "app_version", lambda o, m, r: {
        "user_id": o, "app_version": m}),
    T("i_feedback", "client", lambda o, m, r: {
        "user_id": o, "client": "web", "answers": {"m": m}}),
    T("i_category_rules", "category", lambda o, m, r: {
        "user_id": o, "merchant_key": m, "category": "Food"}),
    T("i_budgets", "name", lambda o, m, r: {
        "user_id": o, "name": m, "fund_amount": 100, "cadence": "MONTHLY",
        "funding_date": "2026-11-01"}),
    T("i_budget_categories", "category", lambda o, m, r: {
        "budget_id": r["i_budgets"], "user_id": o, "category": m}),
    T("i_goals", "name", lambda o, m, r: {
        "user_id": o, "name": m, "type": "OPEN_ENDED"}),
    T("i_goal_contributions", "note", lambda o, m, r: {
        "user_id": o, "goal_id": r["i_goals"], "amount": 1, "note": m}),
    T("i_income_entries", "source", lambda o, m, r: {
        "user_id": o, "type": "ONEOFF", "source": m, "amount": 1}),
    T("i_income_dismissed", "counterparty_match", lambda o, m, r: {
        "user_id": o, "counterparty_match": m}),
    T("i_ledger_entries", "counterparty", lambda o, m, r: {
        "user_id": o, "type": "IOU", "direction": "I_OWE", "counterparty": m,
        "principal": 1, "balance": 1}),
    T("i_ledger_repayments", "note", lambda o, m, r: {
        "entry_id": r["i_ledger_entries"], "user_id": o, "amount": 1, "note": m}),
]


def local_properties():
    props = {}
    path = os.path.join(ROOT, "local.properties")
    if os.path.exists(path):
        with open(path) as f:
            for line in f:
                line = line.strip()
                if line and not line.startswith("#") and "=" in line:
                    k, v = line.split("=", 1)
                    props[k.strip()] = v.strip().replace("\\:", ":").replace("\\", "")
    return props


PROPS = local_properties()
URL = (os.environ.get("STAGING_SUPABASE_URL") or PROPS.get("staging.supabase.url", "")).strip().rstrip("/")
KEY = (os.environ.get("STAGING_SUPABASE_ANON_KEY") or PROPS.get("staging.supabase.anonKey", "")).strip()
LIVE = PROPS.get("supabase.url", "").strip().rstrip("/")


class User(object):
    def __init__(self, label, uid, token):
        self.label, self.id, self.token, self.deleted = label, uid, token, False


def call(method, path, user=None, body=None, prefer="return=representation"):
    req = urllib.request.Request(URL + path, method=method)
    req.add_header("apikey", KEY)
    if user:  # signed out: the key alone (works for legacy anon and new publishable keys)
        req.add_header("Authorization", "Bearer " + user.token)
    req.add_header("Content-Type", "application/json")
    req.add_header("Prefer", prefer)
    data = json.dumps(body).encode() if body is not None else None
    # Retry only dropped connections; any HTTP answer, refusals included, counts.
    for attempt in range(3):
        try:
            with urllib.request.urlopen(req, data, timeout=30) as res:
                return res.status, res.read().decode()
        except urllib.error.HTTPError as e:
            return e.code, e.read().decode()
        except (urllib.error.URLError, OSError):
            if attempt == 2:
                raise
            time.sleep(2 * (attempt + 1))


def q(s):
    return urllib.parse.quote(s, safe="")


def is_empty(code, text):
    return 200 <= code < 300 and text.strip() == "[]"


def refused_or_no_rows(code, text):
    return 400 <= code < 500 or is_empty(code, text)


def sign_up(label):
    email = "kash-isolation-%s-%s@example.com" % (RUN, label)
    code, text = call("POST", "/auth/v1/signup", body={
        "email": email, "password": uuid.uuid4().hex + "Aa1!"})
    token = (json.loads(text) if text.startswith("{") else {}).get("access_token")
    if not token:
        sys.exit("Sign-up of test user %s returned no session (HTTP %s). Turn "
                 "Confirm email OFF on the staging project. %s" % (label, code, text[:200]))
    part = token.split(".")[1]
    claims = json.loads(base64.urlsafe_b64decode(part + "=" * (-len(part) % 4)))
    return User(label, claims["sub"], token)


def insert_rows(user, marker):
    refs = {}
    for t in TABLES:
        if t["row"] is None:
            continue
        code, text = call("POST", "/rest/v1/" + t["table"], user, t["row"](user.id, marker, refs))
        if not 200 <= code < 300:
            sys.exit("User %s could not add their own %s row (HTTP %s): %s"
                     % (user.label, t["table"], code, text[:300]))
        rows = json.loads(text)
        if rows and "id" in rows[0]:
            refs[t["table"]] = rows[0]["id"]
    return refs


def main():
    if not URL or not KEY:
        print("skipped: staging Supabase is not configured (see supabase/staging/README.md)")
        return 0
    if LIVE and URL.lower() == LIVE.lower():
        sys.exit("staging.supabase.url points at the LIVE project; refusing to run")
    if not URL.startswith("https://"):
        sys.exit("staging.supabase.url must start with https://")

    failures = []
    passed = [0]

    def expect(ok, message):
        if ok:
            passed[0] += 1
        else:
            failures.append(message)

    a, b = sign_up("a"), sign_up("b")
    try:
        code, text = call("POST", "/rest/v1/i_banks?on_conflict=bank_name", a, {"bank_name": BANK},
                          prefer="resolution=ignore-duplicates,return=minimal")
        if not 200 <= code < 300:
            sys.exit("Could not add the test bank (HTTP %s): %s" % (code, text))
        insert_rows(a, MARK_A)
        refs_b = insert_rows(b, MARK_B)

        for t in TABLES:
            name, own = t["table"], t["owner"]
            of_a = "/rest/v1/%s?%s=eq.%s" % (name, own, a.id)
            of_b = "/rest/v1/%s?%s=eq.%s" % (name, own, b.id)

            def a_rows():
                return call("GET", of_a + "&select=*", a)[1]

            # 1. B can't read A's rows, by filter or by listing everything.
            code, text = call("GET", of_a + "&select=*", b)
            expect(is_empty(code, text), "%s: B read A's rows (HTTP %s): %s" % (name, code, text[:200]))
            code, text = call("GET", "/rest/v1/%s?select=*" % name, b)
            expect(200 <= code < 300 and a.id not in text and MARK_A not in text,
                   "%s: B's full listing shows A's data (HTTP %s): %s" % (name, code, text[:200]))

            # 2. Signed-out visitors see nothing.
            code, text = call("GET", "/rest/v1/%s?select=*" % name)
            expect(is_empty(code, text), "%s: signed-out visitor read rows (HTTP %s): %s" % (name, code, text[:200]))

            # 3. B can't change A's rows.
            code, text = call("PATCH", of_a, b, {t["tamper"]: TAMPERED})
            expect(refused_or_no_rows(code, text), "%s: B's change to A's rows went through (HTTP %s): %s" % (name, code, text[:200]))
            expect(TAMPERED not in a_rows(), "%s: A's row was changed by B" % name)

            # 4. B can't delete A's rows.
            code, text = call("DELETE", of_a, b)
            expect(refused_or_no_rows(code, text), "%s: B's delete of A's rows went through (HTTP %s): %s" % (name, code, text[:200]))
            expect(a_rows().strip() != "[]", "%s: A's row is gone after B's delete" % name)

            # 5. B can't create a row in A's name.
            forged = t["row"](a.id, FORGED, refs_b) if t["row"] else {"id": a.id, "name": FORGED}
            code, text = call("POST", "/rest/v1/" + name, b, forged)
            expect(400 <= code < 500, "%s: B created a row owned by A (HTTP %s): %s" % (name, code, text[:200]))
            expect(FORGED not in a_rows(), "%s: A now has B's forged row" % name)

            # 6. B can't hand its own row over to A.
            code, text = call("PATCH", of_b, b, {own: a.id})
            expect(refused_or_no_rows(code, text), "%s: B moved its row to A (HTTP %s): %s" % (name, code, text[:200]))
            expect(MARK_B not in a_rows(), "%s: A now owns B's row" % name)

        # 7. Admin-only tables and views.
        for name in ADMIN_ONLY_TABLES:
            for who, user in (("B", b), ("signed-out visitor", None)):
                code, text = call("GET", "/rest/v1/%s?select=*" % name, user)
                expect(refused_or_no_rows(code, text), "%s: %s can read it (HTTP %s): %s" % (name, who, code, text[:200]))
        for name in ADMIN_VIEWS:
            code, text = call("GET", "/rest/v1/%s?select=*" % name, b)
            expect(200 <= code < 300 and a.id not in text and MARK_A not in text,
                   "%s: B sees A's data or the view failed (HTTP %s): %s" % (name, code, text[:300]))
        code, text = call("POST", "/rest/v1/rpc/is_admin", b, {})
        expect(text.strip() == "false", "is_admin() is not false for a normal user: %s" % text)

        # 8. The shared bank list can't be renamed or deleted.
        bank = "/rest/v1/i_banks?bank_name=eq." + q(BANK)
        code, text = call("PATCH", bank, b, {"bank_name": TAMPERED})
        expect(400 <= code < 500, "i_banks: a user renamed a shared bank (HTTP %s): %s" % (code, text[:200]))
        code, text = call("DELETE", bank, b)
        expect(refused_or_no_rows(code, text), "i_banks: a user deleted a shared bank (HTTP %s): %s" % (code, text[:200]))
        expect(BANK in call("GET", bank + "&select=bank_name", a)[1], "i_banks: the shared bank is gone")

        # 9. Signed-out visitors can't delete accounts; deleting A leaves B alone.
        code, text = call("POST", "/rest/v1/rpc/delete_my_account", None, {})
        expect(400 <= code < 500, "delete_my_account ran for a signed-out visitor (HTTP %s)" % code)
        code, text = call("POST", "/rest/v1/rpc/delete_my_account", a, {})
        expect(200 <= code < 300, "delete_my_account failed for A (HTTP %s): %s" % (code, text[:200]))
        a.deleted = 200 <= code < 300
        for t in TABLES:
            text = call("GET", "/rest/v1/%s?%s=eq.%s&select=*" % (t["table"], t["owner"], b.id), b)[1]
            expect(text.strip() not in ("", "[]"), "%s: B's row vanished when A deleted their account" % t["table"])
    finally:
        for user in (a, b):
            if not user.deleted:
                call("POST", "/rest/v1/rpc/delete_my_account", user, {})

    print("%d checks passed, %d failed (run %s)" % (passed[0], len(failures), RUN))
    for f in failures:
        print("FAIL " + f)
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
