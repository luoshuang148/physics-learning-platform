import { test as base, expect } from '@playwright/test';
import { requireIsolatedWriteTarget } from './writeGuard';
export const test = base.extend<{ isolatedWriteTarget: void }>({
  isolatedWriteTarget: [async ({ baseURL }, use) => {
    requireIsolatedWriteTarget(baseURL!, process.env.E2E_ALLOW_WRITES);
    await use();
  }, { auto: true }],
});
export { expect };
