// PostHog page analytics (EU cloud) for the public pages and the web dashboard.
// Not loaded on /admin/. See docs/DECISIONS.md (2026-11-09).
//
// What it sends: page views, page leaves and "apk_download_clicked". No
// autocapture, no session replay, no heatmaps, no surveys, no feature flags,
// and no person profiles (visitors stay anonymous). Every URL is cut to its
// path, because password-reset and sign-in links carry tokens in the query
// or #hash. Nothing from the dashboard (amounts, names, accounts) is sent.
//
// POSTHOG_KEY is the project's public "phc_..." key (write-only, like the
// Supabase anon key in app.js). Empty = analytics off and nothing loads.
(function () {
  var POSTHOG_KEY = "";
  var POSTHOG_HOST = "https://eu.i.posthog.com";
  var LIB = "https://cdn.jsdelivr.net/npm/posthog-js@1.438.21/dist/array.no-external.js";
  var LIB_SRI = "sha384-jVwiCZzKpfd2ToNCud6WsPEZ2vt4k1gma4T7j0kj5L5u5+5bCRPVYxiQhu2JXA+o";
  if (!POSTHOG_KEY) return;

  var URL_PROPS = ["$current_url", "$referrer", "$initial_referrer",
                   "$initial_current_url", "$prev_pageview_pathname"];
  function bare(u) {
    if (typeof u !== "string") return u;
    return u.split("#")[0].split("?")[0];
  }
  function scrub(event) {
    if (!event) return event;
    [event.properties, event.$set, event.$set_once].forEach(function (p) {
      if (!p) return;
      URL_PROPS.forEach(function (k) { if (k in p) p[k] = bare(p[k]); });
    });
    return event;
  }

  var s = document.createElement("script");
  s.src = LIB;
  s.integrity = LIB_SRI;
  s.crossOrigin = "anonymous";
  s.async = true;
  s.onload = function () {
    if (!window.posthog || !window.posthog.init) return;
    window.posthog.init(POSTHOG_KEY, {
      api_host: POSTHOG_HOST,
      person_profiles: "identified_only",
      persistence: "localStorage",
      autocapture: false,
      capture_pageview: true,
      capture_pageleave: true,
      capture_heatmaps: false,
      capture_dead_clicks: false,
      capture_exceptions: false,
      disable_session_recording: true,
      disable_surveys: true,
      disable_web_experiments: true,
      advanced_disable_feature_flags: true,
      before_send: scrub
    });
  };
  document.head.appendChild(s);

  document.addEventListener("click", function (e) {
    var a = e.target.closest && e.target.closest('a[href$=".apk"]');
    if (!a || !window.posthog || !window.posthog.capture) return;
    var m = a.getAttribute("href").match(/Kash-([\w.-]+)\.apk$/);
    window.posthog.capture("apk_download_clicked", { app_version: m ? m[1] : null });
  });
})();
