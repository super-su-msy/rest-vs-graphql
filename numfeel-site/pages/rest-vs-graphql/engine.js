/**
 * engine.js - Core logic for the REST vs GraphQL comparison demo.
 * Pure functions only; no DOM access, so it can run in both browsers and Node.js.
 *
 * Exports:
 *   CLAUSES - fields returned by the full REST payload.
 *   REST_CORE_FIELDS - fields actually used by the list card.
 *   summarizeFields(selectedEncoding) - returned/used/extra field counts.
 *   estimateOverfetch(fullBytes, coreBytes) - over-fetch byte estimates.
 *   predictGraphqlDbCalls(limit, withAuthor, withReviews, reviewsPerBook) - N+1 cost prediction.
 *   accumulateMeans(times) - averages for the cache race.
 */

(function () {
  'use strict';

  /**
   * Fields returned by the full REST payload. Heavy fields are large text-like
   * fields that make generic DTO responses noticeably wasteful.
   */
  var CLAUSES = [
    { key: 'id', label: 'id', heavy: false },
    { key: 'title', label: 'Title', heavy: false },
    { key: 'author', label: 'Author', heavy: false },
    { key: 'rating', label: 'Rating', heavy: false },
    { key: 'price', label: 'Price', heavy: false },
    { key: 'isbn', label: 'ISBN', heavy: false },
    { key: 'category', label: 'Category', heavy: false },
    { key: 'pages', label: 'Pages', heavy: false },
    { key: 'stock', label: 'Stock', heavy: false },
    { key: 'publishedYear', label: 'Published year', heavy: false },
    { key: 'description', label: 'Description (long text)', heavy: true }
  ];

  /**
   * Fields actually rendered by the page card.
   */
  var REST_CORE_FIELDS = ['id', 'title', 'author', 'rating', 'price'];

  /**
   * Summarize field usage for the current UI shape.
   * @param {boolean} selectDescription whether the page also asks for description
   * @returns {{core:number, full:number, wasted:number, wastePct:number}}
   *   core   fields the page actually needs
   *   full   fields returned by the full REST payload
   *   wasted extra fields not used by the page
   *   wastePct extra fields as a fraction of returned fields
   */
  function summarizeFields(selectDescription) {
    var core = REST_CORE_FIELDS.length + (selectDescription ? 1 : 0);
    var requested = selectDescription ? 6 : 5;
    var full = CLAUSES.length;
    var wasted = Math.max(0, full - requested);
    return {
      core: core,
      full: full,
      wasted: wasted,
      wastePct: wasted / full
    };
  }

  /**
   * Estimate over-fetch waste in bytes.
   * @param {number} fullBytes actual byte size of the full REST response
   * @param {number} coreBytes actual byte size of the slim REST or GraphQL response
   * @returns {{wastedBytes:number, wastePct:number, savedPct:number}}
   */
  function estimateOverfetch(fullBytes, coreBytes) {
    var wastedBytes = Math.max(0, fullBytes - coreBytes);
    var base = Math.max(fullBytes, 1);
    return {
      wastedBytes: wastedBytes,
      wastePct: wastedBytes / base,
      savedPct: coreBytes > 0 ? Math.min(1, (fullBytes - coreBytes) / fullBytes) : 0
    };
  }

  /**
   * Predict DB calls for a nested GraphQL query under the intentional N+1 model.
   * Scalars = 1 query; +author = one author query per book; +reviews = one review query per book.
   * @param {number} limit query limit
   * @param {boolean} withAuthor whether author is selected
   * @param {boolean} withReviews whether reviews is selected
   * @param {number} [reviewsPerBook=2] estimated reviews per book for row counts
   * @returns {{dbCalls:number, rowsLoaded:number}}
   *   dbCalls    total SQL calls = 1 + (withAuthor?limit:0) + (withReviews?limit:0)
   *   rowsLoaded estimated rows loaded = limit + author rows + review rows
   */
  function predictGraphqlDbCalls(limit, withAuthor, withReviews, reviewsPerBook) {
    reviewsPerBook = reviewsPerBook == null ? 2 : reviewsPerBook;
    var n = Math.max(0, Math.floor(limit));
    var dbCalls = 1 + (withAuthor ? n : 0) + (withReviews ? n : 0);
    var rowsLoaded = n
      + (withAuthor ? n : 0)
      + (withReviews ? n * reviewsPerBook : 0);
    return { dbCalls: dbCalls, rowsLoaded: rowsLoaded };
  }

  /**
   * Average multiple timing series for the cache race.
   * @param {Array<Array<number>>} series timing samples in milliseconds
   * @returns {Array<number>} one average per series
   */
  function accumulateMeans(series) {
    return series.map(function (times) {
      if (!times || times.length === 0) return 0;
      var sum = 0;
      for (var i = 0; i < times.length; i++) sum += times[i];
      return sum / times.length;
    });
  }

  // Exports
  var exports = {
    CLAUSES: CLAUSES,
    REST_CORE_FIELDS: REST_CORE_FIELDS,
    summarizeFields: summarizeFields,
    estimateOverfetch: estimateOverfetch,
    predictGraphqlDbCalls: predictGraphqlDbCalls,
    accumulateMeans: accumulateMeans
  };

  if (typeof module !== 'undefined' && module.exports) {
    module.exports = exports;
  }
  if (typeof window !== 'undefined') {
    window.RestVsGraphqlEngine = exports;
  }
})();
