<template>
  <div class="video-landing student-font">
    <div class="video-shell">
      <div class="video-head">
        <div>
          <span class="video-kicker">Lecture video</span>
          <h1 class="video-title">讲解视频</h1>
        </div>
        <button class="video-back-btn" type="button" @click="goBackToQuestion">返回答题页</button>
      </div>

      <div v-if="decodedSrc" class="video-player-wrap">
        <video
          class="video-player"
          :src="decodedSrc"
          controls
          preload="metadata"
          playsinline
        />
      </div>

      <div v-else class="video-empty">
        未找到视频地址，请返回上一页重新打开。
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { resolveApiBaseUrl } from '@/utils/request';

const route = useRoute();
const router = useRouter();

const toPlayableVideoUrl = (url: string) => {
  const value = url.trim();
  if (!value) return '';
  if (/^https?:\/\//i.test(value)) return value;

  const normalizedPath = value.startsWith('/') ? value : `/${value}`;
  const normalizedBase = resolveApiBaseUrl().trim().replace(/\/+$/, '');

  if (normalizedBase && normalizedPath.startsWith(`${normalizedBase}/`)) {
    return normalizedPath;
  }

  if (normalizedPath.startsWith('/api/')) {
    if (/^https?:\/\//i.test(normalizedBase)) {
      try {
        return `${new URL(normalizedBase).origin}${normalizedPath}`;
      } catch {
        return normalizedPath;
      }
    }
    return normalizedPath;
  }

  return `${normalizedBase}${normalizedPath}`;
};

const decodedSrc = computed(() => {
  const raw = route.query.src;
  const value = Array.isArray(raw) ? raw[0] : raw;
  if (!value) return '';
  try {
    return toPlayableVideoUrl(decodeURIComponent(String(value)));
  } catch {
    return toPlayableVideoUrl(String(value));
  }
});

const goBackToQuestion = () => {
  const targetPath = route.path.startsWith('/preview/') ? '/preview/student/question' : '/student/question';
  router.push(targetPath);
};
</script>

<style scoped>
.video-landing {
  min-height: 100vh;
  padding: 32px 20px;
  background:
    radial-gradient(circle at top, rgba(90, 243, 211, 0.12), transparent 28%),
    linear-gradient(180deg, #070b18 0%, #0d1326 100%);
  color: #eef3ff;
}

.video-shell {
  width: min(1080px, 100%);
  margin: 0 auto;
  display: grid;
  gap: 18px;
}

.video-head,
.video-player-wrap,
.video-empty {
  border: 1px solid rgba(151, 172, 249, 0.16);
  border-radius: 24px;
  background: rgba(10, 16, 32, 0.86);
  box-shadow: 0 24px 60px rgba(0, 0, 0, 0.28);
}

.video-head {
  padding: 24px 28px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.video-kicker {
  display: inline-flex;
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(90, 243, 211, 0.12);
  color: rgba(183, 248, 235, 0.95);
  font-size: var(--font-size-xs);
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.video-title {
  margin: 14px 0 0;
  font-size: clamp(30px, 5vw, 44px);
}

.video-back-btn {
  padding: 10px 16px;
  border-radius: 14px;
  border: 1px solid rgba(90, 243, 211, 0.32);
  background: rgba(90, 243, 211, 0.08);
  color: rgba(219, 255, 247, 0.96);
  font-weight: 700;
  font-size: var(--font-size-sm);
  cursor: pointer;
  white-space: nowrap;
}

.video-player-wrap {
  padding: 18px;
}

.video-player {
  display: block;
  width: 100%;
  max-height: calc(100vh - 180px);
  border-radius: 18px;
  background: #030712;
}

.video-empty {
  padding: 28px;
  text-align: center;
  color: rgba(223, 230, 255, 0.72);
}
</style>

