import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  base: './', // Relative paths for file:// loading in WebView
  build: {
    outDir: '../app/src/main/assets/web',
    emptyOutDir: true,
    assetsInlineLimit: 4096,
  },
})
