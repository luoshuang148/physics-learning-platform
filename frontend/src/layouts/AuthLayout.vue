<template>
  <div class="auth-shell">
    <aside class="auth-visual" aria-label="教学管理平台介绍">
      <div class="auth-visual-brand">
        <span class="auth-visual-mark"><AppIcon name="spark" /></span>
        <div>
          <strong>教学管理平台</strong>
          <span class="auth-visual-wordmark">TEACHING & LEARNING</span>
        </div>
      </div>

      <div class="auth-visual-intro">
        <p class="auth-visual-eyebrow">连接课堂与自主学习</p>
        <h1>让每一次学习，<br /><span>都有清晰的方向。</span></h1>
      </div>

      <div class="auth-visual-art" aria-hidden="true">
        <img :src="loginBackground" alt="" width="1672" height="941" />
      </div>

      <ol class="auth-visual-journey" aria-label="教学与学习流程">
        <li><span>01</span>课堂任务</li>
        <li><span>02</span>在线练习</li>
        <li><span>03</span>学情复盘</li>
      </ol>
    </aside>

    <main class="auth-panel">
      <div class="auth-panel-inner">
        <slot />
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import AppIcon from '@/components/AppIcon.vue';
import loginBackground from '@/assets/login-physics-background.png';
import '@/assets/auth-forms.css';
</script>

<style scoped>
.auth-shell {
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(0, 1.12fr) minmax(400px, 0.88fr);
  background: #f8faf8;
}

.auth-visual {
  position: relative;
  isolation: isolate;
  min-height: max(760px, 100svh);
  display: flex;
  flex-direction: column;
  padding: 44px clamp(36px, 4.3vw, 80px) 34px;
  color: #183d35;
  background: #edf3f1;
}

.auth-visual-brand {
  display: flex;
  align-items: center;
  gap: 12px;
  position: relative;
  z-index: 1;
}

.auth-visual-mark {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border-radius: 12px;
  border: 1px solid #b5cac1;
  color: #246454;
  background: #f7faf8;
}

.auth-visual-mark :deep(svg) {
  width: 25px;
  height: 25px;
}

.auth-visual-brand strong {
  display: block;
  font-size: 16px;
  letter-spacing: 0.05em;
  font-weight: 600;
}

.auth-visual-wordmark {
  display: block;
  margin-top: 5px;
  color: #688078;
  font-size: 9px;
  letter-spacing: 0.18em;
  font-family: 'SFMono-Regular', Consolas, monospace;
}

.auth-visual-intro {
  position: relative;
  z-index: 1;
  margin-top: clamp(52px, 8vh, 100px);
}

.auth-visual-eyebrow {
  font-size: 13px;
  letter-spacing: 0.16em;
  color: #6c8278;
  margin-bottom: 21px;
}

.auth-visual-intro h1 {
  font-family: 'Songti SC', 'STSong', 'Noto Serif CJK SC', 'Source Han Serif SC', 'SimSun', serif;
  font-size: clamp(34px, 3.5vw, 58px);
  font-weight: 500;
  line-height: 1.4;
  letter-spacing: -0.025em;
}

.auth-visual-intro h1 span {
  color: #377d68;
}

.auth-visual-art {
  position: relative;
  flex: 1;
  min-height: 250px;
  margin: 12px calc(-1 * clamp(36px, 4.3vw, 80px)) 0;
  overflow: hidden;
  mask-image: linear-gradient(transparent, #000 16%, #000 85%, transparent);
}

.auth-visual-art img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: contain;
  object-position: center 62%;
  mix-blend-mode: multiply;
}

.auth-visual-journey {
  position: relative;
  z-index: 1;
  display: flex;
  justify-content: space-between;
  list-style: none;
  margin: 0;
  padding: 25px 0 0;
  border-top: 1px solid #cfddd5;
}

.auth-visual-journey li {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #435f52;
  font-size: 12px;
}

.auth-visual-journey li span {
  color: #82978c;
  font-size: 10px;
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.05em;
  font-family: 'SFMono-Regular', Consolas, monospace;
}

.auth-panel {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 32px;
  background: #fff;
  border-left: 1px solid #e2e9e3;
  min-width: 0;
}

.auth-panel-inner {
  width: min(348px, 100%);
}

@media (max-width: 960px) {
  .auth-shell {
    grid-template-columns: 1fr;
    align-content: start;
    background: #fff;
  }

  .auth-visual {
    min-height: auto;
    padding: 26px 28px 30px;
  }

  .auth-visual-intro {
    margin-top: 26px;
  }

  .auth-visual-eyebrow,
  .auth-visual-art,
  .auth-visual-journey {
    display: none;
  }

  .auth-visual-intro h1 {
    font-size: 28px;
    line-height: 1.5;
    letter-spacing: -0.03em;
  }

  .auth-visual-intro h1 br {
    display: none;
  }

  .auth-panel {
    padding: 36px 24px 48px;
    border-left: 0;
    background: #fff;
  }

}

@media (max-width: 480px) {
  .auth-visual-intro h1 {
    font-size: 23px;
  }

  .auth-visual-intro h1 br {
    display: initial;
  }
}
</style>
