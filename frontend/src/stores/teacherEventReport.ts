import { defineStore } from 'pinia';
import { useAuthStore } from './auth';
import { getSessionId } from '@/utils/session';

export interface CachedTeacherEventReport {
  ownerId?: number;
  sessionId?: string;
  loading: boolean;
  progress: number;
  content: string;
  reasoning: string;
  error: string;
  tokenUsage: string;
  currentEventTitle: string;
  eventId?: number;
  updatedAt: string;
}

const STORAGE_KEY = 'teacher_event_report_cache';

const loadCache = (): CachedTeacherEventReport | null => {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    return JSON.parse(raw) as CachedTeacherEventReport;
  } catch {
    return null;
  }
};

const persistCache = (cache: CachedTeacherEventReport | null) => {
  if (!cache) {
    sessionStorage.removeItem(STORAGE_KEY);
    return;
  }
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify(cache));
};

export const useTeacherEventReportStore = defineStore('teacherEventReport', {
  state: () => ({
    cachedReport: loadCache() as CachedTeacherEventReport | null,
  }),
  actions: {
    hydrateFromStorage() {
      const cache=loadCache();
      this.cachedReport=cache?.ownerId===useAuthStore().user?.id && cache?.sessionId===getSessionId(`teacher_event_report_${cache?.eventId}`) ? cache : null;
    },
    setCachedReport(payload: Omit<CachedTeacherEventReport, 'updatedAt'>) {
      this.cachedReport = {
        ...payload,
        ownerId: useAuthStore().user?.id,
        sessionId: getSessionId(`teacher_event_report_${payload.eventId}`),
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

