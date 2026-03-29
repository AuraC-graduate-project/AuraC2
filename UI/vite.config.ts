import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    proxy: {
      // auth endpoints (login, refresh, logout)
      '/auth': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
      },

      // protected API endpoints
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
      },
    },
  },
})
