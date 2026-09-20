/**
 * app.js - UI layer for the REST vs GraphQL comparison demo.
 * Depends on engine.js (window.RestVsGraphqlEngine).
 * Defaults to the production API; local demos can use ?api=http://localhost:8080.
 */
(function () {
  'use strict';

  var params = new URLSearchParams(window.location.search);
  var apiOverride = params.get('api');
  var localSpringHost = /^(localhost|127\.0\.0\.1):8080$/.test(window.location.host);
  var API = apiOverride || (localSpringHost ? window.location.origin : 'https://numfeel-api.996.ninja');
  if (API.endsWith('/')) API = API.slice(0, -1);
  var REST_FULL = API + '/api/rest-vs-graphql/catalog/full?limit=5';
  var REST_LIGHT = API + '/api/rest-vs-graphql/catalog/light?limit=5';
  var GQL_ENDPOINT = API + '/graphql';

  var eng = window.RestVsGraphqlEngine;

  var els = {};

  function $(id) { return document.getElementById(id); }

  function cacheDom() {
    els.fieldPicker = $('field-picker');
    els.ckDescription = $('ck-description');
    els.phoneList = $('phone-list');
    els.vsRestJson = $('vs-rest-json');
    els.vsLightJson = $('vs-light-json');
    els.vsGqlJson = $('vs-gql-json');
    els.vsRestSize = $('vs-rest-size');
    els.vsLightSize = $('vs-light-size');
    els.vsGqlSize = $('vs-gql-size');
    els.vsRestWaste = $('vs-rest-waste');
    els.vsLightWaste = $('vs-light-waste');
    els.vsGqlWaste = $('vs-gql-waste');
    els.exp1Insight = $('exp1-insight');
    els.btnBench = $('btn-bench');
    els.benchStatus = $('bench-status');
    els.bFirstRest = $('b-first-rest');
    els.bFirstLight = $('b-first-light');
    els.bFirstGql = $('b-first-gql');
    els.bAvgRest = $('b-avg-rest');
    els.bAvgLight = $('b-avg-light');
    els.bAvgGql = $('b-avg-gql');
    els.bTotalRest = $('b-total-rest');
    els.bTotalLight = $('b-total-light');
    els.bTotalGql = $('b-total-gql');
    els.exp2Insight = $('exp2-insight');
    els.gqlLimit = $('gql-limit');
    els.gqlLimitVal = $('gql-limit-val');
    els.gqlAuthor = $('gql-author');
    els.gqlReviews = $('gql-reviews');
    els.btnGqlRun = $('btn-gql-run');
    els.costDbCalls = $('cost-dbcalls');
    els.costRows = $('cost-rows');
    els.costMs = $('cost-ms');
    els.predText = $('pred-text');
    els.exp3Insight = $('exp3-insight');
  }

  // Common requests

  function timedFetch(url, options) {
    var start = performance.now();
    return fetch(url, options)
      .then(function (resp) {
        var elapsed = Math.round((performance.now() - start) * 10) / 10;
        return resp.json().then(function (json) {
          return { json: json, ms: elapsed, status: resp.status };
        });
      });
  }

  function apiOk(r) {
    return r && r.json && r.json.status === 200;
  }

  function escapeHtml(s) {
    return String(s).replace(/[&<>"']/g, function (ch) {
      return ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[ch];
    });
  }

  function apiErrorText(error) {
    var api = API || '(same origin)';
    var msg = error && error.message ? error.message : String(error || 'Unknown error');
    return 'API request failed: ' + msg + ' · current API = ' + api +
      '. If this is a local demo, confirm numfeel-service is running on 8080 and MySQL bookstore data has initialized.';
  }

  function renderPanelError(message) {
    var html = '<span class="missing">' + escapeHtml(message) + '</span>';
    els.vsRestJson.innerHTML = html;
    els.vsLightJson.innerHTML = html;
    els.vsGqlJson.innerHTML = html;
    els.exp1Insight.innerHTML = '<b>Backend check:</b> ' + escapeHtml(message);
  }

  // Byte calculations

  function byteLen(s) {
    if (typeof Blob !== 'undefined') return new Blob([s]).size;
    if (typeof Buffer !== 'undefined') return Buffer.byteLength(s, 'utf8');
    return unescape(encodeURIComponent(s)).length;
  }

  function fmtBytes(n) {
    if (n < 1024) return n + ' B';
    if (n < 1048576) return (n / 1024).toFixed(1) + ' KB';
    return (n / 1048576).toFixed(2) + ' MB';
  }

  // Experiment 1: fetching trade-off

  function gqlQuery(withDescription) {
    var fields = 'id title author { name } rating price';
    if (withDescription) fields += ' description';
    return '{ books(limit: 5) { books { ' + fields + ' } meta { dbCalls rowsLoaded elapsedMs } } }';
  }

  function renderPhone(items, withDescription) {
    if (!items || !items.length) { els.phoneList.innerHTML = 'No data yet'; return; }
    var html = items.slice(0, 5).map(function (b) {
      var authorName = (b.author && typeof b.author === 'object') ? b.author.name : b.author;
      var desc = withDescription && b.description
        ? '<div class="phone-desc">' + b.description + '</div>' : '';
      return '<div class="phone-item">' +
        '<span class="phone-title">' + b.title + '</span>' +
        '<span class="phone-author">' + (authorName || '') + '</span>' +
        '<span class="phone-rating">Rating ' + b.rating + '</span>' +
        '<span class="phone-price">$' + b.price + '</span>' + desc +
        '</div>';
    }).join('');
    els.phoneList.innerHTML = html;
  }

  /**
   * Render response JSON one field per line with used/extra highlighting.
   * Missing fields are shown as an explicit warning line.
   */
  function renderVsJson(el, items, coreKeys) {
    if (!items || !items.length) { el.textContent = '(empty)'; return; }
    var first = items[0];
    var keys = Object.keys(first);
    var parts = [];
    keys.forEach(function (k, i) {
      var used = coreKeys.indexOf(k) >= 0;
      var cls = used ? 'used' : 'waste';
      var raw = JSON.stringify(first[k]);
      if (raw && raw.length > 80) raw = raw.slice(0, 80) + '…';
      var comma = i < keys.length - 1 ? ',' : '';
      parts.push('  <span class="' + cls + '">"' + k + '": ' + raw + comma + '</span>');
    });
    var missing = coreKeys.filter(function (k) { return keys.indexOf(k) < 0; });
    if (missing.length) {
      parts.push('  <span class="missing">... missing ' + missing.join(', ') + '</span>');
    }
    el.innerHTML = '{\n' + parts.join('\n') + '\n}';
  }

  function runExp1() {
    var withDesc = els.ckDescription.checked;
    var coreKeys = eng.REST_CORE_FIELDS.concat(withDesc ? ['description'] : []);

    els.vsRestJson.textContent = 'Requesting...';
    els.vsLightJson.textContent = 'Requesting...';
    els.vsGqlJson.textContent = 'Requesting...';

    return Promise.all([
      timedFetch(REST_FULL).then(function (r) {
        var items = apiOk(r) ? r.json.data.items : [];
        renderVsJson(els.vsRestJson, items, coreKeys);
        els.vsRestSize.textContent = fmtBytes(byteLen(JSON.stringify(r.json)));
        els.vsRestWaste.textContent = apiOk(r) && items.length ? fmtBytes(byteLen(JSON.stringify(items))) : '-';
        renderPhone(items, withDesc);
        return r;
      }),
      timedFetch(REST_LIGHT).then(function (r) {
        var items = apiOk(r) ? r.json.data.items : [];
        renderVsJson(els.vsLightJson, items, coreKeys);
        els.vsLightSize.textContent = fmtBytes(byteLen(JSON.stringify(r.json)));
        els.vsLightWaste.textContent = apiOk(r) && items.length ? fmtBytes(byteLen(JSON.stringify(items))) : '-';
        return r;
      }),
      timedFetch(GQL_ENDPOINT, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ query: gqlQuery(withDesc) })
      }).then(function (r) {
        if (r.json && r.json.errors && r.json.errors.length) {
          throw new Error(r.json.errors[0].message);
        }
        var items = r.json && r.json.data ? r.json.data.books.books : [];
        renderVsJson(els.vsGqlJson, items, coreKeys);
        els.vsGqlSize.textContent = fmtBytes(byteLen(JSON.stringify(r.json)));
        els.vsGqlWaste.textContent = items.length ? fmtBytes(byteLen(JSON.stringify(items))) : '-';
        return r;
      })
    ]).then(function (results) {
      var full = results[0], light = results[1], gql = results[2];
      var fb = byteLen(JSON.stringify(full.json));
      var cb = byteLen(JSON.stringify(light.json));
      var ov = eng.estimateOverfetch(fb, cb);
      var fields = eng.summarizeFields(withDesc);

      els.exp1Insight.innerHTML = withDesc
        ? 'The page now wants <b>description</b>. The slim REST endpoint does not include it, so it cannot satisfy the new UI shape; GraphQL adds it directly through the selection set.<br>' +
          'That is the practical difference between a fixed endpoint contract and client-selected fields.'
        : 'The full REST endpoint returns <b>' + fields.full + '</b> fields while the page uses <b>' + fields.core + '</b>. ' +
          'That leaves <b style="color:#ff6b6b">' + fields.wasted + ' extra fields (' + Math.round(fields.wastePct * 100) + '%)</b>. ' +
          'A carefully designed slim REST endpoint cuts the extra payload by about <b style="color:#81c784">' + fmtBytes(ov.wastedBytes) + '</b>, so over-fetching is often a design issue, not an unavoidable REST property.';

      return full;
    }).catch(function (error) {
      renderPanelError(apiErrorText(error));
    });
  }

  // Experiment 2: cache race

  /**
   * Request the same URL N times in sequence and record elapsed time.
   * The first call uses cache:'reload' to force the network; later calls use
   * normal browser behavior so Cache-Control can take effect.
   * @param {string} url
   * @param {number} times
   * @returns {Promise<number[]>} elapsed times in ms
   */
  function restSeries(url, times) {
    var results = [];
    var chain = timedFetch(url, { cache: 'reload' }).then(function (r) { results.push(r.ms); });
    for (var i = 1; i < times; i++) {
      chain = chain.then(function () {
        return timedFetch(url).then(function (r) { results.push(r.ms); });
      });
    }
    return chain.then(function () { return results; });
  }

  /** Send N GraphQL POST requests in sequence. POST does not get normal browser cache reuse. */
  function gqlSeries(times) {
    var results = [];
    var chain = Promise.resolve();
    for (var i = 0; i < times; i++) {
      chain = chain.then(function () {
        return timedFetch(GQL_ENDPOINT, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ query: gqlQuery(false) })
        }).then(function (r) { results.push(r.ms); });
      });
    }
    return chain.then(function () { return results; });
  }

  function sum(arr) {
    return arr.reduce(function (a, b) { return a + b; }, 0);
  }

  function mean(arr) {
    if (!arr.length) return 0;
    return sum(arr) / arr.length;
  }

  function runBench() {
    els.btnBench.disabled = true;
    els.benchStatus.textContent = 'Running 10 requests...';
    var runs = 10;

    var targets = [
      { url: REST_FULL, first: els.bFirstRest, avg: els.bAvgRest, total: els.bTotalRest },
      { url: REST_LIGHT, first: els.bFirstLight, avg: els.bAvgLight, total: els.bTotalLight }
    ];

    var restPlans = targets.map(function (t) {
      return restSeries(t.url, runs).then(function (times) {
        return { times: times, target: t };
      });
    });
    var gqlPlan = gqlSeries(runs).then(function (times) {
      return { times: times };
    });

    Promise.all(restPlans.concat(gqlPlan)).then(function (all) {
      var rests = all.slice(0, 2);
      var gql = all[2];

      rests.forEach(function (r) {
        var times = r.times;
        r.target.first.textContent = times[0] + ' ms';
        r.target.avg.textContent = mean(times.slice(1)).toFixed(1) + ' ms';
        r.target.total.textContent = sum(times).toFixed(1) + ' ms';
      });
      var gqlTimes = gql.times;
      els.bFirstGql.textContent = gqlTimes[0] + ' ms';
      els.bAvgGql.textContent = mean(gqlTimes.slice(1)).toFixed(1) + ' ms';
      els.bTotalGql.textContent = sum(gqlTimes).toFixed(1) + ' ms';

      var gqlTotal = sum(gqlTimes);
      var restTotal = sum(rests[0].times);
      els.exp2Insight.innerHTML = 'GraphQL made 10 full-price POST requests for a total of <b style="color:#ff6b6b">' + gqlTotal.toFixed(1) + ' ms</b>. ' +
        'REST used the network once, then browser cache from request 2 onward via <code>Cache-Control: max-age=60</code>, ' +
        'averaging ' + mean(rests[0].times.slice(1)).toFixed(1) + ' ms and totaling ' + restTotal.toFixed(1) + ' ms.<br>' +
        '<b>GET inherits decades of HTTP caching behavior; POST does not get that by default.</b>';

      els.btnBench.disabled = false;
      els.benchStatus.textContent = '';
    }).catch(function () {
      els.benchStatus.textContent = 'Request failed. Check the backend and network.';
      els.btnBench.disabled = false;
    });
  }

  // Experiment 3: cost explosion

  function updateGqlLimitVal() {
    els.gqlLimitVal.textContent = els.gqlLimit.value;
    updatePred();
  }

  function updatePred() {
    var n = parseInt(els.gqlLimit.value, 10);
    var p = eng.predictGraphqlDbCalls(n, els.gqlAuthor.checked, els.gqlReviews.checked, 2);
    els.predText.textContent = 'limit=' + n +
      (els.gqlAuthor.checked ? ' + author' : '') +
      (els.gqlReviews.checked ? ' + reviews' : '') +
      ' -> expected ' + p.dbCalls + ' SQL calls, loading about ' + p.rowsLoaded + ' rows';
  }

  function runExp3() {
    var n = parseInt(els.gqlLimit.value, 10);
    var author = els.gqlAuthor.checked;
    var reviews = els.gqlReviews.checked;
    var sel = 'title price';
    if (author) sel += ' author { name }';
    if (reviews) sel += ' reviews { rating }';
    var query = '{ books(limit: ' + n + ') { books { ' + sel + ' } meta { dbCalls rowsLoaded elapsedMs } } }';

    els.btnGqlRun.disabled = true;
    els.costDbCalls.textContent = '...';

    timedFetch(GQL_ENDPOINT, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ query: query })
    }).then(function (r) {
      els.btnGqlRun.disabled = false;
      if (!r.json || !r.json.data) {
        els.costDbCalls.textContent = 'ERR';
        els.exp3Insight.innerHTML = 'Request failed: ' + (r.json && r.json.errors ? JSON.stringify(r.json.errors[0].message) : 'unknown error');
        return;
      }
      var meta = r.json.data.books.meta;
      els.costDbCalls.textContent = meta.dbCalls;
      els.costRows.textContent = meta.rowsLoaded;
      els.costMs.textContent = meta.elapsedMs;

      var pred = eng.predictGraphqlDbCalls(n, author, reviews, 2);
      var nesting = (author ? ' + one author query per book' : '') + (author && reviews ? ', ' : '') + (reviews ? ' + one reviews query per book' : '');
      els.exp3Insight.innerHTML = 'The server actually executed <b style="color:#ff6b6b">' + meta.dbCalls + '</b> SQL calls: 1 scalar book query' + nesting +
        '. That is the N+1 explosion in plain numbers. REST stays at 1 SQL call for this list endpoint.<br>' +
        'The model predicted ' + pred.dbCalls + ' calls, and ' + (meta.dbCalls === pred.dbCalls ? 'the result matched.' : 'review row counts can vary slightly.') +
        ' Public GraphQL APIs usually need depth limits, complexity scoring, persisted queries, batching, or join planning.';
    }).catch(function () {
      els.btnGqlRun.disabled = false;
      els.costDbCalls.textContent = 'ERR';
      els.costRows.textContent = 'ERR';
      els.costMs.textContent = 'ERR';
      els.exp3Insight.innerHTML = '<b>Backend check:</b> ' + escapeHtml(apiErrorText(new Error('GraphQL request failed')));
    });
  }

  // Step navigation

  function setupGuide() {
    var steps = document.querySelectorAll('.guide-step');
    if (!('IntersectionObserver' in window)) return;
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (e) {
        if (e.isIntersecting) {
          var target = e.target.id;
          steps.forEach(function (s) {
            s.classList.toggle('active', s.getAttribute('data-target') === target);
          });
        }
      });
    }, { rootMargin: '-45% 0px -50% 0px' });
    ['exp-1', 'exp-2', 'exp-3', 'balance'].forEach(function (id) {
      var el = document.getElementById(id);
      if (el) io.observe(el);
    });
  }

  // Event binding

  function bind() {
    els.ckDescription.addEventListener('change', function () { runExp1(); });
    els.btnBench.addEventListener('click', runBench);
    els.gqlLimit.addEventListener('input', updateGqlLimitVal);
    els.btnGqlRun.addEventListener('click', runExp3);
    els.gqlAuthor.addEventListener('change', updatePred);
    els.gqlReviews.addEventListener('change', updatePred);
  }

  function init() {
    cacheDom();
    bind();
    updateGqlLimitVal();
    setupGuide();
    runExp1();
    runExp3();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
