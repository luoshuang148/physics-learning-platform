import { test } from 'node:test';
import assert from 'node:assert/strict';
import { requireIsolatedWriteTarget } from '../e2e/support/writeGuard.ts';
test('writing browser tests require explicit isolation and a loopback target', () => {
  assert.throws(() => requireIsolatedWriteTarget('http://127.0.0.1:18081/api/v1/', undefined), /E2E_ALLOW_WRITES/);
  assert.throws(() => requireIsolatedWriteTarget('https://school.example/api/v1/', '1'), /loopback/);
  assert.doesNotThrow(() => requireIsolatedWriteTarget('http://127.0.0.1:18081/api/v1/', '1'));
});
