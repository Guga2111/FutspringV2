import { configDefaults, defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'
import path from 'path'

export default defineConfig({
  plugins: [react()],
  define: {
    __APP_VERSION__: JSON.stringify('test'),
  },
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  test: {
    environment: 'node',
    globals: true,
    // zod's jitless mode, as in main.tsx: the schema tests run the same parser as the app
    setupFiles: ['src/lib/zodConfig.ts'],
    // lets src/lib/contrast.test.ts read the design tokens with `@/index.css?raw`
    css: { include: [/index\.css/] },
    // e2e/ holds the Playwright visual tests (bun run test:visual)
    exclude: [...configDefaults.exclude, 'e2e/**'],
  },
})
