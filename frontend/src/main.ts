import { createApp } from 'vue';
import { createPinia } from 'pinia';
import App from './App.vue';
import router from './router';
import './style.css';
import 'katex/dist/katex.min.css';

const FONT_SIZE_STORAGE_KEY = 'zxwl-font-scale-v2';
const FONT_SIZE_MIN = 13;
const FONT_SIZE_MAX = 18;
const DEFAULT_FONT_SIZE = 15;

const normalizeFontSize = (value: number) => Math.min(FONT_SIZE_MAX, Math.max(FONT_SIZE_MIN, value));

const savedFontSize = Number(window.localStorage.getItem(FONT_SIZE_STORAGE_KEY));
const initialFontSize = Number.isFinite(savedFontSize) ? normalizeFontSize(savedFontSize) : DEFAULT_FONT_SIZE;
document.documentElement.style.setProperty('--app-font-size', `${initialFontSize}px`);

const app = createApp(App);

app.use(createPinia());
app.use(router);

app.mount('#app');
