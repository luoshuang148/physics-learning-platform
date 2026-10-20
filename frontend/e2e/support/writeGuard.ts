/** Browser tests that write data must be explicitly pointed at a disposable local environment. */
export function requireIsolatedWriteTarget(baseUrl: string, allowWrites: string | undefined) {
  if (allowWrites !== '1') throw new Error('Set E2E_ALLOW_WRITES=1 only for a disposable isolated test database.');
  const url = new URL(baseUrl);
  if (!['localhost', '127.0.0.1', '[::1]'].includes(url.hostname)) throw new Error('Writing E2E tests require a loopback target.');
}
