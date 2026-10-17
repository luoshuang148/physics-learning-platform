import { defineStore } from 'pinia';

export interface CachedStudentReport {
  content: string;
  reasoning: string;
  tokenUsage: string;
  updatedAt: string;
}

const STORAGE_KEY = 'student_report_cache';

const loadCache = (): CachedStudentReport | null => {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    return JSON.parse(raw) as CachedStudentReport;
  } catch {
    return null;
  }
};

const persistCache = (cache: CachedStudentReport | null) => {
  if (!cache) {
    localStorage.removeItem(STORAGE_KEY);
    return;
  }
  localStorage.setItem(STORAGE_KEY, JSON.stringify(cache));
};

export const useStudentReportStore = defineStore('studentReport', {
  state: () => ({
    cachedReport: loadCache() as CachedStudentReport | null,
  }),
  actions: {
    hydrateFromStorage() {
      this.cachedReport = loadCache();
    },
    setCachedReport(payload: Omit<CachedStudentReport, 'updatedAt'>) {
      this.cachedReport = {
        ...payload,
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
