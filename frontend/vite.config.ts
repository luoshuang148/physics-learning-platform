import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import { fileURLToPath, URL } from 'node:url';

const appBase = process.env.VITE_APP_BASE || (process.env.NODE_ENV === 'production' ? '/api/v1/' : '/');

export default defineConfig({
  base: appBase,
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api/v1': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
});
