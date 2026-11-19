import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import {readFileSync} from 'fs';
import path from 'path';
import {defineConfig} from 'vite';
import {VitePWA} from 'vite-plugin-pwa';

// Versão única do sistema (tela de login etc.): vem do package.json
const APP_VERSION: string = JSON.parse(readFileSync(path.resolve(__dirname, 'package.json'), 'utf8')).version;

/**
 * APP JVM SPDA (vite build --mode spda): a mesma plataforma só com o login e o módulo SPDA,
 * embutida no app Android (android-spda/). Arquivos com caminho relativo, sem PWA.
 */
export default defineConfig(({ mode }) => {
  const spdaApp = mode === 'spda';
  return {
    base: spdaApp ? './' : '/',
    define: {
      __APP_VERSION__: JSON.stringify(APP_VERSION),
      // qual app está sendo gerado (src/appTarget.ts)
      __APP_TARGET__: JSON.stringify(spdaApp ? 'spda' : 'plataforma'),
    },
    plugins: [
      react(),
      tailwindcss(),
      // APP JVM SPDA: nome do app na página
      spdaApp && {
        name: 'jvm-spda-app-html',
        transformIndexHtml: (html: string) => html
          .replace(/<title>[^<]*<\/title>/, '<title>JVM SPDA — Inspeção e ensaios de SPDA</title>')
          .replace('content="JVM Ensaios"', 'content="JVM SPDA"')
          .replace('content="JVM Ensaios Dielétricos"', 'content="JVM SPDA"')
      },
      /**
       * PWA (só na plataforma web): o app pode ser instalado pelo navegador (Android e iPhone) e abre
       * SEM internet. Todos os arquivos do sistema ficam guardados no aparelho;
       * os dados ficam na fila local e são enviados ao Supabase quando houver conexão.
       */
      !spdaApp && VitePWA({
        registerType: 'autoUpdate',
        injectRegister: 'auto',
        includeAssets: ['favicon.png', 'apple-touch-icon.png', 'icon-192.png', 'icon-512.png', 'icon-maskable-512.png'],
        manifest: {
          id: '/',
          name: 'JVM Ensaios Dielétricos - EPI/EPC',
          short_name: 'JVM Ensaios',
          description: 'Ensaios dielétricos, laudos técnicos e certificados NR-10 — funciona offline e sincroniza com a plataforma.',
          lang: 'pt-BR',
          start_url: '/',
          scope: '/',
          display: 'standalone',
          orientation: 'any',
          background_color: '#EEF1F4',
          theme_color: '#0A2540',
          icons: [
            { src: '/icon-192.png', sizes: '192x192', type: 'image/png', purpose: 'any' },
            { src: '/icon-512.png', sizes: '512x512', type: 'image/png', purpose: 'any' },
            { src: '/icon-maskable-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' }
          ],
          shortcuts: [
            { name: 'Novo Ensaio Dielétrico', short_name: 'Novo Ensaio', url: '/?action=new_test', icons: [{ src: '/icon-192.png', sizes: '192x192' }] },
            { name: 'Escanear QR Tag', short_name: 'Escanear', url: '/?action=scan_qr', icons: [{ src: '/icon-192.png', sizes: '192x192' }] },
            { name: 'Consultar Laudos', short_name: 'Laudos', url: '/?action=tests', icons: [{ src: '/icon-192.png', sizes: '192x192' }] }
          ]
        },
        workbox: {
          // Guarda TODO o app no aparelho na primeira visita (inclui telas usadas depois)
          globPatterns: ['**/*.{js,css,html,png,svg,ico,woff,woff2,webmanifest}'],
          maximumFileSizeToCacheInBytes: 8 * 1024 * 1024,
          navigateFallback: '/index.html',
          // validador: sempre pela rede, para receber os cabeçalhos que liberam o Wix;
          // retorno do certificado em nuvem: página própria, nunca o app
          navigateFallbackDenylist: [/^\/validar/i, /^\/validador/i, /^\/psc-retorno/i],
          cleanupOutdatedCaches: true,
          clientsClaim: true,
          skipWaiting: true,
          runtimeCaching: [
            {
              urlPattern: /^https:\/\/fonts\.googleapis\.com\/.*/i,
              handler: 'StaleWhileRevalidate',
              options: { cacheName: 'google-fonts-css' }
            },
            {
              urlPattern: /^https:\/\/fonts\.gstatic\.com\/.*/i,
              handler: 'CacheFirst',
              options: {
                cacheName: 'google-fonts-files',
                expiration: { maxEntries: 30, maxAgeSeconds: 60 * 60 * 24 * 365 },
                cacheableResponse: { statuses: [0, 200] }
              }
            },
            {
              // Fotos dos ensaios no Supabase Storage: disponíveis offline depois de vistas
              urlPattern: /^https:\/\/[a-z0-9]+\.supabase\.co\/storage\/v1\/object\/public\/.*/i,
              handler: 'CacheFirst',
              options: {
                cacheName: 'fotos-ensaios',
                expiration: { maxEntries: 500, maxAgeSeconds: 60 * 60 * 24 * 180 },
                cacheableResponse: { statuses: [0, 200] }
              }
            }
          ]
        },
        devOptions: { enabled: false }
      })
    ],
    resolve: {
      alias: {
        '@': path.resolve(__dirname, '.'),
      },
    },
    build: {
      outDir: spdaApp ? 'android-spda/app/src/main/assets/www' : 'dist',
      emptyOutDir: true,
      chunkSizeWarningLimit: 1000,
      rollupOptions: {
        output: {
          manualChunks: {
            vendor: ['react', 'react-dom', 'lucide-react', '@supabase/supabase-js'],
          },
          // leitor de PDF (pdf.worker.min.mjs) publicado como .js: a hospedagem serve .mjs como
          // text/plain e o navegador recusa; .js também entra no cache offline do PWA
          assetFileNames: info => (/\.mjs$/i.test(info.names?.[0] ?? '') ? 'assets/[name]-[hash].js' : 'assets/[name]-[hash][extname]'),
        },
      },
    },
    server: {
      host: '0.0.0.0',
      port: 3020,
      hmr: process.env.DISABLE_HMR !== 'true',
      watch: process.env.DISABLE_HMR === 'true' ? null : {},
    },
  };
});
