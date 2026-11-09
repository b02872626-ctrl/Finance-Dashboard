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

// Password reset: the emailed link lands back on this page with
// "#...type=recovery" (or "#error_code=otp_expired" once it is stale). Read it
// before createClient, which consumes and strips the hash.
const URL_HASH = new URLSearchParams(window.location.hash.slice(1));
let recoveryMode = URL_HASH.get("type") === "recovery";
const recoveryLinkError = URL_HASH.get("error_code") || URL_HASH.get("error");

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
const loginNotice    = $("login-notice");
const forgotBtn      = $("forgot-btn");
const resetView      = $("reset-view");
const resetForm      = $("reset-form");
const resetEmail     = $("reset-email");
const resetPassword  = $("reset-password");
const resetConfirm   = $("reset-confirm");
const resetBtn       = $("reset-btn");
const resetError     = $("reset-error");
const resetCancel    = $("reset-cancel-btn");
const deniedEmail   = $("denied-email");
const deniedLogout   = $("denied-logout-btn");
const adminEmail     = $("admin-email");
const refreshBtn     = $("refresh-btn");
const logoutBtn      = $("logout-btn");

const parseTable    = $("parse-table");
const unparsedList  = $("unparsed-list");
const fixTable      = $("fix-table");
const noticeTable   = $("notice-table");
const parserVerdict = $("parser-verdict");
const captureTable  = $("capture-table");
const moneySendersTable = $("money-senders-table");
const accuracyTable = $("accuracy-table");
const hotspotTable  = $("hotspot-table");
const funnelEl      = $("funnel");
const peopleList    = $("people-list");
const cohortTable   = $("cohort-table");
const deviceTable   = $("device-table");
const errorList     = $("error-list");
const deletionList  = $("deletion-list");
const dauChart      = $("dau-chart");
const bankPanel     = $("bank-panel");
const phonesTable   = $("phones-table");

const RENDER_TARGETS = [
  dauChart, bankPanel, phonesTable, parseTable, fixTable, noticeTable, parserVerdict, unparsedList, captureTable, moneySendersTable, accuracyTable, hotspotTable,
  funnelEl, peopleList, cohortTable, deviceTable, errorList, deletionList,
];

const PARSE_RATE_TARGET = 95;
const DAY_MS = 24 * 60 * 60 * 1000;
// Addis Ababa is UTC+3 all year (no daylight saving).
const ADDIS_OFFSET_MS = 3 * 60 * 60 * 1000;
const DAU_DAYS = 30;
// A phone counts as a current install if it reported sync status this recently.
const CURRENT_INSTALL_DAYS = 7;

// ----------------------------------------------------------------------------
// Tabs — Overview first; the detailed sections sit behind their own tabs.
// ----------------------------------------------------------------------------
const tabButtons = [...document.querySelectorAll(".side-nav .tab")];
const tabPanels  = [...document.querySelectorAll("[data-panel]")];

const TAB_SUBTITLES = {
  overview:   "How Kash is doing right now.",
  parser:     "Is the parser working?",
  categories: "Is categorization good enough?",
  retention:  "Do people stick?",
  health:     "Are the phones healthy?",
};

function showTab(name) {
  if (!tabPanels.some((p) => p.dataset.panel === name)) name = "overview";
  for (const b of tabButtons) b.setAttribute("aria-selected", String(b.dataset.tab === name));
  const btn = tabButtons.find((b) => b.dataset.tab === name);
  $("crumb-current").textContent = btn ? btn.textContent.trim() : "Overview";
  $("panel-sub").textContent = TAB_SUBTITLES[name] || "";
  for (const p of tabPanels) p.classList.toggle("hidden", p.dataset.panel !== name);
  try { sessionStorage.setItem("kash-admin-tab", name); } catch {}
}
for (const b of tabButtons) b.addEventListener("click", () => showTab(b.dataset.tab));
for (const b of document.querySelectorAll("[data-goto]")) b.addEventListener("click", () => showTab(b.dataset.goto));

function setGreeting() {
  const h = new Date(Date.now() + ADDIS_OFFSET_MS).getUTCHours();
  $("greeting").textContent = h < 12 ? "Good morning" : h < 18 ? "Good afternoon" : "Good evening";
}
try { showTab(sessionStorage.getItem("kash-admin-tab") || "overview"); } catch { showTab("overview"); }

// ----------------------------------------------------------------------------
// Boot
// ----------------------------------------------------------------------------
(async function init() {
  const { data: { session } } = await supabase.auth.getSession();
  routeFromSession(session);

  if (!session && recoveryLinkError) {
    showLoginError("That reset link has expired or was already used. Send a new one.");
  }

  supabase.auth.onAuthStateChange((event, session) => {
    if (event === "PASSWORD_RECOVERY") recoveryMode = true;
    // Never query Supabase inside this callback: it holds the auth lock, so
    // any request made here waits on it forever and the dashboard sticks on
    // "Loading…". Route on the next tick instead.
    setTimeout(() => routeFromSession(session), 0);
  });
})();

function routeFromSession(session) {
  if (!session) { recoveryMode = false; showLogin(); return; }
  const email = session.user?.email || "";
  if (!ADMIN_EMAILS.includes(email)) { showDenied(email); return; }
  // A reset link signs the admin in; make them choose a password before the
  // dashboard opens.
  if (recoveryMode) { showSetPassword(email); return; }
  showAdmin(session);
}

// ----------------------------------------------------------------------------
// Views
// ----------------------------------------------------------------------------
function showLogin() {
  shownAdminId = null;
  stopIdleLock();
  clearAdminData();
  loginView.classList.remove("hidden");
  resetView.classList.add("hidden");
  deniedView.classList.add("hidden");
  adminShell.classList.add("hidden");
}

function showSetPassword(email) {
  shownAdminId = null;
  stopIdleLock();
  clearAdminData();
  resetEmail.textContent = email;
  resetForm.reset();
  resetError.classList.add("hidden");
  loginView.classList.add("hidden");
  resetView.classList.remove("hidden");
  deniedView.classList.add("hidden");
  adminShell.classList.add("hidden");
  resetPassword.focus();
}

// Wipe rendered user data so nothing lingers in the DOM once locked.
function clearAdminData() {
  for (const el of RENDER_TARGETS) el.innerHTML = "";
  for (const el of adminShell.querySelectorAll(".stat-value, .kpi-value")) el.textContent = "—";
  for (const el of adminShell.querySelectorAll(".stat-sub[id], .kpi-foot[id], .trend[id]")) el.textContent = "";
  for (const el of adminShell.querySelectorAll(".trend[id]")) el.className = "trend";
  $("stat-parsed-7d").textContent = "—";
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
  shownAdminId = null;
  deniedEmail.textContent = email || "unknown";
  loginView.classList.add("hidden");
  resetView.classList.add("hidden");
  deniedView.classList.remove("hidden");
  adminShell.classList.add("hidden");
}

// Which admin the dashboard is showing, so a repeat sign-in event (the
// initial session, a token refresh) doesn't reload everything.
let shownAdminId = null;

async function showAdmin(session) {
  if (shownAdminId === session.user.id && !adminShell.classList.contains("hidden")) return;
  shownAdminId = session.user.id;
  loginView.classList.add("hidden");
  resetView.classList.add("hidden");
  deniedView.classList.add("hidden");
  adminShell.classList.remove("hidden");
  adminEmail.textContent = session.user.email;
  hideLoginError();
  startIdleLock();
  setGreeting();
  await loadOverview();
}

// ----------------------------------------------------------------------------
// Auth handlers
// ----------------------------------------------------------------------------
loginForm.addEventListener("submit", async (e) => {
  e.preventDefault();
  hideLoginError();
  hideLoginNotice();
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

// Forgot password: email a reset link that comes back to this page. Only the
// admin addresses get one, so this page can't be used to send reset mail to
// app users; the message is the same either way so it doesn't reveal which
// addresses are admins.
forgotBtn.addEventListener("click", async () => {
  hideLoginError();
  hideLoginNotice();
  const email = loginEmail.value.trim().toLowerCase();
  if (!loginEmail.value.trim() || !loginEmail.checkValidity()) {
    showLoginError("Type your admin email above, then tap Forgot password.");
    loginEmail.focus();
    return;
  }
  forgotBtn.disabled = true;
  forgotBtn.textContent = "Sending…";
  let failed = null;
  if (ADMIN_EMAILS.includes(email)) {
    const { error } = await supabase.auth.resetPasswordForEmail(email, {
      redirectTo: window.location.origin + window.location.pathname,
    });
    failed = error;
  }
  forgotBtn.disabled = false;
  forgotBtn.textContent = "Forgot password?";
  if (failed) {
    showLoginError(/rate limit|security purposes/i.test(failed.message)
      ? "Too many reset emails. Wait a few minutes and try again."
      : prettyAuthError(failed.message));
    return;
  }
  showLoginNotice(`If ${email} is an admin address, a reset link is on its way. Open it in this browser.`);
});

resetForm.addEventListener("submit", async (e) => {
  e.preventDefault();
  resetError.classList.add("hidden");
  const password = resetPassword.value;
  if (password.length < 8) return showResetError("Use at least 8 characters.");
  if (password !== resetConfirm.value) return showResetError("The two passwords don't match.");
  resetBtn.disabled = true;
  resetBtn.textContent = "Saving…";
  const { data, error } = await supabase.auth.updateUser({ password });
  resetBtn.disabled = false;
  resetBtn.textContent = "Save new password";
  if (error) {
    showResetError(/different from the old/i.test(error.message)
      ? "Choose a password different from your old one."
      : prettyAuthError(error.message));
    return;
  }
  recoveryMode = false;
  resetForm.reset();
  const { data: { session } } = await supabase.auth.getSession();
  routeFromSession(session ?? (data?.user ? { user: data.user } : null));
});

resetCancel.addEventListener("click", async () => {
  recoveryMode = false;
  await supabase.auth.signOut({ scope: "local" }).catch(() => {});
  showLogin();
});

function showResetError(msg) { resetError.textContent = msg; resetError.classList.remove("hidden"); }

function showLoginError(msg) { loginError.textContent = msg; loginError.classList.remove("hidden"); }
function hideLoginError() { loginError.classList.add("hidden"); }
function showLoginNotice(msg) { loginNotice.textContent = msg; loginNotice.classList.remove("hidden"); }
function hideLoginNotice() { loginNotice.classList.add("hidden"); }

// ----------------------------------------------------------------------------
// Data loading — one request per card, so a missing view only blanks its card.
// ----------------------------------------------------------------------------
const SCHEMA_HINT =
  `Schema is out of date — run <code>supabase/admin_health_metrics.sql</code> in the Supabase SQL editor.`;

// Each load gets a number; a slower, older load never paints over a newer one.
let loadGen = 0;
const QUERY_TIMEOUT_MS = 25_000;

// Resolve to an error result instead of hanging forever.
function withTimeout(promise) {
  let timer;
  const timeout = new Promise((resolve) => {
    timer = setTimeout(() => resolve({ data: null, count: null, error: { message: "Timed out. Press Refresh to try again." } }), QUERY_TIMEOUT_MS);
  });
  return Promise.race([Promise.resolve(promise), timeout]).finally(() => clearTimeout(timer));
}

function isStale(gen) {
  return gen !== loadGen || adminShell.classList.contains("hidden");
}

async function loadOverview() {
  const gen = ++loadGen;
  for (const el of RENDER_TARGETS) el.innerHTML = `<p class="muted">Loading…</p>`;
  for (const k of ["current-installs", "parsed", "dau"]) setStat(k, "—", "");
  for (const el of adminShell.querySelectorAll(".trend[id]")) { el.textContent = ""; el.className = "trend"; }
  $("stat-parsed-7d").textContent = "—";

  const since24h = new Date(Date.now() - DAY_MS).toISOString();
  const since7d  = new Date(Date.now() - 7 * DAY_MS).toISOString();

  // Overview numbers load on their own so a slow detail view never holds them up.
  const keyNumbers = loadKeyNumbers(since24h, gen);

  const [
    parse, unparsed, capture, moneySenders, quality, accuracy, hotspots, catSummary,
    users, errors, deletions, downloads, installs,
  ] = await Promise.all([
    supabase.from("admin_parse_rate_7d").select("*").order("sort"),
    supabase.from("admin_unparsed_sms").select("*"),
    supabase.from("admin_capture_only_senders").select("*"),
    supabase.from("admin_money_senders").select("*"),
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
  ].map(withTimeout));

  // Locked, or a newer load started, while the requests were in flight.
  if (isStale(gen)) return;

  const userRows = users.data || [];
  const emailById = new Map(userRows.map((u) => [u.id, u.email || u.name || u.id.slice(0, 8)]));

  // Parser tab: the queue says which unparsed texts are money and which are
  // notices, so the rates below leave notices out.
  const queue = unparsed.error ? null : unparsed.data;
  const parserRows = parse.error ? null : parserStats(parse.data, queue);
  guard(unparsed, [unparsedList, fixTable, noticeTable], () => renderUnparsed(unparsed.data));
  guard(parse, [parseTable, parserVerdict], () => renderParseRate(parserRows, queue));
  guard(capture, [captureTable], () => renderCaptureOnly(capture.data));
  guard(moneySenders, [moneySendersTable], () => renderMoneySenders(moneySenders.data));
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
  guard(users, [phonesTable], () => {
    renderCurrentInstalls(userRows, installs.data);
    renderPhones(userRows);
  });
  guard(parse, [bankPanel], () => renderBankPanel(parserRows));
  await keyNumbers;
}

// Paints one card: its error, or its data. A failing card never breaks the rest.
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
// Overview
// ----------------------------------------------------------------------------
async function loadKeyNumbers(since24h, gen) {
  const since48h = new Date(Date.now() - 2 * DAY_MS).toISOString();
  const dauSince = new Date(addisDayStart(Date.now()) - (DAU_DAYS - 1) * DAY_MS).toISOString();
  const [parsedAll, parsed24h, parsedPrev, sessions] = await Promise.all([
    supabase.from("i_transactions").select("id", { count: "exact", head: true }),
    supabase.from("i_transactions").select("id", { count: "exact", head: true }).gte("created_at", since24h),
    supabase.from("i_transactions").select("id", { count: "exact", head: true })
      .gte("created_at", since48h).lt("created_at", since24h),
    fetchAll(() => supabase.from("i_sessions")
      .select("user_id, started_at")
      .eq("client", "android")
      .or("app_version.is.null,app_version.neq.backfill-v1")
      .gte("started_at", dauSince)
      .order("started_at")),
  ].map(withTimeout));
  if (isStale(gen)) return;

  if (parsedAll.error) { console.warn(parsedAll.error); setStat("parsed", "—", "Couldn't load"); }
  else {
    setStat("parsed", num(parsedAll.count), "Bank SMS turned into transactions, all time");
    if (!parsed24h.error) {
      setTrend("parsed", `+${num(parsed24h.count)} today`,
        parsedPrev.error ? "flat" : trendDir(parsed24h.count, parsedPrev.count));
    }
  }

  guard(sessions, [dauChart], () => renderDau(sessions.data));
}

// Pages past Supabase's 1,000-row cap.
async function fetchAll(build) {
  const rows = [];
  for (let from = 0; ; from += 1000) {
    const { data, error } = await build().range(from, from + 999);
    if (error) return { data: null, error };
    rows.push(...data);
    if (data.length < 1000) return { data: rows, error: null };
  }
}

function setTrend(key, text, dir) {
  const el = $(`trend-${key}`);
  if (!el) return;
  const arrow = dir === "up" ? "↗" : dir === "down" ? "↘" : "→";
  el.textContent = `${arrow} ${text}`;
  el.className = `trend trend-${dir}`;
}
function trendDir(now, before) {
  if (now > before) return "up";
  if (now < before) return "down";
  return "flat";
}

function renderCurrentInstalls(users, installs) {
  const cutoff = Date.now() - CURRENT_INSTALL_DAYS * DAY_MS;
  const current = users.filter((u) => u.status_reported_at && new Date(u.status_reported_at) >= cutoff).length;
  setStat("current-installs", num(current), `Phones that synced in the last ${CURRENT_INSTALL_DAYS} days`);
  if (installs) {
    const weekAgo = Date.now() - 7 * DAY_MS;
    const fresh = installs.filter((r) => new Date(r.created_at) >= weekAgo).length;
    setTrend("current-installs", `${num(fresh)} new this week`, fresh > 0 ? "up" : "flat");
  }
}

function addisDayStart(ms) {
  return Math.floor((ms + ADDIS_OFFSET_MS) / DAY_MS) * DAY_MS - ADDIS_OFFSET_MS;
}
function addisDayKey(ms) {
  return new Date(ms + ADDIS_OFFSET_MS).toISOString().slice(0, 10);
}
function addisLabel(ms, opts = { month: "short", day: "numeric" }) {
  return new Date(ms + ADDIS_OFFSET_MS).toLocaleDateString(undefined, { ...opts, timeZone: "UTC" });
}

// Block chart: each column is a stack of rounded cells. Filled cells are that
// day's active users; grey cells pad every column up to the busiest day.
function renderDau(sessions) {
  const today = addisDayStart(Date.now());
  const days = Array.from({ length: DAU_DAYS }, (_, i) => {
    const start = today - (DAU_DAYS - 1 - i) * DAY_MS;
    return { key: addisDayKey(start), start, users: new Set() };
  });
  const byKey = new Map(days.map((d) => [d.key, d]));
  for (const s of sessions) byKey.get(addisDayKey(new Date(s.started_at).getTime()))?.users.add(s.user_id);

  const counts = days.map((d) => d.users.size);
  const todayCount = counts[counts.length - 1];
  const yesterday = counts[counts.length - 2];
  const last7 = counts.slice(-8, -1); // the 7 full days before today
  const avg7 = last7.reduce((a, b) => a + b, 0) / last7.length;
  setStat("dau", num(todayCount), `Yesterday ${num(yesterday)} · 7-day average ${avg7.toFixed(1)}`);
  setTrend("dau", `vs ${avg7.toFixed(1)} avg`, trendDir(todayCount, Math.round(avg7 * 10) / 10));

  const max = Math.max(1, ...counts);
  const rows = Math.min(10, Math.max(4, max));           // cells per column
  const perCell = max / rows;
  const peak = counts.indexOf(max);
  const ticks = [max, Math.round(max / 2), 0];

  dauChart.innerHTML = `
    <div class="blk-plot">
      <div class="blk-y">${ticks.map((t) => `<span>${t}</span>`).join("")}</div>
      <div class="blk-cols">${days.map((d, i) => {
        const n = d.users.size;
        const filled = n === 0 ? 0 : Math.max(1, Math.round(n / perCell));
        const cells = Array.from({ length: rows }, (_, r) =>
          `<i class="${rows - r <= filled ? "on" : ""}"></i>`).join("");
        return `<div class="blk-col${i === days.length - 1 ? " is-today" : ""}${i === peak && max > 0 ? " is-peak" : ""}"
          data-label="${escapeAttr(addisLabel(d.start, { weekday: "short", month: "short", day: "numeric" }))}" data-n="${n}">${cells}</div>`;
      }).join("")}</div>
    </div>
    <div class="blk-x">${days.map((d, i) =>
      `<span>${i % 5 === 0 || i === days.length - 1 ? escapeHtml(i === days.length - 1 ? "Today" : addisLabel(d.start)) : ""}</span>`).join("")}</div>
    <div class="blk-tip hidden" role="status"></div>`;

  const tip = dauChart.querySelector(".blk-tip");
  const plot = dauChart.querySelector(".blk-cols");
  const show = (col) => {
    if (!col) { tip.classList.add("hidden"); return; }
    tip.innerHTML = `<strong>${escapeHtml(col.dataset.label)}</strong><span>Active users <b>${col.dataset.n}</b></span>`;
    tip.classList.remove("hidden");
    const box = dauChart.getBoundingClientRect();
    const c = col.getBoundingClientRect();
    const x = Math.min(Math.max(c.left - box.left + c.width / 2, 70), box.width - 70);
    tip.style.left = `${x}px`;
  };
  plot.addEventListener("pointerover", (e) => show(e.target.closest(".blk-col")));
  plot.addEventListener("pointerleave", () => show(null));
}

const BANK_TONES = ["tone-green", "tone-orange", "tone-blue"];

function renderBankPanel(rows) {
  const banks = rows.filter((r) => r.texts > 0).sort((a, b) => b.parsed - a.parsed);
  const parsed = sum(banks, "parsed");
  const texts = sum(banks, "texts");
  $("stat-parsed-7d").textContent = num(parsed);
  if (texts) {
    const rate = (parsed / texts) * 100;
    const approx = banks.some((r) => r.approx) ? "≈" : "";
    setTrend("banks", `${approx}${rate.toFixed(1)}% parsed`, rate >= PARSE_RATE_TARGET ? "up" : "down");
  }
  if (!banks.length) { bankPanel.innerHTML = `<p class="muted small">No bank SMS in the last 7 days.</p>`; return; }

  const top = banks.slice(0, 3);
  bankPanel.innerHTML = `
    <div class="pillars">${top.map((b, i) => `
      <div class="pillar ${BANK_TONES[i]}" title="${escapeAttr(b.sender_group)}: ${b.rate.toFixed(1)}% of money texts parsed">
        <div class="pillar-track"><div class="pillar-fill" style="height:${Math.min(100, b.rate)}%"></div></div>
        <span>${b.rate.toFixed(0)}%</span></div>`).join("")}</div>
    <p class="nav-label">Parse rate, top banks · notices left out</p>
    <ul class="bank-list">${banks.slice(0, 5).map((b, i) => `
      <li><span class="dot-c ${BANK_TONES[i] || "tone-grey"}"></span><span class="bank-name">${escapeHtml(b.sender_group)}</span>
      <span class="bank-num">${num(b.parsed)}${b.missed ? ` <span class="text-bad">· ${num(b.missed)} missed</span>` : ""}</span></li>`).join("")}
    </ul>`;
}

function renderPhones(users) {
  const rows = users.filter((u) => u.status_reported_at)
    .sort((a, b) => new Date(b.status_reported_at) - new Date(a.status_reported_at))
    .slice(0, 6);
  if (!rows.length) { phonesTable.innerHTML = `<tbody><tr><td class="muted">No phone has reported yet.</td></tr></tbody>`; return; }
  const latest = users.map((u) => versionOf(u.app_version)).filter(Boolean).sort(compareVersions).pop();
  phonesTable.innerHTML = `
    <thead><tr><th>User</th><th>App version</th><th>Last sync</th><th class="num">Waiting to sync</th><th>Status</th></tr></thead>
    <tbody>${rows.map((u) => {
      const v = versionOf(u.app_version);
      const waiting = Number(u.unsynced_transactions || 0) + Number(u.unsynced_raw || 0);
      const status = u.last_sync_error ? ["pill-bad", "Sync error"]
        : v && latest && compareVersions(v, latest) < 0 ? ["pill-warn", "Old version"]
        : ["pill-good", "Healthy"];
      return `<tr>
        <td><div class="user-cell"><span class="name">${escapeHtml(u.name || u.email || "—")}</span>${u.name ? `<span class="email">${escapeHtml(u.email || "")}</span>` : ""}</div></td>
        <td>${v ? escapeHtml(v) : `<span class="muted">—</span>`}</td>
        <td class="muted">${u.last_sync_ok_at ? formatRelative(u.last_sync_ok_at) : "—"}</td>
        <td class="num">${num(waiting)}</td>
        <td><span class="pill ${status[0]}">● ${status[1]}</span></td>
      </tr>`;
    }).join("")}</tbody>`;
}

// ----------------------------------------------------------------------------
// 1. Parser
// ----------------------------------------------------------------------------
// ----------------------------------------------------------------------------
// Money text or notice? Mirrors SmsParserEngine (app/src/main/java/com/
// financeapp/parsing/SmsParserEngine.kt, RULES_VERSION 4): a text is a notice
// if it matches an ignore pattern, otherwise it is money only when it has a
// birr amount next to a money word. Keep the two in sync.
// ----------------------------------------------------------------------------
const NOTICE_PATTERNS = [
  /\bOTP\b/i, /\bverification code\b/i, /\bone[- ]time password\b/i, /\bsecurity code\b/i,
  /\bactivation code\b/i, /^<#>\s*\d+/m, /\bYour otp is\b/i, /\bPIN[\s:]+\d{3,}\b/i,
  /please keep this code private/i, /\b(do not share|don't share|please don't share)\b/i,
  /ATM withdrawal secret code/i, /CBE Birr ATM cash out voucher/i,
  /insufficient balance/i, /\bSorry,\s*You have insufficient/i,
  /combanketh\.et[^\s]*\/holidays/i, /\blottery ticket\b/i,
  /እንኳን/, /ዓለም ዋንጫ/, /ኢድ ሙባረክ/, /መልካም በዓል/, /BYD/, /ፋይዳ/,
  /\bFayda\s+(ID|Alias\s+Number|FAN)\b/i, /link\s+(?:your\s+)?Fayda/i,
  /\bis\s+\d+\s+days?\s+overdue\b/i, /\bsuccessfully\s+activated\s+the\b.*\bservice\b/i,
  /\binsufficient\b/i, /\bfor\s+T&C\b/i, /\bEKSIPOO\b|\bFEESTIVAALA\b/i, /ኤክስፖ/, /ቨርቹዋል/,
  /using\s+Ethiotel\s+Credit/i, /pay\s+your\s+credit\s+within/i, /withdraw\s+secret\s+code\s+is/i,
  /collect\s+transfer\s+amount\s+with\s+this\s+Secret\s+code/i, /lottery\s+ID/i,
  /bonus\s+that\s+is\s+used\s+for\s+airtime/i, /request\s+to\s+buy\s+package\s+was\s+unsuccessful/i,
  /airtime\s+from\s+\d/i, /account\s+balance\s+is\s*:/i, /BIRR\s+School\s+Fee\s+for/i,
  /USD\s*[\d,.]+\s+is\s+Locked/i, /payment\s+request\s+of\s+ETB\s*[\d,.]+\s+is\s+pending/i,
  /Package\s+Purchase\s+request.*?is\s+reversed/is, /to\s+your\s+\w+\s+account\s+from\s+Awash\s+Bank/i,
];
const AMOUNT_RE = /(?:ETB|Birr|Br\.?)\s*[\d,]+(?:\.\d+)?|[\d,]+(?:\.\d+)?\s*(?:ETB|Birr|Br\b|ብር)/i;
const MONEY_VERB_RE = /\b(credited|debited|deposited|withdrawn|withdrew|transferred|transfered|received|paid|sent|purchased?|payment|refunded|reversed)\b|ገቢ|ወጪ|ተላልፏል|ተቀብለዋል|ተከፍሏል/i;

function isMoneyText(body) {
  const text = String(body || "");
  if (NOTICE_PATTERNS.some((re) => re.test(text))) return false;
  return AMOUNT_RE.test(text) && MONEY_VERB_RE.test(text);
}

// The same template with different amounts, dates, accounts and names gets
// one key, so ten "debited ETB …" texts show as one thing to fix.
function smsShape(body) {
  return String(body || "")
    .replace(/https?:\/\/\S+/gi, "‹link›")
    .replace(/^Dear\s+(?:[^\s,!]+\s+){1,3}?(?=(?:your|you)\b)/i, "Dear ‹name› ")
    .replace(/^Dear\s+[^,!]{1,40}[,!]/i, "Dear ‹name›,")
    .replace(/[\d*Xx]{4,}/g, "‹#›")
    .replace(/\d[\d,.:/-]*/g, "‹#›")
    .replace(/\s+/g, " ")
    .trim();
}

function groupByShape(rows) {
  const groups = new Map();
  for (const r of rows) {
    const shape = smsShape(r.raw_message);
    const key = `${r.sender_group}|${shape.slice(0, 70)}`;
    let g = groups.get(key);
    if (!g) groups.set(key, g = { bank: r.sender_group, shape, sample: r.raw_message, count: 0, users: new Set(), last: r.received_at });
    g.count++;
    g.users.add(r.user_id);
    if (r.received_at > g.last) { g.last = r.received_at; g.sample = r.raw_message; }
  }
  return [...groups.values()].sort((a, b) => b.count - a.count || String(b.last).localeCompare(String(a.last)));
}

// Per bank, last 7 days: texts that should become transactions, how many did,
// and which unparsed ones were really notices. The queue holds the 300 newest
// unparsed texts; if it doesn't reach back 7 days the split is approximate.
function parserStats(parseRows, queue) {
  const since = Date.now() - 7 * DAY_MS;
  const recent = (queue || []).filter((r) => new Date(r.received_at).getTime() >= since);
  const reachesBack = !queue || queue.length < 300 ||
    queue.some((r) => new Date(r.received_at).getTime() < since);
  return parseRows.map((r) => {
    const parsed = Number(r.parsed || 0);
    const unparsed = Number(r.unparsed || 0);
    const mine = recent.filter((q) => q.sender_group === r.sender_group);
    const missed = queue ? mine.filter((q) => isMoneyText(q.raw_message)).length : unparsed;
    const notices = Math.max(0, unparsed - missed) + Number(r.ignored || 0);
    const texts = parsed + missed;
    return {
      ...r, parsed, missed, notices, texts,
      rate: texts ? (parsed / texts) * 100 : null,
      approx: !reachesBack,
    };
  });
}

function renderParseRate(rows, queue) {
  const parsed = sum(rows, "parsed");
  const missed = sum(rows, "missed");
  const texts = parsed + missed;
  const approx = rows.some((r) => r.approx);
  const rate = texts ? (parsed / texts) * 100 : null;

  setStat("real-rate", rate == null ? "—" : `${approx ? "≈" : ""}${rate.toFixed(1)}%`,
    texts ? `${num(parsed)} of ${num(texts)} money texts became transactions` : "no bank texts this week");
  const missedBanks = rows.filter((r) => r.missed > 0);
  setStat("missed", num(missed), missed
    ? missedBanks.map((r) => `${r.sender_group} ${r.missed}`).join(" · ")
    : "nothing slipped through");

  const bad = rate != null && rate < PARSE_RATE_TARGET;
  parserVerdict.className = `verdict ${missed === 0 ? "verdict-good" : bad ? "verdict-bad" : "verdict-warn"}`;
  parserVerdict.innerHTML = missed === 0
    ? `<strong>The parser is healthy.</strong> No money texts were missed in the last 7 days. Everything left unparsed is a notice.`
    : `<strong>${num(missed)} money text${missed === 1 ? "" : "s"} missed in the last 7 days</strong> (${
        missedBanks.map((r) => `${escapeHtml(r.sender_group)} ${r.missed}`).join(", ")}). The shapes to fix are under “Fix next”.`;

  parseTable.innerHTML = `
    <thead><tr>
      <th>Bank</th><th class="num">Money texts</th><th class="num">Parsed</th><th class="num">Missed</th>
      <th class="num">Notices</th><th class="num">Users</th><th>Last SMS</th><th class="num">Parse rate</th>
    </tr></thead>
    <tbody>${rows.map((r) => `
      <tr>
        <td><strong>${escapeHtml(r.sender_group)}</strong></td>
        <td class="num">${num(r.texts)}</td>
        <td class="num">${num(r.parsed)}</td>
        <td class="num ${r.missed > 0 ? "text-bad" : "muted"}">${num(r.missed)}</td>
        <td class="num muted">${num(r.notices)}</td>
        <td class="num">${num(r.users)}</td>
        <td class="muted">${formatRelative(r.last_received_at) || "—"}</td>
        <td class="num">${r.texts ? rateBadge(r.rate, r.approx) : `<span class="muted">${r.notices ? "only notices" : "no SMS"}</span>`}</td>
      </tr>`).join("")}
    </tbody>`;
}

function rateBadge(rate, approx = false) {
  if (rate == null) return `<span class="muted">no SMS</span>`;
  const r = Number(rate);
  return `<span class="pill ${r < PARSE_RATE_TARGET ? "pill-bad" : "pill-good"}">${approx ? "≈" : ""}${r.toFixed(1)}%</span>`;
}

// Every SMS not turned into a transaction, for writing parsers: sender,
// bank, status, date and text. No user ids or emails. Pages through
// i_raw_data 1,000 rows at a time (admin SELECT policy).
async function exportUnparsedSms(btn) {
  const label = btn.textContent;
  btn.disabled = true;
  try {
    const rows = [];
    for (let from = 0; ; from += 1000) {
      btn.textContent = `Fetching… ${num(rows.length)}`;
      const { data, error } = await supabase.from("i_raw_data")
        .select("sender_name, bank_name, parse_status, received_at, created_at, raw_message")
        .or("parse_status.is.null,parse_status.neq.PARSED")
        .order("id", { ascending: true })
        .range(from, from + 999);
      if (error) throw error;
      rows.push(...data);
      if (data.length < 1000) break;
    }
    const cell = (v) => `"${String(v ?? "").replace(/"/g, '""')}"`;
    const csv = ["sender_name,bank_name,parse_status,received_at,raw_message"]
      .concat(rows.map((r) => [r.sender_name, r.bank_name, r.parse_status || "NO_STATUS",
        r.received_at || r.created_at, r.raw_message].map(cell).join(",")))
      .join("\n");
    const a = document.createElement("a");
    a.href = URL.createObjectURL(new Blob(["\uFEFF" + csv], { type: "text/csv;charset=utf-8" }));
    a.download = "kash_unparsed_sms.csv";
    a.click();
    URL.revokeObjectURL(a.href);
    btn.textContent = `Downloaded ${num(rows.length)} SMS`;
  } catch (e) {
    console.error(e);
    btn.textContent = `Couldn't export: ${e.message || e}`;
  } finally {
    btn.disabled = false;
    setTimeout(() => { btn.textContent = label; }, 6000);
  }
}
document.getElementById("export-sms-btn")?.addEventListener("click", (e) => exportUnparsedSms(e.currentTarget));

function renderUnparsed(rows) {
  const money = rows.filter((r) => isMoneyText(r.raw_message));
  const notices = rows.filter((r) => !isMoneyText(r.raw_message));
  const capped = rows.length >= 300 ? "+" : "";
  $("stat-unparsed-sub").textContent = `${num(rows.length)}${capped} texts · ${num(money.length)} money · ${num(notices.length)} notices`;
  $("notice-count").textContent = `${num(notices.length)}${capped} texts · nothing to fix`;
  renderShapes(fixTable, groupByShape(money), "No money texts are waiting. Every unparsed text is a notice. 🎉");
  renderShapes(noticeTable, groupByShape(notices), "No notices in the queue.");
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

function renderShapes(table, groups, emptyText) {
  if (!groups.length) { table.innerHTML = `<tbody><tr><td class="muted">${emptyText}</td></tr></tbody>`; return; }
  table.innerHTML = `
    <thead><tr><th>Bank</th><th>Shape</th><th class="num">Texts</th><th class="num">Users</th><th>Last seen</th></tr></thead>
    <tbody>${groups.slice(0, 40).map((g) => `
      <tr>
        <td><span class="pill">${escapeHtml(g.bank)}</span></td>
        <td class="shape-cell"><details><summary title="Show the newest full text">${escapeHtml(g.shape)}</summary><pre class="sms-body">${escapeHtml(g.sample)}</pre></details></td>
        <td class="num"><strong>${num(g.count)}</strong></td>
        <td class="num">${num(g.users.size)}</td>
        <td class="muted">${formatRelative(g.last) || "—"}</td>
      </tr>`).join("")}
    </tbody>`;
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

// Banks Kash doesn't follow yet (supabase/money_senders.sql).
function renderMoneySenders(rows) {
  if (!rows.length) { moneySendersTable.innerHTML = `<tbody><tr><td class="muted">None reported yet. Phones on 1.0.11+ report weekly.</td></tr></tbody>`; return; }
  moneySendersTable.innerHTML = `
    <thead><tr>
      <th>Sender</th><th class="num">Users</th><th class="num">Messages</th><th>Last SMS</th><th>Reported</th>
    </tr></thead>
    <tbody>${rows.map((r) => `
      <tr>
        <td><strong>${escapeHtml(r.sender)}</strong></td>
        <td class="num">${num(r.users)}</td>
        <td class="num">${num(r.messages)}</td>
        <td class="muted">${formatRelative(r.last_sms_at) || "—"}</td>
        <td class="muted">${formatRelative(r.last_reported_at) || "—"}</td>
      </tr>`).join("")}
    </tbody>`;
}

function renderQuality(q) {
  if (!q) return;
  setStat("dup-7d", pct(q.duplicates_7d, q.total_7d), `${num(q.duplicates_7d)} of ${num(q.total_7d)} new rows`);
  setStat("unknown", pct(q.unknown, q.total), `${num(q.unknown)} rows typed UNKNOWN · ${num(q.unknown_7d)} this week`);
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
