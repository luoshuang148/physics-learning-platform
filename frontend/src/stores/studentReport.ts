import { defineStore } from 'pinia';
import { useAuthStore } from './auth';
import { getSessionId } from '@/utils/session';

export interface CachedStudentReport {
  scope: string;
  content: string;
  reasoning: string;
  tokenUsage: string;
  updatedAt: string;
}

const STORAGE_KEY = 'student_report_cache';

const loadCache = (): CachedStudentReport | null => {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    return JSON.parse(raw) as CachedStudentReport;
  } catch {
    return null;
  }
};

const persistCache = (cache: CachedStudentReport | null) => {
  if (!cache) {
    sessionStorage.removeItem(STORAGE_KEY);
    return;
  }
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify(cache));
};

export const useStudentReportStore = defineStore('studentReport', {
  state: () => ({
    cachedReport: loadCache() as CachedStudentReport | null,
  }),
  actions: {
    hydrateFromStorage(eventId?: number) {
      const scope = `${useAuthStore().user?.id}:${eventId}:${getSessionId()}`;
      const cache = loadCache();
      this.cachedReport = cache?.scope === scope ? cache : null;
    },
    setCachedReport(payload: Omit<CachedStudentReport, 'updatedAt' | 'scope'>, eventId?: number) {
      this.cachedReport = {
        ...payload,
        scope: `${useAuthStore().user?.id}:${eventId}:${getSessionId()}`,
        updatedAt: new Date().toISOString(),
      };
      persistCache(this.cachedReport);
    },
    clearCachedReport() {
      this.cachedReport = null;
      persistCache(null);
    },
  },
});
