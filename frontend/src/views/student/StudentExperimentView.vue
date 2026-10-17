<template>
  <AppShell :subtitle="`实验 · ${auth.user?.realName ?? ''}`" :links="links">
    <div class="exp-page">

      <transition name="exp-intro-fade">
        <div
          v-if="showIntroOverlay && activeIntroVideo"
          class="exp-intro-overlay"
          @click.self="closeIntroOverlay"
        >
          <div class="exp-intro-dialog">
            <button class="exp-intro-close" type="button" @click="closeIntroOverlay">×</button>
            <div class="exp-intro-copy">
              <span class="exp-intro-kicker">{{ introVideoKicker }}</span>
              <h3>{{ activeExperiment?.title }}</h3>
            </div>

            <video
              v-if="activeIntroVideo"
              :key="activeIntroVideo"
              ref="introVideoRef"
              class="exp-intro-video"
              :src="encodedIntroVideo"
              controls
              preload="metadata"
              playsinline
            />

            <div class="exp-intro-actions">
              <button class="neon-btn secondary" type="button" @click="skipIntroVideo">跳过视频</button>
              <button class="neon-btn primary" type="button" @click="closeIntroOverlay">关闭</button>
            </div>
          </div>
        </div>
      </transition>

      <!-- 左：实验选择栏 -->
      <div class="exp-sidebar">
        <div class="exp-sidebar-head">
          <span class="exp-series-badge" v-if="currentSeries">{{ currentSeries }}</span>
          <span class="exp-count">{{ completedCount }}/{{ experiments.length }}</span>
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
            v-if="activeIntroVideo"
            class="neon-btn secondary"
            type="button"
            @click="showIntroOverlay = true"
          >▶ 查看导引视频</button>
          <button
            v-if="activeExperiment"
            class="neon-btn secondary"
            type="button"
            @click="enterFullscreen"
          >⛶ 全屏</button>
          <button
            v-if="activeExperiment"
            class="mark-btn"
            :class="{ done: doneMap[activeExperiment.id] }"
            @click="toggleDone(activeExperiment.id)"
          >
            {{ doneMap[activeExperiment.id] ? '已标记完成' : '标记当前实验完成' }}
          </button>
          <button class="neon-btn primary" :disabled="!allDone" @click="goQuestion">完成 → 答题</button>
          <p v-if="!allDone" class="action-hint">请先将本系列全部实验标记完成后再进入答题</p>
        </div>
      </div>

      <!-- 右：实验 iframe -->
      <div class="exp-main" ref="fullscreenContainerRef">
        <div v-if="!activeExperiment" class="exp-placeholder">
          <p class="muted">{{ experiments.length ? '请从左侧选择实验' : '正在读取教师任务…' }}</p>
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
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import AppShell from '@/layouts/AppShell.vue';
import AppToast from '@/components/AppToast.vue';
import { useToast } from '@/composables/useToast';
import { useAuthStore } from '@/stores/auth';
import { getTeacherEventApi } from '@/api/teacher-event';

const EXPERIMENT_STATE_KEY_PREFIX = 'student_experiment_view_state';
const EXPERIMENT_STATE_LEGACY_KEY = 'student_experiment_view_state';

const auth = useAuthStore();
const router = useRouter();
const { visible, type, message, show } = useToast();

const getExperimentStateKey = () => `${EXPERIMENT_STATE_KEY_PREFIX}_${auth.user?.id ?? 'anonymous'}`;

const links = [
  { label: '首页', to: '/student/home' },
  { label: '错题本', to: '/student/wrong-book' },
  { label: '学习报告', to: '/student/report' },
];

const resolveStaticAssetUrl = (path: string) => {
  const base = (import.meta.env.BASE_URL || '/').replace(/\/+$/, '/');
  const normalizedPath = path.replace(/^\/+/, '');
  return `${base}${normalizedPath}`;
};

const ATOMIC_INTRO_VIDEOS: Record<string, string> = {
  alpha: resolveStaticAssetUrl('yuanziwuli/part1(split-video.com).mp4'),
  bohr: resolveStaticAssetUrl('yuanziwuli/part2(split-video.com).mp4'),
  cloud: resolveStaticAssetUrl('yuanziwuli/part3(split-video.com).mp4'),
  photoelectric: resolveStaticAssetUrl('yuanziwuli/part4(split-video.com).mp4'),
};

const SPACE_INTRO_VIDEOS: Record<string, string> = {
  kepler: resolveStaticAssetUrl('yuzhouhangtian/开普勒三大定律实验修正.mp4'),
  cosmic: resolveStaticAssetUrl('yuzhouhangtian/三大宇宙速度修正.mp4'),
  hohmann: resolveStaticAssetUrl('yuzhouhangtian/卫星变轨实验修正.mp4'),
  lightspeed: resolveStaticAssetUrl('yuzhouhangtian/光速飞船.mp4'),
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

const VALID_SERIES = Object.keys(SERIES_EXPERIMENTS) as Array<keyof typeof SERIES_EXPERIMENTS>;

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

const currentSeries = ref('');
const experiments = ref<ExperimentItem[]>([]);
const activeExperiment = ref<ExperimentItem | null>(null);
const doneMap = ref<Record<string, boolean>>({});
const experimentFrameRef = ref<HTMLIFrameElement | null>(null);
const fullscreenContainerRef = ref<HTMLElement | null>(null);
const introVideoRef = ref<HTMLVideoElement | null>(null);
const showIntroOverlay = ref(false);
const dismissedIntroIds = ref<Record<string, boolean>>({});
const currentTaskEventId = ref('');
const isFullscreen = ref(false);
const expMainWidth = ref(0);
const layoutMode = ref<ExperimentLayoutMode>('normal');

let expMainResizeObserver: ResizeObserver | null = null;
let expMainFallbackResizeHandler: (() => void) | null = null;

const completedCount = computed(() => Object.values(doneMap.value).filter(Boolean).length);
const allDone = computed(() => experiments.value.length > 0 && completedCount.value === experiments.value.length);
const activeIntroVideo = computed(() => {
  const experimentId = activeExperiment.value?.id;
  if (!experimentId) return '';
  if (currentSeries.value === '原子物理系列实验') return ATOMIC_INTRO_VIDEOS[experimentId] ?? '';
  if (currentSeries.value === '万有引力与宇宙航天系列实验') return SPACE_INTRO_VIDEOS[experimentId] ?? '';
  return '';
});
const encodedIntroVideo = computed(() => encodeURI(activeIntroVideo.value));
const introVideoKicker = computed(() => (
  currentSeries.value === '万有引力与宇宙航天系列实验' ? '宇宙航天实验导引' : '原子物理实验导引'
));

const persistExperimentState = () => {
  sessionStorage.setItem(getExperimentStateKey(), JSON.stringify({
    taskEventId: currentTaskEventId.value,
    currentSeries: currentSeries.value,
    activeExperimentId: activeExperiment.value?.id ?? null,
    doneMap: doneMap.value,
    dismissedIntroIds: dismissedIntroIds.value,
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
      taskEventId?: string;
      currentSeries?: string;
      activeExperimentId?: string | null;
      doneMap?: Record<string, boolean>;
      dismissedIntroIds?: Record<string, boolean>;
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

const maybeShowIntroVideo = (item: ExperimentItem) => {
  const introVideos = currentSeries.value === '原子物理系列实验'
    ? ATOMIC_INTRO_VIDEOS
    : currentSeries.value === '万有引力与宇宙航天系列实验'
      ? SPACE_INTRO_VIDEOS
      : null;

  if (!introVideos) {
    showIntroOverlay.value = false;
    return;
  }

  if (!introVideos[item.id] || dismissedIntroIds.value[item.id]) {
    showIntroOverlay.value = false;
    return;
  }

  showIntroOverlay.value = true;
};

const switchExperiment = (item: ExperimentItem) => {
  if (activeExperiment.value?.id === item.id) return;
  disposeCurrentExperiment();
  activeExperiment.value = item;
  maybeShowIntroVideo(item);
  persistExperimentState();
};

const closeIntroOverlay = () => {
  const experimentId = activeExperiment.value?.id;
  if (experimentId) {
    dismissedIntroIds.value[experimentId] = true;
    persistExperimentState();
  }
  showIntroOverlay.value = false;
};

const skipIntroVideo = () => {
  closeIntroOverlay();
  show('已跳过导引视频', 'info');
};

watch(showIntroOverlay, async (visible) => {
  if (!visible) {
    introVideoRef.value?.pause();
    return;
  }

  await nextTick();
  introVideoRef.value?.load();
});

watch(() => activeExperiment.value?.id, async () => {
  await nextTick();
  syncLayoutState();
});

const loadSeries = async () => {
  try {
    const task = await getTeacherEventApi();
    const title = task?.eventTitle ?? '';
    const taskEventId = String(task?.id ?? '');
    const storedState = restoreExperimentState();
    currentTaskEventId.value = taskEventId;
    currentSeries.value = title;
    if (!VALID_SERIES.includes(title as keyof typeof SERIES_EXPERIMENTS)) {
      disposeCurrentExperiment();
      experiments.value = [];
      activeExperiment.value = null;
      showIntroOverlay.value = false;
      show('当前任务不是已配置的实验系列，请先返回首页确认', 'info');
      return;
    }
    disposeCurrentExperiment();
    experiments.value = [...SERIES_EXPERIMENTS[title as keyof typeof SERIES_EXPERIMENTS]];

    const canReuseStoredState = Boolean(
      storedState
      && storedState.taskEventId
      && storedState.taskEventId === taskEventId
      && storedState.currentSeries === title
    );

    doneMap.value = canReuseStoredState ? (storedState?.doneMap ?? {}) : {};
    dismissedIntroIds.value = canReuseStoredState ? (storedState?.dismissedIntroIds ?? {}) : {};

    const restoredExperimentId = canReuseStoredState ? storedState?.activeExperimentId ?? null : null;
    activeExperiment.value = experiments.value.find((item) => item.id === restoredExperimentId) ?? experiments.value[0] ?? null;

    if (activeExperiment.value) {
      if (doneMap.value[activeExperiment.value.id]) {
        showIntroOverlay.value = false;
      } else {
        maybeShowIntroVideo(activeExperiment.value);
      }
    }

    persistExperimentState();
  } catch {
    show('读取教师任务失败，请稍后重试', 'error');
  }
};

const toggleDone = (id: string) => {
  doneMap.value[id] = !doneMap.value[id];
  if (doneMap.value[id]) {
    dismissedIntroIds.value[id] = true;
    if (activeExperiment.value?.id === id) {
      showIntroOverlay.value = false;
    }
  }
  persistExperimentState();
};

const goQuestion = async () => {
  if (!allDone.value) {
    show('请先将本系列全部实验标记完成后再开始答题', 'info');
    return;
  }

  try {
    const task = await getTeacherEventApi();
    const questionIds = task?.questionIds ?? String(task?.eventDescription ?? '')
      .replace(/^QIDS:/, '')
      .split(',')
      .map((item) => Number(item.trim()))
      .filter((id) => Number.isInteger(id) && id > 0);

    if (!task) {
      show('教师暂未发布任务，无法进入答题', 'info');
      return;
    }

    if (!questionIds.length) {
      show('当前任务未配置题目，请联系教师重新发布', 'error');
      return;
    }

    disposeCurrentExperiment();
    await router.push('/student/question');
  } catch {
    show('读取任务失败，暂时无法进入答题', 'error');
  }
};

onMounted(() => {
  document.addEventListener('fullscreenchange', syncFullscreenState);
  observeExperimentContainer();
  syncLayoutState();
  loadSeries();
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

.exp-intro-overlay {
  position: fixed;
  inset: 0;
  z-index: 80;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 28px;
  background: rgba(3, 8, 20, 0.74);
  backdrop-filter: blur(10px);
}

.exp-intro-dialog {
  position: relative;
  width: min(860px, 100%);
  display: grid;
  gap: 18px;
  padding: 22px;
  border-radius: 24px;
  border: 1px solid rgba(151, 172, 249, 0.18);
  background: linear-gradient(145deg, rgba(10, 13, 31, 0.98), rgba(7, 10, 22, 0.98));
  box-shadow: 0 28px 80px rgba(0, 0, 0, 0.42);
}

.exp-intro-close {
  position: absolute;
  top: 14px;
  right: 14px;
  width: 38px;
  height: 38px;
  border: none;
  border-radius: 999px;
  background: rgba(255,255,255,0.08);
  color: #f3f6ff;
  font-size: 24px;
  cursor: pointer;
}

.exp-intro-copy {
  display: grid;
  gap: 8px;
  padding-right: 44px;
}

.exp-intro-kicker {
  font-size: var(--font-size-sm);
  font-weight: 800;
  letter-spacing: 0.12em;
  color: rgba(90, 243, 211, 0.88);
}

.exp-intro-copy h3 {
  margin: 0;
  font-size: 28px;
  color: #f7fbff;
}

.exp-intro-copy p {
  margin: 0;
  line-height: 1.8;
  color: #b9c7ea;
  font-size: var(--font-size-sm);
}

.exp-intro-video {
  width: 100%;
  max-height: 56vh;
  border-radius: 18px;
  background: #000;
  border: 1px solid rgba(151, 172, 249, 0.12);
}

.exp-intro-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
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

.exp-intro-fade-enter-active,
.exp-intro-fade-leave-active {
  transition: opacity 0.2s ease;
}

.exp-intro-fade-enter-from,
.exp-intro-fade-leave-to {
  opacity: 0;
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

  .exp-intro-dialog {
    padding: 18px;
  }

  .exp-intro-copy h3 {
    font-size: 22px;
  }
}
</style>
