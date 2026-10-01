import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

// Where `npm run dev` proxies /api to (the backend already serves everything under /api).
const apiProxyTarget = process.env.API_PROXY_TARGET ?? 'http://localhost:8080';

export default defineConfig({
  plugins: [react()],
  define: {
    // Set by the Docker build from the release tag (APP_VERSION); "dev" for local builds.
    __APP_VERSION__: JSON.stringify(process.env.APP_VERSION || 'dev'),
  },
  build: {
    rolldownOptions: {
      output: {
        // Libraries change less often than app code: separate chunks stay cached between releases.
        codeSplitting: {
          groups: [
            { name: 'mantine', test: /node_modules[\\/]@mantine/ },
            { name: 'echarts', test: /node_modules[\\/](echarts|zrender)/ },
            { name: 'vendor', test: /node_modules/ },
          ],
        },
      },
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': apiProxyTarget,
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    // Tests fill whole forms key by key: the default 5 s is short on a busy CI runner.
    testTimeout: 20_000,
  },
});
