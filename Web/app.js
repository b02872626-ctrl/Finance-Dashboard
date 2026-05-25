// =============================================================================
// Finance Lore — Web companion
//
// Single-page app that signs the user into the same Supabase project as the
// Android app and renders their finance data (accounts, balance, transactions,
// category breakdown, daily/monthly trends, top counterparties, recurring
// spend, day-of-week patterns). All requests go directly to Supabase;
// Row-Level Security in secure_schema.sql restricts each user to their rows.
// =============================================================================

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

// ----------------------------------------------------------------------------
// Config — same project as the Android app (local.properties).
// The anon key is safe in client because the database uses RLS on auth.uid().
// ----------------------------------------------------------------------------
const SUPABASE_URL  = "https://sadbxjnmcgzwtjbqqhbk.supabase.co";
const SUPABASE_ANON = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InNhZGJ4am5tY2d6d3RqYnFxaGJrIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzQ2Mzg1NzcsImV4cCI6MjA5MDIxNDU3N30.aXA5I5CD--DjpnsbrHPZLebAmITNSDVmYDo1mkj3Gz4";

const supabase = createClient(SUPABASE_URL, SUPABASE_ANON, {
  auth: { persistSession: true, autoRefreshToken: true },
});

// ----------------------------------------------------------------------------
// DOM refs
// ----------------------------------------------------------------------------
const $ = (id) => document.getElementById(id);

const loginView      = $("login-view");
const dashboardView  = $("dashboard-view");
const loginForm      = $("login-form");
const loginBtn       = $("login-btn");
const retryBtn       = $("retry-btn");
const errorBox       = $("login-error");
const emailInput     = $("email");
const passwordInput  = $("password");
const userNameEl     = $("user-name");
const greetingEl     = $("greeting");
const logoutBtn      = $("logout-btn");
const refreshBtn     = $("refresh-btn");

// Stat cards
const statBalance       = $("stat-balance");
const statBalanceSub    = $("stat-balance-sub");
const statMonthSpend    = $("stat-month-spend");
const statMonthSpendSub = $("stat-month-spend-sub");
const statMonthIncome   = $("stat-month-income");
const statMonthIncomeSub= $("stat-month-income-sub");
const statNet           = $("stat-net");
const statNetSub        = $("stat-net-sub");
const statAvgDaily      = $("stat-avg-daily");
const statAvgDailySub   = $("stat-avg-daily-sub");
const statLargest       = $("stat-largest");
const statLargestSub    = $("stat-largest-sub");
const statPeriodLabel1  = $("stat-period-label-1");
const statPeriodLabel2  = $("stat-period-label-2");

// Lists / charts
const periodBar      = $("period-bar");
const accountsList   = $("accounts-list");
const accountsCount  = $("accounts-count");
const txTbody        = $("tx-tbody");
const txCount        = $("tx-count");
const txSearch       = $("tx-search");
const txTypeFilter   = $("tx-type-filter");
const csvBtn         = $("csv-btn");
const categoryList   = $("category-list");
const counterpartyList = $("counterparty-list");
const recurringList  = $("recurring-list");
const dailyChart     = $("daily-chart");
const dailyChartWin  = $("daily-chart-window");
const monthlyChart   = $("monthly-chart");
const weekdayStrip   = $("weekday-strip");
const projectRef     = $("project-ref");
const batchBar       = $("batch-bar");
const batchCount     = $("batch-count");
const batchPills     = $("batch-pills");
const batchClear     = $("batch-clear");
const batchApply     = $("batch-apply");
const toastEl        = $("toast");

// Auth shell + view tabs (dashboardView is already declared above)
const authShell      = $("auth-shell");
const viewTabs       = $("view-tabs");
const categoriesView = $("categories-view");
const ledgerView     = $("ledger-view");
const incomeView     = $("income-view");
const goalsView      = $("goals-view");

// Goals refs
const goalsBadge          = $("goals-badge");
const goalsTotalSaved     = $("goals-total-saved");
const goalsTotalSavedSub  = $("goals-total-saved-sub");
const goalsOnTrack        = $("goals-on-track");
const goalsOnTrackSub     = $("goals-on-track-sub");
const goalsNeeded         = $("goals-needed");
const goalsNeededSub      = $("goals-needed-sub");
const goalsList           = $("goals-list");
const goalsCount          = $("goals-count");
const goalHistoryList     = $("goal-history-list");
const goalHistoryToggle   = $("goal-history-toggle");

// Goal form refs
const goalFormToggle      = $("goal-form-toggle");
const goalForm            = $("goal-form");
const goalFormTitle       = $("goal-form-title");
const goalType            = $("goal-type");
const goalEmoji           = $("goal-emoji");
const goalName            = $("goal-name");
const goalTargetAmount    = $("goal-target-amount");
const goalTargetAmountField = $("goal-target-amount-field");
const goalDeadline        = $("goal-deadline");
const goalDeadlineField   = $("goal-deadline-field");
const goalTargetMonths    = $("goal-target-months");
const goalTargetMonthsField   = $("goal-target-months-field");
const goalTargetMonthsPreview = $("goal-target-months-preview");
const goalNote            = $("goal-note");
const goalCancel          = $("goal-cancel");
const goalSave            = $("goal-save");
const goalFormError       = $("goal-form-error");

// Goal contribution form refs
const goalContribToggle   = $("goal-contrib-toggle");
const goalContribForm     = $("goal-contrib-form");
const goalContribAmount   = $("goal-contrib-amount");
const goalContribDate     = $("goal-contrib-date");
const goalContribMode     = $("goal-contrib-mode");
const goalContribTarget   = $("goal-contrib-target");
const goalContribTargetField = $("goal-contrib-target-field");
const goalContribNote     = $("goal-contrib-note");
const goalContribCancel   = $("goal-contrib-cancel");
const goalContribSave     = $("goal-contrib-save");
const goalContribError    = $("goal-contrib-error");
const goalContribPreview  = $("goal-contrib-preview");

// Income refs
const incomeBadge          = $("income-badge");
const incomeActual         = $("income-actual");
const incomeActualSub      = $("income-actual-sub");
const incomeExpected       = $("income-expected");
const incomeExpectedSub    = $("income-expected-sub");
const incomeVariance       = $("income-variance");
const incomeVarianceSub    = $("income-variance-sub");
const incomePeriodLabel    = $("income-period-label");
const incomeSuggestions    = $("income-suggestions");
const incomeSuggCount      = $("income-sugg-count");
const incomeFormToggle     = $("income-form-toggle");
const incomeForm           = $("income-form");
const incomeType           = $("income-type");
const incomeAmount         = $("income-amount");
const incomeSource         = $("income-source");
const incomeCadence        = $("income-cadence");
const incomeCadenceField   = $("income-cadence-field");
const incomeIntervalField  = $("income-interval-field");
const incomeIntervalDays   = $("income-interval-days");
const incomeOccursOn       = $("income-occurs-on");
const incomeDateLabel      = $("income-date-label");
const incomeNote           = $("income-note");
const incomeCancel         = $("income-cancel");
const incomeSave           = $("income-save");
const incomeFormError      = $("income-form-error");
const incomeRecurringList  = $("income-recurring-list");
const incomeRecurringCount = $("income-recurring-count");
const incomeOneoffList     = $("income-oneoff-list");
const incomeOneoffCount    = $("income-oneoff-count");

// Ledger refs
const ledgerBadge       = $("ledger-badge");
const ledgerIOwe        = $("ledger-i-owe");
const ledgerIOweSub     = $("ledger-i-owe-sub");
const ledgerOwedToMe    = $("ledger-owed-to-me");
const ledgerOwedToMeSub = $("ledger-owed-to-me-sub");
const ledgerNet         = $("ledger-net");
const ledgerNetSub      = $("ledger-net-sub");
const ledgerFormToggle  = $("ledger-form-toggle");
const ledgerForm        = $("ledger-form");
const ledgerType        = $("ledger-type");
const ledgerDirection   = $("ledger-direction");
const ledgerCounterparty= $("ledger-counterparty");
const ledgerAmount      = $("ledger-amount");
const ledgerDue         = $("ledger-due");
const ledgerCadence     = $("ledger-cadence");
const ledgerCadenceField= $("ledger-cadence-field");
const ledgerIntervalField = $("ledger-interval-field");
const ledgerIntervalDays  = $("ledger-interval-days");
const ledgerNote        = $("ledger-note");
const ledgerCancel      = $("ledger-cancel");
const ledgerSave        = $("ledger-save");
const ledgerFormError   = $("ledger-form-error");
const ledgerOweList     = $("ledger-owe-list");
const ledgerLentList    = $("ledger-lent-list");
const ledgerOweCount    = $("ledger-owe-count");
const ledgerLentCount   = $("ledger-lent-count");
const ledgerClosedList  = $("ledger-closed-list");
const ledgerClosedToggle= $("ledger-closed-toggle");

// Category analysis refs
const catTotal        = $("cat-total");
const catTotalSub     = $("cat-total-sub");
const catTopName      = $("cat-top-name");
const catTopSub       = $("cat-top-sub");
const catUncategorised    = $("cat-uncategorised");
const catUncategorisedSub = $("cat-uncategorised-sub");
const catDonut        = $("cat-donut");
const catDonutLegend  = $("cat-donut-legend");
const catTrend        = $("cat-trend");
const catTrendLegend  = $("cat-trend-legend");
const catCards        = $("cat-cards");
const catDetail       = $("cat-detail");
const catDetailTitle  = $("cat-detail-title");
const catDetailClose  = $("cat-detail-close");
const catDetailTotal  = $("cat-detail-total");
const catDetailTotalSub = $("cat-detail-total-sub");
const catDetailTrend  = $("cat-detail-trend");
const catDetailTrendSub = $("cat-detail-trend-sub");
const catDetailAvg    = $("cat-detail-avg");
const catDetailAvgSub = $("cat-detail-avg-sub");
const catDetailTbody  = $("cat-detail-tbody");

// Palette for category colours (donut, legend, stack, card border)
const CAT_PALETTE = [
  "#4FB99F", "#E56B5C", "#3C6BC9", "#59C792",
  "#8A8F39", "#7BD0E5", "#D4A24C", "#B59BE0",
  "#E08AB6", "#6FAF82", "#C97A4B", "#8DA0CA",
];

projectRef.textContent = new URL(SUPABASE_URL).hostname.split(".")[0];

// ----------------------------------------------------------------------------
// State
// ----------------------------------------------------------------------------
const EXPENSE_TYPES = new Set(["DEBIT", "TRANSFER_OUT", "PAYMENT"]);

const state = {
  allTxs: [],     // raw transactions for the signed-in user (most recent first)
  period: "month",
  search: "",
  typeFilter: "",
  userId: null,
  selectedCps: new Set(),  // counterparties picked for batch categorization
  pickedCategory: null,    // chosen category for the next Apply
  view: "dashboard",       // "dashboard" | "categories" | "ledger"
  activeCatDetail: null,   // category name currently drilled into
  ledger: [],              // active + settled ledger entries
  editingId: null,         // id of the entry the form is editing, or null = new
  payingId: null,          // id of the entry whose inline pay form is open
  showClosed: false,       // whether the "Closed entries" section is expanded
  income: [],              // i_income_entries rows
  incomeDismissed: new Set(), // counterparty_match keys the user has dismissed
  editingIncomeId: null,
  goals: [],               // i_goals rows
  goalContributions: [],   // i_goal_contributions rows (most recent first)
  editingGoalId: null,
  goalHistoryOpen: false,
  // Notion-style per-tab view modes.
  ledgerListView: "cards",   // "cards" | "table" | "calendar"
  incomeListView: "cards",   // "cards" | "table" | "calendar"
  goalsListView:  "cards",   // "cards" | "table" | "progress"
  // Cursor for calendar views (first of the visible month).
  ledgerCalCursor: new Date(new Date().getFullYear(), new Date().getMonth(), 1).getTime(),
  incomeCalCursor: new Date(new Date().getFullYear(), new Date().getMonth(), 1).getTime(),
};

// Defaults match TransactionCategoryCatalog.kt on Android.
const DEFAULT_CATEGORIES = [
  "Food",
  "Coffee and refreshments",
  "Bills",
  "Loan",
  "Drinks and fun",
  "Transport",
];

const PERIOD_LABELS = {
  month: "this month",
  "last-month": "last month",
  "30d": "last 30 days",
  "90d": "last 90 days",
  year: "this year",
  all: "all time",
};

// ----------------------------------------------------------------------------
// Boot
// ----------------------------------------------------------------------------
(async function init() {
  const { data: { session } } = await supabase.auth.getSession();
  if (session) await showDashboard(session);
  else showLogin();

  supabase.auth.onAuthStateChange(async (_event, session) => {
    if (session) await showDashboard(session);
    else showLogin();
  });
})();

// ----------------------------------------------------------------------------
// Login / Logout
// ----------------------------------------------------------------------------
loginForm.addEventListener("submit", async (e) => { e.preventDefault(); await attemptLogin(); });
retryBtn.addEventListener("click", () => { hideError(); retryBtn.classList.add("hidden"); passwordInput.value = ""; passwordInput.focus(); });
logoutBtn.addEventListener("click", async () => { await supabase.auth.signOut(); });
refreshBtn.addEventListener("click", async () => {
  const { data: { session } } = await supabase.auth.getSession();
  if (session) await showDashboard(session);
});

async function attemptLogin() {
  hideError();
  setLoginBusy(true);
  const email = emailInput.value.trim();
  const password = passwordInput.value;
  const { error } = await supabase.auth.signInWithPassword({ email, password });
  setLoginBusy(false);
  if (error) {
    showError(prettyAuthError(error.message));
    retryBtn.classList.remove("hidden");
  }
}

function showError(msg) { errorBox.textContent = msg; errorBox.classList.remove("hidden"); }
function hideError() { errorBox.classList.add("hidden"); }
function setLoginBusy(busy) {
  loginBtn.disabled = busy;
  loginBtn.textContent = busy ? "Signing in…" : "Sign in";
}

function prettyAuthError(raw) {
  if (!raw) return "Sign-in failed.";
  if (/invalid login credentials/i.test(raw)) return "Wrong email or password.";
  if (/email not confirmed/i.test(raw)) return "Confirm your email before signing in.";
  if (/network/i.test(raw)) return "Network error — check your connection and retry.";
  return raw;
}

// ----------------------------------------------------------------------------
// Period selector
// ----------------------------------------------------------------------------
periodBar.addEventListener("click", (e) => {
  const btn = e.target.closest(".period-pill");
  if (!btn) return;
  periodBar.querySelectorAll(".period-pill").forEach((b) => b.classList.toggle("active", b === btn));
  state.period = btn.dataset.period;
  rerender();
});

// ----------------------------------------------------------------------------
// View tabs (Dashboard ↔ Category Analysis)
// ----------------------------------------------------------------------------
viewTabs.addEventListener("click", (e) => {
  const btn = e.target.closest(".view-tab");
  if (!btn) return;
  switchView(btn.dataset.view);
});

function switchView(name) {
  state.view = name;
  viewTabs.querySelectorAll(".view-tab").forEach((t) => t.classList.toggle("active", t.dataset.view === name));
  dashboardView.classList.toggle("hidden", name !== "dashboard");
  categoriesView.classList.toggle("hidden", name !== "categories");
  ledgerView.classList.toggle("hidden", name !== "ledger");
  incomeView.classList.toggle("hidden", name !== "income");
  goalsView.classList.toggle("hidden", name !== "goals");
  if (name === "categories") renderCategoriesView();
  if (name === "ledger") renderLedgerView();
  if (name === "income") renderIncomeView();
  if (name === "goals") renderGoalsView();
  // Scroll to top so the user lands above-the-fold on the new view.
  window.scrollTo({ top: 0, behavior: "smooth" });
}

if (catDetailClose) {
  catDetailClose.addEventListener("click", () => {
    state.activeCatDetail = null;
    catDetail.classList.add("hidden");
    catCards.querySelectorAll(".cat-card").forEach((c) => c.classList.remove("active"));
  });
}

// ----------------------------------------------------------------------------
// Tx search + type filter
// ----------------------------------------------------------------------------
txSearch.addEventListener("input", () => { state.search = txSearch.value.trim().toLowerCase(); renderTxTable(); });
txTypeFilter.addEventListener("change", () => { state.typeFilter = txTypeFilter.value; renderTxTable(); });
csvBtn.addEventListener("click", exportCsv);

// ----------------------------------------------------------------------------
// Dashboard rendering
// ----------------------------------------------------------------------------
function showLogin() {
  authShell.classList.add("hidden");
  loginView.classList.remove("hidden");
}

async function showDashboard(session) {
  loginView.classList.add("hidden");
  authShell.classList.remove("hidden");
  setupGreeting(session.user);

  const userId = session.user.id;
  const [profileResult, txResult] = await Promise.all([
    supabase.from("i_users").select("name, email").eq("id", userId).maybeSingle(),
    supabase.from("i_transactions")
      .select("id, occurred_at, bank_name, sender, type, category, amount, balance, counterparty, ref_num")
      .eq("user_id", userId)
      .order("occurred_at", { ascending: false })
      .limit(2000),
  ]);

  if (profileResult.data?.name) userNameEl.textContent = profileResult.data.name;

  if (txResult.error) {
    txTbody.innerHTML = `<tr><td colspan="7" class="muted">Couldn't load transactions: ${escapeHtml(txResult.error.message)}</td></tr>`;
    return;
  }

  state.allTxs = txResult.data || [];
  state.userId = userId;
  renderBatchPills();
  rerender();
  // Fire-and-forget ledger + income + goals fetches so the dashboard renders immediately.
  fetchLedger().catch((e) => console.warn("Ledger fetch failed:", e));
  fetchIncome().catch((e) => console.warn("Income fetch failed:", e));
  fetchGoals().catch((e) => console.warn("Goals fetch failed:", e));
}

// ----------------------------------------------------------------------------
// Ledger — fetch + render + CRUD
// ----------------------------------------------------------------------------
async function fetchLedger() {
  if (!state.userId) return;
  const { data, error } = await supabase
    .from("i_ledger_entries")
    .select("*")
    .eq("user_id", state.userId)
    .order("created_at", { ascending: false });
  if (error) {
    // The migration may not be applied yet — surface a friendly hint, not a crash.
    if (/relation .* does not exist/i.test(error.message)) {
      console.warn("i_ledger_entries table missing — run supabase/add_ledger.sql in your Supabase project.");
    } else {
      console.warn("Ledger fetch error:", error.message);
    }
    state.ledger = [];
  } else {
    state.ledger = data || [];
  }
  renderLedgerBadge();
  if (state.view === "ledger") renderLedgerView();
}

function renderLedgerBadge() {
  const overdue = state.ledger.filter((e) => e.status === "ACTIVE" && isOverdue(e.due_date)).length;
  if (overdue > 0) {
    ledgerBadge.textContent = String(overdue);
    ledgerBadge.classList.remove("hidden");
  } else {
    ledgerBadge.classList.add("hidden");
  }
}

function renderLedgerView() {
  const entries = state.ledger;
  const active  = entries.filter((e) => e.status === "ACTIVE");
  const closed  = entries.filter((e) => e.status !== "ACTIVE");

  const iOwe       = active.filter((e) => e.direction === "I_OWE");
  const owedToMe   = active.filter((e) => e.direction === "OWED_TO_ME");
  const iOweTotal     = iOwe.reduce((s, e) => s + Number(e.balance), 0);
  const owedToMeTotal = owedToMe.reduce((s, e) => s + Number(e.balance), 0);
  const net           = owedToMeTotal - iOweTotal;
  const overdueCount  = active.filter((e) => isOverdue(e.due_date)).length;

  ledgerIOwe.textContent = formatETB(iOweTotal);
  ledgerIOweSub.textContent = iOwe.length === 0
    ? "no debts recorded"
    : `${iOwe.length} entr${iOwe.length === 1 ? "y" : "ies"} · ${overdueOf(iOwe)} overdue`;

  ledgerOwedToMe.textContent = formatETB(owedToMeTotal);
  ledgerOwedToMeSub.textContent = owedToMe.length === 0
    ? "nothing outstanding"
    : `${owedToMe.length} entr${owedToMe.length === 1 ? "y" : "ies"} · ${overdueOf(owedToMe)} overdue`;

  ledgerNet.textContent = (net >= 0 ? "+ " : "- ") + formatNumberAbs(net);
  ledgerNet.classList.remove("coral", "income");
  ledgerNet.classList.add(net >= 0 ? "income" : "coral");
  ledgerNetSub.textContent = net >= 0 ? "people owe you more than you owe" : "you owe more than is owed to you";
  if (overdueCount > 0) ledgerNetSub.textContent += ` · ${overdueCount} overdue total`;

  // Lists.
  ledgerOweCount.textContent = `${iOwe.length} active`;
  ledgerLentCount.textContent = `${owedToMe.length} active`;
  ledgerOweList.innerHTML  = iOwe.length === 0
    ? `<p class="muted">Nothing here. Tap + Add when you take a loan or rent comes up.</p>`
    : iOwe.sort(sortByDue).map(renderLedgerEntry).join("");
  ledgerLentList.innerHTML = owedToMe.length === 0
    ? `<p class="muted">No outstanding loans out yet.</p>`
    : owedToMe.sort(sortByDue).map(renderLedgerEntry).join("");

  // Closed entries.
  ledgerClosedList.innerHTML = closed.length === 0
    ? `<p class="muted">Nothing settled yet.</p>`
    : closed.map(renderLedgerEntry).join("");

  attachLedgerEntryHandlers();
  renderLedgerBadge();
  applyLedgerListView();
}

function sortByDue(a, b) {
  const ax = a.due_date ? Date.parse(a.due_date) : Number.POSITIVE_INFINITY;
  const bx = b.due_date ? Date.parse(b.due_date) : Number.POSITIVE_INFINITY;
  return ax - bx;
}

function overdueOf(entries) {
  return entries.filter((e) => isOverdue(e.due_date)).length;
}

function isOverdue(dueIso) {
  if (!dueIso) return false;
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  return Date.parse(dueIso) < today.getTime();
}

function renderLedgerEntry(e) {
  const overdue   = e.status === "ACTIVE" && isOverdue(e.due_date);
  const isSettled = e.status !== "ACTIVE";
  const klass = [
    "ledger-entry",
    e.direction === "I_OWE" ? "i-owe" : "owed-to-me",
    overdue ? "overdue" : "",
    isSettled ? "settled" : "",
  ].filter(Boolean).join(" ");
  const typePill = e.type === "RECURRING"
    ? `RECURRING · ${cadenceLabel(e.cadence, e.interval_days)}`
    : "IOU";
  const principal = Number(e.principal || 0);
  const balance   = Number(e.balance || 0);
  const partial   = balance > 0 && balance < principal;
  const dueLabel = e.due_date
    ? (overdue ? `Overdue · ${formatDueDate(e.due_date)}` : `Due ${formatDueDate(e.due_date)}`)
    : "No due date";

  const payInline = state.payingId === e.id ? `
    <div class="ledger-pay-form">
      <input type="number" min="0.01" step="0.01" max="${balance}" value="${balance.toFixed(2)}"
             class="pay-amount" data-id="${e.id}" />
      <button class="confirm-pay" data-id="${e.id}">Pay</button>
      <button class="cancel-pay" data-id="${e.id}">Cancel</button>
    </div>
  ` : "";

  const actions = isSettled
    ? `<button data-action="restore" data-id="${e.id}">Reopen</button>
       <button data-action="delete"  data-id="${e.id}">Delete</button>`
    : `<button class="primary" data-action="pay" data-id="${e.id}">Mark Paid</button>
       <button data-action="edit"    data-id="${e.id}">Edit</button>
       <button data-action="archive" data-id="${e.id}">Archive</button>`;

  return `
    <div class="${klass}" data-id="${e.id}">
      <div class="ledger-head">
        <span class="ledger-cp" title="${escapeHtml(e.counterparty)}">${escapeHtml(e.counterparty)}</span>
        <span class="ledger-type-pill">${escapeHtml(typePill)}</span>
      </div>
      <div class="ledger-amounts">
        <span class="ledger-balance">${formatETB(balance)}</span>
        ${partial ? `<span class="ledger-of-principal">of ${formatETB(principal)}</span>` : ""}
      </div>
      <div class="ledger-due">${escapeHtml(dueLabel)}</div>
      ${e.note ? `<div class="ledger-note">${escapeHtml(e.note)}</div>` : ""}
      ${payInline}
      <div class="ledger-actions">${actions}</div>
    </div>
  `;
}

function formatDueDate(iso) {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleDateString(undefined, { day: "numeric", month: "short", year: "numeric" });
}

function attachLedgerEntryHandlers() {
  // Action buttons
  ledgerView.querySelectorAll(".ledger-actions button[data-action]").forEach((btn) => {
    btn.addEventListener("click", async (e) => {
      e.stopPropagation();
      const id = btn.dataset.id;
      const action = btn.dataset.action;
      const entry = state.ledger.find((x) => x.id === id);
      if (!entry) return;

      if (action === "pay") {
        state.payingId = (state.payingId === id) ? null : id;
        renderLedgerView();
      } else if (action === "edit") {
        openEditForm(entry);
      } else if (action === "archive") {
        await updateEntry(id, { status: "ARCHIVED" });
      } else if (action === "restore") {
        await updateEntry(id, { status: "ACTIVE", settled_at: null });
      } else if (action === "delete") {
        if (!confirm("Delete this entry permanently?")) return;
        await deleteEntry(id);
      }
    });
  });

  // Confirm / cancel pay
  ledgerView.querySelectorAll(".confirm-pay").forEach((btn) => {
    btn.addEventListener("click", async () => {
      const id = btn.dataset.id;
      const input = ledgerView.querySelector(`.pay-amount[data-id="${id}"]`);
      const amount = parseFloat(input.value);
      if (!Number.isFinite(amount) || amount <= 0) return;
      await logRepayment(id, amount);
    });
  });
  ledgerView.querySelectorAll(".cancel-pay").forEach((btn) => {
    btn.addEventListener("click", () => {
      state.payingId = null;
      renderLedgerView();
    });
  });
}

// ----------------------------------------------------------------------------
// Form handlers
// ----------------------------------------------------------------------------
function showFormForCadence() {
  const isRecurring = ledgerType.value === "RECURRING";
  ledgerCadenceField.style.display = isRecurring ? "" : "none";
  const isCustom = isRecurring && ledgerCadence.value === "CUSTOM";
  ledgerIntervalField.classList.toggle("hidden", !isCustom);
}
ledgerType.addEventListener("change", showFormForCadence);
ledgerCadence.addEventListener("change", showFormForCadence);

ledgerFormToggle.addEventListener("click", () => {
  if (state.editingId) {
    resetLedgerForm();
  } else {
    ledgerForm.classList.toggle("hidden");
  }
  showFormForCadence();
});

ledgerCancel.addEventListener("click", () => resetLedgerForm());

ledgerForm.addEventListener("submit", async (e) => {
  e.preventDefault();
  ledgerFormError.classList.add("hidden");

  if (!state.userId) {
    showFormError("You're not signed in. Refresh and log in again.");
    return;
  }

  const isRecurring = ledgerType.value === "RECURRING";
  const isCustom = isRecurring && ledgerCadence.value === "CUSTOM";
  const intervalDays = isCustom ? parseInt(ledgerIntervalDays.value, 10) : null;
  if (isCustom && (!Number.isFinite(intervalDays) || intervalDays <= 0)) {
    showFormError("Custom cadence needs a positive number of days.");
    return;
  }
  const payload = {
    user_id:     state.userId,
    type:        ledgerType.value,
    direction:   ledgerDirection.value,
    counterparty: ledgerCounterparty.value.trim(),
    principal:   parseFloat(ledgerAmount.value),
    balance:     parseFloat(ledgerAmount.value),  // overridden below if editing
    due_date:    ledgerDue.value || null,
    cadence:     isRecurring ? ledgerCadence.value : null,
    interval_days: intervalDays,
    note:        ledgerNote.value.trim() || null,
  };
  if (!payload.counterparty || !Number.isFinite(payload.principal) || payload.principal < 0) {
    showFormError("Counterparty and a non-negative amount are required.");
    return;
  }

  ledgerSave.disabled = true;
  ledgerSave.textContent = "Saving…";

  let res;
  try {
    if (state.editingId) {
      const existing = state.ledger.find((x) => x.id === state.editingId);
      const ratio = existing && existing.principal > 0 ? Number(existing.balance) / Number(existing.principal) : 1;
      payload.balance = payload.principal * ratio;
      res = await supabase.from("i_ledger_entries").update(payload).eq("id", state.editingId).select().maybeSingle();
    } else {
      res = await supabase.from("i_ledger_entries").insert(payload).select().maybeSingle();
    }
  } catch (err) {
    res = { error: { message: err?.message || String(err) } };
  }

  ledgerSave.disabled = false;
  ledgerSave.textContent = state.editingId ? "Update entry" : "Save entry";

  if (res?.error) {
    console.error("[ledger save] insert/update failed:", res.error, "payload:", payload);
    showFormError(prettyLedgerError(res.error.message));
    showToast(`Save failed: ${prettyLedgerError(res.error.message)}`, "error");
    return;
  }

  console.log("[ledger save] success:", res?.data);
  await fetchLedger();
  resetLedgerForm();
  showToast(state.editingId ? "Entry updated" : "Entry saved", "success");
});

function showFormError(msg) {
  ledgerFormError.innerHTML = msg;
  ledgerFormError.classList.remove("hidden");
  ledgerFormError.scrollIntoView({ behavior: "smooth", block: "center" });
}

function prettyLedgerError(raw) {
  if (!raw) return "Save failed.";

  const url = "https://supabase.com/dashboard/project/sadbxjnmcgzwtjbqqhbk/sql/new";

  // PostgREST error: "Could not find the '<col>' column of '<table>' in the schema cache"
  // Test column-missing BEFORE table-missing so the column branch catches it first.
  const colCacheMatch = raw.match(/could not find the ['"]?(\w+)['"]? column of ['"]?(\w+)['"]?/i);
  if (colCacheMatch) {
    const col = colCacheMatch[1];
    const table = colCacheMatch[2];
    let file = "supabase/add_custom_cadence.sql";
    if (col === "counterparty_match" || col === "last_received_at") file = "supabase/add_income.sql";
    return `Schema is out of date — column <code>${col}</code> on <code>${table}</code> is missing. Paste <code>${file}</code> into the <a href="${url}" target="_blank" rel="noopener" style="color:var(--coral);text-decoration:underline">Supabase SQL editor</a> and run it.`;
  }

  // SQL "column X does not exist" (raw Postgres error, less common via PostgREST).
  if (/column .* does not exist/i.test(raw)) {
    const colMatch = raw.match(/column\s+"?(\w+)"?/i);
    const col = colMatch?.[1] || "";
    return `Schema is out of date — column <code>${col || "?"}</code> is missing. Paste <code>supabase/add_custom_cadence.sql</code> into the <a href="${url}" target="_blank" rel="noopener" style="color:var(--coral);text-decoration:underline">Supabase SQL editor</a> and run it.`;
  }

  // Missing table — pick the right migration based on the table name in the error.
  if (/relation .* does not exist|could not find the table|schema cache/i.test(raw)) {
    const tableMatch = raw.match(/(?:relation\s+"?|table\s+'?)(?:public\.)?(i_\w+)/i)
                    || raw.match(/['"]?(i_\w+)['"]?/);
    const table = tableMatch?.[1] || "";
    let file = "supabase/add_ledger.sql";
    if (/i_income/.test(table))      file = "supabase/add_income.sql";
    else if (/i_ledger/.test(table)) file = "supabase/add_ledger.sql";
    return `Database isn't set up yet — <code>${table || "the required table"}</code> is missing. Paste <code>${file}</code> into the <a href="${url}" target="_blank" rel="noopener" style="color:var(--coral);text-decoration:underline">Supabase SQL editor</a> and run it, then try again.`;
  }

  // Check constraint — usually a stale cadence enum.
  if (/violates check constraint/i.test(raw) && /cadence/i.test(raw)) {
    return `Your DB still has the old cadence list. Run <code>supabase/add_custom_cadence.sql</code> in the <a href="${url}" target="_blank" rel="noopener" style="color:var(--coral);text-decoration:underline">Supabase SQL editor</a> to allow Bi-weekly / Every-N-days values.`;
  }

  if (/permission denied|RLS|row-level security/i.test(raw)) {
    return `Insert blocked by Row-Level Security. Make sure you re-ran the policies in the matching migration file.`;
  }
  if (/duplicate key|unique constraint/i.test(raw)) {
    return "Looks like this entry already exists.";
  }
  return raw;
}

function openEditForm(entry) {
  state.editingId = entry.id;
  ledgerType.value        = entry.type;
  ledgerDirection.value   = entry.direction;
  ledgerCounterparty.value= entry.counterparty;
  ledgerAmount.value      = Number(entry.principal).toFixed(2);
  ledgerDue.value         = entry.due_date || "";
  ledgerCadence.value     = entry.cadence || "MONTHLY";
  ledgerIntervalDays.value = entry.interval_days ?? "";
  ledgerNote.value        = entry.note || "";
  ledgerSave.textContent  = "Update entry";
  showFormForCadence();
  ledgerForm.classList.remove("hidden");
  ledgerForm.scrollIntoView({ behavior: "smooth", block: "start" });
}

function resetLedgerForm() {
  state.editingId = null;
  ledgerForm.reset();
  ledgerForm.classList.add("hidden");
  ledgerSave.textContent = "Save entry";
  ledgerFormError.classList.add("hidden");
  showFormForCadence();
}

// ----------------------------------------------------------------------------
// Repayments + recurring auto-roll
// ----------------------------------------------------------------------------
async function logRepayment(entryId, amount) {
  const entry = state.ledger.find((x) => x.id === entryId);
  if (!entry) return;
  const currentBalance = Number(entry.balance);
  const newBalance = Math.max(0, currentBalance - amount);
  const fullyPaid = newBalance === 0;

  const { error: repError } = await supabase.from("i_ledger_repayments").insert({
    entry_id: entryId,
    user_id:  state.userId,
    amount:   Math.min(amount, currentBalance),
  });
  if (repError) { showToast(`Couldn't record payment: ${repError.message}`, "error"); return; }

  const updates = { balance: newBalance };
  if (fullyPaid) {
    updates.status = "SETTLED";
    updates.settled_at = new Date().toISOString();
  }
  const { error: upError } = await supabase.from("i_ledger_entries").update(updates).eq("id", entryId);
  if (upError) { showToast(`Couldn't update entry: ${upError.message}`, "error"); return; }

  // Auto-roll a recurring entry on full payment.
  if (fullyPaid && entry.type === "RECURRING") {
    const nextDue = nextDueDate(entry.due_date, entry.cadence, entry.interval_days);
    await supabase.from("i_ledger_entries").insert({
      user_id:     state.userId,
      type:        "RECURRING",
      direction:   entry.direction,
      counterparty: entry.counterparty,
      principal:   entry.principal,
      balance:     entry.principal,
      due_date:    nextDue,
      cadence:     entry.cadence,
      interval_days: entry.interval_days,
      parent_id:   entry.id,
      note:        entry.note,
    });
    showToast(`Paid. Next ${cadenceLabel(entry.cadence, entry.interval_days)} entry queued for ${formatDueDate(nextDue)}.`, "success");
  } else {
    showToast(fullyPaid ? "Settled in full" : "Payment recorded", "success");
  }

  state.payingId = null;
  await fetchLedger();
}

function nextDueDate(currentIso, cadence, intervalDays) {
  const base = currentIso ? new Date(currentIso) : new Date();
  switch ((cadence || "MONTHLY").toUpperCase()) {
    case "WEEKLY":        base.setDate(base.getDate() + 7); break;
    case "BIWEEKLY":      base.setDate(base.getDate() + 14); break;
    case "EVERY_30_DAYS": base.setDate(base.getDate() + 30); break;
    case "QUARTERLY":     base.setMonth(base.getMonth() + 3); break;
    case "YEARLY":        base.setFullYear(base.getFullYear() + 1); break;
    case "CUSTOM": {
      const n = Number(intervalDays);
      base.setDate(base.getDate() + (Number.isFinite(n) && n > 0 ? n : 30));
      break;
    }
    case "MONTHLY":
    default:              base.setMonth(base.getMonth() + 1); break;
  }
  return base.toISOString().slice(0, 10);
}

/** Pretty label for a cadence (used in entry cards and toasts). */
function cadenceLabel(cadence, intervalDays) {
  const c = (cadence || "MONTHLY").toUpperCase();
  if (c === "CUSTOM") {
    const n = Number(intervalDays);
    return Number.isFinite(n) && n > 0 ? `every ${n} days` : "custom";
  }
  if (c === "BIWEEKLY")      return "bi-weekly";
  if (c === "EVERY_30_DAYS") return "every 30 days";
  return c.toLowerCase();
}

async function updateEntry(id, patch) {
  const { error } = await supabase.from("i_ledger_entries").update(patch).eq("id", id);
  if (error) { showToast(`Update failed: ${error.message}`, "error"); return; }
  await fetchLedger();
}

async function deleteEntry(id) {
  const { error } = await supabase.from("i_ledger_entries").delete().eq("id", id);
  if (error) { showToast(`Delete failed: ${error.message}`, "error"); return; }
  await fetchLedger();
}

ledgerClosedToggle.addEventListener("click", () => {
  state.showClosed = !state.showClosed;
  ledgerClosedList.classList.toggle("hidden", !state.showClosed);
  ledgerClosedToggle.textContent = state.showClosed ? "Hide" : "Show";
});

// ============================================================================
// Income — fetch, detection, render, CRUD
// ============================================================================
async function fetchIncome() {
  if (!state.userId) return;
  const [entriesRes, dismissedRes] = await Promise.all([
    supabase.from("i_income_entries").select("*").eq("user_id", state.userId).order("created_at", { ascending: false }),
    supabase.from("i_income_dismissed").select("counterparty_match").eq("user_id", state.userId),
  ]);
  if (entriesRes.error) {
    if (/relation .* does not exist/i.test(entriesRes.error.message)) {
      console.warn("i_income_entries table missing — run supabase/add_income.sql in your Supabase project.");
    } else {
      console.warn("Income fetch error:", entriesRes.error.message);
    }
    state.income = [];
  } else {
    state.income = entriesRes.data || [];
  }
  state.incomeDismissed = new Set((dismissedRes?.data || []).map((d) => d.counterparty_match));
  renderIncomeBadge();
  if (state.view === "income") renderIncomeView();
}

function renderIncomeBadge() {
  const overdue = state.income.filter((e) => e.type === "RECURRING" && e.status === "ACTIVE" && isOverdue(e.occurs_on)).length;
  if (overdue > 0) {
    incomeBadge.textContent = String(overdue);
    incomeBadge.classList.remove("hidden");
  } else {
    incomeBadge.classList.add("hidden");
  }
}

function renderIncomeView() {
  const entries = state.income;
  const recurring = entries.filter((e) => e.type === "RECURRING" && e.status === "ACTIVE");
  const oneoff    = entries.filter((e) => e.type === "ONEOFF"    && e.status === "ACTIVE");

  // Period-scoped actual income (from CREDIT transactions in state.allTxs).
  const range = periodRange(state.period);
  const actual = state.allTxs
    .filter((t) => t.type === "CREDIT")
    .filter((t) => {
      const ts = Date.parse(t.occurred_at);
      return Number.isFinite(ts) && ts >= range.start && ts <= range.end;
    })
    .reduce((s, t) => s + Number(t.amount || 0), 0);
  const oneoffInRange = oneoff
    .filter((e) => {
      const ts = e.occurs_on ? Date.parse(e.occurs_on) : 0;
      return ts >= range.start && ts <= range.end;
    })
    .reduce((s, e) => s + Number(e.amount || 0), 0);
  const totalActual = actual + oneoffInRange;

  // Expected recurring (always monthlyised — Weekly → ×4, Quarterly → /3, Yearly → /12).
  const monthlyExpected = recurring.reduce(
    (s, e) => s + monthlyEquivalent(Number(e.amount), e.cadence, e.interval_days),
    0
  );

  incomePeriodLabel.textContent = PERIOD_LABELS[state.period] || "";
  incomeActual.textContent = formatETB(totalActual);
  incomeActualSub.textContent = `${state.allTxs.filter((t) => t.type === "CREDIT").length} CREDIT tx tracked total`;

  incomeExpected.textContent = formatETB(monthlyExpected);
  incomeExpectedSub.textContent = recurring.length === 0
    ? "no recurring sources yet"
    : `${recurring.length} source${recurring.length === 1 ? "" : "s"} (monthly equivalent)`;

  const variance = totalActual - monthlyExpected;
  incomeVariance.textContent = (variance >= 0 ? "+ " : "- ") + formatNumberAbs(variance);
  incomeVariance.classList.remove("income", "coral");
  incomeVariance.classList.add(variance >= 0 ? "income" : "coral");
  incomeVarianceSub.textContent = variance >= 0
    ? "ahead of expectation"
    : "below expectation so far";

  // Suggestions — strict matching: same counterparty + ±10% amount + ~monthly cadence.
  const suggestions = detectRecurringIncome();
  incomeSuggCount.textContent = suggestions.length === 0
    ? "no patterns found"
    : `${suggestions.length} suggestion${suggestions.length === 1 ? "" : "s"}`;
  incomeSuggestions.innerHTML = suggestions.length === 0
    ? `<p class="muted">No recurring income patterns detected in your CREDIT history (yet). The app looks for the same counterparty paying within ±10% of the same amount, roughly once a month, at least 3 times.</p>`
    : suggestions.map(renderSuggestionCard).join("");
  incomeSuggestions.querySelectorAll(".income-suggestion").forEach(attachSuggestionHandlers);

  // Tracked recurring.
  incomeRecurringCount.textContent = `${recurring.length} active`;
  incomeRecurringList.innerHTML = recurring.length === 0
    ? `<p class="muted">No recurring incomes tracked yet. Confirm a suggestion above or add one manually.</p>`
    : recurring.sort(sortByDueIncome).map(renderIncomeEntry).join("");

  // One-off.
  incomeOneoffCount.textContent = `${oneoff.length} recorded`;
  incomeOneoffList.innerHTML = oneoff.length === 0
    ? `<p class="muted">Nothing recorded yet.</p>`
    : oneoff.sort((a, b) => Date.parse(b.occurs_on || 0) - Date.parse(a.occurs_on || 0)).map(renderIncomeEntry).join("");

  attachIncomeHandlers();
  renderIncomeBadge();
  applyIncomeListView();
}

function sortByDueIncome(a, b) {
  const ax = a.occurs_on ? Date.parse(a.occurs_on) : Number.POSITIVE_INFINITY;
  const bx = b.occurs_on ? Date.parse(b.occurs_on) : Number.POSITIVE_INFINITY;
  return ax - bx;
}

function monthlyEquivalent(amount, cadence, intervalDays) {
  const DAYS_PER_MONTH = 30.4375;  // 365.25 / 12
  switch ((cadence || "MONTHLY").toUpperCase()) {
    case "WEEKLY":        return amount * (DAYS_PER_MONTH / 7);   // ≈ 4.346
    case "BIWEEKLY":      return amount * (DAYS_PER_MONTH / 14);  // ≈ 2.173
    case "EVERY_30_DAYS": return amount * (DAYS_PER_MONTH / 30);  // ≈ 1.014
    case "QUARTERLY":     return amount / 3;
    case "YEARLY":        return amount / 12;
    case "CUSTOM": {
      const n = Number(intervalDays);
      return Number.isFinite(n) && n > 0 ? amount * (DAYS_PER_MONTH / n) : amount;
    }
    case "MONTHLY":
    default:              return amount;
  }
}

// ----------------------------------------------------------------------------
// Detection — strict: same counterparty + ±10% amount + monthly cadence (25-35d)
// ----------------------------------------------------------------------------
function detectRecurringIncome() {
  const credits = state.allTxs.filter((t) => t.type === "CREDIT");

  // Group by normalised counterparty.
  const groups = new Map();
  for (const tx of credits) {
    const key = normaliseSource(tx.counterparty || tx.sender);
    if (!key) continue;
    if (!groups.has(key)) groups.set(key, { display: tx.counterparty || tx.sender, txs: [] });
    groups.get(key).txs.push(tx);
  }

  // Filter to those already tracked or dismissed.
  const trackedKeys = new Set(
    state.income.filter((e) => e.counterparty_match).map((e) => e.counterparty_match)
  );

  const suggestions = [];
  for (const [key, g] of groups) {
    if (g.txs.length < 3) continue;
    if (trackedKeys.has(key) || state.incomeDismissed.has(key)) continue;

    const sorted = [...g.txs].sort((a, b) => Date.parse(a.occurred_at) - Date.parse(b.occurred_at));

    const amounts = sorted.map((t) => Number(t.amount)).filter((n) => Number.isFinite(n) && n > 0);
    if (amounts.length < 3) continue;
    const sortedAmt = [...amounts].sort((a, b) => a - b);
    const median = sortedAmt[Math.floor(sortedAmt.length / 2)];

    // Within ±10% of the median.
    const close = sorted.filter((t) => Math.abs(Number(t.amount) - median) / median <= 0.10);
    if (close.length < 3) continue;

    // Median interval in days.
    const intervals = [];
    for (let i = 1; i < close.length; i++) {
      const days = Math.round((Date.parse(close[i].occurred_at) - Date.parse(close[i - 1].occurred_at)) / 864e5);
      if (days > 0) intervals.push(days);
    }
    if (intervals.length === 0) continue;
    const medianInterval = [...intervals].sort((a, b) => a - b)[Math.floor(intervals.length / 2)];
    if (medianInterval < 25 || medianInterval > 35) continue;

    const last = close[close.length - 1];
    const nextDue = new Date(Date.parse(last.occurred_at) + medianInterval * 864e5)
      .toISOString().slice(0, 10);

    suggestions.push({
      key,
      source: g.display,
      median,
      count: close.length,
      intervalDays: medianInterval,
      lastReceivedAt: last.occurred_at,
      nextExpected: nextDue,
    });
  }
  return suggestions.sort((a, b) => b.median - a.median);
}

function normaliseSource(name) {
  if (!name) return "";
  return String(name)
    .toLowerCase()
    .replace(/\(2519[\d*]+\)/g, "")             // strip phone tails like (2519****1234)
    .replace(/account\s+number\s+\d+/gi, "")     // strip bank-account suffix
    .replace(/\s+/g, " ")
    .trim();
}

function renderSuggestionCard(s) {
  const lastAgo = formatRelativeDate(Date.parse(s.lastReceivedAt));
  return `
    <div class="income-suggestion" data-key="${escapeHtml(s.key)}"
         data-source="${escapeHtml(s.source)}"
         data-median="${s.median}"
         data-interval="${s.intervalDays}"
         data-next="${s.nextExpected}">
      <span class="src">${escapeHtml(s.source)}</span>
      <span class="amount">${formatETB(s.median)}</span>
      <span class="meta">${s.count} payments · ~every ${s.intervalDays} days · last ${escapeHtml(lastAgo)} · next ${escapeHtml(formatDueDate(s.nextExpected))}</span>
      <div class="actions">
        <button class="confirm" data-action="confirm">Track this</button>
        <button data-action="dismiss">Not income</button>
      </div>
    </div>
  `;
}

function attachSuggestionHandlers(card) {
  card.querySelectorAll("button[data-action]").forEach((btn) => {
    btn.addEventListener("click", async () => {
      const key = card.dataset.key;
      const action = btn.dataset.action;
      if (action === "confirm") {
        await confirmSuggestion({
          key,
          source: card.dataset.source,
          median: parseFloat(card.dataset.median),
          nextExpected: card.dataset.next,
        });
      } else if (action === "dismiss") {
        await dismissSuggestion(key);
      }
    });
  });
}

async function confirmSuggestion({ key, source, median, nextExpected }) {
  const { error } = await supabase.from("i_income_entries").insert({
    user_id:    state.userId,
    type:       "RECURRING",
    source:     source || "Unknown",
    amount:     median,
    occurs_on:  nextExpected,
    cadence:    "MONTHLY",
    counterparty_match: key,
  });
  if (error) { showToast(prettyLedgerError(error.message), "error"); return; }
  showToast(`Now tracking "${source}" as recurring income`, "success");
  await fetchIncome();
}

async function dismissSuggestion(key) {
  const { error } = await supabase.from("i_income_dismissed").insert({
    user_id: state.userId,
    counterparty_match: key,
  });
  if (error) { showToast(prettyLedgerError(error.message), "error"); return; }
  state.incomeDismissed.add(key);
  renderIncomeView();
}

// ----------------------------------------------------------------------------
// Income entry rendering (reuses ledger-entry markup with .income variant)
// ----------------------------------------------------------------------------
function renderIncomeEntry(e) {
  const recurring = e.type === "RECURRING";
  const overdue   = recurring && isOverdue(e.occurs_on);
  const klass = ["ledger-entry", "income", overdue ? "overdue" : ""].filter(Boolean).join(" ");
  const typePill = recurring
    ? `RECURRING · ${cadenceLabel(e.cadence, e.interval_days)}`
    : "ONE-OFF";
  const dateLabel = e.occurs_on
    ? (recurring
        ? (overdue ? `Expected · overdue ${formatDueDate(e.occurs_on)}` : `Expected ${formatDueDate(e.occurs_on)}`)
        : `Received ${formatDueDate(e.occurs_on)}`)
    : (recurring ? "No next date" : "No date");

  const actions = recurring
    ? `<button class="primary" data-income-action="received" data-id="${e.id}">Mark Received</button>
       <button data-income-action="edit"    data-id="${e.id}">Edit</button>
       <button data-income-action="archive" data-id="${e.id}">Archive</button>`
    : `<button data-income-action="edit"    data-id="${e.id}">Edit</button>
       <button data-income-action="delete"  data-id="${e.id}">Delete</button>`;

  return `
    <div class="${klass}" data-id="${e.id}">
      <div class="ledger-head">
        <span class="ledger-cp" title="${escapeHtml(e.source)}">${escapeHtml(e.source)}</span>
        <span class="ledger-type-pill">${escapeHtml(typePill)}</span>
      </div>
      <div class="ledger-amounts">
        <span class="ledger-balance">${formatETB(Number(e.amount))}</span>
      </div>
      <div class="ledger-due">${escapeHtml(dateLabel)}</div>
      ${e.note ? `<div class="ledger-note">${escapeHtml(e.note)}</div>` : ""}
      <div class="ledger-actions">${actions}</div>
    </div>
  `;
}

function attachIncomeHandlers() {
  incomeView.querySelectorAll("button[data-income-action]").forEach((btn) => {
    btn.addEventListener("click", async () => {
      const id = btn.dataset.id;
      const action = btn.dataset.action || btn.dataset.incomeAction;
      const entry = state.income.find((x) => x.id === id);
      if (!entry) return;
      if (action === "received") await markIncomeReceived(entry);
      else if (action === "edit") openIncomeEdit(entry);
      else if (action === "archive") await updateIncomeEntry(id, { status: "ARCHIVED" });
      else if (action === "delete") {
        if (!confirm("Delete this entry?")) return;
        await deleteIncomeEntry(id);
      }
    });
  });
}

async function markIncomeReceived(entry) {
  const nextIso = nextDueDate(entry.occurs_on, entry.cadence, entry.interval_days);
  const { error } = await supabase.from("i_income_entries").update({
    last_received_at: new Date().toISOString(),
    occurs_on: nextIso,
  }).eq("id", entry.id);
  if (error) { showToast(prettyLedgerError(error.message), "error"); return; }
  showToast(`Marked received. Next ${cadenceLabel(entry.cadence, entry.interval_days)} due ${formatDueDate(nextIso)}.`, "success");
  await fetchIncome();
}

async function updateIncomeEntry(id, patch) {
  const { error } = await supabase.from("i_income_entries").update(patch).eq("id", id);
  if (error) { showToast(prettyLedgerError(error.message), "error"); return; }
  await fetchIncome();
}

async function deleteIncomeEntry(id) {
  const { error } = await supabase.from("i_income_entries").delete().eq("id", id);
  if (error) { showToast(prettyLedgerError(error.message), "error"); return; }
  await fetchIncome();
}

// ----------------------------------------------------------------------------
// Income form
// ----------------------------------------------------------------------------
function refreshIncomeFormForType() {
  const isRecurring = incomeType.value === "RECURRING";
  incomeCadenceField.style.display = isRecurring ? "" : "none";
  incomeDateLabel.textContent = isRecurring ? "Next expected date" : "Date received";
  const isCustom = isRecurring && incomeCadence.value === "CUSTOM";
  incomeIntervalField.classList.toggle("hidden", !isCustom);
}
incomeType.addEventListener("change", refreshIncomeFormForType);
incomeCadence.addEventListener("change", refreshIncomeFormForType);

incomeFormToggle.addEventListener("click", () => {
  if (state.editingIncomeId) { resetIncomeForm(); return; }
  incomeForm.classList.toggle("hidden");
  refreshIncomeFormForType();
});
incomeCancel.addEventListener("click", () => resetIncomeForm());

incomeForm.addEventListener("submit", async (e) => {
  e.preventDefault();
  incomeFormError.classList.add("hidden");
  if (!state.userId) { showIncomeFormError("Not signed in."); return; }

  const isRecurring = incomeType.value === "RECURRING";
  const isCustom = isRecurring && incomeCadence.value === "CUSTOM";
  const intervalDays = isCustom ? parseInt(incomeIntervalDays.value, 10) : null;
  if (isCustom && (!Number.isFinite(intervalDays) || intervalDays <= 0)) {
    showIncomeFormError("Custom cadence needs a positive number of days.");
    return;
  }
  const payload = {
    user_id:   state.userId,
    type:      incomeType.value,
    source:    incomeSource.value.trim(),
    amount:    parseFloat(incomeAmount.value),
    occurs_on: incomeOccursOn.value || null,
    cadence:   isRecurring ? incomeCadence.value : null,
    interval_days: intervalDays,
    note:      incomeNote.value.trim() || null,
  };
  if (!payload.source || !Number.isFinite(payload.amount) || payload.amount < 0) {
    showIncomeFormError("Source and a non-negative amount are required.");
    return;
  }

  incomeSave.disabled = true;
  incomeSave.textContent = "Saving…";
  let res;
  try {
    if (state.editingIncomeId) {
      res = await supabase.from("i_income_entries").update(payload).eq("id", state.editingIncomeId).select().maybeSingle();
    } else {
      res = await supabase.from("i_income_entries").insert(payload).select().maybeSingle();
    }
  } catch (err) {
    res = { error: { message: err?.message || String(err) } };
  }
  incomeSave.disabled = false;
  incomeSave.textContent = state.editingIncomeId ? "Update income" : "Save income";

  if (res?.error) {
    console.error("[income save] failed:", res.error, "payload:", payload);
    showIncomeFormError(prettyLedgerError(res.error.message));
    showToast(`Save failed: ${stripHtml(prettyLedgerError(res.error.message))}`, "error");
    return;
  }
  await fetchIncome();
  resetIncomeForm();
  showToast(state.editingIncomeId ? "Income updated" : "Income saved", "success");
});

function showIncomeFormError(msg) {
  incomeFormError.innerHTML = msg;
  incomeFormError.classList.remove("hidden");
  incomeFormError.scrollIntoView({ behavior: "smooth", block: "center" });
}

function openIncomeEdit(entry) {
  state.editingIncomeId = entry.id;
  incomeType.value     = entry.type;
  incomeSource.value   = entry.source;
  incomeAmount.value   = Number(entry.amount).toFixed(2);
  incomeOccursOn.value = entry.occurs_on || "";
  incomeCadence.value  = entry.cadence || "MONTHLY";
  incomeIntervalDays.value = entry.interval_days ?? "";
  incomeNote.value     = entry.note || "";
  incomeSave.textContent = "Update income";
  refreshIncomeFormForType();
  incomeForm.classList.remove("hidden");
  incomeForm.scrollIntoView({ behavior: "smooth", block: "start" });
}

function resetIncomeForm() {
  state.editingIncomeId = null;
  incomeForm.reset();
  incomeForm.classList.add("hidden");
  incomeSave.textContent = "Save income";
  incomeFormError.classList.add("hidden");
  refreshIncomeFormForType();
}

function stripHtml(s) {
  const div = document.createElement("div");
  div.innerHTML = s;
  return div.textContent || "";
}

// ============================================================================
// Goals — fetch, status, contributions, render, CRUD
// ============================================================================
async function fetchGoals() {
  if (!state.userId) return;
  const [gRes, cRes] = await Promise.all([
    supabase.from("i_goals").select("*").eq("user_id", state.userId).order("priority", { ascending: true }),
    supabase.from("i_goal_contributions").select("*").eq("user_id", state.userId).order("contributed_at", { ascending: false }).limit(200),
  ]);
  if (gRes.error) {
    if (/relation .* does not exist/i.test(gRes.error.message)) {
      console.warn("i_goals table missing — run supabase/add_goals.sql in your Supabase project.");
    } else {
      console.warn("Goals fetch error:", gRes.error.message);
    }
    state.goals = [];
  } else {
    state.goals = gRes.data || [];
  }
  state.goalContributions = cRes?.data || [];
  refreshGoalContribTargetOptions();
  renderGoalsBadge();
  if (state.view === "goals") renderGoalsView();
}

function renderGoalsBadge() {
  const behind = state.goals.filter((g) => g.status === "ACTIVE" && goalStatus(g) === "behind").length;
  if (behind > 0) {
    goalsBadge.textContent = String(behind);
    goalsBadge.classList.remove("hidden");
  } else {
    goalsBadge.classList.add("hidden");
  }
}

// ---- Aggregation helpers ----------------------------------------------------
function sumContributions(goalId, sinceMs = 0) {
  return state.goalContributions
    .filter((c) => c.goal_id === goalId && Date.parse(c.contributed_at) >= sinceMs)
    .reduce((s, c) => s + Number(c.amount), 0);
}

function startOfMonth(date = new Date()) {
  return new Date(date.getFullYear(), date.getMonth(), 1).getTime();
}

function daysInMonth(date = new Date()) {
  return new Date(date.getFullYear(), date.getMonth() + 1, 0).getDate();
}

/** Trailing-3-month average spend, used as the basis for OPEN_ENDED goals. */
function avgMonthlySpend() {
  const now = Date.now();
  const cutoff = now - 90 * 864e5;
  const expenseTypes = new Set(["DEBIT", "TRANSFER_OUT", "PAYMENT"]);
  const total = state.allTxs
    .filter((t) => expenseTypes.has(t.type))
    .filter((t) => {
      const ts = Date.parse(t.occurred_at);
      return Number.isFinite(ts) && ts >= cutoff;
    })
    .reduce((s, t) => s + Number(t.amount || 0), 0);
  return total / 3;
}

/** Computed target for a goal (resolves OPEN_ENDED to a number). */
function goalTarget(g) {
  if (g.type === "OPEN_ENDED") return (g.target_months || 0) * avgMonthlySpend();
  return Number(g.target_amount || 0);
}

/** How much money this goal still wants right now (zero if met for the period). */
function goalShortfall(g) {
  if (g.status !== "ACTIVE") return 0;
  if (g.type === "RECURRING") {
    const monthStart = startOfMonth();
    const savedThisMonth = sumContributions(g.id, monthStart);
    return Math.max(0, Number(g.target_amount || 0) - savedThisMonth);
  }
  // DATE_BOUND + OPEN_ENDED → lifetime shortfall
  return Math.max(0, goalTarget(g) - sumContributions(g.id));
}

/** Required contribution this month to stay on pace. */
function requiredThisMonth(g) {
  if (g.status !== "ACTIVE") return 0;
  if (g.type === "RECURRING") return goalShortfall(g);
  if (g.type === "OPEN_ENDED") {
    // No deadline → spread the shortfall over target_months as a guide.
    const months = Math.max(1, g.target_months || 1);
    return goalShortfall(g) / months;
  }
  // DATE_BOUND
  const now = new Date();
  const deadline = g.deadline ? new Date(g.deadline) : null;
  if (!deadline || deadline <= now) return goalShortfall(g);
  const monthsLeft = Math.max(1,
    (deadline.getFullYear() - now.getFullYear()) * 12 + (deadline.getMonth() - now.getMonth()) + 1
  );
  return goalShortfall(g) / monthsLeft;
}

function goalStatus(g) {
  if (g.status === "ACHIEVED") return "achieved";
  if (g.status === "ARCHIVED") return "archived";

  if (g.type === "RECURRING") {
    const monthStart = startOfMonth();
    const saved = sumContributions(g.id, monthStart);
    const target = Number(g.target_amount || 0);
    if (target <= 0) return "on-track";
    if (saved >= target) return "achieved"; // this month
    const dayRatio = new Date().getDate() / daysInMonth();
    const progressRatio = saved / target;
    if (progressRatio >= dayRatio * 0.95) return "on-track";
    if (progressRatio >= dayRatio * 0.80) return "catching";
    return "behind";
  }

  const target = goalTarget(g);
  if (target <= 0) return "on-track";
  const saved = sumContributions(g.id);
  if (saved >= target) return "achieved";

  if (g.type === "OPEN_ENDED") {
    // No deadline; consider behind only if 0 progress for a meaningful goal.
    return saved > 0 ? "on-track" : "catching";
  }

  // DATE_BOUND
  const now = Date.now();
  const start = Date.parse(g.created_at);
  const deadline = Date.parse(g.deadline);
  if (!Number.isFinite(deadline) || deadline <= start) return "on-track";
  const elapsed = Math.max(0, Math.min(1, (now - start) / (deadline - start)));
  const progress = saved / target;
  if (progress >= elapsed * 0.95) return "on-track";
  if (progress >= elapsed * 0.80) return "catching";
  return "behind";
}

// ---- Render -----------------------------------------------------------------
function renderGoalsView() {
  const goals = state.goals;
  const active = goals.filter((g) => g.status === "ACTIVE");
  const totalSaved = state.goalContributions.reduce((s, c) => s + Number(c.amount), 0);
  const totalNeeded = active.reduce((s, g) => s + requiredThisMonth(g), 0);
  const onTrackCount = active.filter((g) => {
    const st = goalStatus(g);
    return st === "on-track" || st === "achieved";
  }).length;

  goalsTotalSaved.textContent = formatETB(totalSaved);
  goalsTotalSavedSub.textContent = `${state.goalContributions.length} contribution${state.goalContributions.length === 1 ? "" : "s"} logged`;
  goalsOnTrack.textContent = `${onTrackCount} / ${active.length}`;
  goalsOnTrackSub.textContent = active.length === 0 ? "no active goals" : "active goals on pace";
  goalsNeeded.textContent = formatETB(totalNeeded);
  goalsNeededSub.textContent = totalNeeded === 0 ? "you're all caught up" : "to stay on pace this month";

  // Goals list (ACTIVE first by priority, then non-active grouped after)
  const sorted = [...goals].sort((a, b) => {
    if (a.status !== b.status) return a.status === "ACTIVE" ? -1 : 1;
    return (a.priority ?? 100) - (b.priority ?? 100);
  });
  goalsCount.textContent = `${active.length} active${goals.length > active.length ? ` · ${goals.length - active.length} closed` : ""}`;
  goalsList.innerHTML = goals.length === 0
    ? `<p class="muted">No goals yet. Tap + Add above to create one.</p>`
    : sorted.map((g, i, arr) => renderGoalCard(g, i, arr)).join("");
  attachGoalCardHandlers();

  // History
  const recent = state.goalContributions.slice(0, 20);
  goalHistoryList.innerHTML = recent.length === 0
    ? `<p class="muted">No contributions yet.</p>`
    : recent.map((c) => {
        const g = state.goals.find((x) => x.id === c.goal_id);
        return `<div class="goal-history-row">
          <span>${escapeHtml(g?.emoji || "💰")} ${escapeHtml(g?.name || "Unknown goal")}${c.note ? ` <span class="meta">— ${escapeHtml(c.note)}</span>` : ""}</span>
          <span><span class="amt">+ ${formatETB(Number(c.amount))}</span> <span class="meta">${escapeHtml(formatRelativeDate(Date.parse(c.contributed_at)))}</span></span>
        </div>`;
      }).join("");

  renderGoalsBadge();
  applyGoalsListView();
}

function renderGoalCard(g, idx, arr) {
  const st = goalStatus(g);
  const klass = `goal-card ${st}`;
  const target = goalTarget(g);
  const saved  = sumContributions(g.id);
  const pct    = target > 0 ? Math.min(100, (saved / target) * 100) : 0;
  const typeLabel = g.type === "DATE_BOUND" ? "DATE-BOUND"
                  : g.type === "OPEN_ENDED" ? "OPEN-ENDED"
                  : "RECURRING";
  const statusLabel = st === "on-track" ? "On track"
                    : st === "catching" ? "Catching up"
                    : st === "behind"   ? "Behind"
                    : st === "achieved" ? "Achieved"
                    : "Archived";

  let meta = "";
  if (g.type === "DATE_BOUND") {
    const remaining = Math.max(0, target - saved);
    const need = requiredThisMonth(g);
    meta = remaining > 0
      ? `${formatETB(remaining)} to go${g.deadline ? ` · due ${formatDueDate(g.deadline)}` : ""}${need > 0 ? ` · need ${formatETB(need)}/mo` : ""}`
      : `Target reached`;
  } else if (g.type === "OPEN_ENDED") {
    const months = g.target_months || 0;
    meta = `Target = ${months}× monthly spend = ${formatETB(target)} (recomputes from your data)`;
  } else { // RECURRING
    const monthStart = startOfMonth();
    const savedThisMonth = sumContributions(g.id, monthStart);
    const mTarget = Number(g.target_amount || 0);
    meta = `This month: ${formatETB(savedThisMonth)} / ${formatETB(mTarget)} target`;
  }

  // Lifetime stats for RECURRING + DATE_BOUND
  const savedRow = g.type === "RECURRING"
    ? `<div class="row"><span class="saved">${formatETB(sumContributions(g.id, startOfMonth()))}</span><span class="target">/ ${formatETB(Number(g.target_amount || 0))} this month</span></div>`
    : `<div class="row"><span class="saved">${formatETB(saved)}</span><span class="target">/ ${formatETB(target)}</span></div>`;

  const activeArr = arr.filter((x) => x.status === "ACTIVE");
  const activeIdx = activeArr.findIndex((x) => x.id === g.id);
  const isFirst = activeIdx <= 0;
  const isLast  = activeIdx === activeArr.length - 1;

  const actions = g.status === "ARCHIVED"
    ? `<button data-goal-action="restore" data-id="${g.id}">Restore</button>
       <button data-goal-action="delete"  data-id="${g.id}">Delete</button>`
    : g.status === "ACHIEVED"
      ? `<button data-goal-action="reopen"  data-id="${g.id}">Reopen</button>
         <button data-goal-action="archive" data-id="${g.id}">Archive</button>`
      : `<button class="primary" data-goal-action="contribute" data-id="${g.id}">+ Contribute</button>
         <button data-goal-action="edit"     data-id="${g.id}">Edit</button>
         ${st === "achieved" ? "" : `<button data-goal-action="mark-achieved" data-id="${g.id}">Mark Achieved</button>`}
         <button data-goal-action="archive"  data-id="${g.id}">Archive</button>`;

  const prioControls = g.status === "ACTIVE"
    ? `<div class="goal-prio-controls">
         <button data-goal-action="prio-up"   data-id="${g.id}" ${isFirst ? "disabled" : ""} title="Higher priority">▲</button>
         <button data-goal-action="prio-down" data-id="${g.id}" ${isLast  ? "disabled" : ""} title="Lower priority">▼</button>
       </div>`
    : "";

  return `
    <div class="${klass}" data-id="${g.id}">
      <div class="goal-emoji">${escapeHtml(g.emoji || "💰")}</div>
      <div class="goal-name">
        <span class="goal-name-text">${escapeHtml(g.name)}</span>
        <span class="goal-type-pill">${typeLabel}</span>
      </div>
      <div style="display:flex; align-items:center; gap:8px;">
        <span class="goal-status-pill ${st}">${statusLabel}</span>
        ${prioControls}
      </div>
      <div class="goal-progress">
        ${savedRow}
        <div class="goal-bar"><span style="width: ${pct.toFixed(1)}%"></span></div>
      </div>
      <div class="goal-meta">${escapeHtml(meta)}</div>
      <div class="goal-actions">${actions}</div>
    </div>
  `;
}

function attachGoalCardHandlers() {
  goalsList.querySelectorAll("button[data-goal-action]").forEach((btn) => {
    btn.addEventListener("click", async () => {
      const id = btn.dataset.id;
      const action = btn.dataset.goalAction;
      const g = state.goals.find((x) => x.id === id);
      if (!g) return;
      if (action === "contribute") openContribForSpecificGoal(g);
      else if (action === "edit") openGoalEdit(g);
      else if (action === "archive") await updateGoal(g.id, { status: "ARCHIVED" });
      else if (action === "restore") await updateGoal(g.id, { status: "ACTIVE" });
      else if (action === "reopen")  await updateGoal(g.id, { status: "ACTIVE", achieved_at: null });
      else if (action === "mark-achieved") await updateGoal(g.id, { status: "ACHIEVED", achieved_at: new Date().toISOString() });
      else if (action === "delete") {
        if (!confirm("Delete this goal and all its contributions?")) return;
        await deleteGoal(g.id);
      } else if (action === "prio-up" || action === "prio-down") {
        await reorderGoal(g, action === "prio-up" ? -1 : +1);
      }
    });
  });
}

async function updateGoal(id, patch) {
  const { error } = await supabase.from("i_goals").update(patch).eq("id", id);
  if (error) { showToast(prettyLedgerError(error.message), "error"); return; }
  await fetchGoals();
}

async function deleteGoal(id) {
  const { error } = await supabase.from("i_goals").delete().eq("id", id);
  if (error) { showToast(prettyLedgerError(error.message), "error"); return; }
  await fetchGoals();
}

async function reorderGoal(goal, delta) {
  const active = state.goals.filter((g) => g.status === "ACTIVE").sort((a, b) => (a.priority ?? 100) - (b.priority ?? 100));
  const idx = active.findIndex((g) => g.id === goal.id);
  const swapIdx = idx + delta;
  if (swapIdx < 0 || swapIdx >= active.length) return;
  const other = active[swapIdx];
  // Swap priorities
  const a = goal.priority ?? 100;
  const b = other.priority ?? 100;
  const { error: e1 } = await supabase.from("i_goals").update({ priority: b }).eq("id", goal.id);
  const { error: e2 } = await supabase.from("i_goals").update({ priority: a }).eq("id", other.id);
  if (e1 || e2) { showToast("Reorder failed", "error"); return; }
  await fetchGoals();
}

// ---- Goal form (create / edit) ---------------------------------------------
function refreshGoalFormForType() {
  const t = goalType.value;
  goalTargetAmountField.classList.toggle("hidden", t === "OPEN_ENDED");
  goalDeadlineField.classList.toggle("hidden",     t !== "DATE_BOUND");
  goalTargetMonthsField.classList.toggle("hidden", t !== "OPEN_ENDED");
  if (t === "OPEN_ENDED") refreshOpenEndedPreview();
  if (t === "RECURRING") {
    goalTargetAmount.placeholder = "Monthly amount, e.g. 5000.00";
  } else {
    goalTargetAmount.placeholder = "0.00";
  }
}
function refreshOpenEndedPreview() {
  const m = parseInt(goalTargetMonths.value, 10);
  if (!Number.isFinite(m) || m <= 0) {
    goalTargetMonthsPreview.textContent = "";
    return;
  }
  const avg = avgMonthlySpend();
  const target = avg * m;
  goalTargetMonthsPreview.textContent = avg > 0
    ? `Computed target: ${formatETB(target)} (${m} × ${formatETB(avg)} avg/mo)`
    : "Need more expense history to compute this — record some transactions first.";
}
goalType.addEventListener("change", refreshGoalFormForType);
goalTargetMonths.addEventListener("input", refreshOpenEndedPreview);

goalFormToggle.addEventListener("click", () => {
  if (state.editingGoalId) { resetGoalForm(); return; }
  goalForm.classList.toggle("hidden");
  refreshGoalFormForType();
});
goalCancel.addEventListener("click", () => resetGoalForm());

goalForm.addEventListener("submit", async (e) => {
  e.preventDefault();
  goalFormError.classList.add("hidden");
  if (!state.userId) { showGoalFormError("Not signed in."); return; }
  const t = goalType.value;
  const payload = {
    user_id: state.userId,
    name: goalName.value.trim(),
    emoji: (goalEmoji.value || "").trim() || null,
    type: t,
    target_amount: (t === "OPEN_ENDED") ? null : parseFloat(goalTargetAmount.value),
    target_months: (t === "OPEN_ENDED") ? parseInt(goalTargetMonths.value, 10) : null,
    deadline: t === "DATE_BOUND" ? (goalDeadline.value || null) : null,
    note: goalNote.value.trim() || null,
  };
  if (!payload.name) { showGoalFormError("Name is required."); return; }
  if (t !== "OPEN_ENDED" && (!Number.isFinite(payload.target_amount) || payload.target_amount < 0)) {
    showGoalFormError("Target amount is required.");
    return;
  }
  if (t === "DATE_BOUND" && !payload.deadline) {
    showGoalFormError("Date-bound goals need a deadline.");
    return;
  }
  if (t === "OPEN_ENDED" && (!Number.isFinite(payload.target_months) || payload.target_months <= 0)) {
    showGoalFormError("Open-ended goals need a positive number of months.");
    return;
  }

  // Assign priority for new goals (one slot below the current lowest).
  if (!state.editingGoalId) {
    const maxPrio = state.goals.reduce((m, g) => Math.max(m, g.priority ?? 0), 0);
    payload.priority = maxPrio + 10;
  }

  goalSave.disabled = true;
  goalSave.textContent = "Saving…";
  let res;
  try {
    if (state.editingGoalId) {
      res = await supabase.from("i_goals").update(payload).eq("id", state.editingGoalId).select().maybeSingle();
    } else {
      res = await supabase.from("i_goals").insert(payload).select().maybeSingle();
    }
  } catch (err) {
    res = { error: { message: err?.message || String(err) } };
  }
  goalSave.disabled = false;
  goalSave.textContent = state.editingGoalId ? "Update goal" : "Save goal";

  if (res?.error) {
    console.error("[goal save] failed:", res.error, "payload:", payload);
    showGoalFormError(prettyLedgerError(res.error.message));
    showToast(`Save failed: ${stripHtml(prettyLedgerError(res.error.message))}`, "error");
    return;
  }
  showToast(state.editingGoalId ? "Goal updated" : "Goal saved", "success");
  await fetchGoals();
  resetGoalForm();
});

function showGoalFormError(msg) {
  goalFormError.innerHTML = msg;
  goalFormError.classList.remove("hidden");
  goalFormError.scrollIntoView({ behavior: "smooth", block: "center" });
}

function openGoalEdit(g) {
  state.editingGoalId = g.id;
  goalFormTitle.textContent = "Edit goal";
  goalType.value = g.type;
  goalEmoji.value = g.emoji || "";
  goalName.value = g.name;
  goalTargetAmount.value = g.target_amount != null ? Number(g.target_amount).toFixed(2) : "";
  goalTargetMonths.value = g.target_months ?? 3;
  goalDeadline.value = g.deadline || "";
  goalNote.value = g.note || "";
  goalSave.textContent = "Update goal";
  refreshGoalFormForType();
  goalForm.classList.remove("hidden");
  goalForm.scrollIntoView({ behavior: "smooth", block: "start" });
}

function resetGoalForm() {
  state.editingGoalId = null;
  goalForm.reset();
  goalForm.classList.add("hidden");
  goalFormTitle.textContent = "New goal";
  goalSave.textContent = "Save goal";
  goalFormError.classList.add("hidden");
  refreshGoalFormForType();
}

// ---- Contribution form ------------------------------------------------------
function refreshGoalContribTargetOptions() {
  if (!goalContribTarget) return;
  const active = state.goals.filter((g) => g.status === "ACTIVE")
    .sort((a, b) => (a.priority ?? 100) - (b.priority ?? 100));
  goalContribTarget.innerHTML = active.length === 0
    ? `<option value="">— no active goals —</option>`
    : active.map((g) => `<option value="${g.id}">${escapeHtml(g.emoji || "💰")} ${escapeHtml(g.name)}</option>`).join("");
}

function refreshContribUI() {
  const isSpecific = goalContribMode.value === "SPECIFIC";
  goalContribTargetField.classList.toggle("hidden", !isSpecific);
  refreshContribPreview();
}
goalContribMode.addEventListener("change", refreshContribUI);
goalContribAmount.addEventListener("input", refreshContribPreview);

function refreshContribPreview() {
  const amount = parseFloat(goalContribAmount.value);
  if (!Number.isFinite(amount) || amount <= 0) { goalContribPreview.textContent = ""; return; }
  if (goalContribMode.value === "SPECIFIC") {
    const g = state.goals.find((x) => x.id === goalContribTarget.value);
    goalContribPreview.textContent = g ? `→ all ${formatETB(amount)} to ${g.name}` : "";
    return;
  }
  // Auto preview
  const splits = waterfallPlan(amount);
  if (splits.length === 0) {
    goalContribPreview.textContent = "→ no active goals to receive this";
    return;
  }
  goalContribPreview.textContent = "→ " + splits.map((s) => {
    const g = state.goals.find((x) => x.id === s.goalId);
    return `${formatETB(s.amount)} → ${g?.emoji || ""}${g?.name || "?"}`;
  }).join(", ");
}

function waterfallPlan(amount) {
  let remaining = amount;
  const out = [];
  const active = state.goals.filter((g) => g.status === "ACTIVE")
    .sort((a, b) => (a.priority ?? 100) - (b.priority ?? 100));
  for (const g of active) {
    if (remaining <= 0) break;
    const need = goalShortfall(g);
    if (need <= 0) continue;
    const alloc = Math.min(remaining, need);
    out.push({ goalId: g.id, amount: alloc });
    remaining -= alloc;
  }
  // Leftover goes to the LAST active goal (so nothing is dropped).
  if (remaining > 0 && active.length > 0) {
    const last = active[active.length - 1];
    const existing = out.find((x) => x.goalId === last.id);
    if (existing) existing.amount += remaining;
    else out.push({ goalId: last.id, amount: remaining });
  }
  return out;
}

goalContribToggle.addEventListener("click", () => {
  goalContribForm.classList.toggle("hidden");
  refreshGoalContribTargetOptions();
  // Default date today.
  if (!goalContribDate.value) goalContribDate.value = new Date().toISOString().slice(0, 10);
  refreshContribUI();
});
goalContribCancel.addEventListener("click", () => {
  goalContribForm.classList.add("hidden");
  goalContribError.classList.add("hidden");
  goalContribForm.reset();
  goalContribPreview.textContent = "";
});

goalContribForm.addEventListener("submit", async (e) => {
  e.preventDefault();
  goalContribError.classList.add("hidden");
  if (!state.userId) { showContribError("Not signed in."); return; }
  const amount = parseFloat(goalContribAmount.value);
  if (!Number.isFinite(amount) || amount <= 0) { showContribError("Enter a positive amount."); return; }

  const dateIso = goalContribDate.value
    ? new Date(goalContribDate.value).toISOString()
    : new Date().toISOString();
  const note = goalContribNote.value.trim() || null;

  let splits;
  if (goalContribMode.value === "SPECIFIC") {
    const id = goalContribTarget.value;
    if (!id) { showContribError("Pick a goal first."); return; }
    splits = [{ goalId: id, amount }];
  } else {
    splits = waterfallPlan(amount);
    if (splits.length === 0) { showContribError("No active goals to receive this."); return; }
  }

  goalContribSave.disabled = true;
  goalContribSave.textContent = "Saving…";
  const rows = splits.map((s) => ({
    user_id: state.userId,
    goal_id: s.goalId,
    amount: s.amount,
    contributed_at: dateIso,
    note,
  }));
  const { error } = await supabase.from("i_goal_contributions").insert(rows);
  goalContribSave.disabled = false;
  goalContribSave.textContent = "Log save";

  if (error) {
    console.error("[goal contribution] failed:", error, rows);
    showContribError(prettyLedgerError(error.message));
    showToast(`Save failed: ${stripHtml(prettyLedgerError(error.message))}`, "error");
    return;
  }

  // Auto-mark any goal whose lifetime saved >= target.
  await autoMarkAchieved();

  showToast(`Logged ${formatETB(amount)} across ${splits.length} goal${splits.length === 1 ? "" : "s"}`, "success");
  await fetchGoals();
  goalContribForm.classList.add("hidden");
  goalContribForm.reset();
  goalContribPreview.textContent = "";
});

function showContribError(msg) {
  goalContribError.innerHTML = msg;
  goalContribError.classList.remove("hidden");
  goalContribError.scrollIntoView({ behavior: "smooth", block: "center" });
}

async function autoMarkAchieved() {
  // Refresh contributions first so the sum is accurate.
  const { data } = await supabase.from("i_goal_contributions").select("*").eq("user_id", state.userId);
  if (data) state.goalContributions = data;
  for (const g of state.goals.filter((x) => x.status === "ACTIVE" && x.type !== "RECURRING")) {
    const target = goalTarget(g);
    if (target > 0 && sumContributions(g.id) >= target) {
      await supabase.from("i_goals").update({ status: "ACHIEVED", achieved_at: new Date().toISOString() }).eq("id", g.id);
    }
  }
}

function openContribForSpecificGoal(g) {
  goalContribForm.classList.remove("hidden");
  refreshGoalContribTargetOptions();
  goalContribMode.value = "SPECIFIC";
  goalContribTarget.value = g.id;
  if (!goalContribDate.value) goalContribDate.value = new Date().toISOString().slice(0, 10);
  refreshContribUI();
  goalContribForm.scrollIntoView({ behavior: "smooth", block: "start" });
  goalContribAmount.focus();
}

goalHistoryToggle.addEventListener("click", () => {
  state.goalHistoryOpen = !state.goalHistoryOpen;
  goalHistoryList.classList.toggle("hidden", !state.goalHistoryOpen);
  goalHistoryToggle.textContent = state.goalHistoryOpen ? "Hide" : "Show";
});

// ============================================================================
// Notion-style view switchers (Ledger / Income / Goals)
// ============================================================================
const ledgerViewSwitch     = $("ledger-view-switch");
const ledgerCardsView      = $("ledger-cards-view");
const ledgerTableView      = $("ledger-table-view");
const ledgerCalendarView   = $("ledger-calendar-view");
const ledgerTableTbody     = $("ledger-table-tbody");
const ledgerTableCount     = $("ledger-table-count");
const ledgerCalendarTitle  = $("ledger-calendar-title");
const ledgerCalendar       = $("ledger-calendar");

const incomeViewSwitch     = $("income-view-switch");
const incomeCardsView      = $("income-cards-view");
const incomeTableView      = $("income-table-view");
const incomeCalendarView   = $("income-calendar-view");
const incomeTableTbody     = $("income-table-tbody");
const incomeTableCount     = $("income-table-count");
const incomeCalendarTitle  = $("income-calendar-title");
const incomeCalendar       = $("income-calendar");

const goalsViewSwitch      = $("goals-view-switch");
const goalsCardsView       = $("goals-cards-view");
const goalsTableView       = $("goals-table-view");
const goalsProgressView    = $("goals-progress-view");
const goalsTableTbody      = $("goals-table-tbody");
const goalsTableCount      = $("goals-table-count");
const goalsProgressGrid    = $("goals-progress-grid");

function bindViewSwitch(switchEl, key, applyFn) {
  if (!switchEl) return;
  switchEl.addEventListener("click", (e) => {
    const btn = e.target.closest(".view-switch-pill");
    if (!btn) return;
    state[key] = btn.dataset.view;
    switchEl.querySelectorAll(".view-switch-pill").forEach((p) =>
      p.classList.toggle("active", p.dataset.view === state[key]));
    applyFn();
  });
}
bindViewSwitch(ledgerViewSwitch, "ledgerListView", applyLedgerListView);
bindViewSwitch(incomeViewSwitch, "incomeListView", applyIncomeListView);
bindViewSwitch(goalsViewSwitch,  "goalsListView",  applyGoalsListView);

// Calendar nav buttons (shared markup pattern: data-cal-nav inside the view).
function bindCalendarNav(viewEl, key, render) {
  if (!viewEl) return;
  viewEl.querySelectorAll("[data-cal-nav]").forEach((btn) => {
    btn.addEventListener("click", () => {
      const dir = btn.dataset.calNav;
      const cur = new Date(state[key]);
      if (dir === "prev")  cur.setMonth(cur.getMonth() - 1);
      if (dir === "next")  cur.setMonth(cur.getMonth() + 1);
      if (dir === "today") cur.setTime(new Date(new Date().getFullYear(), new Date().getMonth(), 1).getTime());
      state[key] = new Date(cur.getFullYear(), cur.getMonth(), 1).getTime();
      render();
    });
  });
}
bindCalendarNav(ledgerCalendarView, "ledgerCalCursor", renderLedgerCalendar);
bindCalendarNav(incomeCalendarView, "incomeCalCursor", renderIncomeCalendar);

// ---- Appliers — pick which view is visible and render it -------------------
function applyLedgerListView() {
  ledgerCardsView.classList.toggle("hidden",    state.ledgerListView !== "cards");
  ledgerTableView.classList.toggle("hidden",    state.ledgerListView !== "table");
  ledgerCalendarView.classList.toggle("hidden", state.ledgerListView !== "calendar");
  if (state.ledgerListView === "table")    renderLedgerTable();
  if (state.ledgerListView === "calendar") renderLedgerCalendar();
}
function applyIncomeListView() {
  incomeCardsView.classList.toggle("hidden",    state.incomeListView !== "cards");
  incomeTableView.classList.toggle("hidden",    state.incomeListView !== "table");
  incomeCalendarView.classList.toggle("hidden", state.incomeListView !== "calendar");
  if (state.incomeListView === "table")    renderIncomeTable();
  if (state.incomeListView === "calendar") renderIncomeCalendar();
}
function applyGoalsListView() {
  goalsCardsView.classList.toggle("hidden",    state.goalsListView !== "cards");
  goalsTableView.classList.toggle("hidden",    state.goalsListView !== "table");
  goalsProgressView.classList.toggle("hidden", state.goalsListView !== "progress");
  if (state.goalsListView === "table")    renderGoalsTable();
  if (state.goalsListView === "progress") renderGoalsProgress();
}

// ---- Ledger: Table ----------------------------------------------------------
function renderLedgerTable() {
  const all = [...state.ledger].sort((a, b) => {
    if (a.status !== b.status) return a.status === "ACTIVE" ? -1 : 1;
    return sortByDue(a, b);
  });
  ledgerTableCount.textContent = `${all.length} total`;
  ledgerTableTbody.innerHTML = all.length === 0
    ? `<tr><td colspan="6" class="muted">Nothing here yet.</td></tr>`
    : all.map((e) => {
        const overdue = e.status === "ACTIVE" && isOverdue(e.due_date);
        const status = e.status === "ACTIVE" ? (overdue ? "overdue" : "active")
                     : e.status === "SETTLED" ? "settled" : "archived";
        const balanceClass = e.direction === "I_OWE" ? "amount-out" : "amount-in";
        const directionLabel = e.direction === "I_OWE" ? "I owe" : "Owed to me";
        const typeLabel = e.type === "RECURRING" ? `Recurring (${cadenceLabel(e.cadence, e.interval_days)})` : "IOU";
        return `
          <tr>
            <td><strong>${escapeHtml(e.counterparty)}</strong>${e.note ? `<div class="muted small">${escapeHtml(e.note)}</div>` : ""}</td>
            <td><span class="tag">${escapeHtml(typeLabel)}</span></td>
            <td>${escapeHtml(directionLabel)}</td>
            <td>${e.due_date ? escapeHtml(formatDueDate(e.due_date)) : '<span class="muted">—</span>'}</td>
            <td class="right ${balanceClass}">${formatETB(Number(e.balance))}</td>
            <td><span class="status-pill ${status}">${status.toUpperCase()}</span></td>
          </tr>
        `;
      }).join("");
}

// ---- Income: Table ---------------------------------------------------------
function renderIncomeTable() {
  const all = [...state.income].sort((a, b) => {
    if (a.status !== b.status) return a.status === "ACTIVE" ? -1 : 1;
    if (a.type !== b.type)     return a.type === "RECURRING" ? -1 : 1;
    return sortByDueIncome(a, b);
  });
  incomeTableCount.textContent = `${all.length} total`;
  incomeTableTbody.innerHTML = all.length === 0
    ? `<tr><td colspan="6" class="muted">Nothing here yet.</td></tr>`
    : all.map((e) => {
        const overdue = e.type === "RECURRING" && e.status === "ACTIVE" && isOverdue(e.occurs_on);
        const status = e.status !== "ACTIVE" ? "archived"
                     : overdue ? "overdue" : "active";
        const cadence = e.type === "RECURRING" ? cadenceLabel(e.cadence, e.interval_days) : "—";
        const date = e.occurs_on ? formatDueDate(e.occurs_on) : "—";
        return `
          <tr>
            <td><strong>${escapeHtml(e.source)}</strong>${e.note ? `<div class="muted small">${escapeHtml(e.note)}</div>` : ""}</td>
            <td><span class="tag">${e.type === "RECURRING" ? "Recurring" : "One-off"}</span></td>
            <td>${escapeHtml(cadence)}</td>
            <td>${escapeHtml(date)}</td>
            <td class="right amount-in">+ ${formatETB(Number(e.amount))}</td>
            <td><span class="status-pill ${status}">${status.toUpperCase()}</span></td>
          </tr>
        `;
      }).join("");
}

// ---- Goals: Table ----------------------------------------------------------
function renderGoalsTable() {
  const all = [...state.goals].sort((a, b) => {
    if (a.status !== b.status) return a.status === "ACTIVE" ? -1 : 1;
    return (a.priority ?? 100) - (b.priority ?? 100);
  });
  goalsTableCount.textContent = `${all.length} total`;
  goalsTableTbody.innerHTML = all.length === 0
    ? `<tr><td colspan="6" class="muted">No goals yet.</td></tr>`
    : all.map((g) => {
        const target = goalTarget(g);
        const saved  = sumContributions(g.id);
        const pct    = target > 0 ? Math.min(100, (saved / target) * 100) : 0;
        const status = g.status === "ACTIVE" ? goalStatus(g) : g.status.toLowerCase();
        const typeLabel = g.type === "DATE_BOUND" ? "Date-bound"
                        : g.type === "OPEN_ENDED" ? "Open-ended"
                        : "Recurring";
        return `
          <tr>
            <td><strong>${escapeHtml(g.emoji || "💰")} ${escapeHtml(g.name)}</strong></td>
            <td><span class="tag">${typeLabel}</span></td>
            <td class="right amount-in">${formatETB(saved)}</td>
            <td class="right">${target > 0 ? formatETB(target) : '<span class="muted">—</span>'}</td>
            <td>
              <span class="mini-bar"><span style="width: ${pct.toFixed(0)}%"></span></span>
              <span class="muted small">${pct.toFixed(0)}%</span>
            </td>
            <td><span class="status-pill ${status}">${status.toUpperCase().replace("-", " ")}</span></td>
          </tr>
        `;
      }).join("");
}

// ---- Goals: Progress wall --------------------------------------------------
function renderGoalsProgress() {
  const active = state.goals.filter((g) => g.status === "ACTIVE")
    .sort((a, b) => (a.priority ?? 100) - (b.priority ?? 100));
  if (active.length === 0) {
    goalsProgressGrid.innerHTML = `<p class="muted">No active goals.</p>`;
    return;
  }
  goalsProgressGrid.innerHTML = active.map((g) => {
    const target = goalTarget(g);
    const saved  = sumContributions(g.id);
    const pct    = target > 0 ? Math.min(100, (saved / target) * 100) : 0;
    const st     = goalStatus(g);
    const r = 60, c = 2 * Math.PI * r;
    const dash = (pct / 100) * c;
    const ringColor = st === "behind" ? "var(--coral)" : "var(--green-income)";
    const trackColor = "rgba(255, 255, 255, 0.08)";
    const cardClass = `goal-progress-card ${st === "behind" ? "behind" : st === "achieved" ? "achieved" : ""}`;
    return `
      <div class="${cardClass}">
        <div class="ring-wrap">
          <svg viewBox="0 0 140 140">
            <circle cx="70" cy="70" r="${r}" fill="none" stroke="${trackColor}" stroke-width="12"/>
            <circle cx="70" cy="70" r="${r}" fill="none" stroke="${ringColor}" stroke-width="12"
                    stroke-linecap="round" stroke-dasharray="${c.toFixed(2)}" stroke-dashoffset="${(c - dash).toFixed(2)}"/>
          </svg>
          <div class="ring-pct">
            <span>${pct.toFixed(0)}%</span>
            <span class="meta">${st.toUpperCase().replace("-", " ")}</span>
          </div>
        </div>
        <div class="name-row"><span class="emoji">${escapeHtml(g.emoji || "💰")}</span>${escapeHtml(g.name)}</div>
        <div class="meta-row">${formatETB(saved)} of ${target > 0 ? formatETB(target) : "—"}</div>
      </div>
    `;
  }).join("");
}

// ---- Calendar — shared layout helper ---------------------------------------
/**
 * Render a month calendar into `container`.
 * @param {HTMLElement} container - the .calendar-grid element.
 * @param {HTMLElement} titleEl   - element to set the "Month YYYY" title in.
 * @param {number}      cursorMs  - epoch ms of the 1st of the month to show.
 * @param {(date: Date) => Array<{label, klass, title}>} chipsForDay - returns up to N chips per cell.
 */
function renderMonthCalendar(container, titleEl, cursorMs, chipsForDay) {
  const cursor = new Date(cursorMs);
  const year = cursor.getFullYear();
  const month = cursor.getMonth();
  titleEl.textContent = cursor.toLocaleDateString(undefined, { month: "long", year: "numeric" });

  const firstDayOfMonth = new Date(year, month, 1);
  const startWeekday = firstDayOfMonth.getDay(); // 0=Sun
  const daysInMonth = new Date(year, month + 1, 0).getDate();
  const prevMonthDays = new Date(year, month, 0).getDate();
  const todayKey = todayKeyOf(new Date());

  let html = ["S", "M", "T", "W", "T", "F", "S"]
    .map((l) => `<div class="calendar-day-label">${l}</div>`)
    .join("");

  // Total 42 cells (6 rows × 7 cols) — fills nicely.
  for (let i = 0; i < 42; i++) {
    let cellDate, isOther;
    if (i < startWeekday) {
      cellDate = new Date(year, month - 1, prevMonthDays - (startWeekday - 1 - i));
      isOther = true;
    } else if (i - startWeekday < daysInMonth) {
      cellDate = new Date(year, month, i - startWeekday + 1);
      isOther = false;
    } else {
      cellDate = new Date(year, month + 1, i - startWeekday - daysInMonth + 1);
      isOther = true;
    }
    const isToday = todayKeyOf(cellDate) === todayKey;
    const chips = chipsForDay(cellDate) || [];
    const visibleChips = chips.slice(0, 3);
    const extra = chips.length - visibleChips.length;
    const klasses = ["calendar-cell"];
    if (isOther) klasses.push("other-month");
    if (isToday) klasses.push("today");
    html += `<div class="${klasses.join(" ")}">
      <span class="day-num">${cellDate.getDate()}</span>
      ${visibleChips.map((c) =>
        `<span class="day-chip ${c.klass}" title="${escapeHtml(c.title || "")}">${escapeHtml(c.label)}</span>`
      ).join("")}
      ${extra > 0 ? `<span class="day-overflow">+ ${extra} more</span>` : ""}
    </div>`;
  }
  container.innerHTML = html;
}

function todayKeyOf(d) {
  return `${d.getFullYear()}-${d.getMonth()}-${d.getDate()}`;
}

// ---- Ledger: Calendar ------------------------------------------------------
function renderLedgerCalendar() {
  renderMonthCalendar(
    ledgerCalendar,
    ledgerCalendarTitle,
    state.ledgerCalCursor,
    (date) => {
      const key = todayKeyOf(date);
      return state.ledger
        .filter((e) => e.status === "ACTIVE" && e.due_date && todayKeyOf(new Date(e.due_date)) === key)
        .map((e) => {
          const overdue = isOverdue(e.due_date);
          const klass = overdue ? "overdue" : (e.direction === "I_OWE" ? "owe" : "lent");
          const sign = e.direction === "I_OWE" ? "-" : "+";
          return {
            label: `${sign} ${e.counterparty.slice(0, 14)} · ${formatNumber(Number(e.balance))}`,
            klass,
            title: `${e.counterparty}: ${e.direction === "I_OWE" ? "I owe" : "owed to me"} ${formatETB(Number(e.balance))}${overdue ? " — OVERDUE" : ""}`,
          };
        });
    }
  );
}

// ---- Income: Calendar ------------------------------------------------------
function renderIncomeCalendar() {
  renderMonthCalendar(
    incomeCalendar,
    incomeCalendarTitle,
    state.incomeCalCursor,
    (date) => {
      const key = todayKeyOf(date);
      const chips = [];
      // Tracked recurring income — expected dates.
      for (const e of state.income) {
        if (e.status !== "ACTIVE" || !e.occurs_on) continue;
        if (todayKeyOf(new Date(e.occurs_on)) !== key) continue;
        const overdue = e.type === "RECURRING" && isOverdue(e.occurs_on);
        chips.push({
          label: `${e.source.slice(0, 14)} · ${formatNumber(Number(e.amount))}`,
          klass: overdue ? "overdue" : "income",
          title: `${e.source}: ${e.type === "RECURRING" ? "expected" : "received"} ${formatETB(Number(e.amount))}`,
        });
      }
      return chips;
    }
  );
}

function setupGreeting(user) {
  const hour = new Date().getHours();
  const greet = hour < 12 ? "Good morning," : hour < 17 ? "Good afternoon," : "Good evening,";
  greetingEl.textContent = greet;
  userNameEl.textContent =
    user.user_metadata?.name ||
    user.user_metadata?.full_name ||
    (user.email ? user.email.split("@")[0] : "There");
}

// ----------------------------------------------------------------------------
// Period helpers
// ----------------------------------------------------------------------------
function periodRange(period, now = new Date()) {
  const start = new Date(now);
  switch (period) {
    case "month": {
      start.setDate(1); start.setHours(0, 0, 0, 0);
      return { start: start.getTime(), end: now.getTime() };
    }
    case "last-month": {
      const s = new Date(now.getFullYear(), now.getMonth() - 1, 1);
      const e = new Date(now.getFullYear(), now.getMonth(), 1) - 1;
      return { start: s.getTime(), end: e };
    }
    case "30d":  return { start: now.getTime() - 30 * 864e5, end: now.getTime() };
    case "90d":  return { start: now.getTime() - 90 * 864e5, end: now.getTime() };
    case "year": {
      const s = new Date(now.getFullYear(), 0, 1);
      return { start: s.getTime(), end: now.getTime() };
    }
    case "all":
    default:     return { start: 0, end: now.getTime() };
  }
}

function periodDays(period, range) {
  if (period === "all") return Math.max(1, Math.ceil((range.end - (state.allTxs.at(-1)?.occurred_at ? Date.parse(state.allTxs.at(-1).occurred_at) : range.end)) / 864e5));
  return Math.max(1, Math.ceil((range.end - range.start) / 864e5));
}

// ----------------------------------------------------------------------------
// Render
// ----------------------------------------------------------------------------
function rerender() {
  if (!state.allTxs) return;
  const range = periodRange(state.period);
  const txs = state.allTxs;
  const inRange = txs.filter((t) => {
    const ts = Date.parse(t.occurred_at);
    return Number.isFinite(ts) && ts >= range.start && ts <= range.end;
  });

  const label = PERIOD_LABELS[state.period] || "";
  statPeriodLabel1.textContent = label;
  statPeriodLabel2.textContent = label;

  renderHeaderStats(txs, inRange, range);
  renderAccounts(txs);
  renderCategoryList(inRange);
  renderCounterparties(inRange);
  renderRecurring(inRange);
  renderDailyChart(txs);
  renderMonthlyChart(txs);
  renderWeekdayStrip(inRange);
  renderTxTable();
  if (state.view === "categories") renderCategoriesView();
}

function renderHeaderStats(allTxs, inRange, range) {
  // Latest balance per bank → total balance (independent of period filter).
  const byBank = groupBy(allTxs, (t) => t.bank_name || "Unknown");
  let totalBalance = 0;
  let accountCount = 0;
  for (const [, list] of byBank) {
    const latestWithBalance = list.find((t) => typeof t.balance === "number" && t.balance >= 0);
    if (latestWithBalance) { totalBalance += latestWithBalance.balance; accountCount++; }
    else accountCount++;
  }

  let spend = 0, spendCount = 0;
  let income = 0, incomeCount = 0;
  let largestExpense = null;
  // Per-day expense totals, keyed by yyyy-mm-dd. We want the avg-daily-spend
  // metric to be "average of the days you actually spent money", not just
  // "total spend ÷ raw days elapsed" — that way a quiet weekend doesn't drag
  // your daily average to nothing.
  const dailyTotals = new Map();

  for (const tx of inRange) {
    if (EXPENSE_TYPES.has(tx.type)) {
      spend += tx.amount;
      spendCount++;
      if (!largestExpense || tx.amount > largestExpense.amount) largestExpense = tx;
      const d = new Date(tx.occurred_at);
      if (!Number.isNaN(d.getTime())) {
        const key = `${d.getFullYear()}-${d.getMonth()}-${d.getDate()}`;
        dailyTotals.set(key, (dailyTotals.get(key) || 0) + tx.amount);
      }
    } else if (tx.type === "CREDIT") {
      income += tx.amount;
      incomeCount++;
    }
  }

  const net = income - spend;
  // Sum of daily totals ÷ count of distinct days with spend.
  // (Sum is mathematically the same as `spend`, but doing it via dailyTotals
  // makes the intent explicit and matches the sub-line copy below.)
  const sumOfDailyTotals = [...dailyTotals.values()].reduce((s, v) => s + v, 0);
  const daysWithSpend = dailyTotals.size;
  const avgDaily = daysWithSpend > 0 ? sumOfDailyTotals / daysWithSpend : 0;
  const savingsRate = income > 0 ? ((income - spend) / income) * 100 : null;

  statBalance.textContent       = formatETB(totalBalance);
  statBalanceSub.textContent    = `${accountCount} account${accountCount === 1 ? "" : "s"}`;

  statMonthSpend.textContent    = formatETB(spend);
  statMonthSpendSub.textContent = `${spendCount} transaction${spendCount === 1 ? "" : "s"}`;

  statMonthIncome.textContent   = formatETB(income);
  statMonthIncomeSub.textContent = `${incomeCount} credit${incomeCount === 1 ? "" : "s"}`;

  statNet.textContent = (net >= 0 ? "+ " : "- ") + formatNumberAbs(net);
  statNet.classList.remove("teal", "coral", "income");
  statNet.classList.add(net >= 0 ? "income" : "coral");
  statNetSub.textContent = savingsRate == null
    ? "no income recorded"
    : `${savingsRate >= 0 ? "saving" : "overspending"} ${formatPercent(savingsRate)}`;

  statAvgDaily.textContent = formatETB(avgDaily);
  statAvgDailySub.textContent = daysWithSpend === 0
    ? "no spending days"
    : `over ${daysWithSpend} day${daysWithSpend === 1 ? "" : "s"} with spend`;

  if (largestExpense) {
    statLargest.textContent = formatETB(largestExpense.amount);
    statLargestSub.textContent = (largestExpense.counterparty || largestExpense.bank_name || "—").slice(0, 36);
  } else {
    statLargest.textContent = "—";
    statLargestSub.textContent = "no expenses yet";
  }
}

function renderAccounts(allTxs) {
  const byBank = groupBy(allTxs, (t) => t.bank_name || "Unknown");
  const accounts = [...byBank.entries()].map(([name, list]) => {
    const latestWithBalance = list.find((t) => typeof t.balance === "number" && t.balance >= 0);
    return {
      name,
      balance: latestWithBalance?.balance ?? 0,
      count: list.length,
    };
  }).sort((a, b) => b.balance - a.balance);

  accountsCount.textContent = `${accounts.length} bank${accounts.length === 1 ? "" : "s"}`;
  accountsList.innerHTML = accounts.length === 0
    ? `<p class="muted">No accounts synced yet. Pull-to-refresh in the Android app to push your data.</p>`
    : accounts.map(renderAccountCard).join("");
}

function renderAccountCard(account) {
  return `
    <div class="bank-card">
      <span class="rail"></span>
      <div class="info">
        <p class="name">${escapeHtml(account.name)}</p>
        <p class="balance">${formatNumber(account.balance)}<span class="unit">ETB</span></p>
      </div>
    </div>
  `;
}

function renderCategoryList(inRange) {
  const byCategory = new Map();
  for (const tx of inRange) {
    if (!EXPENSE_TYPES.has(tx.type)) continue;
    const cat = (tx.category || "").trim();
    if (!cat) continue;
    byCategory.set(cat, (byCategory.get(cat) || 0) + tx.amount);
  }
  const rows = [...byCategory.entries()].sort((a, b) => b[1] - a[1]);
  categoryList.innerHTML = rows.length === 0
    ? `<p class="muted">No categorised expenses for this period.</p>`
    : rows.map(([name, total]) => `
        <div class="category-row">
          <span class="name">${escapeHtml(name)}</span>
          <span class="amount">${formatETB(total)}</span>
        </div>
      `).join("");
}

function renderCounterparties(inRange) {
  const byCp = new Map();
  for (const tx of inRange) {
    if (!EXPENSE_TYPES.has(tx.type)) continue;
    const cp = (tx.counterparty || "").trim();
    if (!cp) continue;
    const entry = byCp.get(cp) || { total: 0, count: 0 };
    entry.total += tx.amount;
    entry.count += 1;
    byCp.set(cp, entry);
  }
  const rows = [...byCp.entries()]
    .sort((a, b) => b[1].total - a[1].total)
    .slice(0, 10);
  counterpartyList.innerHTML = rows.length === 0
    ? `<p class="muted">No counterparty data yet.</p>`
    : rows.map(([name, { total, count }]) => renderSelectableRow({
        name, total, count, type: "counterparty",
        meta: `${count}×`,
      })).join("");
  attachSelectionHandlers(counterpartyList);
}

function renderRecurring(inRange) {
  const byCp = new Map();
  for (const tx of inRange) {
    if (!EXPENSE_TYPES.has(tx.type)) continue;
    const cp = (tx.counterparty || "").trim();
    if (!cp) continue;
    const entry = byCp.get(cp) || { total: 0, count: 0, last: 0 };
    entry.total += tx.amount;
    entry.count += 1;
    const ts = Date.parse(tx.occurred_at) || 0;
    if (ts > entry.last) entry.last = ts;
    byCp.set(cp, entry);
  }
  const rows = [...byCp.entries()]
    .filter(([, v]) => v.count >= 3)
    .sort((a, b) => b[1].count - a[1].count)
    .slice(0, 12);
  recurringList.innerHTML = rows.length === 0
    ? `<p class="muted">No recurring patterns detected yet (need 3+ payments to the same counterparty).</p>`
    : rows.map(([name, { total, count, last }]) => renderSelectableRow({
        name, total, count, type: "recurring",
        meta: `${count} payments · last ${formatRelativeDate(last)}`,
      })).join("");
  attachSelectionHandlers(recurringList);
}

// ----------------------------------------------------------------------------
// Selectable row (used by Top Counterparties and Recurring Spend)
// ----------------------------------------------------------------------------
function renderSelectableRow({ name, total, meta, type }) {
  const isSelected = state.selectedCps.has(name);
  const currentCategory = currentCategoryFor(name);
  const rowClass = type === "recurring" ? "recurring-row" : "counterparty-row";
  return `
    <div class="${rowClass}${isSelected ? " selected" : ""}" data-cp="${escapeHtml(name)}">
      <span class="row-check" aria-hidden="true"></span>
      <span class="name" title="${escapeHtml(name)}">${escapeHtml(name)}</span>
      ${currentCategory ? `<span class="category-currently">${escapeHtml(currentCategory)}</span>` : ""}
      <span class="meta">${escapeHtml(meta)}</span>
      <span class="amount">${formatETB(total)}</span>
    </div>
  `;
}

function currentCategoryFor(counterparty) {
  // Look up the most common existing category for this counterparty.
  const counts = new Map();
  for (const tx of state.allTxs) {
    if ((tx.counterparty || "").trim() !== counterparty) continue;
    const c = (tx.category || "").trim();
    if (!c) continue;
    counts.set(c, (counts.get(c) || 0) + 1);
  }
  let best = null, bestCount = 0;
  for (const [c, n] of counts) if (n > bestCount) { best = c; bestCount = n; }
  return best;
}

function attachSelectionHandlers(container) {
  container.querySelectorAll("[data-cp]").forEach((row) => {
    row.addEventListener("click", () => {
      const cp = row.dataset.cp;
      if (state.selectedCps.has(cp)) state.selectedCps.delete(cp);
      else state.selectedCps.add(cp);
      // Re-render both lists so a selection in one mirrors to the other.
      const range = periodRange(state.period);
      const inRange = state.allTxs.filter((t) => {
        const ts = Date.parse(t.occurred_at);
        return Number.isFinite(ts) && ts >= range.start && ts <= range.end;
      });
      renderCounterparties(inRange);
      renderRecurring(inRange);
      updateBatchBar();
    });
  });
}

// ----------------------------------------------------------------------------
// Batch action bar
// ----------------------------------------------------------------------------
function renderBatchPills() {
  // Build pills: defaults + any custom categories already used in the data.
  const used = new Set();
  for (const tx of state.allTxs) {
    const c = (tx.category || "").trim();
    if (c) used.add(c);
  }
  const customs = [...used].filter((c) => !DEFAULT_CATEGORIES.includes(c)).sort();
  const all = [...DEFAULT_CATEGORIES, ...customs];

  batchPills.innerHTML = all.map((c) => `
    <button type="button" class="category-pill" data-category="${escapeHtml(c)}">${escapeHtml(c)}</button>
  `).join("") + `
    <button type="button" class="category-pill" data-category="__custom__">+ Custom…</button>
  `;
  batchPills.querySelectorAll(".category-pill").forEach((p) => {
    p.addEventListener("click", () => {
      if (p.dataset.category === "__custom__") {
        const custom = window.prompt("New category name:");
        if (!custom || !custom.trim()) return;
        state.pickedCategory = custom.trim();
      } else {
        state.pickedCategory = p.dataset.category;
      }
      // Highlight the chosen pill.
      batchPills.querySelectorAll(".category-pill").forEach((el) => {
        el.classList.toggle("selected", el.dataset.category === state.pickedCategory);
      });
      batchApply.disabled = state.selectedCps.size === 0 || !state.pickedCategory;
    });
  });
}

function updateBatchBar() {
  const n = state.selectedCps.size;
  if (n === 0) {
    batchBar.classList.remove("visible");
    state.pickedCategory = null;
    batchPills.querySelectorAll(".category-pill").forEach((el) => el.classList.remove("selected"));
    batchApply.disabled = true;
    return;
  }
  batchCount.textContent = `${n} counterpart${n === 1 ? "y" : "ies"} selected`;
  batchBar.classList.add("visible");
  batchApply.disabled = !state.pickedCategory;
}

batchClear.addEventListener("click", () => {
  state.selectedCps.clear();
  // Force re-render of selection visuals.
  const range = periodRange(state.period);
  const inRange = state.allTxs.filter((t) => {
    const ts = Date.parse(t.occurred_at);
    return Number.isFinite(ts) && ts >= range.start && ts <= range.end;
  });
  renderCounterparties(inRange);
  renderRecurring(inRange);
  updateBatchBar();
});

batchApply.addEventListener("click", applyBatchCategory);

async function applyBatchCategory() {
  if (state.selectedCps.size === 0 || !state.pickedCategory || !state.userId) return;
  const counterparties = [...state.selectedCps];
  const category = state.pickedCategory;

  batchApply.disabled = true;
  batchApply.textContent = "Applying…";

  const { error, count } = await supabase
    .from("i_transactions")
    .update({ category }, { count: "exact" })
    .eq("user_id", state.userId)
    .in("counterparty", counterparties);

  batchApply.textContent = "Apply";

  if (error) {
    showToast(`Update failed: ${error.message}`, "error");
    batchApply.disabled = false;
    return;
  }

  // Optimistically update local state so the UI reflects the change immediately.
  for (const tx of state.allTxs) {
    if (counterparties.includes((tx.counterparty || "").trim())) tx.category = category;
  }
  state.selectedCps.clear();
  state.pickedCategory = null;
  showToast(`Categorised ${count ?? "—"} transactions as ${category}`, "success");
  rerender();
  updateBatchBar();
}

function showToast(message, kind = "success") {
  toastEl.textContent = message;
  toastEl.className = `toast ${kind} visible`;
  clearTimeout(showToast._t);
  showToast._t = setTimeout(() => { toastEl.className = "toast"; }, 3500);
}

// ----------------------------------------------------------------------------
// Charts — inline SVG bar charts (no external libraries)
// ----------------------------------------------------------------------------
function renderDailyChart(allTxs) {
  // Last 30 days of expenses, one bar per day.
  const days = 30;
  const today = new Date();
  today.setHours(23, 59, 59, 999);
  const start = today.getTime() - (days - 1) * 864e5;
  const buckets = Array.from({ length: days }, (_, i) => ({
    day: new Date(start + i * 864e5),
    total: 0,
  }));

  for (const tx of allTxs) {
    if (!EXPENSE_TYPES.has(tx.type)) continue;
    const ts = Date.parse(tx.occurred_at);
    if (!Number.isFinite(ts) || ts < start) continue;
    const idx = Math.floor((ts - start) / 864e5);
    if (idx >= 0 && idx < days) buckets[idx].total += tx.amount;
  }

  const max = Math.max(1, ...buckets.map((b) => b.total));
  dailyChartWin.textContent = `last ${days} days · max ${formatNumber(max)} ETB`;
  dailyChart.innerHTML = renderBarChartSvg(
    buckets.map((b) => ({ value: b.total, label: b.day.getDate().toString() })),
    { color: "var(--coral)" }
  );
}

function renderMonthlyChart(allTxs) {
  // Last 12 months, side-by-side income + expense bars.
  const months = 12;
  const now = new Date();
  const buckets = Array.from({ length: months }, (_, i) => {
    const d = new Date(now.getFullYear(), now.getMonth() - (months - 1 - i), 1);
    return { d, income: 0, expense: 0 };
  });

  const monthIndex = (date) => {
    const monthsBack = (now.getFullYear() - date.getFullYear()) * 12 + (now.getMonth() - date.getMonth());
    return (months - 1) - monthsBack;
  };
  for (const tx of allTxs) {
    const ts = Date.parse(tx.occurred_at);
    if (!Number.isFinite(ts)) continue;
    const idx = monthIndex(new Date(ts));
    if (idx < 0 || idx >= months) continue;
    if (EXPENSE_TYPES.has(tx.type)) buckets[idx].expense += tx.amount;
    else if (tx.type === "CREDIT") buckets[idx].income += tx.amount;
  }

  const max = Math.max(1, ...buckets.flatMap((b) => [b.income, b.expense]));
  monthlyChart.innerHTML = renderGroupedBarChartSvg(
    buckets.map((b) => ({
      label: b.d.toLocaleDateString(undefined, { month: "short" }),
      values: [b.income, b.expense],
    })),
    { colors: ["var(--green-income)", "var(--coral)"], max }
  );
}

function renderBarChartSvg(points, { color }) {
  if (points.length === 0) return `<p class="muted">No data.</p>`;
  const W = 600, H = 220, pad = { l: 8, r: 8, t: 12, b: 26 };
  const innerW = W - pad.l - pad.r;
  const innerH = H - pad.t - pad.b;
  const max = Math.max(1, ...points.map((p) => p.value));
  const gap = 2;
  const barW = (innerW - gap * (points.length - 1)) / points.length;

  const bars = points.map((p, i) => {
    const h = (p.value / max) * innerH;
    const x = pad.l + i * (barW + gap);
    const y = pad.t + innerH - h;
    return `<rect class="chart-bar" x="${x.toFixed(2)}" y="${y.toFixed(2)}" width="${barW.toFixed(2)}" height="${Math.max(0, h).toFixed(2)}" rx="3" fill="${color}">
      <title>${escapeHtml(p.label)}: ${formatETB(p.value)}</title>
    </rect>`;
  }).join("");

  // Sparse x-axis labels (~6 evenly spaced).
  const labelStride = Math.max(1, Math.floor(points.length / 6));
  const labels = points.map((p, i) => {
    if (i % labelStride !== 0) return "";
    const x = pad.l + i * (barW + gap) + barW / 2;
    return `<text x="${x.toFixed(2)}" y="${(H - 6).toFixed(2)}" class="chart-axis-label" text-anchor="middle">${escapeHtml(p.label)}</text>`;
  }).join("");

  return `<svg viewBox="0 0 ${W} ${H}" preserveAspectRatio="none">${bars}${labels}</svg>`;
}

function renderGroupedBarChartSvg(groups, { colors, max }) {
  if (groups.length === 0) return `<p class="muted">No data.</p>`;
  const W = 600, H = 220, pad = { l: 8, r: 8, t: 12, b: 26 };
  const innerW = W - pad.l - pad.r;
  const innerH = H - pad.t - pad.b;
  const groupCount = groups.length;
  const valuesPerGroup = groups[0].values.length;
  const groupGap = 6;
  const groupW = (innerW - groupGap * (groupCount - 1)) / groupCount;
  const barW = Math.max(2, (groupW - 2 * (valuesPerGroup - 1)) / valuesPerGroup);

  const bars = groups.flatMap((g, gi) => g.values.map((v, vi) => {
    const h = (v / max) * innerH;
    const groupX = pad.l + gi * (groupW + groupGap);
    const x = groupX + vi * (barW + 2);
    const y = pad.t + innerH - h;
    return `<rect class="chart-bar" x="${x.toFixed(2)}" y="${y.toFixed(2)}" width="${barW.toFixed(2)}" height="${Math.max(0, h).toFixed(2)}" rx="2" fill="${colors[vi]}">
      <title>${escapeHtml(g.label)} · ${vi === 0 ? "Income" : "Expense"}: ${formatETB(v)}</title>
    </rect>`;
  })).join("");

  const labels = groups.map((g, gi) => {
    const x = pad.l + gi * (groupW + groupGap) + groupW / 2;
    return `<text x="${x.toFixed(2)}" y="${(H - 6).toFixed(2)}" class="chart-axis-label" text-anchor="middle">${escapeHtml(g.label)}</text>`;
  }).join("");

  return `<svg viewBox="0 0 ${W} ${H}" preserveAspectRatio="none">${bars}${labels}</svg>`;
}

function renderWeekdayStrip(inRange) {
  // Average spend per weekday (Sun..Sat).
  const sums = [0, 0, 0, 0, 0, 0, 0];
  const counts = [0, 0, 0, 0, 0, 0, 0];
  const seenDays = [new Set(), new Set(), new Set(), new Set(), new Set(), new Set(), new Set()];
  for (const tx of inRange) {
    if (!EXPENSE_TYPES.has(tx.type)) continue;
    const ts = Date.parse(tx.occurred_at);
    if (!Number.isFinite(ts)) continue;
    const d = new Date(ts);
    const dow = d.getDay();
    sums[dow] += tx.amount;
    counts[dow] += 1;
    seenDays[dow].add(`${d.getFullYear()}-${d.getMonth()}-${d.getDate()}`);
  }
  const avgs = sums.map((s, i) => seenDays[i].size > 0 ? s / seenDays[i].size : 0);
  const max = Math.max(1, ...avgs);
  const labels = ["S", "M", "T", "W", "T", "F", "S"];
  weekdayStrip.innerHTML = labels.map((day, i) => {
    const pct = Math.round((avgs[i] / max) * 100);
    return `
      <div class="weekday-cell">
        <div class="day">${day}</div>
        <div class="amount">${avgs[i] > 0 ? formatNumber(avgs[i]) : "—"}</div>
        <div class="meter"><span style="width: ${pct}%"></span></div>
      </div>
    `;
  }).join("");
}

// ----------------------------------------------------------------------------
// Transactions table (search + type filter + CSV export)
// ----------------------------------------------------------------------------
function renderTxTable() {
  const filtered = applyTxFilters(state.allTxs);
  txCount.textContent = `${state.allTxs.length} loaded · ${filtered.length} shown`;
  const recent = filtered.slice(0, 100);
  txTbody.innerHTML = recent.length === 0
    ? `<tr><td colspan="7" class="muted">No transactions match this filter.</td></tr>`
    : recent.map(renderTxRow).join("");
}

function applyTxFilters(txs) {
  const q = state.search;
  const t = state.typeFilter;
  return txs.filter((tx) => {
    if (t && tx.type !== t) return false;
    if (q) {
      const blob = `${tx.counterparty || ""} ${tx.bank_name || ""} ${tx.sender || ""} ${tx.ref_num || ""} ${tx.category || ""}`.toLowerCase();
      if (!blob.includes(q)) return false;
    }
    return true;
  });
}

function renderTxRow(tx) {
  const outgoing = EXPENSE_TYPES.has(tx.type);
  const amount = `${outgoing ? "-" : "+"} ${formatNumber(tx.amount)}`;
  const amountClass = outgoing ? "amount-out" : "amount-in";
  return `
    <tr>
      <td>${formatDate(tx.occurred_at)}</td>
      <td>${escapeHtml(tx.counterparty || tx.sender || "—")}</td>
      <td>${escapeHtml(tx.bank_name || "—")}</td>
      <td><span class="tag">${escapeHtml(prettyType(tx.type))}</span></td>
      <td>${tx.category ? escapeHtml(tx.category) : '<span class="muted">—</span>'}</td>
      <td class="right ${amountClass}">${amount}</td>
      <td class="right">${tx.balance != null ? formatNumber(tx.balance) : '<span class="muted">—</span>'}</td>
    </tr>
  `;
}

function exportCsv() {
  const rows = applyTxFilters(state.allTxs);
  if (rows.length === 0) return;
  const header = ["Date", "Counterparty", "Bank", "Type", "Category", "Amount", "Balance", "Reference"];
  const escape = (v) => {
    const s = v == null ? "" : String(v);
    return /[",\n]/.test(s) ? `"${s.replaceAll('"', '""')}"` : s;
  };
  const lines = [header.join(",")];
  for (const tx of rows) {
    lines.push([
      tx.occurred_at || "",
      tx.counterparty || tx.sender || "",
      tx.bank_name || "",
      tx.type || "",
      tx.category || "",
      tx.amount ?? "",
      tx.balance ?? "",
      tx.ref_num || "",
    ].map(escape).join(","));
  }
  const blob = new Blob(["﻿" + lines.join("\n")], { type: "text/csv;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = `finance-lore-${new Date().toISOString().slice(0, 10)}.csv`;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}

// ----------------------------------------------------------------------------
// Category analysis view
// ----------------------------------------------------------------------------
function renderCategoriesView() {
  const range = periodRange(state.period);
  const inRange = state.allTxs.filter((t) => {
    const ts = Date.parse(t.occurred_at);
    return Number.isFinite(ts) && ts >= range.start && ts <= range.end;
  });

  // Aggregate by category (only expenses).
  const byCategory = new Map();
  let totalCategorised = 0;
  let uncategorisedCount = 0;
  let totalExpense = 0;
  for (const tx of inRange) {
    if (!EXPENSE_TYPES.has(tx.type)) continue;
    totalExpense += tx.amount;
    const cat = (tx.category || "").trim();
    if (!cat) { uncategorisedCount++; continue; }
    if (!byCategory.has(cat)) byCategory.set(cat, { total: 0, count: 0, txs: [] });
    const e = byCategory.get(cat);
    e.total += tx.amount;
    e.count += 1;
    e.txs.push(tx);
    totalCategorised += tx.amount;
  }

  const sortedCats = [...byCategory.entries()].sort((a, b) => b[1].total - a[1].total);

  // Hero stats.
  catTotal.textContent = formatETB(totalCategorised);
  catTotalSub.textContent =
    totalExpense > 0 ? `${((totalCategorised / totalExpense) * 100).toFixed(0)}% of spend categorised` : "no spend recorded";
  if (sortedCats.length > 0) {
    const [topName, top] = sortedCats[0];
    catTopName.textContent = topName;
    const pct = totalCategorised > 0 ? (top.total / totalCategorised) * 100 : 0;
    catTopSub.textContent = `${formatETB(top.total)} · ${pct.toFixed(0)}% of categorised spend`;
  } else {
    catTopName.textContent = "—";
    catTopSub.textContent = "no categorised data yet";
  }
  catUncategorised.textContent = String(uncategorisedCount);
  catUncategorisedSub.textContent = uncategorisedCount === 0
    ? "all expenses tagged"
    : `${uncategorisedCount} expense${uncategorisedCount === 1 ? "" : "s"} missing a tag`;

  // Donut + legend.
  renderCategoryDonut(sortedCats, totalCategorised);

  // 12-month trend stacked by category.
  renderCategoryTrend(sortedCats.map(([n]) => n));

  // Per-category cards with trend vs the prior equivalent period.
  const priorRange = priorPeriodRange(state.period);
  const priorByCategory = priorRange ? aggregateCategoryTotals(state.allTxs, priorRange) : new Map();

  if (sortedCats.length === 0) {
    catCards.innerHTML = `<p class="muted">No categorised expenses for this period. Tag some transactions first.</p>`;
    if (state.activeCatDetail) {
      state.activeCatDetail = null;
      catDetail.classList.add("hidden");
    }
    return;
  }

  catCards.innerHTML = sortedCats.map(([name, data], i) => {
    const color = CAT_PALETTE[i % CAT_PALETTE.length];
    const pct = totalCategorised > 0 ? (data.total / totalCategorised) * 100 : 0;
    const prior = priorByCategory.get(name)?.total ?? 0;
    const trend = prior > 0 ? ((data.total - prior) / prior) * 100 : null;
    const trendClass = trend == null ? "flat" : trend > 5 ? "up" : trend < -5 ? "down" : "flat";
    const trendLabel = trend == null
      ? "no prior data"
      : `${trend >= 0 ? "↑" : "↓"} ${formatPercent(trend)} vs prior`;
    const isActive = state.activeCatDetail === name ? " active" : "";
    return `
      <div class="cat-card${isActive}" data-cat="${escapeHtml(name)}" style="border-top: 3px solid ${color}">
        <div class="cat-head">
          <div class="cat-emoji">${emojiFor(name)}</div>
          <span class="cat-name">${escapeHtml(name)}</span>
          <span class="cat-pct">${pct.toFixed(0)}%</span>
        </div>
        <p class="cat-total">${formatETB(data.total)}</p>
        <div class="cat-bar"><span style="width: ${pct.toFixed(0)}%; background: ${color}"></span></div>
        <div class="cat-meta">
          <span>${data.count} tx</span>
          <span class="cat-trend ${trendClass}">${trendLabel}</span>
        </div>
      </div>
    `;
  }).join("");

  // Wire click → drill-in
  catCards.querySelectorAll(".cat-card").forEach((card) => {
    card.addEventListener("click", () => {
      const name = card.dataset.cat;
      openCategoryDetail(name);
    });
  });

  // Refresh open detail if any.
  if (state.activeCatDetail && byCategory.has(state.activeCatDetail)) {
    openCategoryDetail(state.activeCatDetail);
  } else if (state.activeCatDetail) {
    state.activeCatDetail = null;
    catDetail.classList.add("hidden");
  }
}

function aggregateCategoryTotals(allTxs, range) {
  const m = new Map();
  for (const tx of allTxs) {
    if (!EXPENSE_TYPES.has(tx.type)) continue;
    const ts = Date.parse(tx.occurred_at);
    if (!Number.isFinite(ts) || ts < range.start || ts > range.end) continue;
    const cat = (tx.category || "").trim();
    if (!cat) continue;
    if (!m.has(cat)) m.set(cat, { total: 0, count: 0 });
    const e = m.get(cat);
    e.total += tx.amount;
    e.count += 1;
  }
  return m;
}

function priorPeriodRange(period, now = new Date()) {
  switch (period) {
    case "month":      return periodRange("last-month", now);
    case "last-month": {
      const s = new Date(now.getFullYear(), now.getMonth() - 2, 1);
      const e = new Date(now.getFullYear(), now.getMonth() - 1, 1) - 1;
      return { start: s.getTime(), end: e };
    }
    case "30d":        return { start: Date.now() - 60 * 864e5, end: Date.now() - 30 * 864e5 };
    case "90d":        return { start: Date.now() - 180 * 864e5, end: Date.now() - 90 * 864e5 };
    case "year": {
      const s = new Date(now.getFullYear() - 1, 0, 1);
      const e = new Date(now.getFullYear(), 0, 1) - 1;
      return { start: s.getTime(), end: e };
    }
    default: return null;
  }
}

function openCategoryDetail(name) {
  state.activeCatDetail = name;
  catCards.querySelectorAll(".cat-card").forEach((c) => c.classList.toggle("active", c.dataset.cat === name));

  const range = periodRange(state.period);
  const txs = state.allTxs.filter((t) => {
    if (!EXPENSE_TYPES.has(t.type)) return false;
    if ((t.category || "").trim() !== name) return false;
    const ts = Date.parse(t.occurred_at);
    return Number.isFinite(ts) && ts >= range.start && ts <= range.end;
  });

  const total = txs.reduce((s, t) => s + t.amount, 0);
  const avg = txs.length > 0 ? total / txs.length : 0;

  // Prior-period comparison
  const prior = priorPeriodRange(state.period);
  let trendStr = "no prior data", trendColor = "muted";
  if (prior) {
    const priorTotal = state.allTxs
      .filter((t) => EXPENSE_TYPES.has(t.type) && (t.category || "").trim() === name)
      .filter((t) => {
        const ts = Date.parse(t.occurred_at);
        return Number.isFinite(ts) && ts >= prior.start && ts <= prior.end;
      })
      .reduce((s, t) => s + t.amount, 0);
    if (priorTotal > 0) {
      const pct = ((total - priorTotal) / priorTotal) * 100;
      trendStr = `${pct >= 0 ? "↑" : "↓"} ${formatPercent(pct)}`;
      trendColor = pct > 0 ? "coral" : pct < 0 ? "income" : "teal";
    } else if (total > 0) {
      trendStr = "first-time spend";
      trendColor = "teal";
    }
  }

  catDetailTitle.textContent = `${emojiFor(name)}  ${name}`;
  catDetailTotal.textContent = formatETB(total);
  catDetailTotalSub.textContent = `${txs.length} transaction${txs.length === 1 ? "" : "s"}`;
  catDetailTrend.textContent = trendStr;
  catDetailTrend.classList.remove("coral", "income", "teal", "muted");
  catDetailTrend.classList.add(trendColor);
  catDetailTrendSub.textContent = "compared to prior equivalent window";
  catDetailAvg.textContent = formatETB(avg);
  catDetailAvgSub.textContent = `over ${txs.length} payment${txs.length === 1 ? "" : "s"}`;

  catDetailTbody.innerHTML = txs.length === 0
    ? `<tr><td colspan="4" class="muted">No transactions tagged ${escapeHtml(name)} in this period.</td></tr>`
    : txs.slice(0, 100).map((tx) => `
        <tr>
          <td>${formatDate(tx.occurred_at)}</td>
          <td>${escapeHtml(tx.counterparty || tx.sender || "—")}</td>
          <td>${escapeHtml(tx.bank_name || "—")}</td>
          <td class="right amount-out">- ${formatNumber(tx.amount)}</td>
        </tr>
      `).join("");

  catDetail.classList.remove("hidden");
}

function renderCategoryDonut(sortedCats, total) {
  if (sortedCats.length === 0 || total === 0) {
    catDonut.innerHTML = `<p class="muted">No categorised expenses yet.</p>`;
    catDonutLegend.innerHTML = "";
    return;
  }
  const W = 240, H = 240, cx = W / 2, cy = H / 2, r = 96, strokeW = 28;
  let angle = -Math.PI / 2;  // start at 12 o'clock
  const arcs = sortedCats.map(([name, data], i) => {
    const slicePct = data.total / total;
    const sweep = slicePct * Math.PI * 2;
    const a0 = angle;
    const a1 = angle + sweep;
    angle = a1;
    const x0 = cx + Math.cos(a0) * r;
    const y0 = cy + Math.sin(a0) * r;
    const x1 = cx + Math.cos(a1) * r;
    const y1 = cy + Math.sin(a1) * r;
    const largeArc = sweep > Math.PI ? 1 : 0;
    const color = CAT_PALETTE[i % CAT_PALETTE.length];
    // Stroke-arc path so we get a donut without filling the centre.
    return `<path d="M ${x0.toFixed(2)} ${y0.toFixed(2)} A ${r} ${r} 0 ${largeArc} 1 ${x1.toFixed(2)} ${y1.toFixed(2)}"
      fill="none" stroke="${color}" stroke-width="${strokeW}" stroke-linecap="butt">
      <title>${escapeHtml(name)}: ${formatETB(data.total)} (${(slicePct * 100).toFixed(0)}%)</title>
    </path>`;
  }).join("");

  catDonut.innerHTML = `
    <svg viewBox="0 0 ${W} ${H}">
      ${arcs}
      <text x="${cx}" y="${cy - 6}" text-anchor="middle" fill="var(--body-muted)" font-size="11">total</text>
      <text x="${cx}" y="${cy + 16}" text-anchor="middle" fill="var(--headline)" font-size="18" font-weight="700">${escapeHtml(formatNumber(total))}</text>
    </svg>
  `;
  catDonutLegend.innerHTML = sortedCats.map(([name, data], i) => {
    const color = CAT_PALETTE[i % CAT_PALETTE.length];
    const pct = ((data.total / total) * 100).toFixed(0);
    return `<div class="legend-row"><span class="swatch" style="background: ${color}"></span>${escapeHtml(name)} <span class="muted small" style="margin-left:auto">${pct}%</span></div>`;
  }).join("");
}

function renderCategoryTrend(orderedCategoryNames) {
  // Build 12-month per-category totals.
  const months = 12;
  const now = new Date();
  const buckets = Array.from({ length: months }, (_, i) => {
    const d = new Date(now.getFullYear(), now.getMonth() - (months - 1 - i), 1);
    return { d, totals: new Map() };
  });

  for (const tx of state.allTxs) {
    if (!EXPENSE_TYPES.has(tx.type)) continue;
    const cat = (tx.category || "").trim();
    if (!cat) continue;
    const ts = Date.parse(tx.occurred_at);
    if (!Number.isFinite(ts)) continue;
    const d = new Date(ts);
    const monthsBack = (now.getFullYear() - d.getFullYear()) * 12 + (now.getMonth() - d.getMonth());
    const idx = (months - 1) - monthsBack;
    if (idx < 0 || idx >= months) continue;
    buckets[idx].totals.set(cat, (buckets[idx].totals.get(cat) || 0) + tx.amount);
  }

  // Categories present in either the current period or anywhere in the last 12 months.
  const categorySet = new Set(orderedCategoryNames);
  for (const b of buckets) for (const k of b.totals.keys()) categorySet.add(k);
  const categories = [...categorySet];
  // Largest current-period categories first (matches donut/legend ordering).
  categories.sort((a, b) => {
    const ai = orderedCategoryNames.indexOf(a);
    const bi = orderedCategoryNames.indexOf(b);
    if (ai === -1 && bi === -1) return a.localeCompare(b);
    if (ai === -1) return 1;
    if (bi === -1) return -1;
    return ai - bi;
  });

  if (categories.length === 0) {
    catTrend.innerHTML = `<p class="muted">No data.</p>`;
    catTrendLegend.innerHTML = "";
    return;
  }

  const max = Math.max(1, ...buckets.map((b) => [...b.totals.values()].reduce((a, c) => a + c, 0)));
  const W = 600, H = 220, pad = { l: 8, r: 8, t: 12, b: 26 };
  const innerW = W - pad.l - pad.r;
  const innerH = H - pad.t - pad.b;
  const gap = 4;
  const barW = (innerW - gap * (months - 1)) / months;

  const segs = buckets.map((b, gi) => {
    const x = pad.l + gi * (barW + gap);
    let yCursor = pad.t + innerH;
    return categories.map((c, ci) => {
      const v = b.totals.get(c) || 0;
      if (v <= 0) return "";
      const h = (v / max) * innerH;
      yCursor -= h;
      const color = CAT_PALETTE[ci % CAT_PALETTE.length];
      return `<rect class="chart-bar" x="${x.toFixed(2)}" y="${yCursor.toFixed(2)}" width="${barW.toFixed(2)}" height="${h.toFixed(2)}" fill="${color}">
        <title>${escapeHtml(b.d.toLocaleDateString(undefined, { month: "short", year: "numeric" }))} · ${escapeHtml(c)}: ${formatETB(v)}</title>
      </rect>`;
    }).join("");
  }).join("");

  const labels = buckets.map((b, gi) => {
    const x = pad.l + gi * (barW + gap) + barW / 2;
    return `<text x="${x.toFixed(2)}" y="${(H - 6).toFixed(2)}" class="chart-axis-label" text-anchor="middle">${escapeHtml(b.d.toLocaleDateString(undefined, { month: "short" }))}</text>`;
  }).join("");

  catTrend.innerHTML = `<svg viewBox="0 0 ${W} ${H}" preserveAspectRatio="none">${segs}${labels}</svg>`;
  catTrendLegend.innerHTML = categories.map((name, i) => {
    const color = CAT_PALETTE[i % CAT_PALETTE.length];
    return `<div class="legend-row"><span class="swatch" style="background: ${color}"></span>${escapeHtml(name)}</div>`;
  }).join("");
}

function emojiFor(category) {
  const lc = (category || "").toLowerCase();
  if (lc.includes("food"))      return "🍩";
  if (lc.includes("coffee"))    return "☕";
  if (lc.includes("bill"))      return "💡";
  if (lc.includes("loan"))      return "🔄";
  if (lc.includes("drink") || lc.includes("fun")) return "🔥";
  if (lc.includes("transport") || lc.includes("fuel") || lc.includes("car")) return "🛵";
  if (lc.includes("internet") || lc.includes("airtime") || lc.includes("telecom")) return "📶";
  if (lc.includes("rent") || lc.includes("home")) return "🏠";
  if (lc.includes("health") || lc.includes("medic")) return "💊";
  if (lc.includes("shop") || lc.includes("cloth")) return "🛍️";
  return "💳";
}

// ----------------------------------------------------------------------------
// Helpers
// ----------------------------------------------------------------------------
const ETB_FMT = new Intl.NumberFormat("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
function formatNumber(n) { return ETB_FMT.format(Number.isFinite(n) ? n : 0); }
function formatNumberAbs(n) { return formatNumber(Math.abs(n || 0)); }
function formatETB(n) { return `ETB ${formatNumber(n)}`; }
function formatPercent(n) { return `${(Math.abs(n)).toFixed(0)}%`; }

function formatDate(iso) {
  if (!iso) return "—";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleString(undefined, {
    day: "numeric", month: "short", year: "numeric",
    hour: "2-digit", minute: "2-digit"
  });
}

function formatRelativeDate(ts) {
  if (!ts) return "—";
  const days = Math.floor((Date.now() - ts) / 864e5);
  if (days <= 0) return "today";
  if (days === 1) return "yesterday";
  if (days < 7) return `${days} days ago`;
  if (days < 31) return `${Math.floor(days / 7)} week${Math.floor(days / 7) === 1 ? "" : "s"} ago`;
  return new Date(ts).toLocaleDateString(undefined, { day: "numeric", month: "short" });
}

function prettyType(type) {
  switch (type) {
    case "CREDIT":       return "Credit";
    case "DEBIT":        return "Debit";
    case "PAYMENT":      return "Payment";
    case "TRANSFER_OUT": return "Transfer";
    case "UNKNOWN":      return "Unknown";
    default:             return type || "—";
  }
}

function groupBy(items, keyFn) {
  const m = new Map();
  for (const it of items) {
    const k = keyFn(it);
    if (!m.has(k)) m.set(k, []);
    m.get(k).push(it);
  }
  return m;
}

function escapeHtml(value) {
  if (value == null) return "";
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}
