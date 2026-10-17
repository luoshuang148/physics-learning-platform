import { ref } from 'vue';

interface StreamState {
  loading: boolean;
  progress: number;
  content: string;
  reasoning: string;
  error: string;
  tokenUsage: string;
}

export const useAIStreamState = () => {
  const state = ref<StreamState>({
    loading: false,
    progress: 0,
    content: '',
    reasoning: '',
    error: '',
    tokenUsage: '',
  });

  const reset = () => {
    state.value = {
      loading: false,
      progress: 0,
      content: '',
      reasoning: '',
      error: '',
      tokenUsage: '',
    };
  };

  return { state, reset };
};

