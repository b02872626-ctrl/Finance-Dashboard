// =============================================================================
// Kash — Web companion
//
// Single-page app that signs the user into the same Supabase project as the
// Android app and renders their finance data (accounts, balance, transactions,
// category breakdown, daily/monthly trends, top counterparties, recurring
// spend, day-of-week patterns). All requests go directly to Supabase;
// Row-Level Security in secure_schema.sql restricts each user to their rows.
// =============================================================================

import {
  EXPENSE_TYPES, OWN_ACCOUNTS, isOwnAccountTransfer, isSpending, isIncome,
  ETHIOPIAN_MONTHS, gregorianToEthiopian, ethiopianToGregorian, prevEthiopianMonth,
  accountSuffix, shortBankLabel, groupAccounts,
  inContext, spendOverview, monthlyInOut, paydayPattern, lowBalanceDays, monthBounds, weekStart, DAY,
} from "./insights.js";

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

// ----------------------------------------------------------------------------
// Config — same project as the Android app (local.properties).
// The anon key is safe in client because the database uses RLS on auth.uid().
// ----------------------------------------------------------------------------
const SUPABASE_URL  = "https://sadbxjnmcgzwtjbqqhbk.supabase.co";
const SUPABASE_ANON = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InNhZGJ4am5tY2d6d3RqYnFxaGJrIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzQ2Mzg1NzcsImV4cCI6MjA5MDIxNDU3N30.aXA5I5CD--DjpnsbrHPZLebAmITNSDVmYDo1mkj3Gz4";

// Bump this each release. Matches the Android versionName convention so the
// admin dashboard can show "web-beta17", "android-beta17", etc. in one place.
const APP_VERSION = "web-beta18";

// Password reset links come back here with "#...type=recovery" (or
// "#error_code=otp_expired" once stale). Read the hash before createClient,
// which signs the link's session in and strips it.
const URL_HASH = new URLSearchParams(window.location.hash.slice(1));
let recoveryMode = URL_HASH.get("type") === "recovery";
const recoveryLinkError = URL_HASH.get("error_code") || URL_HASH.get("error");

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
const loginNotice    = $("login-notice");
const loginForgotBtn = $("login-forgot-btn");
const passwordInput  = $("password");
const userNameEl     = $("user-name");
const greetingEl     = $("greeting");
const logoutBtn      = $("logout-btn");
const refreshBtn     = $("refresh-btn");
const calendarToggle = document.querySelector(".calendar-toggle");
const headerMenuBtn   = $("header-menu-btn");
const headerMenuPanel = $("header-menu-panel");
const feedbackBtn     = $("feedback-btn");
const feedbackBackdrop= $("feedback-backdrop");
const feedbackBody    = $("feedback-body");
const feedbackClose   = $("feedback-close");
const googleSigninBtn= $("google-signin-btn");

// Stat cards
const statBalance       = $("stat-balance");
const statBalanceSub    = $("stat-balance-sub");

// Lists / charts
const periodBar      = $("period-bar");
const accountsList   = $("accounts-list");
const accountsCount  = $("accounts-count");
const txTbody        = $("tx-tbody");
const txCount        = $("tx-count");
const txSearch       = $("tx-search");
const txTypeFilter   = $("tx-type-filter");
const txCategoryFilter = $("tx-category-filter");
const txBankFilter   = $("tx-bank-filter");
const txDateFrom     = $("tx-date-from");
const txDateTo       = $("tx-date-to");
const txMinAmount    = $("tx-min-amount");
const txMaxAmount    = $("tx-max-amount");
const txClearFilters = $("tx-clear-filters");
const csvBtn         = $("csv-btn");
const txDetailBackdrop = $("tx-detail-backdrop");
const txDetailBody     = $("tx-detail-body");
const txDetailClose    = $("tx-detail-close");
const chartTooltip     = $("chart-tooltip");
const categoryList   = $("category-list");
const counterpartyList = $("counterparty-list");
const recurringList  = $("recurring-list");
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
const transactionsView = $("transactions-view");
const categoriesView = $("categories-view");
const ledgerView     = $("ledger-view");
const incomeView     = $("income-view");
const goalsView      = $("goals-view");
const budgetsView    = $("budgets-view");

// Budgets refs
const budgetsBadge          = $("budgets-badge");
const budgetsTotalFunded    = $("budgets-total-funded");
const budgetsTotalFundedSub = $("budgets-total-funded-sub");
const budgetsTotalSpent     = $("budgets-total-spent");
const budgetsTotalSpentSub  = $("budgets-total-spent-sub");
const budgetsTotalRemaining = $("budgets-total-remaining");
const budgetsTotalRemainingSub = $("budgets-total-remaining-sub");
const budgetLeftoverSection = $("budget-leftover-section");
const budgetLeftoverList    = $("budget-leftover-list");
const budgetForm            = $("budget-form");
const budgetFormId          = $("budget-form-id");
const budgetFormTitle       = $("budget-form-title");
const budgetSubmit          = $("budget-submit");
const budgetCancel          = $("budget-cancel");
const budgetNameInput       = $("budget-name");
const budgetEmojiInput      = $("budget-emoji");
const budgetAmountInput     = $("budget-amount");
const budgetCadenceSelect   = $("budget-cadence");
const budgetIntervalWrapper = $("budget-interval-wrapper");
const budgetIntervalInput   = $("budget-interval-days");
const budgetFundingDateInput= $("budget-funding-date");
const budgetCategoryPicker  = $("budget-category-picker");
const budgetCategoryHint    = $("budget-category-hint");
const budgetEmpty           = $("budget-empty");
const budgetCardGrid        = $("budget-card-grid");
const budgetsCountLabel     = $("budgets-count-label");

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
const state = {
  allTxs: [],     // raw transactions for the signed-in user (most recent first)
  period: "month",
  search: "",
  typeFilter: "",
  calendar: (() => {
    try { return localStorage.getItem("financeLore.calendar") || "gregorian"; }
    catch (_) { return "gregorian"; }
  })(),
  categoryFilter: "",
  bankFilter: "",
  dateFrom: "",      // ISO date string yyyy-mm-dd, "" means no lower bound
  dateTo: "",        // same, no upper bound when ""
  minAmount: "",     // numeric string, "" means no lower bound
  maxAmount: "",     // same
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
  // Budgets state — fetched on dashboard load; rendered when user opens
  // the Budgets tab or when a leftover prompt needs surfacing.
  budgets: [],                       // i_budgets rows
  budgetCategories: [],              // i_budget_categories rows (budget_id, category)
  editingBudgetId: null,             // when set, the budget form is editing existing
  budgetPickedCategories: new Set(), // category names selected in the form
  // Overview (NAH-30): the chart's range, picked dates, and months shown.
  ovCustom: null,                    // [from, toExclusive] when period is "custom"
  ioMonths: 6,                       // 6 | 12
};

// Defaults match TransactionCategoryCatalog.kt on Android.
const DEFAULT_CATEGORIES = [
  "Food",
  "Coffee and refreshments",
  "Bills",
  "Loan",
  "Drinks and fun",
  "Transport",
  OWN_ACCOUNTS,
];

const PERIOD_LABELS = {
  week: "this week",
  month: "this month",
  "last-month": "last month",
  "30d": "last 30 days",
  "90d": "last 90 days",
  year: "this year",
  all: "all time",
  custom: "picked dates",
};

// ----------------------------------------------------------------------------
// Boot
// ----------------------------------------------------------------------------
(async function init() {
  // Restore the saved calendar preference so the toggle starts on the right
  // button (and so any pre-render formatters use the correct system).
  if (calendarToggle) {
    calendarToggle.querySelectorAll(".calendar-toggle-btn").forEach((b) => {
      b.classList.toggle("active", b.dataset.cal === state.calendar);
    });
  }

  // Swap the em-dash placeholders for shimmering skeleton bars before any
  // data fetches kick off, so the user sees "something is loading" instead
  // of a wall of "—" when they refresh on a slow network. Render functions
  // later overwrite via textContent, which removes the skeleton element
  // entirely — no per-element cleanup needed.
  installLoadingSkeletons();
  // Subscribe stat updates to a tiny "just-rendered" pop animation so the
  // numbers feel alive when fresh data lands.
  installStatPopOnUpdate();

  const { data: { session } } = await supabase.auth.getSession();
  // Drop the pre-paint visibility hint applied by the inline <head> script —
  // from here on the normal .hidden class drives visibility, so an invalid
  // cached token can correctly fall back to the login view.
  document.documentElement.classList.remove("boot-authed", "boot-anon");
  if (session) {
    await showDashboard(session);
    startSessionTracking(session);
    if (recoveryMode) openSettings();
  } else {
    showLogin();
    if (recoveryLinkError) showError("That reset link has expired or was already used. Send a new one.");
  }

  supabase.auth.onAuthStateChange(async (event, session) => {
    if (session) {
      if (event === "PASSWORD_RECOVERY") recoveryMode = true;
      await showDashboard(session);
      startSessionTracking(session);
      if (recoveryMode && settingsBackdrop.classList.contains("hidden")) openSettings();
    } else {
      endSessionBeacon();
      stopSessionTracking();
      showLogin();
    }
  });
})();

// ----------------------------------------------------------------------------
// Loading skeletons + stat-update pop animation
// ----------------------------------------------------------------------------
function installLoadingSkeletons() {
  // Replace any plain "—" content inside .big-number with a shimmer pill.
  // .stat-card-compact narrows the pill so smaller stats don't get an
  // unrealistically wide skeleton.
  document.querySelectorAll(".big-number").forEach((el) => {
    const t = el.textContent.trim();
    if (t === "—" || t === "" || t === "–") {
      el.innerHTML = '<span class="num-skel"></span>';
    }
  });
}

// Triggers a brief pop animation whenever a .big-number's content changes
// from a skeleton/em-dash to a real value (or between two values, e.g. on
// refresh). Uses MutationObserver to avoid having to refactor every
// statXxx.textContent = ... call site.
function installStatPopOnUpdate() {
  // No-op if the browser lacks MutationObserver (only matters for very old
  // browsers — the animation is purely cosmetic, app still works without).
  if (typeof MutationObserver !== "function") return;
  document.querySelectorAll(".big-number").forEach((el) => {
    let lastText = el.textContent.trim();
    const obs = new MutationObserver(() => {
      const next = el.textContent.trim();
      // Skip animation when we're transitioning into the skeleton placeholder
      // itself (no visible text) or when value didn't actually change.
      if (next === lastText) return;
      if (el.querySelector(".num-skel")) { lastText = next; return; }
      lastText = next;
      // Restart the keyframe animation by toggling the class off then on.
      el.classList.remove("just-rendered");
      // Force reflow so the animation actually replays on re-add.
      void el.offsetWidth;
      el.classList.add("just-rendered");
    });
    obs.observe(el, { childList: true, characterData: true, subtree: true });
  });
}

// ----------------------------------------------------------------------------
// Session tracking — feeds the admin dashboard's time-spent metrics.
// Inserts one row in i_sessions per page lifetime, heartbeats every 30s, and
// best-effort writes ended_at on tab close via fetch keepalive.
// ----------------------------------------------------------------------------
let _sessionRow = null;
let _sessionToken = null;
let _heartbeatTimer = null;

async function startSessionTracking(session) {
  if (_sessionRow || document.visibilityState === "hidden") return;
  _sessionToken = session.access_token;
  const { data, error } = await supabase
    .from("i_sessions")
    .insert({
      user_id: session.user.id,
      client: "web",
      app_version: APP_VERSION,
      user_agent: (navigator.userAgent || "").slice(0, 500),
    })
    .select()
    .maybeSingle();
  if (error || !data) {
    if (error && !/relation .* does not exist/i.test(error.message)) {
      console.warn("Session insert failed:", error.message);
    }
    return;
  }
  _sessionRow = data;
  _heartbeatTimer = setInterval(sendHeartbeat, 30_000);
  window.addEventListener("beforeunload", endSessionBeacon);
  window.addEventListener("pagehide", endSessionBeacon);
}

async function sendHeartbeat() {
  if (!_sessionRow) return;
  const startedAt = new Date(_sessionRow.started_at).getTime();
  const duration = Math.max(0, Math.round((Date.now() - startedAt) / 1000));
  const { error } = await supabase
    .from("i_sessions")
    .update({
      last_heartbeat_at: new Date().toISOString(),
      duration_seconds: duration,
    })
    .eq("id", _sessionRow.id);
  if (error) console.warn("Session heartbeat failed:", error.message);
}

// A session is one visible stretch, like the Android app's foreground
// stretch. Heartbeats used to keep running in a background tab, so a tab
// left open overnight logged 10+ hours of "use".
document.addEventListener("visibilitychange", async () => {
  if (document.visibilityState === "hidden") {
    endSessionBeacon();
    stopSessionTracking();
    return;
  }
  const { data: { session } } = await supabase.auth.getSession();
  if (session) startSessionTracking(session);
});

// keepalive lets the request outlive the page unload — sendBeacon would be
// the canonical choice, but PostgREST needs the Authorization header and
// sendBeacon does not let us set custom headers.
function endSessionBeacon() {
  if (!_sessionRow || !_sessionToken) return;
  const startedAt = new Date(_sessionRow.started_at).getTime();
  const nowIso = new Date().toISOString();
  const payload = JSON.stringify({
    ended_at: nowIso,
    last_heartbeat_at: nowIso,
    duration_seconds: Math.max(0, Math.round((Date.now() - startedAt) / 1000)),
  });
  try {
    fetch(`${SUPABASE_URL}/rest/v1/i_sessions?id=eq.${_sessionRow.id}`, {
      method: "PATCH",
      headers: {
        "Content-Type": "application/json",
        "apikey": SUPABASE_ANON,
        "Authorization": `Bearer ${_sessionToken}`,
        "Prefer": "return=minimal",
      },
      body: payload,
      keepalive: true,
    }).catch(() => {});
  } catch (_) { /* page may be tearing down */ }
}

function stopSessionTracking() {
  if (_heartbeatTimer) { clearInterval(_heartbeatTimer); _heartbeatTimer = null; }
  window.removeEventListener("beforeunload", endSessionBeacon);
  window.removeEventListener("pagehide", endSessionBeacon);
  _sessionRow = null;
  _sessionToken = null;
}

// ----------------------------------------------------------------------------
// Login / Logout
// ----------------------------------------------------------------------------
loginForm.addEventListener("submit", async (e) => { e.preventDefault(); await attemptLogin(); });
retryBtn.addEventListener("click", () => { hideError(); retryBtn.classList.add("hidden"); passwordInput.value = ""; passwordInput.focus(); });
googleSigninBtn.addEventListener("click", async () => {
  hideError();
  googleSigninBtn.disabled = true;
  try {
    // Returning to the current page so the auth-state listener picks up the
    // new session and switches to the dashboard automatically.
    const redirectTo = window.location.origin + window.location.pathname;
    const { error } = await supabase.auth.signInWithOAuth({
      provider: "google",
      options: { redirectTo },
    });
    if (error) {
      showError(prettyAuthError(error.message));
      googleSigninBtn.disabled = false;
    }
    // On success the browser is redirected to Google; no further code runs here.
  } catch (e) {
    showError("Couldn't start Google sign-in. Check your connection and try again.");
    googleSigninBtn.disabled = false;
    console.warn("Google OAuth failed:", e);
  }
});
logoutBtn.addEventListener("click", async () => {
  logoutBtn.disabled = true;
  try {
    // scope: "local" clears the session in this browser even if the remote
    // /auth/v1/logout call fails (expired refresh token, offline, etc).
    const { error } = await supabase.auth.signOut({ scope: "local" });
    if (error) console.warn("signOut error:", error.message);
  } catch (e) {
    console.warn("signOut threw:", e);
  } finally {
    showLogin();
    logoutBtn.disabled = false;
  }
});
refreshBtn.addEventListener("click", async () => {
  // Targeted re-fetch instead of re-running the entire showDashboard so we
  // can show clear loading state, surface errors, and avoid the
  // "early-return-on-tx-error" trap that hides every section.
  refreshBtn.disabled = true;
  const originalLabel = refreshBtn.textContent;
  refreshBtn.textContent = "Refreshing…";
  try {
    const { data: { session } } = await supabase.auth.getSession();
    if (!session) {
      showToast("Not signed in.", "error");
      return;
    }
    const before = state.allTxs.length;
    const { data, error } = await fetchAllTransactions(session.user.id);
    if (error) {
      console.warn("Refresh failed:", error);
      showToast(`Refresh failed: ${error.message}`, "error");
      return;
    }
    state.allTxs = data || [];
    populateTxFilterOptions(state.allTxs);
    rerender();
    // Also kick the ledger/income/goals/budgets re-fetches so those tabs are fresh too.
    fetchLedger().catch((e) => console.warn("Ledger refetch:", e));
    fetchIncome().catch((e) => console.warn("Income refetch:", e));
    fetchGoals().catch((e) => console.warn("Goals refetch:", e));
    fetchBudgets().catch((e) => console.warn("Budgets refetch:", e));
    const delta = state.allTxs.length - before;
    const msg = delta > 0
      ? `Refreshed · ${delta} new transaction${delta === 1 ? "" : "s"}`
      : delta < 0
        ? `Refreshed · ${Math.abs(delta)} fewer rows (filtered or deleted upstream)`
        : `Refreshed · no new transactions`;
    showToast(msg, "success");
  } catch (e) {
    console.warn("Refresh threw:", e);
    showToast("Refresh failed unexpectedly. Check the console.", "error");
  } finally {
    refreshBtn.disabled = false;
    refreshBtn.textContent = originalLabel;
  }
});

async function attemptLogin() {
  hideError();
  hideLoginNotice();
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
function setPeriod(period) {
  state.period = period;
  periodBar.querySelectorAll(".period-pill").forEach((b) => b.classList.toggle("active", b.dataset.period === period));
  const pill = document.getElementById("period-custom-pill");
  if (pill) pill.textContent = period === "custom" && state.ovCustom
    ? `${dayLabel(state.ovCustom[0])} – ${dayLabel(state.ovCustom[1] - 1)}` : "Pick dates";
  // On narrow screens the bar scrolls sideways: keep the chosen one in view.
  periodBar.querySelector(".period-pill.active")?.scrollIntoView({ block: "nearest", inline: "nearest" });
  rerender();
}

periodBar.addEventListener("click", (e) => {
  const btn = e.target.closest(".period-pill");
  if (!btn) return;
  const form = document.getElementById("period-custom-form");
  if (btn.dataset.period === "custom") { form?.classList.toggle("hidden"); return; }
  form?.classList.add("hidden");
  setPeriod(btn.dataset.period);
});

document.getElementById("period-custom-form")?.addEventListener("submit", (e) => {
  e.preventDefault();
  const from = $("period-from").value, to = $("period-to").value;
  if (!from || !to) return;
  const a = new Date(`${from}T00:00:00`).getTime();
  const b = new Date(`${to}T00:00:00`).getTime();
  const [lo, hi] = a <= b ? [a, b] : [b, a];
  state.ovCustom = [lo, hi + 864e5];
  $("period-custom-form").classList.add("hidden");
  setPeriod("custom");
});
document.getElementById("period-custom-cancel")?.addEventListener("click", () => {
  $("period-custom-form").classList.add("hidden");
});

// Hide amounts: blurs every figure (like the app's hidden balance), for
// showing the dashboard to someone. Remembered on this browser.
(() => {
  const btn = document.getElementById("amounts-toggle");
  if (!btn) return;
  const root = document.documentElement;
  const sync = () => {
    const on = root.classList.contains("hide-amounts");
    btn.setAttribute("aria-pressed", String(on));
    btn.setAttribute("aria-label", on ? "Show amounts" : "Hide amounts");
    btn.title = on ? "Show amounts" : "Hide amounts";
  };
  btn.addEventListener("click", () => {
    root.classList.toggle("hide-amounts");
    try { localStorage.setItem("kash.hideAmounts", root.classList.contains("hide-amounts") ? "1" : "0"); } catch (_) {}
    sync();
  });
  sync();
})();

// Dark mode: the sun/moon switch flips what's showing and remembers it;
// until then the system setting decides (CSS prefers-color-scheme).
document.getElementById("theme-toggle")?.addEventListener("click", () => {
  const root = document.documentElement;
  const current = root.getAttribute("data-theme")
    || (window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light");
  const next = current === "dark" ? "light" : "dark";
  root.setAttribute("data-theme", next);
  try { localStorage.setItem("kash.theme", next); } catch (_) {}
});

// The header (greeting + period filter) stays pinned while the page
// scrolls; once pinned it drops to one compact row.
(() => {
  const head = document.querySelector(".k-panel-head");
  if (!head) return;
  const sentinel = document.createElement("div");
  sentinel.className = "k-head-sentinel";
  head.before(sentinel);
  const update = () => head.classList.toggle("stuck", sentinel.getBoundingClientRect().top < 0);
  window.addEventListener("scroll", update, { passive: true });
  window.addEventListener("resize", update);
  update();
})();

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
  const crumb = document.getElementById("k-crumb-view");
  const activeTab = viewTabs.querySelector(`.view-tab[data-view="${name}"] .view-tab-label`);
  if (crumb && activeTab) crumb.textContent = activeTab.textContent;
  dashboardView.classList.toggle("hidden", name !== "dashboard");
  transactionsView.classList.toggle("hidden", name !== "transactions");
  categoriesView.classList.toggle("hidden", name !== "categories");
  ledgerView.classList.toggle("hidden", name !== "ledger");
  incomeView.classList.toggle("hidden", name !== "income");
  goalsView.classList.toggle("hidden", name !== "goals");
  budgetsView.classList.toggle("hidden", name !== "budgets");
  if (name === "transactions") renderTxTable();
  if (name === "categories") renderCategoriesView();
  if (name === "ledger") renderLedgerView();
  if (name === "income") renderIncomeView();
  if (name === "goals") renderGoalsView();
  if (name === "budgets") renderBudgetsView();
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
txCategoryFilter.addEventListener("change", () => { state.categoryFilter = txCategoryFilter.value; renderTxTable(); });
txBankFilter.addEventListener("change", () => { state.bankFilter = txBankFilter.value; renderTxTable(); });
txDateFrom.addEventListener("change", () => { state.dateFrom = txDateFrom.value; renderTxTable(); });
txDateTo.addEventListener("change", () => { state.dateTo = txDateTo.value; renderTxTable(); });
txMinAmount.addEventListener("input", () => { state.minAmount = txMinAmount.value; renderTxTable(); });
txMaxAmount.addEventListener("input", () => { state.maxAmount = txMaxAmount.value; renderTxTable(); });
txClearFilters.addEventListener("click", () => {
  state.search = ""; state.typeFilter = ""; state.categoryFilter = "";
  state.bankFilter = ""; state.dateFrom = ""; state.dateTo = "";
  state.minAmount = ""; state.maxAmount = "";
  txSearch.value = ""; txTypeFilter.value = ""; txCategoryFilter.value = "";
  txBankFilter.value = ""; txDateFrom.value = ""; txDateTo.value = "";
  txMinAmount.value = ""; txMaxAmount.value = "";
  renderTxTable();
});
csvBtn.addEventListener("click", exportCsv);

// ----------------------------------------------------------------------------
// Dashboard rendering
// ----------------------------------------------------------------------------
function showLogin() {
  authShell.classList.add("hidden");
  loginView.classList.remove("hidden");
}

// Every transaction the user has, newest first. Supabase returns at most
// 1,000 rows a request and this used to stop at 2,000, so heavy users lost
// everything older than a few months (empty months in "Money in vs out",
// wrong "All time"). First page with the total count, then the rest in
// parallel. Same { data, error } shape as a single query.
const TX_COLUMNS = "id, occurred_at, bank_name, sender, type, category, amount, balance, counterparty, account_number, ref_num, predicted_category, category_confidence, review_status, merchant_key";
const TX_PAGE = 1000;
const TX_MAX = 50000;
async function fetchAllTransactions(userId) {
  const page = (from) => supabase.from("i_transactions")
    .select(TX_COLUMNS, from === 0 ? { count: "exact" } : undefined)
    .eq("user_id", userId)
    .order("occurred_at", { ascending: false })
    .order("id", { ascending: false })
    .range(from, from + TX_PAGE - 1);
  const first = await page(0);
  if (first.error) return { data: null, error: first.error };
  const total = Math.min(first.count ?? first.data.length, TX_MAX);
  const starts = [];
  for (let from = TX_PAGE; from < total; from += TX_PAGE) starts.push(from);
  const rest = await Promise.all(starts.map(page));
  const failed = rest.find((r) => r.error);
  if (failed) return { data: null, error: failed.error };
  return { data: [first.data, ...rest.map((r) => r.data)].flat(), error: null };
}

async function showDashboard(session) {
  loginView.classList.add("hidden");
  authShell.classList.remove("hidden");
  setupGreeting(session.user);

  const userId = session.user.id;
  const [profileResult, txResult] = await Promise.all([
    supabase.from("i_users").select("name, email").eq("id", userId).maybeSingle(),
    fetchAllTransactions(userId),
  ]);

  if (profileResult.data?.name) userNameEl.textContent = profileResult.data.name;

  if (txResult.error) {
    txTbody.innerHTML = `<tr><td colspan="7" class="muted">Couldn't load transactions: ${escapeHtml(txResult.error.message)}</td></tr>`;
    return;
  }

  state.allTxs = txResult.data || [];
  state.userId = userId;
  populateTxFilterOptions(state.allTxs);
  renderBatchPills();
  rerender();
  // Fire-and-forget ledger + income + goals fetches so the dashboard renders immediately.
  fetchLedger().catch((e) => console.warn("Ledger fetch failed:", e));
  fetchIncome().catch((e) => console.warn("Income fetch failed:", e));
  fetchGoals().catch((e) => console.warn("Goals fetch failed:", e));
  fetchBudgets().catch((e) => console.warn("Budgets fetch failed:", e));
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
  // Stat cards count one-off IOUs only — recurring entries (rent, etc.) inflate
  // the running totals because each cycle is just another instance of the same
  // obligation, not new debt.
  const iOweOneOff     = iOwe.filter((e) => e.type !== "RECURRING");
  const owedToMeOneOff = owedToMe.filter((e) => e.type !== "RECURRING");
  const iOweTotal     = iOweOneOff.reduce((s, e) => s + Number(e.balance), 0);
  const owedToMeTotal = owedToMeOneOff.reduce((s, e) => s + Number(e.balance), 0);
  const net           = owedToMeTotal - iOweTotal;
  const overdueCount  = active.filter((e) => e.type !== "RECURRING" && isOverdue(e.due_date)).length;

  ledgerIOwe.textContent = formatETB(iOweTotal);
  ledgerIOweSub.textContent = iOweOneOff.length === 0
    ? "no debts recorded"
    : `${iOweOneOff.length} entr${iOweOneOff.length === 1 ? "y" : "ies"} · ${overdueOf(iOweOneOff)} overdue`;

  ledgerOwedToMe.textContent = formatETB(owedToMeTotal);
  ledgerOwedToMeSub.textContent = owedToMeOneOff.length === 0
    ? "nothing outstanding"
    : `${owedToMeOneOff.length} entr${owedToMeOneOff.length === 1 ? "y" : "ies"} · ${overdueOf(owedToMeOneOff)} overdue`;

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
  if (state.calendar === "ethiopian") return formatEthiopianDate(d);
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
      // Lock the row while the round-trip is in flight so the user can't
      // double-fire (which on a flaky network could insert two repayments).
      btn.disabled = true;
      const originalText = btn.textContent;
      btn.textContent = "Paying…";
      if (input) input.disabled = true;
      try {
        await logRepayment(id, amount);
      } finally {
        // Re-enable only if the row still exists; the render after a
        // successful payment will replace the markup entirely.
        if (document.body.contains(btn)) {
          btn.disabled = false;
          btn.textContent = originalText;
          if (input) input.disabled = false;
        }
      }
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

  // Wrap the whole sequence so a NETWORK error (which Supabase JS surfaces
  // as a thrown TypeError "Failed to fetch" / ERR_NETWORK_CHANGED on Chrome
  // when Wi-Fi flips, VPN toggles, etc.) doesn't fall through silently.
  // After ANY failure we refetch the ledger so the UI matches what's
  // actually persisted server-side — protects against the half-state where
  // the repayment insert succeeded but the entry update failed.
  try {
    const { error: repError } = await supabase.from("i_ledger_repayments").insert({
      entry_id: entryId,
      user_id:  state.userId,
      amount:   Math.min(amount, currentBalance),
    });
    if (repError) {
      showToast(`Couldn't record payment: ${repError.message}`, "error");
      await fetchLedger().catch(() => {});
      return;
    }

    const updates = { balance: newBalance };
    if (fullyPaid) {
      updates.status = "SETTLED";
      updates.settled_at = new Date().toISOString();
    }
    const { error: upError } = await supabase.from("i_ledger_entries").update(updates).eq("id", entryId);
    if (upError) {
      // Repayment row landed but the parent entry didn't update — refetch
      // so the UI shows the truth (likely an inconsistent state on server).
      showToast(`Payment saved but entry update failed: ${upError.message}. Refreshing…`, "error");
      await fetchLedger().catch(() => {});
      return;
    }

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
  } catch (e) {
    console.warn("logRepayment threw:", e);
    const offline = !navigator.onLine;
    const msg = offline
      ? "You're offline — payment not saved. Reconnect and try again."
      : (/Failed to fetch|NetworkError|ERR_NETWORK|ERR_INTERNET/i.test(String(e))
          ? "Network hiccup — payment not saved. Check your connection and try again."
          : `Couldn't save payment: ${e?.message || e}`);
    showToast(msg, "error");
    // Pull authoritative state from server so UI doesn't lie. Best-effort —
    // if THIS also fails offline, the toast above is the user's signal.
    await fetchLedger().catch(() => {});
  }
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
    .filter((t) => isIncome(t))
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
  incomeActualSub.textContent = `${state.allTxs.filter((t) => isIncome(t)).length} CREDIT tx tracked total`;

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
  const credits = state.allTxs.filter((t) => isIncome(t));

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
    .filter((t) => isSpending(t))
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
  // This week (from Monday) and picked dates (NAH-30 follow-up: one filter
  // for the whole dashboard).
  if (period === "week") return { start: weekStart(now.getTime()), end: now.getTime() };
  if (period === "custom" && state.ovCustom) return { start: state.ovCustom[0], end: state.ovCustom[1] - 1 };
  // 30d / 90d / all are calendar-agnostic — rolling windows.
  if (period === "30d") return { start: now.getTime() - 30 * 864e5, end: now.getTime() };
  if (period === "90d") return { start: now.getTime() - 90 * 864e5, end: now.getTime() };
  if (period === "all") return { start: 0, end: now.getTime() };

  // Ethiopian: this month / last month / this year use EC boundaries.
  if (state.calendar === "ethiopian") {
    const today = gregorianToEthiopian(now);
    const startOfThisEcMonth = ethiopianToGregorian(today.year, today.month, 1).getTime();
    switch (period) {
      case "month":
        return { start: startOfThisEcMonth, end: now.getTime() };
      case "last-month": {
        const prev = prevEthiopianMonth(today.year, today.month);
        const start = ethiopianToGregorian(prev.year, prev.month, 1).getTime();
        // End: 1ms before the first of this EC month.
        return { start, end: startOfThisEcMonth - 1 };
      }
      case "year": {
        // 1 Meskerem (month 1) of the current EC year through now.
        const start = ethiopianToGregorian(today.year, 1, 1).getTime();
        return { start, end: now.getTime() };
      }
    }
  }

  // Gregorian fallback — original behaviour.
  switch (period) {
    case "month": {
      const start = new Date(now); start.setDate(1); start.setHours(0, 0, 0, 0);
      return { start: start.getTime(), end: now.getTime() };
    }
    case "last-month": {
      const s = new Date(now.getFullYear(), now.getMonth() - 1, 1);
      const e = new Date(now.getFullYear(), now.getMonth(), 1) - 1;
      return { start: s.getTime(), end: e };
    }
    case "year": {
      const s = new Date(now.getFullYear(), 0, 1);
      return { start: s.getTime(), end: now.getTime() };
    }
    default: return { start: 0, end: now.getTime() };
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

  renderBalance(txs);
  renderOverview(txs);
  renderInOut(txs);
  renderLowBalance(txs, renderPayday(txs));
  renderAccounts(txs);
  renderCategoryList(inRange);
  renderCounterparties(inRange);
  renderRecurring(inRange);
  renderWeekdayStrip(inRange);
  renderTxTable();
  rerenderBudgets();
  if (state.view === "categories") renderCategoriesView();
}

// ─────────────────────────────────────────────────────────────────────────────
// Overview (NAH-30): the phone's Analytics on the web, plus money in vs out,
// payday and low-balance days. The numbers come from insights.js (tested);
// these functions only draw them.
// ─────────────────────────────────────────────────────────────────────────────
const calKey = () => (state.calendar === "ethiopian" ? "ethiopian" : "gregorian");

// 250 → "250", 12.5 → "12.50", 1,250 → "1.3k", 32,000 → "32k" (as on the phone's notifications).
function shortK(n) {
  const v = Math.abs(n || 0);
  if (v < 1000) return Number.isInteger(v) ? String(v) : v.toFixed(2);
  const one = (x) => (Number.isInteger(x) ? String(x) : x.toFixed(1));
  const k = Math.round(v / 100) / 10;
  if (k < 1000) return `${one(k)}k`;
  return `${one(Math.round(v / 100000) / 10)}m`;
}
const signedK = (n) => `${n < 0 ? "−" : ""}${shortK(n)}`;

// "12 Nov" / "3 Hidar".
function dayLabel(ms) {
  const d = new Date(ms);
  if (state.calendar === "ethiopian") return formatEthiopianDate(d, { includeYear: false });
  return d.toLocaleDateString(undefined, { day: "numeric", month: "short" });
}

function changeText(pct, vs) {
  if (pct == null) return `nothing to compare with ${vs}`;
  if (pct === 0) return `about the same as ${vs}`;
  return `${pct > 0 ? "↑" : "↓"} ${Math.abs(pct)}% vs ${vs}`;
}

function rangeText(o) {
  if (state.period === "custom" || state.period === "all") return `${dayLabel(o.from)} – ${dayLabel(o.to - 1)}`;
  return PERIOD_LABELS[state.period] || "this month";
}

function renderBalance(allTxs) {
  let total = 0;
  const accounts = groupAccounts(allTxs);
  for (const { list } of accounts) {
    const latest = list.find((t) => typeof t.balance === "number" && t.balance >= 0);
    if (latest) total += latest.balance;
  }
  statBalance.textContent = formatETB(total);
  statBalanceSub.textContent = `${accounts.length} account${accounts.length === 1 ? "" : "s"}`;
}

// The top filter as a range for the spending line: week, month and 30 days
// keep their own comparisons; every other period is a span of days.
function overviewRange(allTxs) {
  const p = state.period;
  if (p === "week" || p === "month" || p === "30d") return { preset: p, custom: null };
  const today = new Date(); today.setHours(0, 0, 0, 0);
  const endOfToday = today.getTime() + DAY;
  let start;
  if (p === "all") {
    const times = allTxs.map((t) => Date.parse(t.occurred_at)).filter(Number.isFinite);
    start = times.length ? Math.min(...times) : today.getTime();
  } else {
    start = periodRange(p).start;
  }
  const s = new Date(start); s.setHours(0, 0, 0, 0);
  const r = periodRange(p);
  const e = new Date(Math.min(r.end, endOfToday - 1)); e.setHours(0, 0, 0, 0);
  return { preset: "custom", custom: [s.getTime(), e.getTime() + DAY] };
}

function renderOverview(allTxs) {
  const { preset, custom } = overviewRange(allTxs);
  const o = spendOverview(allTxs, { cal: calKey(), now: Date.now(), preset, custom });
  $("ov-today").textContent = formatETB(o.todaySpent);
  $("ov-today-sub").textContent = o.todayCount === 0 ? "nothing yet today" : `${o.todayCount} payment${o.todayCount === 1 ? "" : "s"}`;
  $("ov-week").textContent = formatETB(o.weekSpent);
  $("ov-week-sub").textContent = changeText(o.weekChangePct, "same point last week");
  $("ov-month").textContent = formatETB(o.monthSpent);
  $("ov-month-sub").textContent = changeText(o.monthChangePct, "same point last month");

  const days = Math.round((o.to - o.from) / DAY);
  const vs = { week: "last week", month: "last month", "30d": "the 30 days before" }[o.preset]
    || { "last-month": "the month before", "90d": "the 90 days before", year: "the same stretch before" }[state.period]
    || `the ${days} days before`;
  $("ot-total").textContent = formatETB(o.total);
  $("ot-change").textContent = o.changePct == null ? "Nothing to compare with yet" : changeText(o.changePct, vs);
  $("ot-avg").textContent = `${formatETB(o.avgPerDay)} a day on average`;
  document.querySelectorAll(".ot-range-label").forEach((el) => { el.textContent = rangeText(o); });
  $("ot-axis-from").textContent = dayLabel(o.from);
  $("ot-axis-to").textContent = dayLabel(o.to - 1);
  const lastIdx = o.days.map((d, i) => (d == null ? -1 : i)).filter((i) => i >= 0).at(-1);
  $("ot-readout").textContent = lastIdx == null ? " " : `${dayLabel(o.from + lastIdx * DAY)}: ${formatETB(o.days[lastIdx])}`;
  $("ot-chart").innerHTML = renderSpendLineSvg(o);

  $("ot-payees").innerHTML = o.topPayees.length === 0
    ? `<p class="muted">No payments ${escapeHtml(rangeText(o))}.</p>`
    : o.topPayees.map((p) => `
        <div class="ins-row">
          <div><p class="ins-name">${escapeHtml(p.name)}</p><p class="muted small">${p.count} payment${p.count === 1 ? "" : "s"}</p></div>
          <span class="ins-amount">${formatETB(p.total)}</span>
        </div>`).join("");
  const maxAcc = Math.max(1, ...o.perAccount.map((a) => a.total));
  $("ot-accounts").innerHTML = o.perAccount.length === 0
    ? `<p class="muted">No spending ${escapeHtml(rangeText(o))}.</p>`
    : o.perAccount.map((a) => `
        <div class="ins-row ins-row-bar">
          <div class="ins-row-top"><p class="ins-name">${escapeHtml(shortBankLabel(a.name))}</p><span class="ins-amount">${formatETB(a.total)}</span></div>
          <div class="ins-bar"><span style="width:${((a.total / maxAcc) * 100).toFixed(1)}%"></span></div>
        </div>`).join("");
}

// Daily line with a soft fill, the average dashed; hover a day for its total.
function renderSpendLineSvg(o) {
  const W = 600, H = 200, padT = 10, padB = 6;
  const n = o.days.length;
  const max = Math.max(1, ...o.days.filter((d) => d != null));
  const x = (i) => (n > 1 ? (i / (n - 1)) * W : W / 2);
  const y = (v) => padT + (1 - v / max) * (H - padT - padB);
  const pts = o.days.map((v, i) => (v == null ? null : [x(i), y(v)])).filter(Boolean);
  const line = pts.map(([px, py], i) => `${i ? "L" : "M"}${px.toFixed(1)} ${py.toFixed(1)}`).join(" ");
  const area = pts.length ? `${line} L${pts.at(-1)[0].toFixed(1)} ${H - padB} L${pts[0][0].toFixed(1)} ${H - padB} Z` : "";
  const avgY = y(o.avgPerDay).toFixed(1);
  const slot = W / Math.max(1, n);
  const hits = o.days.map((v, i) => v == null ? "" : `<rect x="${(x(i) - slot / 2).toFixed(1)}" y="0" width="${slot.toFixed(1)}" height="${H}" fill="transparent"
      data-tip="${escapeHtml(dayLabel(o.from + i * DAY))}|${escapeHtml(formatETB(v))}" data-tip-color="var(--k-sand)"></rect>`).join("");
  const last = pts.at(-1);
  return `<svg viewBox="0 0 ${W} ${H}" preserveAspectRatio="none" role="img"
      aria-label="Spending per day, ${escapeHtml(rangeText(o))}: ${escapeHtml(formatETB(o.total))} in all, ${escapeHtml(formatETB(o.avgPerDay))} a day on average">
    <line x1="0" y1="${H - padB}" x2="${W}" y2="${H - padB}" class="ins-baseline" vector-effect="non-scaling-stroke"/>
    <line x1="0" y1="${avgY}" x2="${W}" y2="${avgY}" class="ins-avg" vector-effect="non-scaling-stroke"/>
    ${area ? `<path d="${area}" class="ins-area"/>` : ""}
    ${line ? `<path d="${line}" class="ins-path" vector-effect="non-scaling-stroke"/>` : ""}
    ${last ? `<line x1="${last[0].toFixed(1)}" y1="${last[1].toFixed(1)}" x2="${last[0].toFixed(1)}" y2="${last[1].toFixed(1)}" class="ins-dot" vector-effect="non-scaling-stroke"/>` : ""}
    ${hits}
  </svg>`;
}

// In and out bars per month, with a kept line that dips below zero when overspent.
function renderInOut(allTxs) {
  const months = monthlyInOut(allTxs, { cal: calKey(), now: Date.now(), count: state.ioMonths });
  document.querySelectorAll("[data-months]").forEach((b) => b.classList.toggle("active", Number(b.dataset.months) === state.ioMonths));
  const W = 600, H = 220, padT = 12, padB = 26;
  const top = Math.max(1, ...months.flatMap((m) => [m.moneyIn, m.out, m.kept]));
  const bottom = Math.min(0, ...months.map((m) => m.kept));
  const y = (v) => padT + ((top - v) / (top - bottom)) * (H - padT - padB);
  const zero = y(0);
  const groupW = W / months.length;
  const barW = Math.min(22, groupW * 0.3);
  const name = (ms) => monthLabel(ms).split(" ")[0].slice(0, 3);
  const bars = months.map((m, i) => {
    const cx = i * groupW + groupW / 2;
    const bar = (v, dx, cls) => {
      const h = Math.max(0, zero - y(v));
      return `<rect x="${(cx + dx).toFixed(1)}" y="${(zero - h).toFixed(1)}" width="${barW.toFixed(1)}" height="${h.toFixed(1)}" rx="3" class="${cls}"/>`;
    };
    const tip = `${name(m.start)}|in ${shortK(m.moneyIn)} · out ${shortK(m.out)} · kept ${signedK(m.kept)}${m.keptPct != null ? ` (${m.keptPct}%)` : ""}`;
    return `${bar(m.moneyIn, -barW - 1, "io-in")}${bar(m.out, 1, "io-out")}
      <rect x="${(i * groupW).toFixed(1)}" y="0" width="${groupW.toFixed(1)}" height="${H - padB}" fill="transparent" data-tip="${escapeHtml(tip)}" data-tip-color="var(--k-sand)"/>
      <text x="${cx.toFixed(1)}" y="${H - 6}" class="chart-axis-label" text-anchor="middle">${escapeHtml(name(m.start))}</text>`;
  }).join("");
  const kept = months.map((m, i) => `${i ? "L" : "M"}${(i * groupW + groupW / 2).toFixed(1)} ${y(m.kept).toFixed(1)}`).join(" ");
  const dots = months.map((m, i) => `<circle cx="${(i * groupW + groupW / 2).toFixed(1)}" cy="${y(m.kept).toFixed(1)}" r="3.5" class="${m.kept < 0 ? "io-kept-dot neg" : "io-kept-dot"}"/>`).join("");
  $("io-chart").innerHTML = `<svg viewBox="0 0 ${W} ${H}" role="img" aria-label="Money in and out by month, with what was kept">
    <line x1="0" y1="${zero.toFixed(1)}" x2="${W}" y2="${zero.toFixed(1)}" class="ins-baseline"/>
    ${bars}<path d="${kept}" class="io-kept"/>${dots}</svg>`;
  const last = months.at(-2);
  $("io-summary").textContent = !last || (last.moneyIn === 0 && last.out === 0)
    ? "Your months show up here as Kash reads your bank SMS."
    : last.kept >= 0
      ? `In ${monthLabel(last.start)} you kept ${formatETB(last.kept)}${last.keptPct != null ? ` (${last.keptPct}% of what came in)` : ""}.`
      : `In ${monthLabel(last.start)}, ${formatETB(-last.kept)} more went out than came in.`;
}

// When income lands and how fast it goes, with a burn-down for this pay cycle.
function renderPayday(allTxs) {
  const p = paydayPattern(allTxs, { cal: calKey(), now: Date.now() });
  const chart = $("pd-chart");
  const legend = $("pd-legend");
  if (!p.found) {
    $("pd-source").textContent = "";
    $("pd-body").innerHTML = `<p class="ins-lead">We haven't spotted a regular income yet.</p>
      <p class="muted small">Once the same person or company pays you a similar amount in two of the last three months, Kash shows when it lands and how fast it goes. Top-ups, loans and moves between your own accounts don't count.</p>`;
    chart.innerHTML = ""; legend.classList.add("hidden");
    return p;
  }
  legend.classList.remove("hidden");
  $("pd-source").textContent = `${p.source} · about ${formatETB(p.amount)}`;
  const dayWord = state.calendar === "ethiopian" ? `day ${p.usualDay} of the month` : `the ${ordinal(p.usualDay)}`;
  // Spent so far this cycle against the usual by the same day (ETB, so it
  // stays honest when spending passes the paycheck).
  const day = p.chart.which === "this" ? p.chart.spent.length : null;
  const spentNow = day ? p.chart.spent[day - 1] : null;
  const usualNow = day ? p.usualSpent[day - 1] : null;
  const pace = spentNow == null || usualNow == null || usualNow <= 0 ? ""
    : spentNow > usualNow * 1.1 ? "That's faster than usual."
    : spentNow < usualNow * 0.9 ? "That's slower than usual."
    : "That's your usual pace.";
  const sinceLine = spentNow == null
    ? `This pay cycle has just started; the chart shows the last one.`
    : spentNow >= p.amount
      ? `Since ${dayLabel(p.lastPayday)} you've spent ${formatETB(spentNow)}, more than this paycheck. ${pace}`
      : `Since ${dayLabel(p.lastPayday)} you've spent ${formatETB(spentNow)}, ${Math.round((spentNow / p.amount) * 100)}% of it. ${pace}`;
  $("pd-body").innerHTML = `
    <p class="ins-lead">Your income usually lands around ${escapeHtml(dayWord)}.</p>
    <ul class="ins-facts muted small">
      ${p.daysTo70 != null ? `<li>By day ${p.daysTo70} after payday you've usually spent 70% of it.</li>`
        : p.spentShare != null ? `<li>You usually spend ${p.spentShare}% of it before the next payday.</li>` : ""}
      <li>${escapeHtml(sinceLine)}</li>
      <li>Next one expected around ${escapeHtml(dayLabel(p.nextPayday))}.</li>
    </ul>`;
  // Money spent since payday (ETB), against the usual pace and the paycheck.
  const W = 600, H = 160, pad = 10;
  const n = p.cycleLen;
  const c = p.chart;
  const top = Math.max(c.paycheck, ...c.spent, ...p.usualSpent.filter((v) => v != null)) * 1.08 || 1;
  const x = (i) => (n > 1 ? (i / (n - 1)) * W : 0);
  const y = (v) => pad + (1 - Math.max(0, v) / top) * (H - 2 * pad);
  const path = (vals) => vals.map((v, i) => (v == null ? "" : `${i ? "L" : "M"}${x(i).toFixed(1)} ${y(v).toFixed(1)}`)).join(" ").trim();
  const payY = y(c.paycheck).toFixed(1);
  const end = c.spent.length - 1;
  const hits = c.spent.map((v, i) => `<rect x="${(x(i) - W / n / 2).toFixed(1)}" y="0" width="${(W / n).toFixed(1)}" height="${H}" fill="transparent"
      data-tip="Day ${i + 1}|${escapeHtml(formatETB(v))} spent${p.usualSpent[i] != null ? ` · usual ${escapeHtml(formatETB(p.usualSpent[i]))}` : ""}" data-tip-color="var(--k-sand)"></rect>`).join("");
  chart.innerHTML = `<svg viewBox="0 0 ${W} ${H}" preserveAspectRatio="none" role="img" aria-label="Money spent since payday: ${c.which === "last" ? "last" : "this"} pay cycle against your usual pace and the paycheck">
    <line x1="0" y1="${H - pad}" x2="${W}" y2="${H - pad}" class="ins-baseline" vector-effect="non-scaling-stroke"/>
    <line x1="0" y1="${payY}" x2="${W}" y2="${payY}" class="pd-paycheck" vector-effect="non-scaling-stroke"/>
    ${p.usualSpent[0] != null ? `<path d="${path(p.usualSpent)}" class="pd-usual" vector-effect="non-scaling-stroke"/>` : ""}
    <path d="${path(c.spent)}" class="pd-current" vector-effect="non-scaling-stroke"/>
    ${end >= 0 ? `<line x1="${x(end).toFixed(1)}" y1="${y(c.spent[end]).toFixed(1)}" x2="${x(end).toFixed(1)}" y2="${y(c.spent[end]).toFixed(1)}" class="ins-dot" vector-effect="non-scaling-stroke"/>` : ""}
    ${hits}
  </svg>`;
  legend.innerHTML = `
    <span class="dot kept-dot"></span><span class="muted small">${c.which === "last" ? "Last pay cycle" : "This pay cycle"}</span>
    <span class="dot usual-dot"></span><span class="muted small">Your usual pace</span>
    <span class="dot paycheck-dot"></span><span class="muted small">Paycheck (${escapeHtml(shortK(c.paycheck))})</span>`;
  return p;
}

// Per account, the days its balance sat under its floor this month.
function renderLowBalance(allTxs, payday) {
  const cal = calKey();
  const now = Date.now();
  // Follows the top filter when it's last month; otherwise this month.
  const at = state.period === "last-month" ? periodRange("last-month").start : now;
  const [monthStart] = monthBounds(at, cal);
  const month = monthLabel(monthStart);
  $("lb-month").textContent = month;
  const accounts = lowBalanceDays(allTxs, { cal, now, month: at, usualPayday: payday?.found ? payday.usualDay : null });
  const body = $("lb-body");
  if (accounts.length === 0) {
    body.innerHTML = `<p class="muted">No balance updates in ${escapeHtml(month)} yet.</p>`;
    return;
  }
  body.innerHTML = accounts.map((a) => {
    const head = a.lowCount === 0
      ? `No low days in ${escapeHtml(month)}`
      : `${a.lowCount} low day${a.lowCount === 1 ? "" : "s"} in ${escapeHtml(month)}${a.beforePayday ? ", mostly the week before payday" : ""}`;
    const cells = a.days.map((d, i) => {
      const cls = d.balance == null ? "lb-day none" : d.low ? "lb-day low" : "lb-day ok";
      const tip = `${dayLabel(monthStart + i * DAY)}|${d.balance == null ? "no balance yet" : formatETB(d.balance)}`;
      return `<span class="${cls}" data-tip="${escapeHtml(tip)}" data-tip-color="${d.low ? "var(--coral)" : "var(--green-income)"}"></span>`;
    }).join("");
    return `<div class="lb-account">
      <div class="ins-row-top"><p class="ins-name">${escapeHtml(a.name)}</p><span class="muted small lb-floor">under ${escapeHtml(formatETB(a.floor))}</span></div>
      <p class="muted small">${head}</p>
      <div class="lb-strip" role="img" aria-label="${escapeHtml(`${a.name}: ${head}`)}">${cells}</div>
    </div>`;
  }).join("");
}

// The 6/12-month switch on Money in vs out.
document.querySelectorAll("[data-months]").forEach((btn) => btn.addEventListener("click", () => {
  state.ioMonths = Number(btn.dataset.months);
  renderInOut(state.allTxs);
}));

function renderAccounts(allTxs) {
  const accounts = groupAccounts(allTxs).map(({ name, suffix, list }) => {
    const latestWithBalance = list.find((t) => typeof t.balance === "number" && t.balance >= 0);
    return {
      name,
      suffix,
      balance: latestWithBalance?.balance ?? 0,
      count: list.length,
    };
  }).sort((a, b) => b.balance - a.balance);

  accountsCount.textContent = `${accounts.length} account${accounts.length === 1 ? "" : "s"}`;
  accountsList.innerHTML = accounts.length === 0
    ? `<p class="muted">No accounts synced yet. Pull-to-refresh in the Android app to push your data.</p>`
    : accounts.map(renderAccountCard).join("");
}

function renderAccountCard(account) {
  return `
    <div class="bank-card">
      <span class="rail"></span>
      <div class="info">
        <p class="name">${escapeHtml(account.name)}${account.suffix ? ` · ••${escapeHtml(account.suffix)}` : ""}</p>
        <p class="balance">${formatNumber(account.balance)}<span class="unit">ETB</span></p>
      </div>
    </div>
  `;
}

function renderCategoryList(inRange) {
  const byCategory = new Map();
  for (const tx of inRange) {
    if (!isSpending(tx)) continue;
    const cat = (tx.category || "").trim();
    if (!cat) continue;
    byCategory.set(cat, (byCategory.get(cat) || 0) + tx.amount);
  }
  const rows = [...byCategory.entries()].sort((a, b) => b[1] - a[1]);
  // Most people never label anything: no labels, no card (NAH-30).
  document.getElementById("category-card")?.classList.toggle("hidden", rows.length === 0);
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
    if (!isSpending(tx)) continue;
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
  attachCounterpartyLinks(counterpartyList);
}

function renderRecurring(inRange) {
  const byCp = new Map();
  for (const tx of inRange) {
    if (!isSpending(tx)) continue;
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
  attachCounterpartyLinks(recurringList);
}

// ----------------------------------------------------------------------------
// Selectable row (used by Top Counterparties and Recurring Spend)
// ----------------------------------------------------------------------------
function renderSelectableRow({ name, total, meta, type }) {
  const currentCategory = currentCategoryFor(name);
  const rowClass = type === "recurring" ? "recurring-row" : "counterparty-row";
  return `
    <div class="${rowClass} cp-link" data-cp="${escapeHtml(name)}" role="button" tabindex="0" title="See transactions with ${escapeHtml(name)}">
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

// Tap a counterparty row: its transactions (no more tick boxes, owner's call).
function attachCounterpartyLinks(container) {
  container.querySelectorAll("[data-cp]").forEach((row) => {
    const open = () => {
      const cp = row.dataset.cp || "";
      state.search = cp.toLowerCase();
      txSearch.value = cp;
      switchView("transactions");
    };
    row.addEventListener("click", open);
    row.addEventListener("keydown", (e) => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); open(); } });
  });
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

  // Also mark these rows as reviewed so the Daily Review queue on mobile
  // doesn't keep asking the user to categorize them. CONFIRMED is correct
  // because the user explicitly picked this category on the web side.
  const { error, count } = await supabase
    .from("i_transactions")
    .update({ category, review_status: "CONFIRMED" }, { count: "exact" })
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
    if (counterparties.includes((tx.counterparty || "").trim())) {
      tx.category = category;
      tx.review_status = "CONFIRMED";
    }
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
function renderWeekdayStrip(inRange) {
  // Average spend on each weekday of the period, counting quiet days too
  // (one Tuesday purchase no longer makes "Tuesday" look expensive).
  const range = periodRange(state.period);
  const first = inRange.reduce((m, t) => Math.min(m, Date.parse(t.occurred_at) || m), Infinity);
  const start = new Date(Math.max(range.start, Number.isFinite(first) ? first : range.start)); start.setHours(0, 0, 0, 0);
  const end = Math.min(range.end, Date.now());
  const sums = [0, 0, 0, 0, 0, 0, 0];
  const days = [0, 0, 0, 0, 0, 0, 0];
  for (let d = new Date(start); d.getTime() <= end; d.setDate(d.getDate() + 1)) days[d.getDay()] += 1;
  for (const tx of inRange) {
    if (!isSpending(tx)) continue;
    const ts = Date.parse(tx.occurred_at);
    if (Number.isFinite(ts)) sums[new Date(ts).getDay()] += tx.amount;
  }
  // Monday first.
  const order = [1, 2, 3, 4, 5, 6, 0];
  const names = ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"];
  const avg = order.map((i) => (days[i] > 0 ? sums[i] / days[i] : 0));
  const max = Math.max(...avg);
  const head = document.getElementById("weekday-head");
  if (max <= 0) {
    if (head) head.textContent = "No spending in this period yet.";
    weekdayStrip.innerHTML = "";
    return;
  }
  const top = avg.indexOf(max);
  const spent = avg.filter((v) => v > 0);
  const low = avg.indexOf(Math.min(...spent));
  if (head) {
    head.textContent = `${names[order[top]]}s cost you most: ${formatETB(max)} on an average ${names[order[top]]}` +
      (low !== top ? `. Quietest: ${names[order[low]]}s, ${formatETB(avg[low])}.` : ".");
  }
  weekdayStrip.innerHTML = `<div class="wd-bars">${order.map((i, k) => `
    <div class="wd-col${k === top ? " top" : ""}" data-tip="${escapeHtml(names[i])}|${escapeHtml(formatETB(avg[k]))} on average|${days[i]} day${days[i] === 1 ? "" : "s"}" data-tip-color="${k === top ? "var(--coral)" : "var(--k-sand)"}">
      <span class="wd-val">${avg[k] > 0 ? escapeHtml(shortK(avg[k])) : "–"}</span>
      <span class="wd-bar"><span style="height:${Math.max(2, (avg[k] / max) * 100).toFixed(1)}%"></span></span>
      <span class="wd-day">${names[i].slice(0, 3)}</span>
    </div>`).join("")}</div>`;
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
  const q   = state.search;
  const t   = state.typeFilter;
  const cat = state.categoryFilter;
  const bnk = state.bankFilter;
  // Date bounds — parse once. Empty string means no bound on that side.
  const fromMs = state.dateFrom ? Date.parse(state.dateFrom) : null;
  // dateTo is inclusive — bump to end-of-day so a tx at 23:59 of that day matches.
  const toMs   = state.dateTo   ? Date.parse(state.dateTo) + 86_400_000 - 1 : null;
  // Amount bounds — empty/NaN means no bound.
  const minA = state.minAmount === "" ? null : Number(state.minAmount);
  const maxA = state.maxAmount === "" ? null : Number(state.maxAmount);
  return txs.filter((tx) => {
    if (t && tx.type !== t) return false;
    if (cat) {
      if (cat === "__UNCATEGORISED__") {
        if (tx.category && tx.category.trim() !== "") return false;
      } else if (tx.category !== cat) {
        return false;
      }
    }
    if (bnk && tx.bank_name !== bnk) return false;
    if (fromMs != null || toMs != null) {
      const txMs = tx.occurred_at ? Date.parse(tx.occurred_at) : NaN;
      if (Number.isNaN(txMs)) return false;
      if (fromMs != null && txMs < fromMs) return false;
      if (toMs   != null && txMs > toMs)   return false;
    }
    if (minA != null || maxA != null) {
      const a = Number(tx.amount);
      if (Number.isNaN(a)) return false;
      if (minA != null && a < minA) return false;
      if (maxA != null && a > maxA) return false;
    }
    if (q) {
      const blob = `${tx.counterparty || ""} ${tx.bank_name || ""} ${tx.sender || ""} ${tx.ref_num || ""} ${tx.category || ""}`.toLowerCase();
      if (!blob.includes(q)) return false;
    }
    return true;
  });
}

// Fills the Category and Bank dropdowns from the unique values present in the
// currently-loaded transactions. Idempotent — preserves the user's current
// selection if that value is still in the new option list.
function populateTxFilterOptions(txs) {
  const cats  = new Set();
  const banks = new Set();
  for (const tx of txs) {
    if (tx.category && tx.category.trim()) cats.add(tx.category);
    if (tx.bank_name && tx.bank_name.trim()) banks.add(tx.bank_name);
  }
  const fillSelect = (sel, items, allLabel, extra = []) => {
    const prev = sel.value;
    const sorted = [...items].sort((a, b) => a.localeCompare(b));
    sel.innerHTML = `<option value="">${allLabel}</option>`
      + extra.map(([v, l]) => `<option value="${escapeHtml(v)}">${escapeHtml(l)}</option>`).join("")
      + sorted.map((x) => `<option value="${escapeHtml(x)}">${escapeHtml(x)}</option>`).join("");
    // Restore selection if still valid (else reset to "All").
    if ([...sel.options].some((o) => o.value === prev)) sel.value = prev;
  };
  fillSelect(txCategoryFilter, cats,  "All categories", [["__UNCATEGORISED__", "(uncategorised)"]]);
  fillSelect(txBankFilter,     banks, "All banks");
}

function renderTxRow(tx) {
  const outgoing = EXPENSE_TYPES.has(tx.type);
  const amount = `${outgoing ? "-" : "+"} ${formatNumber(tx.amount)}`;
  const amountClass = outgoing ? "amount-out" : "amount-in";
  return `
    <tr data-tx-id="${escapeHtml(String(tx.id))}">
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

// ----------------------------------------------------------------------------
// Transaction detail modal — opens on row click, shows every field on the row.
// ----------------------------------------------------------------------------
function openTxDetail(txId) {
  const tx = state.allTxs.find((x) => String(x.id) === String(txId));
  if (!tx) return;
  const outgoing = EXPENSE_TYPES.has(tx.type);
  const amountClass = outgoing ? "amount-out" : "amount-in";
  const directionIcon = outgoing ? "↘" : "↗";
  const directionLabel = outgoing ? "Sent to" : "Received from";
  const counterparty = tx.counterparty || tx.sender || "—";
  const occurredMs = tx.occurred_at ? Date.parse(tx.occurred_at) : NaN;
  const occurredDate = Number.isFinite(occurredMs) ? new Date(occurredMs) : null;
  const absoluteWhen = occurredDate
    ? (state.calendar === "ethiopian"
        ? `${occurredDate.toLocaleDateString(undefined, { weekday: "short" })}, ${formatEthiopianDate(occurredDate, { withTime: true })}`
        : `${occurredDate.toLocaleDateString(undefined, { weekday: "short", day: "numeric", month: "short", year: "numeric" })} · ${occurredDate.toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit" })}`)
    : "—";
  const relativeWhen = occurredDate ? formatRelativeTime(occurredMs) : "";

  // Field rows (everything except the hero stuff already above).
  const rows = [];
  const add = (label, value, valueClass = "") => {
    if (value == null || value === "") return;
    rows.push({ label, value, valueClass });
  };
  add("Bank", tx.bank_name);
  add("Type", prettyType(tx.type));
  add("Balance after", tx.balance != null ? `${formatNumber(tx.balance)} ETB` : null);
  add("Category", tx.category);
  if (tx.predicted_category && tx.predicted_category !== tx.category) {
    add("Predicted", `${tx.predicted_category}${tx.category_confidence != null ? ` · ${Math.round(tx.category_confidence * 100)}% confidence` : ""}`);
  } else if (tx.predicted_category && tx.category_confidence != null) {
    add("Confidence", `${Math.round(tx.category_confidence * 100)}%`);
  }
  add("Review status", tx.review_status);
  add("Reference", tx.ref_num);
  add("Merchant key", tx.merchant_key);
  if (tx.sender && tx.sender !== tx.counterparty) add("Sender", tx.sender);

  // ── Recent activity with this counterparty ─────────────────────────────
  // Match by counterparty OR merchant_key (catches normalised variations).
  const cpKey = (tx.counterparty || tx.sender || "").trim().toLowerCase();
  const mKey  = (tx.merchant_key || "").trim();
  const related = state.allTxs.filter((other) => {
    if (other.id === tx.id) return false;
    const otherCp = (other.counterparty || other.sender || "").trim().toLowerCase();
    if (cpKey && otherCp === cpKey) return true;
    if (mKey && other.merchant_key === mKey) return true;
    return false;
  });
  // Last 30-day rollup for the summary line.
  const THIRTY_D = 30 * 86_400_000;
  const cutoff = (occurredMs || Date.now()) - THIRTY_D;
  const relatedRecent = related.filter((r) => Date.parse(r.occurred_at) >= cutoff);
  const relatedSpend = relatedRecent
    .filter((r) => isSpending(r))
    .reduce((s, r) => s + Number(r.amount || 0), 0);
  const relatedTop = related.slice(0, 5);

  const relatedHtml = related.length === 0 ? "" : `
    <div class="tx-detail-section">
      <h4 class="tx-detail-section-title">Recent activity with ${escapeHtml(counterparty)}</h4>
      <p class="tx-detail-section-meta muted small">
        ${relatedRecent.length} transaction${relatedRecent.length === 1 ? "" : "s"} in the last 30 days · ${formatETB(relatedSpend)} spent
      </p>
      <ul class="tx-related-list">
        ${relatedTop.map((r) => {
          const o = EXPENSE_TYPES.has(r.type);
          const acls = o ? "amount-out" : "amount-in";
          return `<li>
            <span class="tx-related-date muted small">${escapeHtml(formatDate(r.occurred_at))}</span>
            <span class="tx-related-cat muted small">${escapeHtml(r.category || "—")}</span>
            <span class="tx-related-amt ${acls}">${o ? "-" : "+"} ${escapeHtml(formatNumber(r.amount))}</span>
          </li>`;
        }).join("")}
      </ul>
      <button type="button" class="btn-ghost tx-detail-filter-btn" data-cp="${escapeHtml(counterparty)}">
        Show all in Transactions tab
      </button>
    </div>
  `;

  // ── In context: where this one stands in its own month (NAH-30) ───────
  // Money out against the month's spending, money in against what came in,
  // in the user's calendar. Ties share a rank. Own-account moves get none.
  let insightsHtml = "";
  const ctx = inContext(tx, state.allTxs, state.calendar);
  if (ctx && ctx.count > 0) {
    const monthName = monthLabel(ctx.monthStart);
    const what = ctx.kind === "out" ? "payment" : "money in";
    const rankLine = ctx.count === 1
      ? `The only ${what} in ${monthName}`
      : ctx.rank === 1
        ? `The biggest ${what} in ${monthName}, of ${ctx.count}`
        : `${ordinal(ctx.rank)} biggest ${what} in ${monthName}, of ${ctx.count}`;
    const shareLine = ctx.kind === "out"
      ? `${ctx.pct.toFixed(1)}% of ${monthName}'s spending`
      : `${ctx.pct.toFixed(1)}% of what came in during ${monthName}`;
    insightsHtml = `
      <div class="tx-detail-section">
        <h4 class="tx-detail-section-title">In context · ${escapeHtml(monthName)}</h4>
        <ul class="tx-insight-list muted small">
          <li>${escapeHtml(rankLine)}</li>
          <li>${escapeHtml(shareLine)}</li>
          ${ctx.catPct != null ? `<li>${ctx.catPct.toFixed(1)}% of <strong>${escapeHtml(ctx.category)}</strong> spending in ${escapeHtml(monthName)}</li>` : ""}
        </ul>
      </div>
    `;
  }

  txDetailBody.innerHTML = `
    <div class="tx-detail-hero ${amountClass}">
      <div class="tx-detail-direction">${directionIcon}</div>
      <div class="tx-detail-amount-big">${outgoing ? "-" : "+"} ${escapeHtml(formatNumber(tx.amount))} <span class="tx-detail-currency">ETB</span></div>
      <div class="tx-detail-cp muted">${escapeHtml(directionLabel)} <strong>${escapeHtml(counterparty)}</strong></div>
      <div class="tx-detail-when muted small">${escapeHtml(absoluteWhen)}${relativeWhen ? ` · ${escapeHtml(relativeWhen)}` : ""}</div>
    </div>
    <div class="tx-detail-grid">
      ${rows.map((r) => `
        <div class="tx-detail-label">${escapeHtml(r.label)}</div>
        <div class="tx-detail-value ${r.valueClass}">${escapeHtml(String(r.value))}</div>
      `).join("")}
      <div class="tx-detail-label">Row id</div>
      <div class="tx-detail-value"><span class="muted-id">${escapeHtml(String(tx.id))}</span></div>
    </div>
    ${relatedHtml}
    ${insightsHtml}
  `;

  // Wire the "Show all in Transactions tab" button — switches view, sets the
  // search box, closes the modal.
  const filterBtn = txDetailBody.querySelector(".tx-detail-filter-btn");
  if (filterBtn) {
    filterBtn.addEventListener("click", () => {
      const cp = filterBtn.dataset.cp || "";
      // Use the counterparty string as the search term (matches the existing
      // search behavior — case-insensitive `includes` across counterparty/
      // sender/bank/ref/category).
      state.search = cp.toLowerCase();
      txSearch.value = cp;
      closeTxDetail();
      switchView("transactions");
    });
  }

  txDetailBackdrop.classList.remove("hidden");
}

// Returns "3 hours ago", "yesterday", "5 days ago", "2 months ago" etc.
// from a millisecond timestamp. Forward-dated timestamps say "in N…".
function formatRelativeTime(ms) {
  const diff = Date.now() - ms;
  const future = diff < 0;
  const abs = Math.abs(diff);
  const min = 60_000, hr = 3_600_000, day = 86_400_000;
  let value, unit;
  if (abs < min) return future ? "in a moment" : "just now";
  if (abs < hr)        { value = Math.round(abs / min); unit = value === 1 ? "minute" : "minutes"; }
  else if (abs < day)  { value = Math.round(abs / hr);  unit = value === 1 ? "hour"   : "hours"; }
  else if (abs < 30 * day) {
    value = Math.round(abs / day);
    if (value === 1) return future ? "tomorrow" : "yesterday";
    unit = "days";
  }
  else if (abs < 365 * day) { value = Math.round(abs / (30 * day));  unit = value === 1 ? "month" : "months"; }
  else                       { value = Math.round(abs / (365 * day)); unit = value === 1 ? "year"  : "years"; }
  return future ? `in ${value} ${unit}` : `${value} ${unit} ago`;
}

// "1st", "2nd", "3rd", "4th", … ordinal suffix for monthly-rank insight.
// "October" / "Tikimt", with the year when it isn't this year.
function monthLabel(ms) {
  const d = new Date(ms + 864e5 / 2);
  const now = new Date();
  if (state.calendar === "ethiopian") {
    const e = gregorianToEthiopian(d);
    const thisYear = gregorianToEthiopian(now).year;
    return `${ETHIOPIAN_MONTHS[e.month - 1]}${e.year === thisYear ? "" : ` ${e.year}`}`;
  }
  return d.toLocaleDateString(undefined, d.getFullYear() === now.getFullYear() ? { month: "long" } : { month: "long", year: "numeric" });
}

function ordinal(n) {
  if (n <= 0) return String(n);
  const lastTwo = n % 100;
  const lastOne = n % 10;
  if (lastTwo >= 11 && lastTwo <= 13) return `${n}th`;
  if (lastOne === 1) return `${n}st`;
  if (lastOne === 2) return `${n}nd`;
  if (lastOne === 3) return `${n}rd`;
  return `${n}th`;
}

function closeTxDetail() {
  txDetailBackdrop.classList.add("hidden");
  txDetailBody.innerHTML = "";
}

// Row click → open detail. Delegated on tbody so it survives re-renders.
txTbody.addEventListener("click", (e) => {
  const tr = e.target.closest("tr[data-tx-id]");
  if (!tr) return;
  openTxDetail(tr.dataset.txId);
});

txDetailClose.addEventListener("click", closeTxDetail);
txDetailBackdrop.addEventListener("click", (e) => {
  // Click on backdrop (but not on the modal content) closes.
  if (e.target === txDetailBackdrop) closeTxDetail();
});
document.addEventListener("keydown", (e) => {
  if (e.key === "Escape" && !txDetailBackdrop.classList.contains("hidden")) {
    closeTxDetail();
  }
});

// ----------------------------------------------------------------------------
// Chart hover tooltip — single floating element, delegated mousemove/leave on
// the document so it works across every SVG segment that carries data-tip.
//
// data-tip format: "primary|value|meta" — pipe-separated because attribute
// values can't contain DOM, and parsing is trivial. data-tip-color carries
// the matching swatch colour so the tooltip mirrors the segment.
// ----------------------------------------------------------------------------
function showChartTip(e, tipped) {
  const parts = tipped.dataset.tip.split("|");
  const primary = parts[0] || "";
  const value   = parts[1] || "";
  const meta    = parts[2] || "";
  const color   = tipped.dataset.tipColor || "transparent";
  chartTooltip.innerHTML = `
    <span class="tip-swatch" style="background:${color}"></span>
    <strong>${primary}</strong>${value ? ` · ${value}` : ""}${meta ? `<span class="tip-meta">${meta}</span>` : ""}
  `;
  chartTooltip.style.left = `${e.clientX}px`;
  chartTooltip.style.top  = `${e.clientY}px`;
  chartTooltip.classList.remove("hidden");
}

function hideChartTip() {
  chartTooltip.classList.add("hidden");
}

document.addEventListener("mousemove", (e) => {
  // Walk up to find the nearest element with data-tip — works for SVG <path>
  // and <rect> children of <svg> charts.
  const tipped = e.target.closest && e.target.closest("[data-tip]");
  if (tipped) showChartTip(e, tipped);
  else hideChartTip();
});

document.addEventListener("mouseleave", hideChartTip);

// ----------------------------------------------------------------------------
// Header hamburger menu — wraps Feedback / Refresh / Log out so the header
// stays clean on smaller widths. The actual Refresh and Log out click
// handlers live elsewhere (their button IDs are unchanged), this section
// only manages the dropdown's open/close.
// ----------------------------------------------------------------------------
function openHeaderMenu() {
  headerMenuPanel.classList.remove("hidden");
  headerMenuBtn.classList.add("open");
  headerMenuBtn.setAttribute("aria-expanded", "true");
}
function closeHeaderMenu() {
  headerMenuPanel.classList.add("hidden");
  headerMenuBtn.classList.remove("open");
  headerMenuBtn.setAttribute("aria-expanded", "false");
}
if (headerMenuBtn && headerMenuPanel) {
  headerMenuBtn.addEventListener("click", (e) => {
    e.stopPropagation();
    if (headerMenuPanel.classList.contains("hidden")) openHeaderMenu();
    else closeHeaderMenu();
  });
  // Clicking any menu item closes the dropdown after firing (the actual
  // handler — refresh / logout / feedback — runs via the button's own
  // click listener bound elsewhere).
  headerMenuPanel.addEventListener("click", (e) => {
    if (e.target.closest(".header-menu-item")) closeHeaderMenu();
  });
  // Click outside closes.
  document.addEventListener("click", (e) => {
    if (headerMenuPanel.classList.contains("hidden")) return;
    if (!e.target.closest(".header-menu")) closeHeaderMenu();
  });
  document.addEventListener("keydown", (e) => {
    if (e.key === "Escape" && !headerMenuPanel.classList.contains("hidden")) closeHeaderMenu();
  });
}

// ----------------------------------------------------------------------------
// Feedback modal — short tester survey. Same question set as the standalone
// piggybank-feedback.html, ported to the in-app dark theme. Submission
// builds a plaintext summary and lets the tester copy it or fire a mailto.
// ----------------------------------------------------------------------------
const FEEDBACK_QUESTIONS = [
  { grp: "Letting it read your SMS" },
  { id: "perm_feel",   q: "When the app asked to read your SMS, how did you feel?",
    o: ["Totally fine", "A little hesitant", "Quite worried", "Almost didn't allow it"] },

  { grp: "Trusting the numbers" },
  { id: "wrong_guess", q: "Did the app ever tag a transaction wrong?",
    o: ["Never noticed any", "Once or twice", "Fairly often"] },
  { id: "prefer",      q: "Which way would you actually want it to work?",
    o: ["Review once a day (now)", "Confirm every transaction", "Fully automatic, no review"] },
  { id: "trust_total", q: "After about a week, did you trust the total balance?",
    o: ["Yes — stopped checking", "Mostly", "Still checked my bank too"] },

  { grp: "Did it read your banks?" },
  { id: "parse",   q: "Did it read your banks & wallets correctly?",
    o: ["All of them", "Most", "Some were missed"] },
  { id: "unknown", q: 'Sometimes a message the app couldn\'t read shows as "Unknown." How was it to fix one (tag it yourself)?',
    o: ["Easy to fix", "Okay", "A bit annoying", "Never saw one"] },

  { grp: "How you used it" },
  { id: "before",      q: "Before this, how did you track spending?",
    o: ["I didn't", "Kept it in my head", "Notes / spreadsheet", "Another app"] },
  { id: "freq",        q: "How often did you open it?",
    o: ["Daily", "A few times a week", "Weekly", "Rarely"] },
  { id: "opened_for",  q: "What did you open it for most?",
    o: ["The balance", "Daily review", "Insights / spending", "History"] },
  { id: "calendar",    q: "Did you use the Ethiopian (EC) / Gregorian (GC) toggle?",
    o: ["Yes — kept it on EC", "Tried it once", "Didn't notice it"] },

  { grp: "Overall" },
  { id: "keep",      q: "Would you keep using it?",
    o: ["Already do", "Yes", "Maybe", "No"] },
  { id: "recommend", q: "Would you tell a friend about it?",
    o: ["Definitely", "Probably", "Probably not"] },

  { grp: "In your words (optional)" },
  { id: "change",   q: "One thing that annoyed you, or you'd change?", text: true },
  { id: "sentence", q: "Describe Kash to a friend in one sentence.",  text: true },
];

const feedbackAnswers = {};

function renderFeedbackForm() {
  let n = 0;
  const parts = [];
  FEEDBACK_QUESTIONS.forEach((item) => {
    if (item.grp) { parts.push(`<div class="fb-section-title">${escapeHtml(item.grp)}</div>`); return; }
    n++;
    const num = String(n).padStart(2, "0");
    if (item.text) {
      parts.push(`
        <div class="fb-q" data-qid="${escapeHtml(item.id)}">
          <label class="fb-q-label"><span class="fb-q-num">${num}</span>${escapeHtml(item.q)}</label>
          <textarea class="fb-textarea" data-qid="${escapeHtml(item.id)}" placeholder="Type a few words…"></textarea>
        </div>`);
    } else {
      const sizeClass = item.o.length === 3 ? " three" : (item.o.length === 1 ? " one" : "");
      const opts = item.o.map((opt) =>
        `<button type="button" class="fb-opt" data-val="${escapeHtml(opt)}">${escapeHtml(opt)}</button>`
      ).join("");
      parts.push(`
        <div class="fb-q" data-qid="${escapeHtml(item.id)}">
          <label class="fb-q-label"><span class="fb-q-num">${num}</span>${escapeHtml(item.q)}</label>
          <div class="fb-opts${sizeClass}">${opts}</div>
        </div>`);
    }
  });
  parts.push(`<button type="button" id="fb-submit" class="fb-submit">Submit</button>`);
  parts.push(`<p id="fb-form-error" class="error hidden" style="margin-top:10px;font-size:13px;"></p>`);
  feedbackBody.innerHTML = parts.join("");

  // Wire option-pick + textarea handlers.
  feedbackBody.querySelectorAll(".fb-q").forEach((q) => {
    const qid = q.dataset.qid;
    q.querySelectorAll(".fb-opt").forEach((btn) => {
      btn.addEventListener("click", () => {
        q.querySelectorAll(".fb-opt").forEach((b) => b.classList.remove("on"));
        btn.classList.add("on");
        feedbackAnswers[qid] = btn.dataset.val;
      });
    });
    const ta = q.querySelector(".fb-textarea");
    if (ta) ta.addEventListener("input", () => { feedbackAnswers[qid] = ta.value.trim(); });
  });
  feedbackBody.querySelector("#fb-submit").addEventListener("click", submitFeedback);
}

async function submitFeedback() {
  const submitBtn = feedbackBody.querySelector("#fb-submit");
  const errEl     = feedbackBody.querySelector("#fb-form-error");
  if (errEl) { errEl.classList.add("hidden"); errEl.textContent = ""; }
  submitBtn.disabled = true;
  const originalText = submitBtn.textContent;
  submitBtn.textContent = "Submitting…";

  try {
    // Need a user_id to satisfy RLS — fetch the session if state.userId
    // isn't set yet (happens if the user opens feedback before transactions
    // have loaded).
    let userId = state.userId;
    if (!userId) {
      const { data: { session } } = await supabase.auth.getSession();
      userId = session?.user?.id || null;
    }
    if (!userId) {
      throw new Error("Not signed in — please sign in again and retry.");
    }
    const { error } = await supabase.from("i_feedback").insert({
      user_id:    userId,
      user_agent: (navigator.userAgent || "").slice(0, 500),
      client:     "web",
      answers:    feedbackAnswers,
    });
    if (error) {
      // Common case: table doesn't exist yet because the migration hasn't
      // been applied. Show a clear hint.
      if (/relation .* does not exist/i.test(error.message)) {
        throw new Error("Feedback table isn't set up yet. Ask the maintainer to run supabase/add_feedback.sql.");
      }
      throw new Error(error.message);
    }
    showFeedbackSuccess();
  } catch (e) {
    console.warn("Feedback submit failed:", e);
    submitBtn.disabled = false;
    submitBtn.textContent = originalText;
    if (errEl) {
      errEl.textContent = `Couldn't submit: ${e?.message || e}`;
      errEl.classList.remove("hidden");
    }
  }
}

function showFeedbackSuccess() {
  const summary = buildFeedbackSummary();
  feedbackBody.innerHTML = `
    <div class="fb-thanks">
      <h2>Thank you 🐷</h2>
      <p>Your feedback was saved. It really helps shape what comes next.</p>
      <details class="fb-details">
        <summary class="fb-details-summary">Want a copy of your answers?</summary>
        <div class="fb-summary">${escapeHtml(summary)}</div>
        <button type="button" id="fb-copy" class="fb-act-copy" style="width:100%;margin-top:8px;padding:10px;border-radius:10px;font-weight:600;font-size:13px;border:none;cursor:pointer;">Copy answers</button>
        <span id="fb-copied" class="fb-copied"></span>
      </details>
    </div>
  `;
  const copyBtn = feedbackBody.querySelector("#fb-copy");
  if (copyBtn) {
    copyBtn.addEventListener("click", async () => {
      try { await navigator.clipboard.writeText(summary); }
      catch (_) {
        const ta = document.createElement("textarea");
        ta.value = summary; document.body.appendChild(ta); ta.select();
        try { document.execCommand("copy"); } catch (__) {}
        ta.remove();
      }
      feedbackBody.querySelector("#fb-copied").textContent = "Copied ✓";
    });
  }
}

function buildFeedbackSummary() {
  const lines = ["Kash — tester feedback", ""];
  let i = 0;
  FEEDBACK_QUESTIONS.forEach((item) => {
    if (item.grp) { lines.push(`· ${item.grp.toUpperCase()}`); return; }
    i++;
    const a = feedbackAnswers[item.id] || "—";
    lines.push(`${i}. ${item.q}\n   → ${a}`);
  });
  return lines.join("\n");
}

function openFeedback() {
  renderFeedbackForm();
  feedbackBackdrop.classList.remove("hidden");
}
function closeFeedback() {
  feedbackBackdrop.classList.add("hidden");
  feedbackBody.innerHTML = "";
}
if (feedbackBtn) feedbackBtn.addEventListener("click", openFeedback);
if (feedbackClose) feedbackClose.addEventListener("click", closeFeedback);
if (feedbackBackdrop) feedbackBackdrop.addEventListener("click", (e) => {
  if (e.target === feedbackBackdrop) closeFeedback();
});
document.addEventListener("keydown", (e) => {
  if (e.key === "Escape" && feedbackBackdrop && !feedbackBackdrop.classList.contains("hidden")) closeFeedback();
});

// ----------------------------------------------------------------------------
// Settings modal — the signed-in account and a change-password form.
// ----------------------------------------------------------------------------
const settingsBtn       = $("settings-btn");
const settingsBackdrop  = $("settings-backdrop");
const settingsClose     = $("settings-close");
const passwordForm      = $("password-form");
const passwordFormEmail = $("password-form-email");
const currentPasswordEl = $("current-password");
const newPasswordEl     = $("new-password");
const confirmPasswordEl = $("confirm-password");
const passwordError     = $("password-error");
const passwordSaveBtn   = $("password-save-btn");
const noPasswordNote    = $("settings-no-password");

const passwordNotice    = $("password-notice");
const passwordHeading   = $("password-heading");
const currentPwGroup    = $("current-password-group");
const recoveryNote      = $("recovery-note");
const settingsForgotBtn = $("settings-forgot-btn");

const PROVIDER_LABELS = { email: "Email and password", google: "Google" };

async function openSettings() {
  const { data: { session } } = await supabase.auth.getSession();
  if (!session) { showToast("Not signed in.", "error"); return; }
  const user = session.user;
  const providers = user.app_metadata?.providers || [user.app_metadata?.provider].filter(Boolean);

  $("settings-name").textContent =
    user.user_metadata?.name || user.user_metadata?.full_name || "—";
  $("settings-email").textContent = user.email || "—";
  $("settings-provider").textContent =
    providers.map((p) => PROVIDER_LABELS[p] || p).join(", ") || "—";
  $("settings-since").textContent = user.created_at
    ? new Date(user.created_at).toLocaleDateString("en-GB", { day: "numeric", month: "long", year: "numeric" })
    : "—";

  // Opened from a reset link: the emailed link already proved who this is, so
  // ask only for the new password. Otherwise Google-only accounts have no Kash
  // password to check or change.
  const hasPassword = providers.includes("email");
  const showForm = recoveryMode || hasPassword;
  passwordForm.classList.toggle("hidden", !showForm);
  noPasswordNote.classList.toggle("hidden", showForm);
  currentPwGroup.classList.toggle("hidden", recoveryMode);
  currentPasswordEl.required = !recoveryMode;
  recoveryNote.classList.toggle("hidden", !recoveryMode);
  passwordHeading.textContent = recoveryMode ? "Set a new password" : "Change password";
  passwordSaveBtn.textContent = recoveryMode ? "Save new password" : "Change password";
  passwordForm.reset();
  passwordFormEmail.value = user.email || "";
  hidePasswordError();
  hidePasswordNotice();

  settingsBackdrop.classList.remove("hidden");
  if (recoveryMode) newPasswordEl.focus();
}

function closeSettings() {
  settingsBackdrop.classList.add("hidden");
  passwordForm.reset();
  hidePasswordError();
  hidePasswordNotice();
  recoveryMode = false;
}

passwordForm.addEventListener("submit", async (e) => {
  e.preventDefault();
  hidePasswordError();
  hidePasswordNotice();
  const next = newPasswordEl.value;
  if (next.length < 8) return showPasswordError("Use at least 8 characters.");
  if (next !== confirmPasswordEl.value) return showPasswordError("The two new passwords don't match.");
  if (recoveryMode) return saveRecoveredPassword(next);
  return changePasswordWithCurrent(passwordFormEmail.value, currentPasswordEl.value, next);
});

// From a reset link: the page's session came from the emailed link, so set
// the new password on it directly.
async function saveRecoveredPassword(next) {
  setPasswordBusy(true, "Saving…");
  try {
    const { error } = await supabase.auth.updateUser({ password: next });
    if (error) return showPasswordError(prettyPasswordError(error.message));
    closeSettings();
    showToast("New password saved. Use it next time you sign in.", "success");
  } catch (err) {
    console.warn("Password reset failed:", err);
    showPasswordError("Couldn't save the password. Check your connection and try again.");
  } finally {
    setPasswordBusy(false);
  }
}

// Checks the current password on a separate, unsaved client, then changes the
// password through that freshly signed-in session (Supabase may refuse a
// password change on an older session). The page's own session is untouched.
async function changePasswordWithCurrent(email, current, next) {
  if (next === current) return showPasswordError("Choose a password different from your current one.");
  setPasswordBusy(true, "Changing…");
  const verifier = createClient(SUPABASE_URL, SUPABASE_ANON, {
    auth: { persistSession: false, autoRefreshToken: false, detectSessionInUrl: false, storageKey: "kash-password-check" },
  });
  try {
    const { error: signInError } = await verifier.auth.signInWithPassword({ email, password: current });
    if (signInError) {
      showPasswordError(/invalid login credentials/i.test(signInError.message)
        ? "Your current password is wrong. Forgot it? Use the link above."
        : prettyAuthError(signInError.message));
      currentPasswordEl.focus();
      return;
    }
    const { error: updateError } = await verifier.auth.updateUser({ password: next });
    if (updateError) return showPasswordError(prettyPasswordError(updateError.message));
    closeSettings();
    showToast("Password changed. Use the new one next time you sign in.", "success");
  } catch (err) {
    console.warn("Password change failed:", err);
    showPasswordError("Couldn't change the password. Check your connection and try again.");
  } finally {
    await verifier.auth.signOut({ scope: "local" }).catch(() => {});
    setPasswordBusy(false);
  }
}

function setPasswordBusy(busy, label) {
  passwordSaveBtn.disabled = busy;
  passwordSaveBtn.textContent = busy ? label : (recoveryMode ? "Save new password" : "Change password");
}

function prettyPasswordError(raw) {
  if (/different from the old/i.test(raw)) return "Choose a password different from your current one.";
  return prettyAuthError(raw);
}

// Emails a reset link that comes back to this page (see recoveryMode).
async function sendPasswordReset(email) {
  return supabase.auth.resetPasswordForEmail(email, {
    redirectTo: window.location.origin + "/",
  });
}

function resetSentMessage(email) {
  return `We sent a reset link to ${email}. Open it in this browser to choose a new password.`;
}

function prettyResetError(raw) {
  if (/rate limit|security purposes/i.test(raw)) return "Too many reset emails. Wait a few minutes and try again.";
  return prettyAuthError(raw);
}

settingsForgotBtn.addEventListener("click", async () => {
  hidePasswordError();
  hidePasswordNotice();
  const email = passwordFormEmail.value;
  if (!email) return showPasswordError("No email on this account.");
  settingsForgotBtn.disabled = true;
  settingsForgotBtn.textContent = "Sending…";
  const { error } = await sendPasswordReset(email).catch((err) => ({ error: err }));
  settingsForgotBtn.disabled = false;
  settingsForgotBtn.textContent = "Forgot your current password?";
  if (error) return showPasswordError(prettyResetError(error.message || ""));
  showPasswordNotice(resetSentMessage(email));
});

loginForgotBtn.addEventListener("click", async () => {
  hideError();
  hideLoginNotice();
  const email = emailInput.value.trim();
  if (!email || !emailInput.checkValidity()) {
    showError("Type your email above, then tap Forgot password.");
    emailInput.focus();
    return;
  }
  loginForgotBtn.disabled = true;
  loginForgotBtn.textContent = "Sending…";
  const { error } = await sendPasswordReset(email).catch((err) => ({ error: err }));
  loginForgotBtn.disabled = false;
  loginForgotBtn.textContent = "Forgot password?";
  if (error) return showError(prettyResetError(error.message || ""));
  // Same message whether or not the address has an account.
  showLoginNotice(`If ${email} has a Kash account, a reset link is on its way. Open it in this browser.`);
});

function showPasswordError(msg) { passwordError.textContent = msg; passwordError.classList.remove("hidden"); }
function hidePasswordError() { passwordError.classList.add("hidden"); }
function showPasswordNotice(msg) { passwordNotice.textContent = msg; passwordNotice.classList.remove("hidden"); }
function hidePasswordNotice() { passwordNotice.classList.add("hidden"); }
function showLoginNotice(msg) { loginNotice.textContent = msg; loginNotice.classList.remove("hidden"); }
function hideLoginNotice() { loginNotice.classList.add("hidden"); }

if (settingsBtn) settingsBtn.addEventListener("click", openSettings);
if (settingsClose) settingsClose.addEventListener("click", closeSettings);
if (settingsBackdrop) settingsBackdrop.addEventListener("click", (e) => {
  if (e.target === settingsBackdrop) closeSettings();
});
document.addEventListener("keydown", (e) => {
  if (e.key === "Escape" && settingsBackdrop && !settingsBackdrop.classList.contains("hidden")) closeSettings();
});

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
  a.download = `kash-${new Date().toISOString().slice(0, 10)}.csv`;
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
    if (!isSpending(tx)) continue;
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
    const priorName = { week: "last week", month: "last month", "last-month": "the month before", "30d": "the 30 days before", "90d": "the 90 days before", year: "last year" }[state.period] || "before";
    const trendLabel = trend == null
      ? "new this period"
      : `${trend >= 0 ? "↑" : "↓"} ${formatPercent(trend)} vs ${priorName}`;
    const isActive = state.activeCatDetail === name ? " active" : "";
    return `
      <div class="cat-card k2${isActive}" data-cat="${escapeHtml(name)}" style="--c: ${color}" role="button" tabindex="0">
        <div class="cat-head">
          <div class="cat-emoji" aria-hidden="true">${emojiFor(name)}</div>
          <div class="cat-title">
            <span class="cat-name">${escapeHtml(name)}</span>
            <span class="cat-count">${data.count} payment${data.count === 1 ? "" : "s"}</span>
          </div>
          <span class="cat-pct">${pct.toFixed(0)}%</span>
        </div>
        <p class="cat-total">${formatETB(data.total)}</p>
        <div class="cat-bar"><span style="width: ${Math.max(2, pct).toFixed(0)}%"></span></div>
        <span class="cat-trend ${trendClass}">${trendLabel}</span>
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
    if (!isSpending(tx)) continue;
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
    case "week":       return { start: weekStart(now.getTime()) - 7 * 864e5, end: weekStart(now.getTime()) - 1 };
    case "custom": {
      if (!state.ovCustom) return null;
      const [a, b] = state.ovCustom;
      return { start: a - (b - a), end: a - 1 };
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
    if (!isSpending(t)) return false;
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
      .filter((t) => isSpending(t) && (t.category || "").trim() === name)
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
    const tip = `${escapeHtml(name)}|${escapeHtml(formatETB(data.total))}|${(slicePct * 100).toFixed(0)}%`;
    return `<path d="M ${x0.toFixed(2)} ${y0.toFixed(2)} A ${r} ${r} 0 ${largeArc} 1 ${x1.toFixed(2)} ${y1.toFixed(2)}"
      fill="none" stroke="${color}" stroke-width="${strokeW}" stroke-linecap="butt"
      data-tip="${tip}" data-tip-color="${color}">
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
  // Calendar-aware bucketing — same EC/Gregorian switch as renderMonthlyChart.
  const buckets = buildMonthBuckets(now, months).map((b) => ({ ...b, totals: new Map() }));

  for (const tx of state.allTxs) {
    if (!isSpending(tx)) continue;
    const cat = (tx.category || "").trim();
    if (!cat) continue;
    const ts = Date.parse(tx.occurred_at);
    if (!Number.isFinite(ts)) continue;
    const idx = bucketIndexForDate(new Date(ts), buckets);
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
      const monthLabel = state.calendar === "ethiopian"
        ? `${formatChartMonth(b.d)} ${gregorianToEthiopian(b.d).year}`
        : b.d.toLocaleDateString(undefined, { month: "short", year: "numeric" });
      const tip = `${escapeHtml(c)}|${escapeHtml(formatETB(v))}|${escapeHtml(monthLabel)}`;
      return `<rect class="chart-bar" x="${x.toFixed(2)}" y="${yCursor.toFixed(2)}" width="${barW.toFixed(2)}" height="${h.toFixed(2)}" fill="${color}"
        data-tip="${tip}" data-tip-color="${color}">
        <title>${escapeHtml(monthLabel)} · ${escapeHtml(c)}: ${formatETB(v)}</title>
      </rect>`;
    }).join("");
  }).join("");

  const labels = buckets.map((b, gi) => {
    const x = pad.l + gi * (barW + gap) + barW / 2;
    return `<text x="${x.toFixed(2)}" y="${(H - 6).toFixed(2)}" class="chart-axis-label" text-anchor="middle">${escapeHtml(formatChartMonth(b.d))}</text>`;
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
  if (state.calendar === "ethiopian") return formatEthiopianDate(d, { withTime: true });
  return d.toLocaleString(undefined, {
    day: "numeric", month: "short", year: "numeric",
    hour: "2-digit", minute: "2-digit"
  });
}

// ----------------------------------------------------------------------------
// Ethiopian calendar (Ge'ez calendar) support
//
// The Ethiopian year has 13 months: 12 of 30 days plus Pagume (5 or 6 days).
// New Year falls on Sept 11 Gregorian (Sept 12 in years preceding leap years).
// Today (2 Jun 2026 Gregorian) ≈ 25 Ginbot 2018 EC.
//
// Conversion goes Gregorian → Julian Day Number → Ethiopian. The JDN of
// 1 Meskerem 1 EC (Amete Alem epoch) is 1723856.
// ----------------------------------------------------------------------------
// Calendar-aware month buckets used by the 12-month charts. Returns the most
// recent `count` months in chronological order. Each bucket carries `d` (the
// Gregorian Date of the 1st of that month) plus identity fields the index
// lookup uses. The caller adds metric fields (income/expense/totals/etc).
function buildMonthBuckets(now, count) {
  if (state.calendar === "ethiopian") {
    const today = gregorianToEthiopian(now);
    return Array.from({ length: count }, (_, i) => {
      // Walk back (count - 1 - i) EC months from current EC year/month.
      let m = today.month - (count - 1 - i);
      let y = today.year;
      while (m < 1) { m += 13; y -= 1; }
      return { d: ethiopianToGregorian(y, m, 1), ecYear: y, ecMonth: m };
    });
  }
  return Array.from({ length: count }, (_, i) => {
    const d = new Date(now.getFullYear(), now.getMonth() - (count - 1 - i), 1);
    return { d, gYear: d.getFullYear(), gMonth: d.getMonth() };
  });
}

// Given a transaction's date and the bucket array, return the index it
// belongs to, or -1 if outside the chart window.
function bucketIndexForDate(date, buckets) {
  if (state.calendar === "ethiopian") {
    const e = gregorianToEthiopian(date);
    return buckets.findIndex((b) => b.ecYear === e.year && b.ecMonth === e.month);
  }
  return buckets.findIndex((b) => b.gYear === date.getFullYear() && b.gMonth === date.getMonth());
}

// "25 Ginbot 2018" or with time "25 Ginbot 2018 · 14:32".
function formatEthiopianDate(date, { withTime = false, includeYear = true, monthOnly = false } = {}) {
  const e = gregorianToEthiopian(date);
  const monthName = ETHIOPIAN_MONTHS[e.month - 1] || `M${e.month}`;
  if (monthOnly) return monthName;
  const base = `${e.day} ${monthName}${includeYear ? ` ${e.year}` : ""}`;
  if (!withTime) return base;
  const time = date.toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit" });
  return `${base} · ${time}`;
}

// Calendar-aware month label for charts (e.g. "May" or "Ginbot").
function formatChartMonth(date) {
  if (state.calendar === "ethiopian") {
    return ETHIOPIAN_MONTHS[gregorianToEthiopian(date).month - 1];
  }
  return date.toLocaleDateString(undefined, { month: "short" });
}

function setCalendar(calId) {
  if (calId !== "ethiopian") calId = "gregorian";
  state.calendar = calId;
  try { localStorage.setItem("financeLore.calendar", calId); } catch (_) {}
  // Visually mark the active button.
  if (calendarToggle) {
    calendarToggle.querySelectorAll(".calendar-toggle-btn").forEach((b) => {
      b.classList.toggle("active", b.dataset.cal === calId);
    });
  }
  // Re-render everything that displays dates. rerender() repaints the
  // dashboard (charts, tx table, etc.); the secondary tabs each have their
  // own render fn we call directly when present.
  if (state.allTxs && state.allTxs.length) rerender();
  if (typeof renderLedgerView   === "function" && !ledgerView.classList.contains("hidden"))   renderLedgerView();
  if (typeof renderIncomeView   === "function" && !incomeView.classList.contains("hidden"))   renderIncomeView();
  if (typeof renderGoalsView    === "function" && !goalsView.classList.contains("hidden"))    renderGoalsView();
  if (typeof renderBudgetsView  === "function" && !budgetsView.classList.contains("hidden")) renderBudgetsView();
}

if (calendarToggle) {
  calendarToggle.addEventListener("click", (e) => {
    const btn = e.target.closest(".calendar-toggle-btn");
    if (!btn) return;
    setCalendar(btn.dataset.cal);
  });
}

function formatRelativeDate(ts) {
  if (!ts) return "—";
  const days = Math.floor((Date.now() - ts) / 864e5);
  if (days <= 0) return "today";
  if (days === 1) return "yesterday";
  if (days < 7) return `${days} days ago`;
  if (days < 31) return `${Math.floor(days / 7)} week${Math.floor(days / 7) === 1 ? "" : "s"} ago`;
  const d = new Date(ts);
  if (state.calendar === "ethiopian") return formatEthiopianDate(d, { includeYear: false });
  return d.toLocaleDateString(undefined, { day: "numeric", month: "short" });
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

// =============================================================================
// Budgets — envelope-style funded budgets that auto-refund on a cadence and
// prompt the user to send unspent leftover to a savings goal.
//
// Data model (see supabase/add_budgets.sql):
//   i_budgets             — one row per envelope
//   i_budget_categories   — many-to-many: budget ↔ category
//
// Cycle math lives entirely client-side. `funding_date` is the original
// anchor; we step forward in `cadence` units to find the cycle that
// contains today. The "previous cycle leftover" prompt is gated by
// `last_cycle_handled_at`: once the user dismisses or saves a leftover,
// we bump that column to the cycle_end and don't ask again.
// =============================================================================

const BUDGET_CATEGORIES = [
  "Food", "Coffee and refreshments", "Transport", "Bills", "Shopping", "Health",
  "Drinks and fun", "Entertainment", "Rent", "Fees", "Loan",
  "Transfer", "Income", "Other",
];

const BUDGET_EXPENSE_TYPES = new Set(["DEBIT", "TRANSFER_OUT", "PAYMENT"]);

// ─── Fetch + state hydration ───────────────────────────────────────────────
async function fetchBudgets() {
  if (!state.userId) return;
  const [bRes, cRes] = await Promise.all([
    supabase.from("i_budgets")
      .select("id, name, emoji, fund_amount, cadence, interval_days, funding_date, last_cycle_handled_at, status, created_at, updated_at")
      .eq("user_id", state.userId)
      .order("created_at", { ascending: false }),
    supabase.from("i_budget_categories")
      .select("budget_id, category")
      .eq("user_id", state.userId),
  ]);
  if (bRes.error) {
    console.warn("Budgets fetch error:", bRes.error);
    state.budgets = [];
  } else {
    state.budgets = bRes.data || [];
  }
  if (cRes.error) {
    console.warn("Budget categories fetch error:", cRes.error);
    state.budgetCategories = [];
  } else {
    state.budgetCategories = cRes.data || [];
  }
  renderBudgetsBadge();
  // If the user is already on the Budgets tab, refresh now.
  if (state.view === "budgets") renderBudgetsView();
}

function categoriesForBudget(budgetId) {
  return state.budgetCategories
    .filter((r) => r.budget_id === budgetId)
    .map((r) => r.category);
}

// ─── Cycle math ─────────────────────────────────────────────────────────────
/** Step forward one cycle from [date] given a cadence. Returns Date. */
function advanceCycleStart(date, cadence, intervalDays) {
  const d = new Date(date);
  switch ((cadence || "MONTHLY").toUpperCase()) {
    case "WEEKLY":        d.setDate(d.getDate() + 7); break;
    case "BIWEEKLY":      d.setDate(d.getDate() + 14); break;
    case "EVERY_30_DAYS": d.setDate(d.getDate() + 30); break;
    case "QUARTERLY":     d.setMonth(d.getMonth() + 3); break;
    case "YEARLY":        d.setFullYear(d.getFullYear() + 1); break;
    case "CUSTOM": {
      const n = Number(intervalDays);
      d.setDate(d.getDate() + (Number.isFinite(n) && n > 0 ? n : 30));
      break;
    }
    case "MONTHLY":
    default:              d.setMonth(d.getMonth() + 1); break;
  }
  return d;
}

/** Returns { prevStart, prevEnd, curStart, curEnd } around today. */
function cycleBounds(budget, today = new Date()) {
  const anchor = new Date(budget.funding_date);
  // Walk forward until cycle_end exceeds today.
  let curStart = new Date(anchor);
  let curEnd = advanceCycleStart(curStart, budget.cadence, budget.interval_days);
  while (curEnd <= today) {
    curStart = curEnd;
    curEnd = advanceCycleStart(curStart, budget.cadence, budget.interval_days);
  }
  // Previous cycle ended at curStart.
  const prevEnd = new Date(curStart);
  // Step back one cycle from prevEnd to find prevStart. We approximate by
  // counting days between anchor and curStart and stepping anchor forward
  // again — safer than reversing month math.
  let prevStart = new Date(anchor);
  let probe = advanceCycleStart(prevStart, budget.cadence, budget.interval_days);
  while (probe < prevEnd) {
    prevStart = probe;
    probe = advanceCycleStart(prevStart, budget.cadence, budget.interval_days);
  }
  return { prevStart, prevEnd, curStart, curEnd };
}

/** Sum of expense amounts whose category is in [cats] AND occurred in [start, end). */
function spentInCycle(cats, start, end) {
  if (!cats.length) return 0;
  const startMs = start.getTime();
  const endMs   = end.getTime();
  const set = new Set(cats.map((c) => c.toLowerCase()));
  let sum = 0;
  for (const tx of state.allTxs) {
    if (!isSpending(tx)) continue;
    const c = (tx.category || "").toLowerCase();
    if (!c || !set.has(c)) continue;
    const ts = Date.parse(tx.occurred_at);
    if (!Number.isFinite(ts) || ts < startMs || ts >= endMs) continue;
    sum += Number(tx.amount) || 0;
  }
  return sum;
}

/** Composite snapshot used by every budget render path. */
function computeBudgetSnapshot(budget, today = new Date()) {
  const cats = categoriesForBudget(budget.id);
  const { prevStart, prevEnd, curStart, curEnd } = cycleBounds(budget, today);
  const fundAmount = Number(budget.fund_amount) || 0;
  const spentCurrent = spentInCycle(cats, curStart, curEnd);
  const remaining    = fundAmount - spentCurrent;
  const spentPrev    = spentInCycle(cats, prevStart, prevEnd);
  const leftoverPrev = fundAmount - spentPrev;
  const totalCycleMs = curEnd.getTime() - curStart.getTime();
  const elapsedMs    = Math.max(0, today.getTime() - curStart.getTime());
  const daysLeft     = Math.max(0, Math.ceil((curEnd.getTime() - today.getTime()) / 86_400_000));
  const dailyPace    = elapsedMs > 0 ? spentCurrent / (elapsedMs / 86_400_000) : 0;
  // The leftover prompt fires only when:
  //   1. There's a previous cycle (curStart > anchor)
  //   2. There was money left over (positive)
  //   3. The user hasn't already handled this cycle
  const handledIso = budget.last_cycle_handled_at || "";
  const prevEndIso = prevEnd.toISOString().slice(0, 10);
  const anchorDate = new Date(budget.funding_date);
  const hasPrevCycle = curStart.getTime() > anchorDate.getTime();
  const leftoverPending = hasPrevCycle && leftoverPrev > 0 && prevEndIso > handledIso;
  return {
    cats, fundAmount, spentCurrent, remaining,
    prevStart, prevEnd, curStart, curEnd,
    leftoverPrev, leftoverPending, daysLeft, dailyPace,
  };
}

// ─── Badge + hero stats ────────────────────────────────────────────────────
function renderBudgetsBadge() {
  if (!budgetsBadge) return;
  const active = state.budgets.filter((b) => b.status === "ACTIVE");
  const overCount = active.reduce((n, b) => {
    const s = computeBudgetSnapshot(b);
    return n + (s.remaining < 0 ? 1 : 0);
  }, 0);
  const pendingLeftovers = active.reduce((n, b) => {
    const s = computeBudgetSnapshot(b);
    return n + (s.leftoverPending ? 1 : 0);
  }, 0);
  const badgeCount = overCount + pendingLeftovers;
  budgetsBadge.classList.toggle("hidden", badgeCount === 0);
  budgetsBadge.textContent = String(badgeCount);
}

function renderBudgetsHero() {
  const active = state.budgets.filter((b) => b.status === "ACTIVE");
  let totalFunded = 0, totalSpent = 0;
  for (const b of active) {
    const s = computeBudgetSnapshot(b);
    totalFunded += s.fundAmount;
    totalSpent  += s.spentCurrent;
  }
  const remaining = totalFunded - totalSpent;
  budgetsTotalFunded.textContent     = formatETB(totalFunded);
  budgetsTotalFundedSub.textContent  = `${active.length} active envelope${active.length === 1 ? "" : "s"}`;
  budgetsTotalSpent.textContent      = formatETB(totalSpent);
  budgetsTotalSpentSub.textContent   = totalFunded > 0
    ? `${Math.round((totalSpent / totalFunded) * 100)}% of funded`
    : "—";
  budgetsTotalRemaining.textContent  = formatETB(remaining);
  budgetsTotalRemainingSub.textContent = remaining < 0
    ? "over budget"
    : "left in active cycles";
}

// ─── Form (create / edit) ──────────────────────────────────────────────────
function renderBudgetCategoryPicker() {
  const selected = state.budgetPickedCategories;
  budgetCategoryPicker.innerHTML = BUDGET_CATEGORIES
    .map((c) => {
      const on = selected.has(c);
      return `<button type="button" class="category-pill ${on ? "active" : ""}" data-cat="${escapeHtml(c)}">${escapeHtml(c)}</button>`;
    })
    .join("");
  budgetCategoryPicker.querySelectorAll(".category-pill").forEach((btn) => {
    btn.addEventListener("click", () => {
      const c = btn.dataset.cat;
      if (selected.has(c)) selected.delete(c);
      else selected.add(c);
      renderBudgetCategoryPicker();
    });
  });
  budgetCategoryHint.textContent = selected.size > 0
    ? `${selected.size} category${selected.size === 1 ? "" : "ies"} selected`
    : "Pick at least one. Expenses in these categories will deduct from the envelope.";
}

function resetBudgetForm() {
  state.editingBudgetId = null;
  state.budgetPickedCategories = new Set();
  budgetFormId.value = "";
  budgetFormTitle.textContent = "Create a budget";
  budgetSubmit.textContent = "Create budget";
  budgetCancel.classList.add("hidden");
  budgetNameInput.value = "";
  budgetEmojiInput.value = "";
  budgetAmountInput.value = "";
  budgetCadenceSelect.value = "MONTHLY";
  budgetIntervalInput.value = "";
  budgetIntervalWrapper.classList.add("hidden");
  budgetFundingDateInput.value = new Date().toISOString().slice(0, 10);
  renderBudgetCategoryPicker();
}

function loadBudgetIntoForm(b) {
  state.editingBudgetId = b.id;
  state.budgetPickedCategories = new Set(categoriesForBudget(b.id));
  budgetFormId.value = b.id;
  budgetFormTitle.textContent = `Edit ${b.name}`;
  budgetSubmit.textContent = "Save changes";
  budgetCancel.classList.remove("hidden");
  budgetNameInput.value = b.name;
  budgetEmojiInput.value = b.emoji || "";
  budgetAmountInput.value = b.fund_amount;
  budgetCadenceSelect.value = b.cadence || "MONTHLY";
  budgetIntervalInput.value = b.interval_days || "";
  budgetIntervalWrapper.classList.toggle("hidden", b.cadence !== "CUSTOM");
  budgetFundingDateInput.value = b.funding_date;
  renderBudgetCategoryPicker();
  budgetNameInput.focus();
  window.scrollTo({ top: budgetForm.getBoundingClientRect().top + window.scrollY - 80, behavior: "smooth" });
}

if (budgetCadenceSelect) {
  budgetCadenceSelect.addEventListener("change", () => {
    budgetIntervalWrapper.classList.toggle("hidden", budgetCadenceSelect.value !== "CUSTOM");
  });
}
if (budgetCancel) {
  budgetCancel.addEventListener("click", () => resetBudgetForm());
}
if (budgetForm) {
  budgetForm.addEventListener("submit", async (e) => {
    e.preventDefault();
    const name = budgetNameInput.value.trim();
    const emoji = budgetEmojiInput.value.trim() || null;
    const fundAmount = Number(budgetAmountInput.value);
    const cadence = budgetCadenceSelect.value;
    const intervalDays = cadence === "CUSTOM" ? Number(budgetIntervalInput.value) || null : null;
    const fundingDate = budgetFundingDateInput.value;
    const cats = [...state.budgetPickedCategories];
    if (!name || !Number.isFinite(fundAmount) || fundAmount <= 0 || !fundingDate || cats.length === 0) {
      showToast("Fill in name, amount, funding date, and pick at least one category.", "error");
      return;
    }
    const id = state.editingBudgetId;
    try {
      if (id) {
        const { error } = await supabase.from("i_budgets")
          .update({
            name, emoji, fund_amount: fundAmount, cadence, interval_days: intervalDays,
            funding_date: fundingDate,
          })
          .eq("id", id);
        if (error) throw error;
        // Replace category set: delete old, insert new.
        await supabase.from("i_budget_categories").delete().eq("budget_id", id);
        if (cats.length > 0) {
          await supabase.from("i_budget_categories").insert(
            cats.map((c) => ({ budget_id: id, user_id: state.userId, category: c }))
          );
        }
        showToast(`${name} updated`, "success");
      } else {
        const { data, error } = await supabase.from("i_budgets")
          .insert({
            user_id: state.userId, name, emoji,
            fund_amount: fundAmount, cadence, interval_days: intervalDays,
            funding_date: fundingDate,
          })
          .select("id").single();
        if (error) throw error;
        const newId = data.id;
        await supabase.from("i_budget_categories").insert(
          cats.map((c) => ({ budget_id: newId, user_id: state.userId, category: c }))
        );
        showToast(`${name} created`, "success");
      }
    } catch (err) {
      showToast(`Save failed: ${err.message}`, "error");
      return;
    }
    resetBudgetForm();
    await fetchBudgets();
  });
}

async function deleteBudget(id) {
  const b = state.budgets.find((x) => x.id === id);
  if (!b) return;
  if (!confirm(`Delete ${b.name}? This won't remove transactions.`)) return;
  const { error } = await supabase.from("i_budgets").delete().eq("id", id);
  if (error) { showToast(`Delete failed: ${error.message}`, "error"); return; }
  showToast(`${b.name} deleted`, "success");
  await fetchBudgets();
}

// ─── Leftover-prompt handlers ──────────────────────────────────────────────
async function handleLeftoverSkip(budget) {
  const snap = computeBudgetSnapshot(budget);
  const prevEndIso = snap.prevEnd.toISOString().slice(0, 10);
  const { error } = await supabase.from("i_budgets")
    .update({ last_cycle_handled_at: prevEndIso })
    .eq("id", budget.id);
  if (error) { showToast(`Couldn't dismiss: ${error.message}`, "error"); return; }
  showToast(`Leftover dismissed`, "success");
  await fetchBudgets();
}

async function handleLeftoverToGoal(budget, goalId) {
  const snap = computeBudgetSnapshot(budget);
  const prevEndIso = snap.prevEnd.toISOString().slice(0, 10);
  const amount = Math.max(0, Number(snap.leftoverPrev.toFixed(2)));
  if (!goalId || amount <= 0) { showToast("Pick a goal first.", "error"); return; }
  // 1. Insert a contribution
  const contribRes = await supabase.from("i_goal_contributions").insert({
    user_id: state.userId, goal_id: goalId, amount,
    note: `Leftover from ${budget.name} (cycle ${snap.prevStart.toISOString().slice(0,10)}—${prevEndIso})`,
  });
  if (contribRes.error) { showToast(`Couldn't save: ${contribRes.error.message}`, "error"); return; }
  // 2. Mark this cycle handled
  const budgetRes = await supabase.from("i_budgets")
    .update({ last_cycle_handled_at: prevEndIso }).eq("id", budget.id);
  if (budgetRes.error) { showToast(`Saved, but couldn't mark cycle handled: ${budgetRes.error.message}`, "error"); }
  showToast(`Saved ${formatETB(amount)} to your goal`, "success");
  // Refresh both budgets and goals.
  await Promise.all([fetchBudgets(), fetchGoals()]);
}

// ─── Render ────────────────────────────────────────────────────────────────
function renderBudgetsView() {
  renderBudgetsHero();
  // Leftover prompts at top
  const active = state.budgets.filter((b) => b.status === "ACTIVE");
  const pending = active
    .map((b) => ({ b, s: computeBudgetSnapshot(b) }))
    .filter((p) => p.s.leftoverPending);
  if (pending.length === 0) {
    budgetLeftoverSection.classList.add("hidden");
  } else {
    budgetLeftoverSection.classList.remove("hidden");
    budgetLeftoverList.innerHTML = pending.map((p) => renderLeftoverRow(p.b, p.s)).join("");
    // Wire each row's actions
    budgetLeftoverList.querySelectorAll("[data-leftover-action]").forEach((el) => {
      const id = el.dataset.budgetId;
      const b = state.budgets.find((x) => x.id === id);
      if (!b) return;
      if (el.dataset.leftoverAction === "skip") {
        el.addEventListener("click", () => handleLeftoverSkip(b));
      } else if (el.dataset.leftoverAction === "save") {
        el.addEventListener("click", () => {
          const select = budgetLeftoverList.querySelector(`[data-leftover-goal-for="${id}"]`);
          const goalId = select?.value;
          if (!goalId) { showToast("Pick a goal first.", "error"); return; }
          handleLeftoverToGoal(b, goalId);
        });
      }
    });
  }
  // Active budget cards
  if (active.length === 0) {
    budgetEmpty.classList.remove("hidden");
    budgetCardGrid.classList.add("hidden");
    budgetCardGrid.innerHTML = "";
  } else {
    budgetEmpty.classList.add("hidden");
    budgetCardGrid.classList.remove("hidden");
    budgetCardGrid.innerHTML = active.map((b) => renderBudgetCard(b)).join("");
    budgetCardGrid.querySelectorAll("[data-budget-edit]").forEach((el) => {
      el.addEventListener("click", () => {
        const b = state.budgets.find((x) => x.id === el.dataset.budgetEdit);
        if (b) loadBudgetIntoForm(b);
      });
    });
    budgetCardGrid.querySelectorAll("[data-budget-delete]").forEach((el) => {
      el.addEventListener("click", () => deleteBudget(el.dataset.budgetDelete));
    });
  }
  budgetsCountLabel.textContent = `${active.length} active`;
  if (!state.editingBudgetId) resetBudgetForm();
}

function renderLeftoverRow(budget, snap) {
  const amountText = formatETB(snap.leftoverPrev);
  const goals = (state.goals || []).filter((g) => g.status === "ACTIVE");
  const goalOptions = goals.length > 0
    ? goals.map((g) => `<option value="${escapeHtml(g.id)}">${escapeHtml(`${g.emoji || ""} ${g.name}`.trim())}</option>`).join("")
    : `<option value="" disabled>No active goals yet</option>`;
  return `
    <div class="budget-leftover-row">
      <div class="leftover-text">
        <strong>${escapeHtml(budget.name)}</strong> ended its cycle with
        <strong>${amountText}</strong> unspent.
        <div class="muted small">${snap.prevStart.toISOString().slice(0,10)} → ${snap.prevEnd.toISOString().slice(0,10)}</div>
      </div>
      <div class="leftover-actions">
        <select data-leftover-goal-for="${escapeHtml(budget.id)}">${goalOptions}</select>
        <button type="button" data-leftover-action="save" data-budget-id="${escapeHtml(budget.id)}" class="primary" ${goals.length === 0 ? "disabled" : ""}>Send</button>
        <button type="button" data-leftover-action="skip" data-budget-id="${escapeHtml(budget.id)}">Skip</button>
      </div>
    </div>
  `;
}

function renderBudgetCard(budget) {
  const snap = computeBudgetSnapshot(budget);
  const pct = snap.fundAmount > 0 ? Math.min(100, (snap.spentCurrent / snap.fundAmount) * 100) : 0;
  const overshoot = snap.remaining < 0;
  const klass = overshoot ? "budget-over" : pct >= 75 ? "budget-warn" : "";
  const remainingText = overshoot
    ? `Over by ${formatETB(Math.abs(snap.remaining))}`
    : `${formatETB(snap.remaining)} left`;
  const dailyHint = snap.dailyPace > 0
    ? `${formatETB(snap.dailyPace)}/day pace`
    : `not started this cycle`;
  const refundIn = snap.daysLeft === 0
    ? `refunds today`
    : `${snap.daysLeft} day${snap.daysLeft === 1 ? "" : "s"} until refunding`;
  const cats = snap.cats.map((c) => `<span class="budget-category-chip">${escapeHtml(c)}</span>`).join("");
  return `
    <div class="budget-card ${klass}">
      <div class="budget-card-header">
        <div class="budget-card-title">
          <span class="budget-emoji">${escapeHtml(budget.emoji || "💰")}</span>
          <span class="budget-name">${escapeHtml(budget.name)}</span>
        </div>
        <span class="budget-cadence-tag">${escapeHtml(cadenceLabel(budget.cadence, budget.interval_days))}</span>
      </div>
      <div class="budget-remaining">${remainingText}</div>
      <div class="budget-remaining-sub">of ${formatETB(snap.fundAmount)} funded · ${formatETB(snap.spentCurrent)} spent</div>
      <div class="budget-progress-track"><div class="budget-progress-fill" style="width:${pct.toFixed(1)}%"></div></div>
      <div class="budget-meta">
        <span>${refundIn}</span>
        <span>${dailyHint}</span>
      </div>
      <div class="budget-categories">${cats}</div>
      <div class="budget-card-actions">
        <button type="button" data-budget-edit="${escapeHtml(budget.id)}">Edit</button>
        <button type="button" data-budget-delete="${escapeHtml(budget.id)}">Delete</button>
      </div>
    </div>
  `;
}

// Re-render the badge whenever transactions change (since spend → remaining).
function rerenderBudgets() {
  renderBudgetsBadge();
  if (state.view === "budgets") renderBudgetsView();
}

