import { ref } from 'vue';

const message = ref('');
const type = ref<'success' | 'error' | 'info'>('info');
const visible = ref(false);
let hideTimer: number | null = null;

export const useToast = () => {
  const show = (nextMessage: string, nextType: 'success' | 'error' | 'info' = 'info') => {
    message.value = nextMessage;
    type.value = nextType;
    visible.value = true;

    if (hideTimer) {
      window.clearTimeout(hideTimer);
    }

    hideTimer = window.setTimeout(() => {
      visible.value = false;
      hideTimer = null;
    }, 2400);
  };

  return { message, type, visible, show };
};

