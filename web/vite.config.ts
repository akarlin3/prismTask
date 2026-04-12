import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import path from 'path'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'https://averytask-production.up.railway.app',
        changeOrigin: true,
      },
    },
  },
  build: {
    sourcemap: true,
    rollupOptions: {
      output: {
        manualChunks(id: string) {
          if (id.includes('node_modules/react-dom') || id.includes('node_modules/react/') || id.includes('node_modules/react-router')) {
            return 'react-vendor';
          }
          if (id.includes('node_modules/lucide-react') || id.includes('node_modules/sonner') || id.includes('node_modules/recharts')) {
            return 'ui-vendor';
          }
          if (id.includes('node_modules/@dnd-kit')) {
            return 'dnd-vendor';
          }
          if (id.includes('node_modules/date-fns')) {
            return 'date-vendor';
          }
          if (id.includes('node_modules/react-hook-form') || id.includes('node_modules/@hookform') || id.includes('node_modules/zod')) {
            return 'form-vendor';
          }
        },
      },
    },
  },
})
