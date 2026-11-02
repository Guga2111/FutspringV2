import { configDefaults, defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'
import path from 'path'

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  test: {
    environment: 'node',
    globals: true,
    // e2e/ holds the Playwright visual tests (bun run test:visual)
    exclude: [...configDefaults.exclude, 'e2e/**'],
  },
})
