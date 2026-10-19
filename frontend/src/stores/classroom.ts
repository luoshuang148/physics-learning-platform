import { computed, ref } from 'vue';
import { defineStore } from 'pinia';
import { getMyClassesApi } from '@/api/classroom';
import type { TeachingClass } from '@/types/classroom';

const STORAGE_KEY = 'zxwl.activeClassId';

export const useClassroomStore = defineStore('classroom', () => {
  const classes = ref<TeachingClass[]>([]);
  const activeClassId = ref<number | null>(Number(localStorage.getItem(STORAGE_KEY)) || null);
  const loading = ref(false);
  const activeClass = computed(() => classes.value.find(c => c.id === activeClassId.value) ?? null);

  const setActiveClass = (id: number | null) => {
    activeClassId.value = id;
    if (id == null) localStorage.removeItem(STORAGE_KEY);
    else localStorage.setItem(STORAGE_KEY, String(id));
  };

  const loadClasses = async () => {
    loading.value = true;
    try {
      classes.value = await getMyClassesApi();
      if (!classes.value.some(c => c.id === activeClassId.value)) {
        setActiveClass(classes.value[0]?.id ?? null);
      }
    } finally { loading.value = false; }
  };

  return { classes, activeClassId, activeClass, loading, loadClasses, setActiveClass };
});
