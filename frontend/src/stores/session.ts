import { defineStore } from 'pinia';
import { ref } from 'vue';
import { clearSessionId, getSessionId, resetSessionId } from '@/utils/session';

export const useSessionStore = defineStore('session', () => {
  const sessionId = ref(getSessionId());

  const refreshSession = () => {
    sessionId.value = resetSessionId();
  };

  const clearSession = () => {
    clearSessionId();
    sessionId.value = getSessionId();
  };

  return {
    sessionId,
    refreshSession,
    clearSession,
  };
});

