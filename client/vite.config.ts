import { defineConfig, type Plugin } from 'vite'
import react from '@vitejs/plugin-react'
import { VitePWA } from 'vite-plugin-pwa'
import path from 'path'
import { execSync } from 'child_process'
import { contentSecurityPolicyMeta } from './src/lib/csp'

// The build's short git sha, sent as `release` with the error reports; "dev" where git isn't available
function gitSha(): string {
  try {
    return execSync('git rev-parse --short HEAD', { stdio: ['ignore', 'pipe', 'ignore'] }).toString().trim()
  } catch {
    return 'dev'
  }
}

// Preloads the Latin Inter file (the text font of the first paint), so it starts downloading
// with the HTML instead of after the CSS is parsed. Its name is hashed, so it is found in the bundle.
function preloadInter(): Plugin {
  return {
    name: 'preload-inter',
    apply: 'build',
    transformIndexHtml(_html, ctx) {
      const file = Object.keys(ctx.bundle ?? {}).find((name) => /inter-latin-wght-normal-.*\.woff2$/.test(name))
      if (!file) return []
      return [{ tag: 'link', attrs: { rel: 'preload', href: `/${file}`, as: 'font', type: 'font/woff2', crossorigin: '' }, injectTo: 'head' }]
    },
  }
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react(),
    preloadInter(),
    // the Content-Security-Policy as a <meta> of dist/index.html, with the theme script's hash (src/lib/csp.ts)
    contentSecurityPolicyMeta(),
    // Installable app: our own service worker (src/sw.ts) with the build in precache; registered by
    // PwaUpdater, which asks before activating a new version (no reload in the middle of a form)
    VitePWA({
      strategies: 'injectManifest',
      srcDir: 'src',
      filename: 'sw.ts',
      registerType: 'prompt',
      injectRegister: false,
      manifest: {
        id: '/',
        scope: '/',
        // logged out, PrivateRoute sends it to /auth
        start_url: '/home',
        name: 'Futspring',
        short_name: 'Futspring',
        description: 'Sua pelada organizada: presença, times, resultados, ranking e financeiro.',
        display: 'standalone',
        lang: 'pt-BR',
        // the light page background (--p-offwhite-50); the theme-color meta follows the theme live
        background_color: '#F7F7F9',
        theme_color: '#F7F7F9',
        icons: [
          { src: '/pwa-192.png', sizes: '192x192', type: 'image/png' },
          { src: '/pwa-512.png', sizes: '512x512', type: 'image/png' },
          { src: '/pwa-maskable-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
        ],
      },
      injectManifest: {
        globPatterns: ['**/*.{js,css,html,woff2,webp,png,svg}'],
        // the landing photos are large and only used there; the 512 icons are only read at install time;
        // the Inter subsets other than Latin are only fetched (unicode-range) for characters pt-BR doesn't use
        globIgnores: [
          'landing/**',
          'pwa-512.png',
          'pwa-maskable-512.png',
          'assets/inter-{cyrillic,cyrillic-ext,greek,greek-ext,vietnamese,latin-ext}-*.woff2',
        ],
      },
    }),
  ],
  build: {
    // a font under 4 KB would be inlined as a data: URI, which the Content-Security-Policy's font-src ('self',
    // from default-src) refuses; fonts always stay files
    assetsInlineLimit: (file) => (/\.(woff2?|ttf|otf)$/.test(file) ? false : undefined),
  },
  define: {
    global: 'globalThis',
    __APP_VERSION__: JSON.stringify(gitSha()),
  },
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    proxy: {
      '/ws': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        ws: true,
      },
    },
  },
})
