const SESSION_KEY = 'zhixue_session_id';

const randomHex = (length: number): string => {
  const targetLength = Math.max(2, length);
  const globalCrypto = globalThis.crypto;

  if (globalCrypto?.randomUUID) {
    return globalCrypto.randomUUID().replace(/-/g, '').slice(0, targetLength);
  }

  if (globalCrypto?.getRandomValues) {
    const byteLength = Math.ceil(targetLength / 2);
    const bytes = new Uint8Array(byteLength);
    globalCrypto.getRandomValues(bytes);
    return Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('').slice(0, targetLength);
  }

  let result = '';
  while (result.length < targetLength) {
    result += Math.floor(Math.random() * 0xffffffff)
      .toString(16)
      .padStart(8, '0');
  }
  return result.slice(0, targetLength);
};

const generateSessionId = (): string => {
  const timestamp = new Date().toISOString().replace(/[-:T]/g, '').slice(0, 14);
  const uuidPart = randomHex(16);
  return `${timestamp}_${uuidPart}`;
};

const getNamespacedKey = (namespace?: string): string => {
  if (!namespace?.trim()) return SESSION_KEY;
  return `${SESSION_KEY}_${namespace.trim()}`;
};

export const getSessionId = (namespace?: string): string => {
  const key = getNamespacedKey(namespace);
  const current = sessionStorage.getItem(key);
  if (current) {
    return current;
  }
  const created = generateSessionId();
  sessionStorage.setItem(key, created);
  return created;
};

export const resetSessionId = (namespace?: string): string => {
  const key = getNamespacedKey(namespace);
  const created = generateSessionId();
  sessionStorage.setItem(key, created);
  return created;
};

export const clearSessionId = (namespace?: string): void => {
  sessionStorage.removeItem(getNamespacedKey(namespace));
};

