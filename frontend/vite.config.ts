import { defineConfig, createLogger } from 'vite';
import react from '@vitejs/plugin-react';

const logger = createLogger();
const originalError = logger.error.bind(logger);
logger.error = (msg, options) => {
  if (
    typeof msg === 'string' &&
    msg.includes('ws proxy socket error') &&
    (msg.includes('ECONNABORTED') || msg.includes('ECONNRESET'))
  ) {
    return;
  }
  originalError(msg, options);
};

// Dev proxy: /api -> Spring Boot REST, /ws -> Spring Boot WebSocket.
export default defineConfig({
  customLogger: logger,
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
      '/ws': { target: 'ws://localhost:8080', ws: true, changeOrigin: true },
    },
  },
});
