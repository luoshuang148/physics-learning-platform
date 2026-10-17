import { defineStore } from 'pinia';

export interface CachedTeacherEventReport {
  loading: boolean;
  progress: number;
  content: string;
  reasoning: string;
  error: string;
  tokenUsage: string;
  currentEventTitle: string;
  updatedAt: string;
}

const STORAGE_KEY = 'teacher_event_report_cache';

const loadCache = (): CachedTeacherEventReport | null => {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    return JSON.parse(raw) as CachedTeacherEventReport;
  } catch {
    return null;
  }
};

const persistCache = (cache: CachedTeacherEventReport | null) => {
  if (!cache) {
    localStorage.removeItem(STORAGE_KEY);
    return;
  }
  localStorage.setItem(STORAGE_KEY, JSON.stringify(cache));
};

export const useTeacherEventReportStore = defineStore('teacherEventReport', {
  state: () => ({
    cachedReport: loadCache() as CachedTeacherEventReport | null,
  }),
  actions: {
    hydrateFromStorage() {
      this.cachedReport = loadCache();
    },
    setCachedReport(payload: Omit<CachedTeacherEventReport, 'updatedAt'>) {
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

