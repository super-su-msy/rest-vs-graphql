/**
 * engine.test.js - Run with: node pages/rest-vs-graphql/engine.test.js
 * Covers field summaries, over-fetch estimates, GraphQL N+1 cost prediction, and timing averages.
 */
'use strict';

var eng = require('./engine.js');

var passed = 0;
var failed = 0;

function assert(cond, msg) {
  if (cond) {
    passed++;
    console.log('✅ ' + msg);
  } else {
    failed++;
    console.error('❌ ' + msg);
  }
}

function assertClose(a, b, tol, msg) {
  if (Math.abs(a - b) <= tol) {
    passed++;
    console.log('✅ ' + msg);
  } else {
    failed++;
    console.error('❌ ' + msg + ' (got ' + a + ', want ~' + b + ')');
  }
}

// summarizeFields
(function () {
  var s = eng.summarizeFields(false);
  assert(s.core === 5, 'core field count = 5 without description, actual=' + s.core);
  assert(s.full === 11, 'full payload field count = 11, actual=' + s.full);
  assert(s.wasted === 6, 'wasted field count = 6 without description, actual=' + s.wasted);
  assertClose(s.wastePct, 6 / 11, 0.0001, 'wastePct=6/11');

  var s2 = eng.summarizeFields(true);
  assert(s2.wasted === 5, 'wasted field count = 5 with description, actual=' + s2.wasted);
})();

// estimateOverfetch
(function () {
  var r = eng.estimateOverfetch(1000, 400);
  assert(r.wastedBytes === 600, 'over-fetch bytes = 600, actual=' + r.wastedBytes);
  assertClose(r.wastePct, 0.6, 0.0001, 'wastePct = 60%');
  assertClose(r.savedPct, 0.6, 0.0001, 'slim response saves 60%');

  var r2 = eng.estimateOverfetch(0, 0);
  assert(r2.wastedBytes === 0 && r2.wastePct === 0, 'zero-byte input does not break');
})();

// predictGraphqlDbCalls
(function () {
  var scalar = eng.predictGraphqlDbCalls(10, false, false);
  assert(scalar.dbCalls === 1, 'scalar-only query uses 1 SQL call, actual=' + scalar.dbCalls);
  assert(scalar.rowsLoaded === 10, 'scalar-only query loads 10 rows, actual=' + scalar.rowsLoaded);

  var author = eng.predictGraphqlDbCalls(10, true, false);
  assert(author.dbCalls === 11, '+author: 1+10=11 SQL calls, actual=' + author.dbCalls);
  assert(author.rowsLoaded === 20, '+author loads 20 rows, actual=' + author.rowsLoaded);

  var deep = eng.predictGraphqlDbCalls(10, true, true, 2);
  assert(deep.dbCalls === 21, '+author+reviews: 1+10+10=21 SQL calls, actual=' + deep.dbCalls);
  assert(deep.rowsLoaded === 40, '+author+reviews loads 40 rows, actual=' + deep.rowsLoaded);

  var zero = eng.predictGraphqlDbCalls(0, true, true, 2);
  assert(zero.dbCalls === 1, 'limit=0 still has only 1 base SQL call, actual=' + zero.dbCalls);
})();

// accumulateMeans
(function () {
  var means = eng.accumulateMeans([[10, 20, 30], [2, 4, 6]]);
  assertClose(means[0], 20, 0.0001, 'first series average = 20');
  assertClose(means[1], 4, 0.0001, 'second series average = 4');

  var empty = eng.accumulateMeans([[],[1,2,3]]);
  assert(empty[0] === 0, 'empty series average = 0');
})();

console.log('\nResult: ' + passed + ' passed, ' + failed + ' failed');
process.exit(failed === 0 ? 0 : 1);
