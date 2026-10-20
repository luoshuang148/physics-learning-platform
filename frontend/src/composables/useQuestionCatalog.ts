import { onMounted, onUnmounted, ref, watch } from 'vue';
import { getQuestionCatalogApi } from '@/api/question';
import type { QuestionCatalogRow } from '@/types/question';

export function useQuestionCatalog(onError: (message: string, type: 'error') => void) {
  const rows = ref<QuestionCatalogRow[]>([]), loading = ref(true), page = ref(1), total = ref(0), pages = ref(0);
  const search = ref(''), difficulty = ref(0);
  let sequence = 0, timer: ReturnType<typeof setTimeout> | undefined;
  const load = async () => {
    const owner = ++sequence;
    loading.value = true;
    try {
      const result = await getQuestionCatalogApi({ page: page.value, size: 20, search: search.value.trim(), difficulty: difficulty.value || undefined });
      if (owner !== sequence) return;
      rows.value = result.records ?? []; total.value = result.total ?? 0; pages.value = result.pages ?? 0;
      if (page.value > Math.max(1, pages.value)) { page.value = Math.max(1, pages.value); await load(); }
    } catch (error: any) {
      if (owner !== sequence) return;
      rows.value = []; onError(error?.response?.data?.message ?? '题库加载失败，请重试', 'error');
    } finally { if (owner === sequence) loading.value = false; }
  };
  const changePage = (delta: number) => {
    const next = page.value + delta;
    if (loading.value || next < 1 || next > pages.value) return;
    page.value = next; void load();
  };
  watch([search, difficulty], () => {
    ++sequence; clearTimeout(timer); page.value = 1; loading.value = true;
    timer = setTimeout(load, 250);
  });
  onMounted(load);
  onUnmounted(() => { ++sequence; clearTimeout(timer); });
  return { rows, loading, page, total, pages, search, difficulty, load, changePage };
}
