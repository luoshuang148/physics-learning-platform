<template>
  <AppShell :subtitle="`视频管理 · ${auth.user?.realName ?? ''}`" :links="links">
    <div class="video-page ops-typography">
      <section class="video-hero">
        <div class="video-hero-copy">
          <span class="hero-kicker">Media Library</span>
          <h2 class="hero-title">视频管理</h2>
          <p class="hero-desc">将新增、编辑与检索整合为一个内容库界面，帮助教师从知识点视角整理教学视频资源。</p>
        </div>
        <div class="hero-stats">
          <div class="hero-stat-card accent-cyan">
            <span class="hero-stat-num">{{ videos.length }}</span>
            <span class="hero-stat-label">视频总数</span>
          </div>
          <div class="hero-stat-card accent-amber">
            <span class="hero-stat-num">{{ linkedVideoCount }}</span>
            <span class="hero-stat-label">已关联题目</span>
          </div>
          <div class="hero-stat-card accent-rose">
            <span class="hero-stat-num">{{ totalDurationText }}</span>
            <span class="hero-stat-label">总时长</span>
          </div>
        </div>
      </section>

      <section class="video-main">
        <div class="video-form-panel">
          <div class="panel-head">
            <div>
              <div class="panel-kicker">Editor</div>
              <h3 class="panel-title">{{ editingVideo ? `编辑视频 #${editingVideo.id}` : '新增视频' }}</h3>
            </div>
            <button v-if="editingVideo" class="ghost-btn" @click="resetForm">取消编辑</button>
          </div>

          <div class="form-grid">
            <label class="field-block wide">
              <span class="field-label">视频标题</span>
              <input v-model="form.title" class="field-input" placeholder="例如：霍曼转移轨道讲解" />
            </label>

            <label class="field-block wide">
              <span class="field-label">视频文件</span>
              <div class="file-upload-area" :class="{ 'is-uploaded': Boolean(form.url), 'is-uploading': uploading }">
                <input type="file" accept="video/*" class="file-input" @change="handleFileUpload" />
                <div class="file-upload-content">
                  <div v-if="uploading" class="uploading">
                    <div class="upload-spinner"></div>
                    <span>上传中...</span>
                  </div>
                  <div v-else-if="uploadSuccess" class="file-info">
                    <div class="file-status-copy">
                      <span class="file-status-badge">上传成功</span>
                      <span class="file-name">{{ uploadedFileName || '已选择视频文件' }}</span>
                      <span class="file-status-hint">文件已上传成功，如需更换可重新选择或拖拽新视频文件</span>
                    </div>
                    <button class="remove-file" type="button" @click="removeFile">✕</button>
                  </div>
                  <div v-else class="upload-placeholder">
                    <span class="upload-icon">📹</span>
                    <span>点击或拖拽视频文件到此处</span>
                    <span class="upload-hint">仅支持 MP4 格式</span>
                  </div>
                </div>
              </div>
            </label>

            <label class="field-block">
              <span class="field-label">知识点 <em class="field-optional">选填</em></span>
              <input v-model="form.knowledgePoint" class="field-input" placeholder="可不填，例如：开普勒定律" />
            </label>

            <label class="field-block">
              <span class="field-label">时长（秒）</span>
              <input v-model.number="form.duration" type="number" min="0" class="field-input" placeholder="上传后自动填充，也可手动调整" />
            </label>

            <label class="field-block wide">
              <span class="field-label">描述 <em class="field-optional">选填</em></span>
              <textarea v-model="form.description" class="field-textarea" rows="4" placeholder="可不填，简要说明这个视频适合讲什么、配合哪类题目使用" />
            </label>
          </div>

          <div class="form-actions">
            <button class="ghost-btn" @click="resetForm">清空</button>
            <button class="primary-btn" :disabled="submitting || !canSubmit" @click="submitVideo">
              {{ submitting ? '保存中…' : editingVideo ? '保存修改' : '创建视频' }}
            </button>
          </div>
        </div>

        <div class="video-list-panel">
          <div class="panel-head list-head">
            <div>
              <div class="panel-kicker">Library</div>
              <h3 class="panel-title">视频列表</h3>
            </div>
            <div class="list-tools">
              <input v-model="searchText" class="search-input" placeholder="搜索标题 / 知识点…" />
              <button class="ghost-btn" :disabled="loading" @click="load">刷新</button>
            </div>
          </div>

          <div v-if="loading" class="panel-empty">正在加载视频列表…</div>
          <div v-else-if="filteredVideos.length === 0" class="panel-empty">暂无匹配视频</div>
          <div v-else class="video-list">
            <article v-for="video in filteredVideos" :key="video.id" class="video-card">
              <div class="video-card-cover" :style="coverStyle(video)">
                <div class="cover-overlay" />
                <span class="cover-duration">{{ formatDuration(video.duration) }}</span>
                <span class="cover-chip">#{{ video.id }}</span>
              </div>

              <div class="video-card-body">
                <div class="video-card-top">
                  <div>
                    <h4 class="video-title">{{ video.title }}</h4>
                    <span class="video-knowledge">{{ video.knowledgePoint || '未标记知识点' }}</span>
                  </div>
                  <span class="meta-pill ghost">{{ formatDate(video.updatedAt) }}</span>
                </div>

                <p class="video-desc">{{ video.description || '暂无描述，建议补充适用场景与教学价值。' }}</p>

                <div class="video-meta-row">
                  <span class="meta-pill">时长 {{ formatDuration(video.duration) }}</span>
                  <span class="meta-pill">资源类型 教学视频</span>
                </div>

                <div class="video-actions">
                  <a :href="toPlayableVideoUrl(video.url)" target="_blank" rel="noreferrer" class="open-link">打开链接 ↗</a>
                  <div class="action-group">
                    <button class="mini-btn" @click="editVideo(video)">编辑</button>
                    <button class="mini-btn danger" @click="removeVideo(video.id)">删除</button>
                  </div>
                </div>
              </div>
            </article>
          </div>
        </div>
      </section>
    </div>

    <AppToast :visible="visible" :type="type" :message="message" />
  </AppShell>
</template>

<script setup lang="ts">
import axios from 'axios';
import { computed, onMounted, reactive, ref } from 'vue';
import AppShell from '@/layouts/AppShell.vue';
import AppToast from '@/components/AppToast.vue';
import { useToast } from '@/composables/useToast';
import { useAuthStore } from '@/stores/auth';
import { teacherNavLinks } from '@/constants/navigation';
import { resolveApiBaseUrl } from '@/utils/request';
import { createVideoApi, deleteVideoApi, getAllVideosApi, updateVideoApi } from '@/api/video';
import type { VideoCreatePayload, VideoResponse, VideoUploadResponse } from '@/types/video';
import type { ApiResponse } from '@/types/common';

const auth = useAuthStore();
const { visible, type, message, show } = useToast();

const links = teacherNavLinks;

const loading = ref(false);
const submitting = ref(false);
const uploading = ref(false);
const uploadSuccess = ref(false);
const searchText = ref('');
const videos = ref<VideoResponse[]>([]);
const editingVideo = ref<VideoResponse | null>(null);
const uploadedFileName = ref('');

const form = reactive<VideoCreatePayload>({
  title: '',
  url: '',
  knowledgePoint: '',
  coverUrl: '',
  duration: undefined,
  description: '',
});

const canSubmit = computed(() => form.title.trim() && form.url.trim());

const filteredVideos = computed(() => {
  const keyword = searchText.value.trim().toLowerCase();
  if (!keyword) return videos.value;
  return videos.value.filter((video) =>
    video.title.toLowerCase().includes(keyword)
    || (video.knowledgePoint ?? '').toLowerCase().includes(keyword)
    || (video.description ?? '').toLowerCase().includes(keyword)
  );
});

const linkedVideoCount = computed(() => videos.value.filter(video => Boolean(video.knowledgePoint?.trim())).length);
const totalDurationText = computed(() => formatDuration(videos.value.reduce((sum, video) => sum + (video.duration ?? 0), 0)));

const formatDuration = (seconds: number | null | undefined) => {
  const total = Math.max(0, Math.floor(Number(seconds ?? 0)));
  if (total < 60) return `${total}s`;
  const minute = Math.floor(total / 60);
  const second = total % 60;
  return second === 0 ? `${minute}min` : `${minute}min ${second}s`;
};

const formatDate = (date: string) => new Date(date).toLocaleString('zh-CN', {
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
});

const coverStyle = (video: VideoResponse) => {
  const cover = video.coverUrl?.trim();
  if (cover) {
    return {
      backgroundImage: `linear-gradient(135deg, rgba(5,8,25,0.28), rgba(17,25,56,0.72)), url(${cover})`,
    };
  }
  return {
    backgroundImage: 'radial-gradient(circle at top left, rgba(253,163,72,0.65), transparent 35%), linear-gradient(135deg, rgba(32,43,82,0.95), rgba(9,13,26,0.98))',
  };
};

const directUploadBaseUrl = computed(() => {
  return resolveApiBaseUrl();
});

const toPlayableVideoUrl = (url: string) => {
  const value = url.trim();
  if (!value) return '';
  if (/^https?:\/\//i.test(value)) return value;

  const normalizedPath = value.startsWith('/') ? value : `/${value}`;
  const normalizedBase = directUploadBaseUrl.value.trim().replace(/\/+$/, '');

  // 已经是带 API 前缀的相对路径时，不再重复拼接
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

const resetForm = () => {
  editingVideo.value = null;
  form.title = '';
  form.url = '';
  form.knowledgePoint = '';
  form.coverUrl = '';
  form.duration = undefined;
  form.description = '';
  uploadedFileName.value = '';
  uploading.value = false;
  uploadSuccess.value = false;
};

const fillForm = (video: VideoResponse) => {
  form.title = video.title;
  // 表单提交使用后端存储格式，避免将播放地址写入数据库
  form.url = video.url;
  form.knowledgePoint = video.knowledgePoint ?? '';
  form.coverUrl = video.coverUrl ?? '';
  form.duration = video.duration ?? undefined;
  form.description = video.description ?? '';
  uploadedFileName.value = video.title; // 使用视频标题作为文件名显示
  uploading.value = false;
  uploadSuccess.value = Boolean(video.url);
};

const readVideoDuration = (file: File): Promise<number | undefined> => new Promise((resolve) => {
  const objectUrl = URL.createObjectURL(file);
  const video = document.createElement('video');
  video.preload = 'metadata';

  const cleanup = () => {
    URL.revokeObjectURL(objectUrl);
    video.removeAttribute('src');
    video.load();
  };

  video.onloadedmetadata = () => {
    const duration = Number.isFinite(video.duration) ? Math.max(0, Math.round(video.duration)) : undefined;
    cleanup();
    resolve(duration);
  };

  video.onerror = () => {
    cleanup();
    resolve(undefined);
  };

  video.src = objectUrl;
});

const handleFileUpload = async (event: Event) => {
  const target = event.target as HTMLInputElement;
  const file = target.files?.[0];
  if (!file) return;

  uploading.value = true;
  uploadSuccess.value = false;
  uploadedFileName.value = file.name;

  // 提取文件名作为默认标题（移除扩展名）
  const fileNameWithoutExt = file.name.replace(/\.[^/.]+$/, '');
  if (!form.title.trim()) {
    form.title = fileNameWithoutExt;
  }

  try {
    const formData = new FormData();
    formData.append('file', file);

    const { data } = await axios.post<ApiResponse<VideoUploadResponse>>(
      `${directUploadBaseUrl.value}/video/upload`,
      formData,
      {
        headers: {
          Authorization: auth.token ? `Bearer ${auth.token}` : '',
          'X-Session-Id': sessionStorage.getItem('zhixue_session_id') ?? '',
        },
        timeout: 30000,
      }
    );

    // 上传接口返回的是后端可存储路径（通常为 /videos/...）
    form.url = data.data.url;
    form.duration = data.data.duration ?? await readVideoDuration(file);
    uploadSuccess.value = true;
    show('视频上传成功！标题和时长已自动填充', 'success');
  } catch (error: any) {
    const detail = error?.response?.data?.message ?? error?.message ?? '上传失败';
    show(`视频上传失败：${detail}`, 'error');
    uploadedFileName.value = '';
    form.url = '';
    uploadSuccess.value = false;
  } finally {
    uploading.value = false;
  }
};

const removeFile = () => {
  form.url = '';
  uploadedFileName.value = '';
  uploadSuccess.value = false;
};

const load = async () => {
  loading.value = true;
  try {
    videos.value = await getAllVideosApi();
  } catch (error: any) {
    show(error?.response?.data?.message ?? '视频列表加载失败', 'error');
  } finally {
    loading.value = false;
  }
};

const submitVideo = async () => {
  if (!canSubmit.value) {
    show('请至少填写视频标题和视频链接', 'info');
    return;
  }

  submitting.value = true;
  try {
    const payload: VideoCreatePayload = {
      title: form.title.trim(),
      url: form.url.trim(),
      knowledgePoint: form.knowledgePoint?.trim() || undefined,
      coverUrl: undefined, // 移除封面链接
      duration: form.duration,
      description: form.description?.trim() || undefined,
    };

    if (editingVideo.value) {
      await updateVideoApi(editingVideo.value.id, payload);
      show(`视频 #${editingVideo.value.id} 已更新`, 'success');
    } else {
      await createVideoApi(payload);
      show('视频创建成功', 'success');
    }

    await load();
    resetForm();
  } catch (error: any) {
    show(error?.response?.data?.message ?? '保存视频失败', 'error');
  } finally {
    submitting.value = false;
  }
};

const editVideo = (video: VideoResponse) => {
  editingVideo.value = video;
  fillForm(video);
};

const removeVideo = async (id: number) => {
  try {
    await deleteVideoApi(id);
    if (editingVideo.value?.id === id) resetForm();
    await load();
    show(`视频 #${id} 已删除`, 'success');
  } catch (error: any) {
    show(error?.response?.data?.message ?? '删除视频失败', 'error');
  }
};

onMounted(load);
</script>

<style scoped>
.video-page {
  display: grid;
  gap: 18px;
}

.ops-typography {
  font-family: 'Georgia', 'Times New Roman', 'Songti SC', 'STSong', serif;
}

.video-hero {
  display: grid;
  grid-template-columns: 1.35fr 1fr;
  gap: 16px;
  padding: 24px;
  border-radius: 28px;
  border: 1px solid rgba(130, 150, 255, 0.16);
  background:
    radial-gradient(circle at top right, rgba(253,163,72,0.16), transparent 28%),
    radial-gradient(circle at left bottom, rgba(90,243,211,0.13), transparent 30%),
    linear-gradient(145deg, rgba(12,18,38,0.98), rgba(8,10,24,0.98));
  box-shadow: 0 24px 70px rgba(5, 8, 22, 0.35);
}

.video-hero-copy {
  display: grid;
  gap: 12px;
  align-content: center;
}

.hero-kicker,
.panel-kicker {
  font-size: 11px;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: rgba(190, 201, 234, 0.6);
}

.hero-title {
  margin: 0;
  font-size: 34px;
  line-height: 1.1;
  color: #f7fbff;
}

.hero-desc {
  margin: 0;
  max-width: 720px;
  font-size: 14px;
  line-height: 1.9;
  color: #b9c7ea;
}

.hero-stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.hero-stat-card {
  position: relative;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  min-height: 122px;
  padding: 18px;
  border-radius: 20px;
  border: 1px solid rgba(255,255,255,0.08);
  background: rgba(255,255,255,0.04);
}

.hero-stat-card::after {
  content: '';
  position: absolute;
  inset: auto -20% -55% auto;
  width: 110px;
  height: 110px;
  border-radius: 50%;
  opacity: 0.45;
}

.accent-cyan::after { background: rgba(90,243,211,0.45); }
.accent-amber::after { background: rgba(253,163,72,0.45); }
.accent-rose::after { background: rgba(255,107,145,0.42); }

.hero-stat-num {
  position: relative;
  z-index: 1;
  font-size: 30px;
  font-weight: 800;
  color: #ffffff;
}

.hero-stat-label {
  position: relative;
  z-index: 1;
  font-size: 12px;
  color: #b8c6eb;
}

.video-main {
  display: grid;
  grid-template-columns: 380px 1fr;
  gap: 16px;
  min-height: 560px;
}

.video-form-panel,
.video-list-panel {
  border-radius: 24px;
  border: 1px solid rgba(130, 150, 255, 0.14);
  background: linear-gradient(180deg, rgba(11,15,30,0.98), rgba(8,10,22,0.98));
  box-shadow: 0 16px 50px rgba(6,8,20,0.28);
}

.video-form-panel { padding: 18px; }

.video-list-panel {
  padding: 18px;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.panel-title {
  margin: 6px 0 0;
  font-size: 22px;
  color: #f2f6ff;
}

.list-head { align-items: center; }

.list-tools {
  display: flex;
  align-items: center;
  gap: 10px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.field-block {
  display: grid;
  gap: 8px;
}

.field-block.wide { grid-column: 1 / -1; }

.field-label {
  font-size: 12px;
  font-weight: 700;
  color: #c4d0f0;
}

.field-optional {
  margin-left: 6px;
  font-style: normal;
  font-weight: 600;
  color: #8fa0d2;
}

.field-input,
.field-textarea,
.search-input {
  width: 100%;
  border: 1px solid rgba(130,150,255,0.18);
  border-radius: 14px;
  background: rgba(255,255,255,0.04);
  color: #eef4ff;
  padding: 12px 14px;
  outline: none;
  transition: border-color 0.2s, box-shadow 0.2s, background 0.2s;
}

.field-input:focus,
.field-textarea:focus,
.search-input:focus {
  border-color: rgba(90,243,211,0.42);
  box-shadow: 0 0 0 3px rgba(90,243,211,0.12);
  background: rgba(255,255,255,0.06);
}

.field-textarea { resize: vertical; }
.search-input { min-width: 220px; }

.file-upload-area {
  position: relative;
  border: 2px dashed rgba(130,150,255,0.2);
  border-radius: 14px;
  background: rgba(255,255,255,0.03);
  transition: all 0.2s;
  overflow: hidden;
}

.file-upload-area.is-uploaded {
  border-color: rgba(90,243,211,0.48);
  background: linear-gradient(135deg, rgba(90,243,211,0.12), rgba(90,243,211,0.04));
}

.file-upload-area.is-uploading {
  border-color: rgba(90,243,211,0.4);
}

.file-upload-area:hover {
  border-color: rgba(90,243,211,0.4);
  background: rgba(255,255,255,0.05);
}

.file-input {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  opacity: 0;
  cursor: pointer;
  z-index: 2;
}

.file-upload-content {
  position: relative;
  z-index: 1;
  padding: 30px;
  text-align: center;
  min-height: 120px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.upload-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.upload-icon {
  font-size: 32px;
  margin-bottom: 8px;
}

.upload-placeholder span {
  color: #c4d0f0;
  font-size: 14px;
}

.upload-hint {
  font-size: 12px !important;
  color: #8fa0d2 !important;
}

.file-info {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  width: 100%;
  padding: 14px 16px;
  background: rgba(90,243,211,0.1);
  border-radius: 10px;
  border: 1px solid rgba(90,243,211,0.2);
}

.file-status-copy {
  display: grid;
  gap: 6px;
  text-align: left;
  min-width: 0;
}

.file-status-badge {
  display: inline-flex;
  align-items: center;
  width: fit-content;
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(90,243,211,0.16);
  color: #8df5de;
  font-size: 12px;
  font-weight: 700;
}

.file-status-hint {
  color: #a7badf;
  font-size: 12px;
  line-height: 1.6;
}

.file-name {
  color: #8df5de;
  font-size: 14px;
  flex: 1;
  text-align: left;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.remove-file {
  background: none;
  border: none;
  color: #ff98b6;
  font-size: 18px;
  cursor: pointer;
  padding: 4px;
  border-radius: 4px;
  transition: all 0.2s;
}

.remove-file:hover {
  background: rgba(255,107,145,0.1);
  color: #ff6b91;
}

.uploading {
  display: flex;
  align-items: center;
  gap: 12px;
  color: #c4d0f0;
}

.upload-spinner {
  width: 20px;
  height: 20px;
  border: 2px solid rgba(90,243,211,0.3);
  border-top: 2px solid rgba(90,243,211,0.8);
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 18px;
}

.ghost-btn,
.primary-btn,
.mini-btn,
.open-link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  font-weight: 700;
  transition: all 0.2s;
}

.ghost-btn,
.mini-btn {
  border: 1px solid rgba(130,150,255,0.16);
  background: rgba(255,255,255,0.03);
  color: #c7d4f3;
  cursor: pointer;
}

.ghost-btn {
  height: 40px;
  padding: 0 14px;
}

.primary-btn {
  height: 42px;
  padding: 0 18px;
  border: 1px solid rgba(90,243,211,0.46);
  background: linear-gradient(120deg, rgba(90,243,211,0.92), rgba(124,245,221,0.88));
  color: #061016;
  cursor: pointer;
  box-shadow: 0 8px 22px rgba(90,243,211,0.2);
}

.primary-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
  box-shadow: none;
}

.video-list {
  overflow: auto;
  display: grid;
  gap: 14px;
  padding-right: 2px;
}

.panel-empty {
  min-height: 240px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px dashed rgba(130,150,255,0.14);
  border-radius: 18px;
  color: #91a3d8;
}

.video-card {
  display: grid;
  grid-template-columns: 220px 1fr;
  gap: 0;
  overflow: hidden;
  border-radius: 22px;
  border: 1px solid rgba(130,150,255,0.12);
  background: rgba(255,255,255,0.03);
}

.video-card-cover {
  position: relative;
  min-height: 176px;
  background-size: cover;
  background-position: center;
}

.cover-overlay {
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, rgba(0,0,0,0.05), rgba(0,0,0,0.45));
}

.cover-duration,
.cover-chip {
  position: absolute;
  z-index: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
  backdrop-filter: blur(6px);
}

.cover-duration {
  right: 12px;
  bottom: 12px;
  background: rgba(10,14,26,0.72);
  color: #f8fbff;
}

.cover-chip {
  left: 12px;
  top: 12px;
  background: rgba(90,243,211,0.16);
  color: #8ff8e0;
  border: 1px solid rgba(90,243,211,0.24);
}

.video-card-body {
  padding: 18px;
  display: grid;
  gap: 14px;
}

.video-card-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.video-title {
  margin: 0 0 10px;
  font-size: 22px;
  color: #f3f7ff;
}

.video-knowledge {
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(253,163,72,0.12);
  color: #ffd39b;
  font-size: 11px;
  white-space: nowrap;
}

.video-desc {
  margin: 0;
  font-size: 13px;
  line-height: 1.85;
  color: #b5c2e5;
}

.video-meta-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.meta-pill {
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(130,150,255,0.1);
  color: #c7d4f4;
  font-size: 11px;
}

.meta-pill.ghost {
  background: rgba(255,255,255,0.04);
  color: #8fa0d2;
}

.video-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.open-link {
  height: 38px;
  padding: 0 14px;
  border: 1px solid rgba(90,243,211,0.28);
  background: rgba(90,243,211,0.08);
  color: #8df5de;
  text-decoration: none;
}

.action-group {
  display: flex;
  gap: 8px;
}

.mini-btn {
  height: 36px;
  padding: 0 14px;
}

.mini-btn.danger {
  border-color: rgba(255,107,145,0.2);
  color: #ff98b6;
}

@media (max-width: 1180px) {
  .video-hero,
  .video-main {
    grid-template-columns: 1fr;
  }

  .hero-stats {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .hero-title {
    font-size: 26px;
  }

  .hero-stats,
  .form-grid,
  .video-card {
    grid-template-columns: 1fr;
  }

  .search-input {
    max-width: 100%;
  }

  .video-actions,
  .panel-head,
  .list-head,
  .video-card-top,
  .list-tools {
    flex-direction: column;
    align-items: stretch;
  }

  .action-group,
  .form-actions {
    width: 100%;
  }

  .mini-btn,
  .open-link,
  .ghost-btn,
  .primary-btn {
    width: 100%;
  }
}
</style>
