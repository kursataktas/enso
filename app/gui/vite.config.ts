import react from '@vitejs/plugin-react'
import vue from '@vitejs/plugin-vue'
import { COOP_COEP_CORP_HEADERS } from 'enso-common'
import { fileURLToPath } from 'node:url'
import postcssNesting from 'postcss-nesting'
import tailwindcss from 'tailwindcss'
import tailwindcssNesting from 'tailwindcss/nesting'
import { defineConfig, type Plugin } from 'vite'
import VueDevTools from 'vite-plugin-vue-devtools'
import wasm from 'vite-plugin-wasm'
import tailwindConfig from './tailwind.config'

const isE2E = process.env.E2E === 'true'
const dynHostnameWsUrl = (port: number) => JSON.stringify(`ws://__HOSTNAME__:${port}`)
const entrypoint = isE2E ? './src/project-view/e2e-entrypoint.ts' : './src/entrypoint.ts'

process.env.VITE_DEV_PROJECT_MANAGER_URL ??= dynHostnameWsUrl(isE2E ? 30536 : 30535)
process.env.VITE_YDOC_SERVER_URL ??=
  process.env.ENSO_POLYGLOT_YDOC_SERVER ? JSON.stringify(process.env.ENSO_POLYGLOT_YDOC_SERVER)
  : process.env.NODE_ENV === 'development' ? dynHostnameWsUrl(5976)
  : undefined

// https://vitejs.dev/config/
export default defineConfig({
  cacheDir: fileURLToPath(new URL('../../node_modules/.cache/vite', import.meta.url)),
  plugins: [
    wasm(),
    ...(process.env.NODE_ENV === 'development' ?
      [
        await VueDevTools(),
        react({
          include: fileURLToPath(new URL('../dashboard/**/*.tsx', import.meta.url)),
          babel: { plugins: ['@babel/plugin-syntax-import-attributes'] },
        }),
      ]
    : []),
    vue({
      customElement: ['**/components/visualizations/**', '**/components/shared/**'],
      template: {
        compilerOptions: {
          isCustomElement: (tag) => tag.startsWith('enso-'),
        },
      },
    }),
    react({
      include: fileURLToPath(new URL('./src/dashboard/**/*.tsx', import.meta.url)),
      babel: { plugins: ['@babel/plugin-syntax-import-attributes'] },
    }),
    ...(process.env.NODE_ENV === 'development' ? [await projectManagerShim()] : []),
  ],
  optimizeDeps: {
    entries: fileURLToPath(new URL('./index.html', import.meta.url)),
  },
  server: {
    headers: Object.fromEntries(COOP_COEP_CORP_HEADERS),
    ...(process.env.GUI_HOSTNAME ? { host: process.env.GUI_HOSTNAME } : {}),
  },
  resolve: {
    conditions: process.env.NODE_ENV === 'development' ? ['source'] : [],
    alias: {
      '/src/entrypoint.ts': fileURLToPath(new URL(entrypoint, import.meta.url)),
      shared: fileURLToPath(new URL('./shared', import.meta.url)),
      '@': fileURLToPath(new URL('./src/project-view', import.meta.url)),
      '#': fileURLToPath(new URL('./src/dashboard', import.meta.url)),
    },
  },
  define: {
    // Single hardcoded usage of `global` in aws-amplify.
    'global.TYPED_ARRAY_SUPPORT': true,
  },
  esbuild: {
    dropLabels: process.env.NODE_ENV === 'development' ? [] : ['DEV'],
    supported: {
      'top-level-await': true,
    },
  },
  assetsInclude: ['**/*.svg'],
  css: {
    postcss: {
      plugins: [tailwindcssNesting(postcssNesting()), tailwindcss(tailwindConfig)],
    },
  },
  logLevel: 'info',
  build: {
    // dashboard chunk size is larger than the default warning limit
    chunkSizeWarningLimit: 700,
    rollupOptions: {
      output: {
        manualChunks: {
          config: ['./src/config'],
        },
      },
    },
  },
})
async function projectManagerShim(): Promise<Plugin> {
  const module = await import('./project-manager-shim-middleware')
  return {
    name: 'project-manager-shim',
    configureServer(server) {
      server.middlewares.use(module.default)
    },
    configurePreviewServer(server) {
      server.middlewares.use(module.default)
    },
  }
}
