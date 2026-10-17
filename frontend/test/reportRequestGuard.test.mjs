import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createReportRequestGuard } from '../src/utils/reportRequestGuard.ts';

test('replacement invalidates old callbacks without relabeling the old request', () => {
  const guard = createReportRequestGuard();
  const a = guard.begin(1, 'same title');
  const b = guard.begin(2, 'same title');
  assert.equal(a.isCurrent(), false);
  assert.equal(b.isCurrent(), true);
  assert.equal(a.eventId, 1);
  assert.equal(b.eventId, 2);
});
test('archive/clear prevents late responses from repopulating report state', () => {
  const guard = createReportRequestGuard();
  const a = guard.begin(1, 'task A');
  guard.invalidate();
  const content = [];
  if (a.isCurrent()) content.push('late task A chunk');
  assert.deepEqual(content, []);
});
