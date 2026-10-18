<template>
  <AppShell :subtitle="`3D 实验 · ${auth.user?.realName ?? ''}`" :links="links">
    <div class="exp-page">

      <!-- 左：实验选择栏 -->
      <div class="exp-sidebar">
        <div class="exp-series-switch">
          <button
            v-for="series in validSeries"
            :key="series"
            class="series-switch-btn"
            :class="{ active: currentSeries === series }"
            @click="switchSeries(series)"
          >{{ series.includes('原子') ? '原子物理' : '宇宙航天' }}</button>
        </div>
        <div class="exp-sidebar-head">
          <span class="exp-series-badge" v-if="currentSeries">{{ currentSeries }}</span>
          <span class="exp-count">{{ experiments.length }} 个实验</span>
        </div>

        <div class="exp-list">
          <button
            v-for="item in experiments"
            :key="item.id"
            class="exp-item"
            :class="{ active: activeExperiment?.id === item.id, done: doneMap[item.id] }"
            @click="switchExperiment(item)"
          >
            <span class="exp-item-check">{{ doneMap[item.id] ? '✓' : '○' }}</span>
            <div class="exp-item-body">
              <div class="exp-item-title">{{ item.title }}</div>
              <div class="exp-item-desc">{{ item.desc }}</div>
            </div>
          </button>
        </div>

        <div class="exp-sidebar-actions">
          <button
            v-if="activeExperiment"
            class="neon-btn secondary"
            type="button"
            @click="enterFullscreen"
          >全屏</button>
          <button
            v-if="activeExperiment"
            class="mark-btn"
            :class="{ done: doneMap[activeExperiment.id] }"
            @click="toggleDone(activeExperiment.id)"
          >
            {{ doneMap[activeExperiment.id] ? '已标记完成' : '标记当前实验完成' }}
          </button>
        </div>
      </div>

      <!-- 右：实验 iframe -->
      <div class="exp-main" ref="fullscreenContainerRef">
        <div v-if="!activeExperiment" class="exp-placeholder">
        </div>
        <iframe
          v-else
          ref="experimentFrameRef"
          :key="activeExperiment.id"
          :src="activeExperiment.src"
          class="exp-frame"
          title="three-physical-experiment"
          @load="handleFrameLoad"
        />
        <button
          v-if="isFullscreen"
          class="exp-exit-fullscreen"
          type="button"
          @click="exitFullscreen"
        >退出全屏</button>
      </div>

    </div>
    <AppToast :visible="visible" :type="type" :message="message" />
  </AppShell>
</template>

<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import AppShell from '@/layouts/AppShell.vue';
import AppToast from '@/components/AppToast.vue';
import { useToast } from '@/composables/useToast';
import { useAuthStore } from '@/stores/auth';

const EXPERIMENT_STATE_KEY_PREFIX = 'student_experiment_view_state';
const EXPERIMENT_STATE_LEGACY_KEY = 'student_experiment_view_state';

const auth = useAuthStore();
const { visible, type, message, show } = useToast();

const getExperimentStateKey = () => `${EXPERIMENT_STATE_KEY_PREFIX}_${auth.user?.id ?? 'anonymous'}`;

const links = [
  { label: '首页', to: '/student/home' },
  { label: '作业', to: '/student/task' },
  { label: '错题本', to: '/student/wrong-book' },
  { label: '学习报告', to: '/student/report' },
];

const resolveStaticAssetUrl = (path: string) => {
  const base = (import.meta.env.BASE_URL || '/').replace(/\/+$/, '/');
  const normalizedPath = path.replace(/^\/+/, '');
  return `${base}${normalizedPath}`;
};

const SERIES_EXPERIMENTS = {
  原子物理系列实验: [
    { id: 'alpha', title: '卢瑟福 α 粒子散射实验', desc: '观察散射角分布与大角度散射现象', src: resolveStaticAssetUrl('three-physical-project/Alpha%20particle%20scattering%20experiment/index.html') },
    { id: 'bohr', title: '波尔理论实验', desc: '模拟电子跃迁、能级变化与光谱发射', src: resolveStaticAssetUrl('three-physical-project/Bohr%20theory/index.html') },
    { id: 'cloud', title: '电子云模型实验', desc: '理解概率云分布与轨道形态特征', src: resolveStaticAssetUrl('three-physical-project/Electron%20cloud%20model/index.html') },
    { id: 'photoelectric', title: '光电效应实验', desc: '观察截止电压、入射频率与光电子逸出关系', src: resolveStaticAssetUrl('three-physical-project/Photoelectric/index.html') },
  ],
  万有引力与宇宙航天系列实验: [
    { id: 'kepler', title: '开普勒三定律实验', desc: '观察轨道周期、面积速度与椭圆轨道规律', src: resolveStaticAssetUrl('three-physical-project/Kepler/index.html') },
    { id: 'cosmic', title: '三大宇宙速度实验', desc: '体验第一、第二宇宙速度与轨道脱离条件', src: resolveStaticAssetUrl('three-physical-project/Cosmis_Velocity/index.html') },
    { id: 'hohmann', title: '卫星变轨实验', desc: '模拟轨道变轨与最优转移路径', src: resolveStaticAssetUrl('three-physical-project/Hohmann_Transfer/index.html') },
    { id: 'lightspeed', title: '光速视界实验', desc: '在相对论视角下感受接近光速时的飞行视觉变化', src: resolveStaticAssetUrl('three-physical-project/LightspeedVista/index.html') },
  ],
} as const;

const validSeries = Object.keys(SERIES_EXPERIMENTS) as Array<keyof typeof SERIES_EXPERIMENTS>;

type ExperimentItem = (typeof SERIES_EXPERIMENTS)[keyof typeof SERIES_EXPERIMENTS][number];
type DisposableExperimentWindow = Window & {
  disposeExperiment?: () => void;
};
type ExperimentLayoutMode = 'full' | 'normal' | 'compact';
type ExperimentLayoutPayload = {
  type: 'zxwl-experiment-layout';
  mode: ExperimentLayoutMode;
  availableWidth: number;
  threshold: number;
};

const COMPACT_LAYOUT_THRESHOLD = 1500;
const LAYOUT_EVENT_NAME = 'zxwl-experiment-layout-change';
const AUTO_COLLAPSE_ATTR = 'data-auto-collapsed-by-layout';
const secondaryPanelIdPattern = /(log|chart|result|ai|guide|note|matrix|energy-diagram|intro)/i;
const secondaryPanelSummaryPattern = /(记录|日志|图|矩阵|说明|助教|提示|结果|观测)/;
const keepOpenSummaryPattern = /(控制|参数|发射|模式|控制台|调节|系统控制|量子控制台)/;

const currentSeries = ref<keyof typeof SERIES_EXPERIMENTS>(validSeries[0]);
const experiments = ref<ExperimentItem[]>([...SERIES_EXPERIMENTS[validSeries[0]]]);
const activeExperiment = ref<ExperimentItem | null>(null);
const doneMap = ref<Record<string, boolean>>({});
const experimentFrameRef = ref<HTMLIFrameElement | null>(null);
const fullscreenContainerRef = ref<HTMLElement | null>(null);
const isFullscreen = ref(false);
const expMainWidth = ref(0);
const layoutMode = ref<ExperimentLayoutMode>('normal');

let expMainResizeObserver: ResizeObserver | null = null;
let expMainFallbackResizeHandler: (() => void) | null = null;


const persistExperimentState = () => {
  sessionStorage.setItem(getExperimentStateKey(), JSON.stringify({
    currentSeries: currentSeries.value,
    activeExperimentId: activeExperiment.value?.id ?? null,
    doneMap: doneMap.value,
  }));
};

const restoreExperimentState = () => {
  const stateKey = getExperimentStateKey();
  const raw = sessionStorage.getItem(stateKey);
  if (!raw) {
    sessionStorage.removeItem(EXPERIMENT_STATE_LEGACY_KEY);
    return null;
  }
  try {
    return JSON.parse(raw) as {
      currentSeries?: string;
      activeExperimentId?: string | null;
      doneMap?: Record<string, boolean>;
    };
  } catch {
    sessionStorage.removeItem(stateKey);
    return null;
  }
};

const disposeCurrentExperiment = () => {
  const frameWindow = experimentFrameRef.value?.contentWindow as DisposableExperimentWindow | null;
  if (!frameWindow) return;
  try {
    frameWindow.disposeExperiment?.();
  } catch (error) {
    console.warn('disposeExperiment 调用失败', error);
  }
};

const getLayoutPayload = (): ExperimentLayoutPayload => ({
  type: 'zxwl-experiment-layout',
  mode: layoutMode.value,
  availableWidth: expMainWidth.value,
  threshold: COMPACT_LAYOUT_THRESHOLD,
});

const isSecondaryDetailsPanel = (detail: HTMLDetailsElement) => {
  const host = detail.closest('.panel, [id]') as HTMLElement | null;
  const hostId = `${detail.id} ${host?.id ?? ''}`.trim();
  const summaryText = detail.querySelector('summary')?.textContent?.replace(/\s+/g, '') ?? '';

  if (keepOpenSummaryPattern.test(summaryText)) return false;
  return secondaryPanelIdPattern.test(hostId) || secondaryPanelSummaryPattern.test(summaryText);
};

const syncSecondaryPanelState = (frameDoc: Document) => {
  const detailsList = Array.from(frameDoc.querySelectorAll('details')) as HTMLDetailsElement[];
  const shouldAutoCollapse = layoutMode.value !== 'full';
  for (const detail of detailsList) {
    if (!isSecondaryDetailsPanel(detail)) continue;
    if (shouldAutoCollapse) {
      if (detail.hasAttribute('open')) {
        detail.removeAttribute('open');
        detail.setAttribute(AUTO_COLLAPSE_ATTR, 'true');
      }
      continue;
    }
    if (detail.getAttribute(AUTO_COLLAPSE_ATTR) === 'true') {
      detail.setAttribute('open', '');
      detail.removeAttribute(AUTO_COLLAPSE_ATTR);
    }
  }
};

const updateLayoutMode = () => {
  const nextMode: ExperimentLayoutMode = isFullscreen.value
    ? 'full'
    : (expMainWidth.value < COMPACT_LAYOUT_THRESHOLD ? 'compact' : 'normal');
  layoutMode.value = nextMode;
};

const applyLayoutToFrame = () => {
  const payload = getLayoutPayload();
  window.dispatchEvent(new CustomEvent<ExperimentLayoutPayload>(LAYOUT_EVENT_NAME, { detail: payload }));

  const frameElement = experimentFrameRef.value;
  if (!frameElement) return;

  try {
    frameElement.contentWindow?.postMessage(payload, '*');
    const frameDoc = frameElement.contentDocument;
    if (!frameDoc?.body) return;

    frameDoc.body.classList.toggle('layout-nonfull', layoutMode.value !== 'full');
    frameDoc.body.classList.toggle('layout-compact', layoutMode.value === 'compact');
    frameDoc.body.classList.toggle('layout-full', layoutMode.value === 'full');
    frameDoc.body.dataset.layoutMode = layoutMode.value;
    syncSecondaryPanelState(frameDoc);
  } catch (error) {
    console.warn('实验 iframe 布局同步失败', error);
  }
};

const refreshExperimentContainerWidth = () => {
  const width = Math.round(fullscreenContainerRef.value?.getBoundingClientRect().width ?? 0);
  if (width > 0) {
    expMainWidth.value = width;
  }
};

const syncLayoutState = () => {
  refreshExperimentContainerWidth();
  updateLayoutMode();
  applyLayoutToFrame();
};

const observeExperimentContainer = () => {
  const container = fullscreenContainerRef.value;
  if (!container) return;

  if (typeof ResizeObserver !== 'undefined') {
    expMainResizeObserver = new ResizeObserver(() => {
      syncLayoutState();
    });
    expMainResizeObserver.observe(container);
    return;
  }

  expMainFallbackResizeHandler = () => {
    syncLayoutState();
  };
  window.addEventListener('resize', expMainFallbackResizeHandler);
};

const clearExperimentContainerObserver = () => {
  expMainResizeObserver?.disconnect();
  expMainResizeObserver = null;
  if (expMainFallbackResizeHandler) {
    window.removeEventListener('resize', expMainFallbackResizeHandler);
    expMainFallbackResizeHandler = null;
  }
};

const syncFullscreenState = () => {
  isFullscreen.value = document.fullscreenElement === fullscreenContainerRef.value;
  syncLayoutState();
};

const handleFrameLoad = () => {
  syncLayoutState();
};

const enterFullscreen = async () => {
  if (!fullscreenContainerRef.value) return;
  try {
    await fullscreenContainerRef.value.requestFullscreen();
  } catch {
    show('当前浏览器暂不支持全屏或全屏失败', 'error');
  }
};

const exitFullscreen = async () => {
  if (!document.fullscreenElement) return;
  try {
    await document.exitFullscreen();
  } catch {
    show('退出全屏失败，请重试', 'error');
  }
};

const switchExperiment = (item: ExperimentItem) => {
  if (activeExperiment.value?.id === item.id) return;
  disposeCurrentExperiment();
  activeExperiment.value = item;
  persistExperimentState();
};

const switchSeries = (series: keyof typeof SERIES_EXPERIMENTS) => {
  if (currentSeries.value === series) return;
  disposeCurrentExperiment();
  currentSeries.value = series;
  experiments.value = [...SERIES_EXPERIMENTS[series]];
  activeExperiment.value = experiments.value[0] ?? null;
  persistExperimentState();
};

watch(() => activeExperiment.value?.id, async () => {
  await nextTick();
  syncLayoutState();
});

const loadFreeExperiments = () => {
  const storedState = restoreExperimentState();
  const series = validSeries.includes(storedState?.currentSeries as keyof typeof SERIES_EXPERIMENTS)
    ? storedState?.currentSeries as keyof typeof SERIES_EXPERIMENTS
    : validSeries[0];
  currentSeries.value = series;
  experiments.value = [...SERIES_EXPERIMENTS[series]];
  doneMap.value = storedState?.doneMap ?? {};
  activeExperiment.value = experiments.value.find(item => item.id === storedState?.activeExperimentId)
    ?? experiments.value[0] ?? null;
  persistExperimentState();
};

const toggleDone = (id: string) => {
  doneMap.value[id] = !doneMap.value[id];
  persistExperimentState();
};

onMounted(() => {
  document.addEventListener('fullscreenchange', syncFullscreenState);
  observeExperimentContainer();
  syncLayoutState();
  loadFreeExperiments();
});
onBeforeUnmount(() => {
  document.removeEventListener('fullscreenchange', syncFullscreenState);
  clearExperimentContainerObserver();

  const resetPayload: ExperimentLayoutPayload = {
    type: 'zxwl-experiment-layout',
    mode: 'normal',
    availableWidth: window.innerWidth,
    threshold: COMPACT_LAYOUT_THRESHOLD,
  };
  window.dispatchEvent(new CustomEvent<ExperimentLayoutPayload>(LAYOUT_EVENT_NAME, { detail: resetPayload }));
  disposeCurrentExperiment();
});
</script>

<style scoped>
.exp-page {
  flex: 1;
  display: flex;
  gap: 0;
  min-height: 0;
  border-radius: 18px;
  overflow: hidden;
  border: 1px solid var(--line);
  background: rgba(10, 13, 28, 0.6);
}

/* 左侧栏 */
.exp-sidebar {
  width: 240px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--line);
  background: rgba(14, 18, 36, 0.9);
}

.exp-series-switch {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px;
  padding: 12px;
  border-bottom: 1px solid rgba(151, 172, 249, 0.1);
}

.series-switch-btn {
  border: 1px solid rgba(151, 172, 249, 0.18);
  border-radius: 9px;
  background: rgba(255, 255, 255, 0.03);
  color: var(--text-muted);
  padding: 8px 6px;
  font-size: var(--font-size-xs);
  cursor: pointer;
}

.series-switch-btn.active {
  border-color: rgba(90, 243, 211, 0.45);
  background: rgba(90, 243, 211, 0.1);
  color: var(--brand);
  font-weight: 700;
}

.exp-sidebar-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 16px 12px;
  border-bottom: 1px solid rgba(151, 172, 249, 0.1);
}

.exp-series-badge {
  font-size: var(--font-size-sm);
  font-weight: 800;
  color: var(--brand);
  background: rgba(90, 243, 211, 0.1);
  border: 1px solid rgba(90, 243, 211, 0.3);
  padding: 2px 10px;
  border-radius: 999px;
}

.exp-count {
  font-size: var(--font-size-sm);
  color: var(--text-muted);
  font-variant-numeric: tabular-nums;
}

.exp-list {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px 10px;
  overflow-y: auto;
}

.exp-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 12px;
  border: 1px solid transparent;
  background: transparent;
  color: var(--text);
  cursor: pointer;
  text-align: left;
  transition: all 0.18s ease;
}

.exp-item:hover {
  background: rgba(255,255,255,0.03);
  border-color: rgba(151, 172, 249, 0.15);
}

.exp-item.active {
  background: rgba(90, 243, 211, 0.07);
  border-color: rgba(90, 243, 211, 0.3);
}

.exp-item.done .exp-item-check {
  color: var(--ok);
}

.exp-item-check {
  font-size: var(--font-size-md);
  color: var(--text-muted);
  flex-shrink: 0;
  margin-top: 2px;
  width: 16px;
}

.exp-item-body {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.exp-item-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text);
  line-height: 1.4;
}

.exp-item-desc {
  font-size: 11px;
  color: var(--text-muted);
  line-height: 1.4;
}

.exp-sidebar-actions {
  padding: 12px 10px;
  border-top: 1px solid rgba(151, 172, 249, 0.1);
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.mark-btn {
  width: 100%;
  padding: 11px 14px;
  border-radius: 12px;
  border: 1px solid rgba(90, 243, 211, 0.35);
  background: rgba(90, 243, 211, 0.08);
  color: var(--brand);
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.18s ease;
}

.mark-btn.done {
  border-color: rgba(80, 200, 140, 0.35);
  background: rgba(80, 200, 140, 0.1);
  color: var(--ok);
}

.action-hint {
  margin: 2px 2px 0;
  font-size: 11px;
  line-height: 1.5;
  color: var(--text-muted);
}

/* 右侧主区域 */
.exp-main {
  position: relative;
  flex: 1;
  min-width: 0;
  background: rgba(6, 8, 18, 0.92);
}

.exp-placeholder {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.exp-frame {
  width: 100%;
  height: 100%;
  min-height: calc(100vh - 150px);
  border: none;
  background: #000;
}

.exp-exit-fullscreen {
  position: absolute;
  left: 50%;
  bottom: 20px;
  transform: translateX(-50%);
  z-index: 3;
  padding: 12px 18px;
  border: 1px solid rgba(90, 243, 211, 0.42);
  border-radius: 999px;
  background: rgba(7, 14, 26, 0.78);
  color: #dffef7;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  box-shadow: 0 16px 32px rgba(0, 0, 0, 0.28);
  backdrop-filter: blur(10px);
}

.exp-exit-fullscreen:hover {
  background: rgba(10, 20, 34, 0.92);
}

.muted {
  color: var(--text-muted);
  font-size: 14px;
}

@media (max-width: 960px) {
  .exp-page {
    flex-direction: column;
  }

  .exp-sidebar {
    width: 100%;
    border-right: none;
    border-bottom: 1px solid var(--line);
  }

  .exp-main {
    min-height: 55vh;
  }

}
</style>
