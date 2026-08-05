import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  // Capacitor 要求相对路径（从 file:// 加载本地文件）
  base: './',
  // 构建输出
  build: {
    outDir: 'dist',
    assetsDir: 'assets',
  },
  // 开发时用 /api 代理；原生 WebView 中通过 capacitor.config server.url 直连
  server: {
    port: 3000,
    open: true,
    proxy: {
      '/api': {
        target: 'http://localhost:48081',
        changeOrigin: true
      },
      '/uploads': {
        target: 'http://localhost:48081',
        changeOrigin: true
      }
    }
  }
})
