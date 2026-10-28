// =============================================================================
// Kash — Admin dashboard.
//
// Four questions: is the parser working, is categorization good enough, do
// people stick, and are the phones healthy. Every number comes from the
// admin_* views and tables in supabase/admin_health_metrics.sql.
//
// The admin gate is enforced on TWO layers:
//   1. Client-side allowlist (ADMIN_EMAIL) — controls what UI is rendered.
//   2. Postgres RLS policies (see supabase/add_admin_sessions.sql) — every
//      SELECT against the i_* tables (and so the security_invoker admin_*
//      views) requires either user_id = auth.uid() OR is_admin(). If the JWT email
//      isn't on the allowlist, the queries return zero rows even if a
//      malicious client edits ADMIN_EMAIL.
// =============================================================================

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const SUPABASE_URL  = "https://sadbxjnmcgzwtjbqqhbk.supabase.co";
const SUPABASE_ANON = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InNhZGJ4am5tY2d6d3RqYnFxaGJrIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzQ2Mzg1NzcsImV4cCI6MjA5MDIxNDU3N30.aXA5I5CD--DjpnsbrHPZLebAmITNSDVmYDo1mkj3Gz4";

const ADMIN_EMAILS = [
  "aklogteferada@gmail.com",
  "nahusenaygebreamlak@gmail.com",
];

// The admin keeps its own sign-in. With the default storage the page shared
// the main web app's localStorage token (same origin), so anyone signed in to
// Kash on that browser — the admin included — landed straight on the data
// without a password prompt. Here the token lives in sessionStorage under its
// own key: it dies with the tab and never leaks in from (or out to) the app.
const supabase = createClient(SUPABASE_URL, SUPABASE_ANON, {
  auth: {
    persistSession: true,
    autoRefreshToken: true,
    storageKey: "kash-admin-auth",
    storage: window.sessionStorage,
  },
});

// Sign out after this long without input.
const IDLE_LOCK_MS = 15 * 60 * 1000;

// ----------------------------------------------------------------------------
// DOM
// ----------------------------------------------------------------------------
const $ = (id) => document.getElementById(id);

const loginView   = $("login-view");
const deniedView  = $("denied-view");
const adminShell  = $("admin-shell");

const loginForm      = $("login-form");
const loginEmail     = $("login-email");
const loginPassword  = $("login-password");
const loginBtn       = $("login-btn");
const loginError     = $("login-error");
const deniedEmail    = $("denied-email");
const deniedLogout   = $("denied-logout-btn");
const adminEmail     = $("admin-email");
const refreshBtn     = $("refresh-btn");
const logoutBtn      = $("logout-btn");

const parseTable    = $("parse-table");
const unparsedList  = $("unparsed-list");
const captureTable  = $("capture-table");
const accuracyTable = $("accuracy-table");
const hotspotTable  = $("hotspot-table");
const funnelEl      = $("funnel");
const peopleList    = $("people-list");
const cohortTable   = $("cohort-table");
const deviceTable   = $("device-table");
const errorList     = $("error-list");
const deletionList  = $("deletion-list");

const RENDER_TARGETS = [
  parseTable, unparsedList, captureTable, accuracyTable, hotspotTable,
  funnelEl, peopleList, cohortTable, deviceTable, errorList, deletionList,
];

const PARSE_RATE_TARGET = 95;
const DAY_MS = 24 * 60 * 60 * 1000;

// ----------------------------------------------------------------------------
// Boot
// ----------------------------------------------------------------------------
(async function init() {
  const { data: { session } } = await supabase.auth.getSession();
  routeFromSession(session);

  supabase.auth.onAuthStateChange((_event, session) => {
    routeFromSession(session);
  });
})();

function routeFromSession(session) {
  if (!session) { showLogin(); return; }
  const email = session.user?.email || "";
  if (!ADMIN_EMAILS.includes(email)) { showDenied(email); return; }
  showAdmin(session);
}

// ----------------------------------------------------------------------------
// Views
// ----------------------------------------------------------------------------
function showLogin() {
  stopIdleLock();
  clearAdminData();
  loginView.classList.remove("hidden");
  deniedView.classList.add("hidden");
  adminShell.classList.add("hidden");
}

// Wipe rendered user data so nothing lingers in the DOM once locked.
function clearAdminData() {
  for (const el of RENDER_TARGETS) el.innerHTML = "";
  for (const el of adminShell.querySelectorAll(".stat-value")) el.textContent = "—";
  for (const el of adminShell.querySelectorAll(".stat-sub[id]")) el.textContent = "";
}

// ----------------------------------------------------------------------------
// Idle lock — signs out after IDLE_LOCK_MS without input.
// ----------------------------------------------------------------------------
let idleTimer = null;
let lastActivity = 0;
const IDLE_EVENTS = ["pointerdown", "keydown", "wheel", "touchstart"];

function bumpActivity() { lastActivity = Date.now(); }

function startIdleLock() {
  if (idleTimer) return;
  bumpActivity();
  for (const ev of IDLE_EVENTS) document.addEventListener(ev, bumpActivity, { passive: true });
  // Interval check (not a single timeout) so a sleeping laptop still locks
  // on wake: the elapsed wall-clock time is what counts.
  idleTimer = setInterval(checkIdle, 30_000);
  document.addEventListener("visibilitychange", checkIdle);
}

function stopIdleLock() {
  if (!idleTimer) return;
  clearInterval(idleTimer);
  idleTimer = null;
  for (const ev of IDLE_EVENTS) document.removeEventListener(ev, bumpActivity);
  document.removeEventListener("visibilitychange", checkIdle);
}

async function checkIdle() {
  if (!idleTimer || Date.now() - lastActivity < IDLE_LOCK_MS) return;
  await supabase.auth.signOut({ scope: "local" }).catch(() => {});
  showLogin();
  showLoginError("Signed out after 15 minutes of inactivity.");
}

function showDenied(email) {
  deniedEmail.textContent = email || "unknown";
  loginView.classList.add("hidden");
  deniedView.classList.remove("hidden");
  adminShell.classList.add("hidden");
}

async function showAdmin(session) {
  loginView.classList.add("hidden");
  deniedView.classList.add("hidden");
  adminShell.classList.remove("hidden");
  adminEmail.textContent = session.user.email;
  hideLoginError();
  startIdleLock();
  await loadOverview();
}

// ----------------------------------------------------------------------------
// Auth handlers
// ----------------------------------------------------------------------------
loginForm.addEventListener("submit", async (e) => {
  e.preventDefault();
  hideLoginError();
  setLoginBusy(true);
  const email = loginEmail.value.trim();
  const password = loginPassword.value;
  const { error } = await supabase.auth.signInWithPassword({ email, password });
  setLoginBusy(false);
  if (error) {
    showLoginError(prettyAuthError(error.message));
    loginPassword.focus();
    loginPassword.select();
  }
  // On success, onAuthStateChange routes us to admin / denied automatically.
});

function setLoginBusy(busy) {
  loginBtn.disabled = busy;
  loginBtn.textContent = busy ? "Signing in…" : "Sign in";
}

function prettyAuthError(raw) {
  if (!raw) return "Sign-in failed.";
  if (/invalid login credentials/i.test(raw)) return "Wrong email or password.";
  if (/email not confirmed/i.test(raw))       return "Confirm your email before signing in.";
  if (/network/i.test(raw))                   return "Network error — check your connection and retry.";
  return raw;
}

deniedLogout.addEventListener("click", async () => {
  await supabase.auth.signOut({ scope: "local" }).catch(() => {});
  showLogin();
});

logoutBtn.addEventListener("click", async () => {
  await supabase.auth.signOut({ scope: "local" }).catch(() => {});
  showLogin();
});

refreshBtn.addEventListener("click", () => { loadOverview(); });

function showLoginError(msg) { loginError.textContent = msg; loginError.classList.remove("hidden"); }
function hideLoginError() { loginError.classList.add("hidden"); }

// ----------------------------------------------------------------------------
// Data loading — one request per card, so a missing view only blanks its card.
// ----------------------------------------------------------------------------
const SCHEMA_HINT =
  `Schema is out of date — run <code>supabase/admin_health_metrics.sql</code> in the Supabase SQL editor.`;

async function loadOverview() {
  for (const el of RENDER_TARGETS) el.innerHTML = `<p class="muted">Loading…</p>`;

  const since24h = new Date(Date.now() - DAY_MS).toISOString();
  const since7d  = new Date(Date.now() - 7 * DAY_MS).toISOString();

  const [
    parse, unparsed, capture, quality, accuracy, hotspots, catSummary,
    users, errors, deletions, downloads, installs,
  ] = await Promise.all([
    supabase.from("admin_parse_rate_7d").select("*").order("sort"),
    supabase.from("admin_unparsed_sms").select("*"),
    supabase.from("admin_capture_only_senders").select("*"),
    supabase.from("admin_txn_quality").select("*").maybeSingle(),
    supabase.from("admin_prediction_accuracy").select("*"),
    supabase.from("admin_category_hotspots").select("*"),
    supabase.from("admin_categorization_summary").select("*").maybeSingle(),
    supabase.from("admin_user_health").select("*"),
    supabase.from("i_events").select("user_id, name, props, app_version, occurred_at")
      .in("name", ["scan_failed", "error"]).gte("occurred_at", since24h)
      .order("occurred_at", { ascending: false }).limit(200),
    supabase.from("i_account_deletions").select("*").order("deleted_at", { ascending: false }),
    supabase.from("i_downloads").select("created_at, app_version"),
    supabase.from("i_installs").select("install_id, created_at, app_version"),
  ]);

  // Locked while the requests were in flight — don't paint data back in.
  if (adminShell.classList.contains("hidden")) return;

  const userRows = users.data || [];
  const emailById = new Map(userRows.map((u) => [u.id, u.email || u.name || u.id.slice(0, 8)]));

  guard(parse, [parseTable], () => renderParseRate(parse.data));
  guard(unparsed, [unparsedList], () => renderUnparsed(unparsed.data));
  guard(capture, [captureTable], () => renderCaptureOnly(capture.data));
  guard(quality, [], () => renderQuality(quality.data));
  guard(accuracy, [accuracyTable], () => renderAccuracy(accuracy.data));
  guard(hotspots, [hotspotTable], () => renderHotspots(hotspots.data));
  guard(catSummary, [], () => renderCategorySummary(catSummary.data));
  guard(users, [funnelEl, peopleList, cohortTable, deviceTable], () => {
    renderRetention(userRows);
    renderFunnel(userRows, downloads.data, installs.data);
    renderPeople(userRows);
    renderCohorts(userRows);
    renderDevices(userRows);
  });
  guard(downloads, [], () => renderCountTile("downloads", downloads.data, since7d));
  guard(installs, [], () => renderCountTile("installs", installs.data, since7d));
  guard(errors, [errorList], () => renderErrors(errors.data, emailById));
  guard(deletions, [deletionList], () => renderDeletions(deletions.data));
}

function guard(res, targets, render) {
  if (res.error) {
    const msg = /does not exist|schema cache/i.test(res.error.message) ? SCHEMA_HINT : escapeHtml(res.error.message);
    for (const el of targets) el.innerHTML = `<p class="muted">${msg}</p>`;
    console.warn(res.error);
    return;
  }
  try { render(); } catch (e) {
    console.error(e);
    for (const el of targets) el.innerHTML = `<p class="muted">Couldn't render: ${escapeHtml(e.message)}</p>`;
  }
}

// ----------------------------------------------------------------------------
// 1. Parser
// ----------------------------------------------------------------------------
function renderParseRate(rows) {
  parseTable.innerHTML = `
    <thead><tr>
      <th>Bank</th><th class="num">Captured</th><th class="num">Parsed</th>
      <th class="num">Unparsed</th><th class="num">Ignored</th><th class="num">Users</th>
      <th>Last SMS</th><th class="num">Parse rate</th>
    </tr></thead>
    <tbody>${rows.map((r) => `
      <tr>
        <td><strong>${escapeHtml(r.sender_group)}</strong></td>
        <td class="num">${num(r.captured)}</td>
        <td class="num">${num(r.parsed)}</td>
        <td class="num ${r.unparsed > 0 ? "text-bad" : ""}">${num(r.unparsed)}</td>
        <td class="num muted">${num(r.ignored)}</td>
        <td class="num">${num(r.users)}</td>
        <td class="muted">${formatRelative(r.last_received_at) || "—"}</td>
        <td class="num">${rateBadge(r.parse_rate)}</td>
      </tr>`).join("")}
    </tbody>`;
}

function rateBadge(rate) {
  if (rate == null) return `<span class="muted">no SMS</span>`;
  const r = Number(rate);
  return `<span class="pill ${r < PARSE_RATE_TARGET ? "pill-bad" : "pill-good"}">${r.toFixed(1)}%</span>`;
}

function renderUnparsed(rows) {
  $("stat-unparsed").textContent = rows.length >= 300 ? "300+" : num(rows.length);
  if (!rows.length) { unparsedList.innerHTML = `<p class="muted">Nothing unparsed. 🎉</p>`; return; }
  unparsedList.innerHTML = rows.map((r) => `
    <details class="sms-row">
      <summary>
        <span class="pill">${escapeHtml(r.sender_group)}</span>
        <span class="sms-preview">${escapeHtml(r.raw_message)}</span>
        <span class="muted small sms-meta">${formatDateTime(r.received_at)}</span>
      </summary>
      <pre class="sms-body">${escapeHtml(r.raw_message)}</pre>
      <p class="muted small">
        Sender <code>${escapeHtml(r.sender_name)}</code> · ${escapeHtml(r.email || r.user_id)}
        ${r.status_inferred ? ` · <span title="Uploaded by a pre-1.0.9 build; not matched to any transaction">status inferred</span>` : ""}
      </p>
    </details>`).join("");
}

function renderCaptureOnly(rows) {
  if (!rows.length) { captureTable.innerHTML = `<tbody><tr><td class="muted">No capture-only SMS yet.</td></tr></tbody>`; return; }
  captureTable.innerHTML = `
    <thead><tr>
      <th>Sender</th><th class="num">Messages</th><th class="num">Last 30d</th>
      <th class="num">Users</th><th>Last SMS</th><th>Sample</th>
    </tr></thead>
    <tbody>${rows.map((r) => `
      <tr>
        <td><strong>${escapeHtml(r.sender_name)}</strong><div class="muted small">${escapeHtml(r.bank_name || "")}</div></td>
        <td class="num">${num(r.messages)}</td>
        <td class="num">${num(r.messages_30d)}</td>
        <td class="num">${num(r.users)}</td>
        <td class="muted">${formatRelative(r.last_received_at) || "—"}</td>
        <td class="sample" title="${escapeAttr(r.sample || "")}">${escapeHtml(r.sample || "")}</td>
      </tr>`).join("")}
    </tbody>`;
}

function renderQuality(q) {
  if (!q) return;
  setStat("dup-7d", pct(q.duplicates_7d, q.total_7d), `${num(q.duplicates_7d)} of ${num(q.total_7d)} new rows`);
  setStat("dup-all", pct(q.duplicates, q.total), `${num(q.duplicates)} of ${num(q.total)} rows`);
  setStat("unknown", pct(q.unknown, q.total), `${num(q.unknown)} UNKNOWN rows · ${num(q.unknown_7d)} this week`);
}

// ----------------------------------------------------------------------------
// 2. Categorization
// ----------------------------------------------------------------------------
const SOURCE_LABELS = {
  user_rule: "User rule",
  seed: "Seed regex",
  fallback: "Type fallback",
  none: "No guess",
  unknown: "Unknown",
};
const SOURCE_ORDER = ["user_rule", "seed", "fallback", "none", "unknown"];

function renderAccuracy(rows) {
  rows = [...rows].sort((a, b) => SOURCE_ORDER.indexOf(a.source) - SOURCE_ORDER.indexOf(b.source));
  // "No guess" rows were never a prediction, so they stay out of accuracy.
  const scored = rows.filter((r) => r.source !== "none");
  const confirmed = sum(scored, "confirmed");
  const changed = sum(scored, "changed");
  setStat("accuracy", pct(confirmed, confirmed + changed), `${num(confirmed)} kept · ${num(changed)} changed`);

  accuracyTable.innerHTML = `
    <thead><tr>
      <th>Source</th><th class="num">Confirmed</th><th class="num">Changed</th>
      <th class="num">Accuracy</th><th class="num">Accuracy (30d)</th>
      <th class="num">Not a txn</th><th class="num">Unreviewed</th>
    </tr></thead>
    <tbody>${rows.map((r) => {
      const isNone = r.source === "none";
      return `
      <tr>
        <td><strong>${SOURCE_LABELS[r.source] || escapeHtml(r.source)}</strong></td>
        <td class="num">${num(r.confirmed)}</td>
        <td class="num">${num(r.changed)}</td>
        <td class="num">${isNone ? "—" : accuracyPill(r.confirmed, r.changed)}</td>
        <td class="num">${isNone ? "—" : accuracyPill(r.confirmed_30d, r.changed_30d)}</td>
        <td class="num">${num(r.not_a_txn)}</td>
        <td class="num muted">${num(r.unreviewed)}</td>
      </tr>`;
    }).join("")}
    </tbody>`;
}

function accuracyPill(ok, bad) {
  const total = Number(ok || 0) + Number(bad || 0);
  if (!total) return `<span class="muted">—</span>`;
  const r = (100 * Number(ok || 0)) / total;
  const cls = r >= 85 ? "pill-good" : r >= 65 ? "pill-warn" : "pill-bad";
  return `<span class="pill ${cls}">${r.toFixed(0)}%</span>`;
}

function renderHotspots(rows) {
  if (!rows.length) { hotspotTable.innerHTML = `<tbody><tr><td class="muted">Nothing landing in Other or being changed.</td></tr></tbody>`; return; }
  hotspotTable.innerHTML = `
    <thead><tr>
      <th>Counterparty</th><th class="num">In Other</th><th class="num">Changed</th>
      <th>Usually changed</th><th class="num">Users</th><th class="num">Txns</th>
    </tr></thead>
    <tbody>${rows.map((r) => `
      <tr>
        <td><strong>${escapeHtml(r.merchant_key)}</strong>${r.example && r.example.toUpperCase() !== r.merchant_key ? `<div class="muted small">${escapeHtml(r.example)}</div>` : ""}</td>
        <td class="num">${num(r.in_other)}</td>
        <td class="num">${num(r.changed)}</td>
        <td>${r.changed_to ? `${escapeHtml(r.predicted || "no guess")} → <strong>${escapeHtml(r.changed_to)}</strong>` : `<span class="muted">—</span>`}</td>
        <td class="num">${num(r.users)}</td>
        <td class="num muted">${num(r.txns)}</td>
      </tr>`).join("")}
    </tbody>`;
}

function renderCategorySummary(s) {
  if (!s) return;
  setStat("backlog", pct(s.pending_older_than_7d, s.older_than_7d),
    `${num(s.pending_older_than_7d)} of ${num(s.older_than_7d)} rows · ${num(s.skipped_older_than_7d)} skipped`);
  setStat("transfers", num(s.transfers_flagged),
    `${num(s.transfers_recategorised)} of ${num(s.transfers_reviewed)} reviewed were re-categorised (${pct(s.transfers_recategorised, s.transfers_reviewed)})`);
  setStat("transfers-missed", num(s.transfers_missed));
}

// ----------------------------------------------------------------------------
// 3. Retention
// ----------------------------------------------------------------------------
function renderRetention(users) {
  const retained = users.filter((u) => u.review_days_7d >= 3).length;
  setStat("retention", `${retained} / ${users.length}`, `reviewed on 3+ of the last 7 days (${pct(retained, users.length)})`);
  const new7d = users.filter((u) => u.account_created_at && Date.now() - new Date(u.account_created_at) < 7 * DAY_MS).length;
  setStat("users", num(users.length), `${num(new7d)} in the last 7 days`);
}

function renderCountTile(key, rows, since7d) {
  const recent = rows.filter((r) => r.created_at >= since7d).length;
  setStat(key, num(rows.length), `${num(recent)} in the last 7 days`);
}

function renderFunnel(users, downloads, installs) {
  const steps = [
    { label: "Downloaded APK", n: downloads ? downloads.length : null, anon: true },
    { label: "Installed", n: installs ? installs.length : null, anon: true },
    { label: "Signed up", n: users.length },
    { label: "SMS permission granted", n: users.filter((u) => u.sms_permission_at).length },
    { label: "First scan done", n: users.filter((u) => u.first_scan_at).length },
    { label: "First review completed", n: users.filter((u) => u.first_review_at).length },
    { label: "PIN set", n: users.filter((u) => u.pin_set_at).length },
  ];
  const max = Math.max(1, ...steps.map((s) => s.n || 0));
  let prev = null;
  funnelEl.innerHTML = steps.map((s) => {
    const n = s.n ?? 0;
    const conv = prev != null && prev > 0 ? `${Math.round((100 * n) / prev)}% of previous` : "";
    // Drop from the previous step, highlighted where it's biggest.
    const html = `
      <div class="funnel-row">
        <div class="funnel-label">${escapeHtml(s.label)}${s.anon ? ` <span class="muted small">anon</span>` : ""}</div>
        <div class="funnel-track"><div class="funnel-fill" style="width:${Math.max(2, (100 * n) / max)}%"></div></div>
        <div class="funnel-value"><strong>${s.n == null ? "—" : num(n)}</strong><span class="muted small">${conv}</span></div>
      </div>`;
    prev = s.n == null ? prev : n;
    return html;
  }).join("");
}

function renderPeople(users) {
  const rows = [...users].sort((a, b) => (b.days_since_review ?? 1e9) - (a.days_since_review ?? 1e9));
  if (!rows.length) { peopleList.innerHTML = `<p class="muted">No users.</p>`; return; }
  peopleList.innerHTML = rows.map((u) => {
    const d = u.days_since_review;
    const label = d == null ? "never" : d === 0 ? "today" : d === 1 ? "1 day" : `${d} days`;
    const cls = d == null || d > 7 ? "pill-bad" : d > 2 ? "pill-warn" : "pill-good";
    const dots = Array.from({ length: 7 }, (_, i) => `<span class="dot ${i < u.review_days_7d ? "on" : ""}"></span>`).join("");
    return `
      <div class="person-row">
        <div class="user-cell">
          <span class="name">${escapeHtml(u.name || u.email || "—")}</span>
          ${u.name ? `<span class="email">${escapeHtml(u.email || "")}</span>` : ""}
        </div>
        <div class="dots" title="${u.review_days_7d} review days in the last 7">${dots}</div>
        <span class="pill ${cls}">${label}</span>
      </div>`;
  }).join("");
}

function renderCohorts(users) {
  const byWeek = new Map();
  for (const u of users) {
    if (!u.account_created_at) continue;
    const ws = weekStart(new Date(u.account_created_at));
    const wk = `${ws.getFullYear()}-${String(ws.getMonth() + 1).padStart(2, "0")}-${String(ws.getDate()).padStart(2, "0")}`;
    const c = byWeek.get(wk) || { users: 0, closed: 0, retained: 0 };
    c.users += 1;
    if (u.week1_retained != null) { c.closed += 1; if (u.week1_retained) c.retained += 1; }
    byWeek.set(wk, c);
  }
  const weeks = [...byWeek.entries()].sort((a, b) => b[0].localeCompare(a[0]));
  if (!weeks.length) { cohortTable.innerHTML = `<tbody><tr><td class="muted">No signups yet.</td></tr></tbody>`; return; }
  cohortTable.innerHTML = `
    <thead><tr><th>Signup week</th><th class="num">Signed up</th><th class="num">Window closed</th><th class="num">Retained</th><th class="num">Week-1 retention</th></tr></thead>
    <tbody>${weeks.map(([wk, c]) => `
      <tr>
        <td>${formatDate(`${wk}T00:00:00`)}</td>
        <td class="num">${num(c.users)}</td>
        <td class="num muted">${num(c.closed)}</td>
        <td class="num">${num(c.retained)}</td>
        <td class="num">${c.closed ? accuracyPill(c.retained, c.closed - c.retained) : `<span class="muted">pending</span>`}</td>
      </tr>`).join("")}
    </tbody>`;
}

function weekStart(d) {
  const x = new Date(d);
  x.setHours(0, 0, 0, 0);
  x.setDate(x.getDate() - ((x.getDay() + 6) % 7)); // Monday
  return x;
}

// ----------------------------------------------------------------------------
// 4. Health
// ----------------------------------------------------------------------------
function renderDevices(users) {
  const latest = users.map((u) => versionOf(u.app_version)).filter(Boolean).sort(compareVersions).pop();
  const old = users.filter((u) => versionOf(u.app_version) && compareVersions(versionOf(u.app_version), latest) < 0);
  setStat("old-version", num(old.length), latest ? `latest seen: ${latest}` : "no versions reported");

  const rows = [...users].sort((a, b) =>
    (Number(b.unsynced_transactions || 0) + Number(b.unsynced_raw || 0)) -
    (Number(a.unsynced_transactions || 0) + Number(a.unsynced_raw || 0)));
  deviceTable.innerHTML = `
    <thead><tr>
      <th>User</th><th>Version</th><th class="num">Unsynced txns</th><th class="num">Unsynced SMS</th>
      <th>Oldest unsynced</th><th>Last sync OK</th><th>Last report</th><th>PIN</th>
    </tr></thead>
    <tbody>${rows.map((u) => {
      const v = versionOf(u.app_version);
      const isOld = v && latest && compareVersions(v, latest) < 0;
      const oldestAge = u.oldest_unsynced_at ? Date.now() - new Date(u.oldest_unsynced_at) : 0;
      return `
      <tr>
        <td><div class="user-cell"><span class="name">${escapeHtml(u.name || u.email || "—")}</span>${u.name ? `<span class="email">${escapeHtml(u.email || "")}</span>` : ""}</div></td>
        <td>${v ? `<span class="pill ${isOld ? "pill-warn" : ""}">${escapeHtml(v)}</span>` : `<span class="muted">—</span>`}</td>
        <td class="num">${u.unsynced_transactions == null ? `<span class="muted">—</span>` : num(u.unsynced_transactions)}</td>
        <td class="num">${u.unsynced_raw == null ? `<span class="muted">—</span>` : num(u.unsynced_raw)}</td>
        <td class="${oldestAge > DAY_MS ? "text-bad" : ""}">${u.oldest_unsynced_at ? formatRelative(u.oldest_unsynced_at) : `<span class="muted">—</span>`}</td>
        <td>${u.last_sync_ok_at ? formatRelative(u.last_sync_ok_at) : `<span class="muted">—</span>`}${u.last_sync_error ? `<div class="text-bad small" title="${escapeAttr(u.last_sync_error)}">sync error</div>` : ""}</td>
        <td class="muted">${u.status_reported_at ? formatRelative(u.status_reported_at) : "never (pre-1.0.9)"}</td>
        <td>${u.has_pin == null ? `<span class="muted">—</span>` : u.has_pin ? "Yes" : "No"}</td>
      </tr>`;
    }).join("")}
    </tbody>`;
}

// "android-1.0.9" / "web-beta17" → "1.0.9" / "beta17".
function versionOf(v) {
  if (!v) return null;
  return v.replace(/^(android|web|ios)-/i, "");
}
function compareVersions(a, b) {
  const pa = String(a).split(/[.-]/).map((x) => (isNaN(x) ? x : Number(x)));
  const pb = String(b).split(/[.-]/).map((x) => (isNaN(x) ? x : Number(x)));
  for (let i = 0; i < Math.max(pa.length, pb.length); i++) {
    const x = pa[i] ?? 0, y = pb[i] ?? 0;
    if (x === y) continue;
    if (typeof x === "number" && typeof y === "number") return x - y;
    return String(x).localeCompare(String(y));
  }
  return 0;
}

function renderErrors(rows, emailById) {
  const scans = rows.filter((r) => r.name === "scan_failed");
  const errs = rows.filter((r) => r.name === "error");
  setStat("scan-fail", num(scans.length), `${new Set(scans.map((r) => r.user_id)).size} users`);
  setStat("errors", num(errs.length), `${new Set(errs.map((r) => r.user_id)).size} users`);
  if (!rows.length) { errorList.innerHTML = `<p class="muted">No failures in the last 24 hours.</p>`; return; }
  errorList.innerHTML = rows.map((r) => `
    <div class="event-row">
      <div>
        <span class="pill ${r.name === "scan_failed" ? "pill-bad" : "pill-warn"}">${r.name === "scan_failed" ? "Scan failed" : escapeHtml(r.props?.summary || "Error")}</span>
        <span class="muted small">${escapeHtml(emailById.get(r.user_id) || r.user_id.slice(0, 8))} · ${escapeHtml(versionOf(r.app_version) || "")}</span>
      </div>
      <div class="mono event-detail">${escapeHtml(r.props?.error || "")}</div>
      <div class="muted small">${formatDateTime(r.occurred_at)}</div>
    </div>`).join("");
}

function renderDeletions(rows) {
  const recent = rows.filter((r) => Date.now() - new Date(r.deleted_at) < 30 * DAY_MS).length;
  setStat("deletions", num(rows.length), `${num(recent)} in the last 30 days`);
  if (!rows.length) { deletionList.innerHTML = `<p class="muted">No deletions recorded.</p>`; return; }
  deletionList.innerHTML = rows.map((r) => {
    const stayed = r.account_created_at ? Math.max(0, Math.round((new Date(r.deleted_at) - new Date(r.account_created_at)) / DAY_MS)) : null;
    return `
    <div class="event-row">
      <div><strong>${formatDateTime(r.deleted_at)}</strong> <span class="muted small">${escapeHtml(versionOf(r.app_version) || "")}</span></div>
      <div class="muted small">
        ${stayed == null ? "" : `stayed ${stayed} day${stayed === 1 ? "" : "s"} · `}${num(r.txn_count)} txns · ${num(r.reviewed_count)} reviewed
      </div>
    </div>`;
  }).join("");
}

// ----------------------------------------------------------------------------
// Helpers
// ----------------------------------------------------------------------------
function setStat(key, value, sub) {
  $(`stat-${key}`).textContent = value;
  const subEl = $(`stat-${key}-sub`);
  if (subEl && sub != null) subEl.textContent = sub;
}
function num(n) { return Number(n || 0).toLocaleString(); }
function sum(rows, key) { return rows.reduce((a, r) => a + Number(r[key] || 0), 0); }
function pct(part, whole) {
  const w = Number(whole || 0);
  if (!w) return "—";
  const p = (100 * Number(part || 0)) / w;
  return `${p < 10 && p > 0 ? p.toFixed(1) : Math.round(p)}%`;
}

// ----------------------------------------------------------------------------
// Formatters
// ----------------------------------------------------------------------------
function formatDate(iso) {
  if (!iso) return "—";
  const d = new Date(iso);
  if (isNaN(d.getTime())) return "—";
  return d.toLocaleDateString(undefined, { year: "numeric", month: "short", day: "numeric" });
}

function formatDateTime(iso) {
  if (!iso) return "—";
  const d = new Date(iso);
  if (isNaN(d.getTime())) return "—";
  return d.toLocaleString(undefined, { month: "short", day: "numeric", hour: "2-digit", minute: "2-digit" });
}

function formatRelative(iso) {
  if (!iso) return "";
  const t = new Date(iso).getTime();
  if (isNaN(t)) return "";
  const diff = Math.max(0, Date.now() - t);
  const s = Math.round(diff / 1000);
  if (s < 60)   return `${s}s ago`;
  const m = Math.round(s / 60);
  if (m < 60)   return `${m}m ago`;
  const h = Math.round(m / 60);
  if (h < 48)   return `${h}h ago`;
  const d = Math.round(h / 24);
  if (d < 30)   return `${d}d ago`;
  return formatDate(iso);
}

function escapeHtml(s) {
  return String(s ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}
function escapeAttr(s) { return escapeHtml(s); }
