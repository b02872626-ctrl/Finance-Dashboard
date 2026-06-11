// =============================================================================
// Finance Lore — Admin dashboard.
//
// Lists every user of the app with their session/time-spent metrics. The
// admin gate is enforced on TWO layers:
//   1. Client-side allowlist (ADMIN_EMAIL) — controls what UI is rendered.
//   2. Postgres RLS policies (see supabase/add_admin_sessions.sql) — every
//      SELECT against i_users / i_sessions / i_transactions / i_raw_data
//      requires either user_id = auth.uid() OR is_admin(). If the JWT email
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

const supabase = createClient(SUPABASE_URL, SUPABASE_ANON, {
  auth: { persistSession: true, autoRefreshToken: true },
});

// ----------------------------------------------------------------------------
// DOM
// ----------------------------------------------------------------------------
const $ = (id) => document.getElementById(id);

const loginView   = $("login-view");
const deniedView  = $("denied-view");
const adminShell  = $("admin-shell");
const drillPane   = $("drill-pane");

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

const statUsers          = $("stat-users");
const statTotalTime      = $("stat-total-time");
const statTotalSessions  = $("stat-total-sessions");
const statActiveNow      = $("stat-active-now");
const statNew7d          = $("stat-new-7d");
const statAvgSession     = $("stat-avg-session");
const statDau            = $("stat-dau");
const statWau            = $("stat-wau");

const chartDaily      = $("chart-daily");
const chartHour       = $("chart-hour");
const chartWeekday    = $("chart-weekday");
const chartTopUsers   = $("chart-top-users");

const usersSearch  = $("users-search");
const usersSort    = $("users-sort");
const usersTbody   = $("users-tbody");

const drillEmail        = $("drill-email");
const drillName         = $("drill-name");
const drillTotalTime    = $("drill-total-time");
const drillSessionCount = $("drill-session-count");
const drillAvgSession   = $("drill-avg-session");
const drillLastSeen     = $("drill-last-seen");
const drillTxns         = $("drill-txns");
const drillRaw          = $("drill-raw");
const drillSessions     = $("drill-sessions");

const state = {
  users: [],       // rows from admin_user_overview
  sessions: [],    // rows from i_sessions (last 30 days)
  search: "",
  sort: "last_session_at",
};

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
  loginView.classList.remove("hidden");
  deniedView.classList.add("hidden");
  adminShell.classList.add("hidden");
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
// Data loading
// ----------------------------------------------------------------------------
async function loadOverview() {
  usersTbody.innerHTML = `<tr><td colspan="8" class="muted">Loading…</td></tr>`;
  setChartsLoading();

  const thirtyDaysAgo = new Date(Date.now() - 30 * 24 * 60 * 60 * 1000).toISOString();
  const [usersRes, sessionsRes] = await Promise.all([
    supabase.from("admin_user_overview").select("*"),
    supabase
      .from("i_sessions")
      .select("user_id, client, app_version, started_at, last_heartbeat_at, ended_at, duration_seconds")
      .gte("started_at", thirtyDaysAgo)
      .order("started_at", { ascending: false })
      .limit(10000),
  ]);

  if (usersRes.error) {
    const hint = /relation .* does not exist/i.test(usersRes.error.message)
      ? `Schema is out of date — run <code>supabase/add_admin_sessions.sql</code> in the Supabase SQL editor.`
      : escapeHtml(usersRes.error.message);
    usersTbody.innerHTML = `<tr><td colspan="8" class="muted">${hint}</td></tr>`;
    setChartsEmpty(hint);
    return;
  }
  state.users = usersRes.data || [];
  state.sessions = sessionsRes.error ? [] : (sessionsRes.data || []);

  // Derive latest app_version per user from the 30-day session log. Sessions
  // are ordered started_at DESC, so the first hit per user_id wins. We carry
  // the client too so the table can show e.g. "web-beta17" vs "android-beta17".
  // Users with no recent session keep app_version = null and render "—".
  const latestByUser = new Map();
  for (const s of state.sessions) {
    if (latestByUser.has(s.user_id)) continue;
    if (!s.app_version) continue;
    latestByUser.set(s.user_id, { version: s.app_version, client: s.client });
  }
  for (const u of state.users) {
    const v = latestByUser.get(u.id);
    u.app_version = v ? v.version : null;
    u.last_client = v ? v.client : null;
  }

  renderStats();
  renderUsers();
  renderCharts();
}

function renderStats() {
  const users = state.users;
  const sessions = state.sessions;
  const totalSeconds = users.reduce((acc, u) => acc + Number(u.total_seconds || 0), 0);
  const totalSessions = users.reduce((acc, u) => acc + Number(u.session_count || 0), 0);
  const twoMinAgo = Date.now() - 2 * 60 * 1000;
  const sevenDaysAgo = Date.now() - 7 * 24 * 60 * 60 * 1000;
  const startOfToday = startOfLocalDay(new Date()).getTime();

  const activeNow = users.filter((u) => {
    const t = u.last_session_at ? new Date(u.last_session_at).getTime() : 0;
    return t >= twoMinAgo;
  }).length;

  const new7d = users.filter((u) => {
    const t = u.account_created_at ? new Date(u.account_created_at).getTime() : 0;
    return t >= sevenDaysAgo;
  }).length;

  const avgSeconds = totalSessions > 0 ? Math.round(totalSeconds / totalSessions) : 0;

  // DAU / WAU are computed from i_sessions (last 30d window). DAU = distinct
  // user_ids with a session that started today (local). WAU = last 7 days.
  const dauSet = new Set();
  const wauSet = new Set();
  for (const s of sessions) {
    const t = new Date(s.started_at).getTime();
    if (t >= startOfToday) dauSet.add(s.user_id);
    if (t >= sevenDaysAgo) wauSet.add(s.user_id);
  }

  statUsers.textContent = users.length.toLocaleString();
  statTotalTime.textContent = formatDuration(totalSeconds);
  statTotalSessions.textContent = totalSessions.toLocaleString();
  statActiveNow.textContent = activeNow.toString();
  statNew7d.textContent = new7d.toLocaleString();
  statAvgSession.textContent = avgSeconds ? formatDuration(avgSeconds) : "—";
  statDau.textContent = dauSet.size.toLocaleString();
  statWau.textContent = wauSet.size.toLocaleString();
}

function renderUsers() {
  const q = state.search.trim().toLowerCase();
  const filtered = q
    ? state.users.filter((u) =>
        (u.email || "").toLowerCase().includes(q) ||
        (u.name || "").toLowerCase().includes(q))
    : [...state.users];

  filtered.sort((a, b) => compareRows(a, b, state.sort));

  if (!filtered.length) {
    usersTbody.innerHTML = `<tr><td colspan="8" class="muted">No users match.</td></tr>`;
    return;
  }

  const twoMinAgo = Date.now() - 2 * 60 * 1000;
  usersTbody.innerHTML = filtered.map((u) => {
    const lastT = u.last_session_at ? new Date(u.last_session_at).getTime() : 0;
    const live = lastT >= twoMinAgo;
    return `
      <tr data-user-id="${escapeAttr(u.id)}">
        <td>
          <div class="user-cell">
            <span class="name">${live ? '<span class="dot-online" title="Active"></span>' : ''}${escapeHtml(u.name || "—")}</span>
            <span class="email">${escapeHtml(u.email || "")}</span>
          </div>
        </td>
        <td>${formatDate(u.account_created_at)}</td>
        <td>${formatRelative(u.last_session_at) || formatRelative(u.last_sign_in_at) || '—'}</td>
        <td>${formatVersionCell(u)}</td>
        <td class="num">${(u.session_count || 0).toLocaleString()}</td>
        <td class="num">${formatDuration(u.total_seconds || 0)}</td>
        <td class="num">${(u.txn_count || 0).toLocaleString()}</td>
        <td class="num">${(u.raw_count || 0).toLocaleString()}</td>
      </tr>
    `;
  }).join("");
}

function compareRows(a, b, key) {
  const av = a[key]; const bv = b[key];
  if (key === "account_created_at" || key === "last_session_at") {
    return (new Date(bv || 0).getTime()) - (new Date(av || 0).getTime());
  }
  return Number(bv || 0) - Number(av || 0);
}

usersSearch.addEventListener("input", (e) => {
  state.search = e.target.value;
  renderUsers();
});
usersSort.addEventListener("change", (e) => {
  state.sort = e.target.value;
  renderUsers();
});

usersTbody.addEventListener("click", (e) => {
  const tr = e.target.closest("tr[data-user-id]");
  if (!tr) return;
  const id = tr.getAttribute("data-user-id");
  const user = state.users.find((u) => u.id === id);
  if (user) openDrill(user);
});

// ----------------------------------------------------------------------------
// Drill-in
// ----------------------------------------------------------------------------
async function openDrill(user) {
  drillEmail.textContent = user.email || "—";
  drillName.textContent = user.name || user.email || "User";
  drillTotalTime.textContent = formatDuration(user.total_seconds || 0);
  drillSessionCount.textContent = (user.session_count || 0).toLocaleString();
  const avg = (user.session_count || 0) > 0
    ? Math.round(Number(user.total_seconds || 0) / Number(user.session_count))
    : 0;
  drillAvgSession.textContent = avg ? formatDuration(avg) : "—";
  drillLastSeen.textContent = formatRelative(user.last_session_at) || "—";
  drillTxns.textContent = (user.txn_count || 0).toLocaleString();
  drillRaw.textContent = (user.raw_count || 0).toLocaleString();

  drillSessions.innerHTML = `<p class="muted">Loading…</p>`;
  drillPane.classList.remove("hidden");
  drillPane.setAttribute("aria-hidden", "false");

  const { data, error } = await supabase
    .from("i_sessions")
    .select("id, client, started_at, last_heartbeat_at, ended_at, duration_seconds")
    .eq("user_id", user.id)
    .order("started_at", { ascending: false })
    .limit(200);

  if (error) {
    drillSessions.innerHTML = `<p class="muted">Couldn't load sessions: ${escapeHtml(error.message)}</p>`;
    return;
  }

  const sessions = data || [];
  if (!sessions.length) {
    drillSessions.innerHTML = `<p class="muted">No sessions recorded yet.</p>`;
    return;
  }
  const twoMinAgo = Date.now() - 2 * 60 * 1000;
  drillSessions.innerHTML = sessions.map((s) => {
    const live = !s.ended_at && new Date(s.last_heartbeat_at).getTime() >= twoMinAgo;
    const dur = s.duration_seconds != null
      ? Number(s.duration_seconds)
      : Math.max(0, Math.round((new Date(s.last_heartbeat_at).getTime() - new Date(s.started_at).getTime()) / 1000));
    return `
      <div class="session-row ${live ? 'live' : ''}">
        <div class="marker" title="${live ? 'Live' : 'Ended'}"></div>
        <div>
          <span class="time">${formatDateTime(s.started_at)}</span>
          <span class="client">${escapeHtml(s.client || '')}</span>
        </div>
        <div class="dur">${live ? 'Live · ' : ''}${formatDuration(dur)}</div>
      </div>
    `;
  }).join("");
}

function closeDrill() {
  drillPane.classList.add("hidden");
  drillPane.setAttribute("aria-hidden", "true");
}

drillPane.addEventListener("click", (e) => {
  if (e.target.matches("[data-drill-close]")) closeDrill();
});
document.addEventListener("keydown", (e) => {
  if (e.key === "Escape" && !drillPane.classList.contains("hidden")) closeDrill();
});

// ----------------------------------------------------------------------------
// Charts — inline SVG, no external libs (matches the main app's approach).
// All charts use viewBox so they scale to whatever width the card gives them.
// ----------------------------------------------------------------------------
function setChartsLoading() {
  for (const el of [chartDaily, chartHour, chartWeekday, chartTopUsers]) {
    if (el) el.innerHTML = `<p class="muted">Loading…</p>`;
  }
}
function setChartsEmpty(msg) {
  for (const el of [chartDaily, chartHour, chartWeekday, chartTopUsers]) {
    if (el) el.innerHTML = `<p class="muted">${msg || "No data."}</p>`;
  }
}

function renderCharts() {
  renderDailyChart();
  renderHourChart();
  renderWeekdayChart();
  renderTopUsersChart();
}

function startOfLocalDay(d) {
  const x = new Date(d);
  x.setHours(0, 0, 0, 0);
  return x;
}

// 30-day bar chart of sessions per day. Bars are split: count + total time.
function renderDailyChart() {
  const days = 30;
  const buckets = new Array(days).fill(0).map((_, i) => ({
    label: "",
    date: null,
    sessions: 0,
    seconds: 0,
  }));
  const today = startOfLocalDay(new Date());
  for (let i = 0; i < days; i++) {
    const d = new Date(today.getTime() - (days - 1 - i) * 86400000);
    buckets[i].date = d;
    buckets[i].label = d.toLocaleDateString(undefined, { month: "short", day: "numeric" });
  }
  for (const s of state.sessions) {
    const t = new Date(s.started_at).getTime();
    const idx = Math.floor((t - (today.getTime() - (days - 1) * 86400000)) / 86400000);
    if (idx < 0 || idx >= days) continue;
    buckets[idx].sessions += 1;
    buckets[idx].seconds += Math.max(0, Number(s.duration_seconds || 0));
  }

  if (state.sessions.length === 0) {
    chartDaily.innerHTML = `<p class="muted">No sessions in the last 30 days yet.</p>`;
    return;
  }

  const W = 600, H = 180, padL = 8, padR = 8, padT = 12, padB = 28;
  const innerW = W - padL - padR;
  const innerH = H - padT - padB;
  const maxSessions = Math.max(1, ...buckets.map((b) => b.sessions));
  const barW = innerW / buckets.length;
  const gap = Math.min(2, barW * 0.18);

  const bars = buckets.map((b, i) => {
    const x = padL + i * barW + gap / 2;
    const w = barW - gap;
    const h = (b.sessions / maxSessions) * innerH;
    const y = padT + innerH - h;
    const tip = `${b.label} · ${b.sessions} sessions · ${formatDuration(b.seconds)}`;
    const color = b.sessions ? "var(--headline)" : "rgba(127,227,203,0.18)";
    return `<rect class="chart-bar" x="${x.toFixed(2)}" y="${y.toFixed(2)}" width="${w.toFixed(2)}" height="${Math.max(2, h).toFixed(2)}" rx="2" fill="${color}"><title>${escapeHtml(tip)}</title></rect>`;
  }).join("");

  // x-axis ticks every 5 days
  const ticks = buckets.map((b, i) => {
    if (i % 5 !== 0 && i !== buckets.length - 1) return "";
    const x = padL + i * barW + barW / 2;
    return `<text x="${x.toFixed(2)}" y="${(H - 8).toFixed(2)}" class="chart-axis-label" text-anchor="middle">${escapeHtml(b.label)}</text>`;
  }).join("");

  const totalSessions = buckets.reduce((a, b) => a + b.sessions, 0);
  const totalSeconds = buckets.reduce((a, b) => a + b.seconds, 0);
  document.getElementById("chart-daily-sub").textContent =
    `${totalSessions.toLocaleString()} sessions · ${formatDuration(totalSeconds)}`;

  chartDaily.innerHTML = `<svg viewBox="0 0 ${W} ${H}" preserveAspectRatio="none">${bars}${ticks}</svg>`;
}

function renderHourChart() {
  const buckets = new Array(24).fill(0);
  for (const s of state.sessions) {
    const h = new Date(s.started_at).getHours();
    if (h >= 0 && h < 24) buckets[h] += 1;
  }
  if (state.sessions.length === 0) { chartHour.innerHTML = `<p class="muted">No data.</p>`; return; }

  const W = 320, H = 160, padL = 8, padR = 8, padT = 12, padB = 24;
  const innerW = W - padL - padR;
  const innerH = H - padT - padB;
  const max = Math.max(1, ...buckets);
  const barW = innerW / 24;
  const gap = Math.min(2, barW * 0.2);

  const bars = buckets.map((v, i) => {
    const x = padL + i * barW + gap / 2;
    const w = barW - gap;
    const h = (v / max) * innerH;
    const y = padT + innerH - h;
    const color = v ? "var(--green-income)" : "rgba(76,212,149,0.18)";
    return `<rect class="chart-bar" x="${x.toFixed(2)}" y="${y.toFixed(2)}" width="${w.toFixed(2)}" height="${Math.max(2, h).toFixed(2)}" rx="2" fill="${color}"><title>${i}:00 — ${v}</title></rect>`;
  }).join("");

  const ticks = [0, 6, 12, 18, 23].map((i) => {
    const x = padL + i * barW + barW / 2;
    return `<text x="${x.toFixed(2)}" y="${(H - 6).toFixed(2)}" class="chart-axis-label" text-anchor="middle">${i}</text>`;
  }).join("");

  chartHour.innerHTML = `<svg viewBox="0 0 ${W} ${H}" preserveAspectRatio="none">${bars}${ticks}</svg>`;
}

function renderWeekdayChart() {
  const labels = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];
  const buckets = new Array(7).fill(0);
  for (const s of state.sessions) buckets[new Date(s.started_at).getDay()] += 1;
  if (state.sessions.length === 0) { chartWeekday.innerHTML = `<p class="muted">No data.</p>`; return; }

  const W = 320, H = 160, padL = 12, padR = 12, padT = 12, padB = 24;
  const innerW = W - padL - padR;
  const innerH = H - padT - padB;
  const max = Math.max(1, ...buckets);
  const barW = innerW / 7;
  const gap = Math.min(6, barW * 0.25);

  const bars = buckets.map((v, i) => {
    const x = padL + i * barW + gap / 2;
    const w = barW - gap;
    const h = (v / max) * innerH;
    const y = padT + innerH - h;
    return `<rect class="chart-bar" x="${x.toFixed(2)}" y="${y.toFixed(2)}" width="${w.toFixed(2)}" height="${Math.max(2, h).toFixed(2)}" rx="3" fill="var(--headline)"><title>${labels[i]} — ${v}</title></rect>`;
  }).join("");

  const ticks = labels.map((lab, i) => {
    const x = padL + i * barW + barW / 2;
    return `<text x="${x.toFixed(2)}" y="${(H - 6).toFixed(2)}" class="chart-axis-label" text-anchor="middle">${lab}</text>`;
  }).join("");

  chartWeekday.innerHTML = `<svg viewBox="0 0 ${W} ${H}" preserveAspectRatio="none">${bars}${ticks}</svg>`;
}

// Horizontal bar chart, top 10 users by total_seconds.
function renderTopUsersChart() {
  const top = [...state.users]
    .filter((u) => Number(u.total_seconds || 0) > 0)
    .sort((a, b) => Number(b.total_seconds || 0) - Number(a.total_seconds || 0))
    .slice(0, 10);

  if (!top.length) { chartTopUsers.innerHTML = `<p class="muted">No session data yet.</p>`; return; }

  const max = Math.max(1, ...top.map((u) => Number(u.total_seconds || 0)));
  chartTopUsers.innerHTML = top.map((u) => {
    const sec = Number(u.total_seconds || 0);
    const pct = Math.max(2, Math.round((sec / max) * 100));
    const name = u.name || u.email || u.id.slice(0, 8);
    return `
      <div class="hbar-row" data-user-id="${escapeAttr(u.id)}">
        <div class="hbar-label" title="${escapeAttr(u.email || '')}">${escapeHtml(name)}</div>
        <div class="hbar-track"><div class="hbar-fill" style="width:${pct}%"></div></div>
        <div class="hbar-value">${formatDuration(sec)}</div>
      </div>
    `;
  }).join("");
}

// Click a top-user bar to drill in.
document.addEventListener("click", (e) => {
  const row = e.target.closest(".hbar-row[data-user-id]");
  if (!row) return;
  const id = row.getAttribute("data-user-id");
  const user = state.users.find((u) => u.id === id);
  if (user) openDrill(user);
});

// ----------------------------------------------------------------------------
// Formatters
// ----------------------------------------------------------------------------
// Renders the per-user Version cell. Strings like "web-beta17" or
// "android-beta17" get split into a small "WEB"/"ANDROID" client tag plus
// the version label so the column is scannable. Unknown / missing values
// render as a muted em-dash.
function formatVersionCell(u) {
  const v = u.app_version;
  if (!v) return '<span class="muted">—</span>';
  // Common patterns: "web-beta17", "android-1.0.0-beta17", "web-1.2.3"
  let client = u.last_client || "";
  let label = v;
  const dash = v.indexOf("-");
  if (dash > 0) {
    const head = v.slice(0, dash).toLowerCase();
    if (head === "web" || head === "android" || head === "ios") {
      client = head;
      label = v.slice(dash + 1);
    }
  }
  const clientPill = client
    ? `<span class="version-client version-client-${escapeAttr(client)}">${escapeHtml(client.toUpperCase())}</span>`
    : "";
  return `<span class="version-cell">${clientPill}<code class="version-label">${escapeHtml(label)}</code></span>`;
}

function formatDuration(seconds) {
  const s = Math.max(0, Math.round(Number(seconds) || 0));
  if (s < 60) return `${s}s`;
  const m = Math.floor(s / 60);
  if (m < 60) return `${m}m ${s % 60}s`;
  const h = Math.floor(m / 60);
  if (h < 24) return `${h}h ${m % 60}m`;
  const d = Math.floor(h / 24);
  return `${d}d ${h % 24}h`;
}

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
