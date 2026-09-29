import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Dev proxy: /api -> Spring Boot REST, /ws -> Spring Boot WebSocket.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
      '/ws': { target: 'ws://localhost:8080', ws: true, changeOrigin: true },
    },
  },
});
