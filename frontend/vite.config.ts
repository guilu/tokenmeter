import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

/**
 * Short commit of the build, for the preproduction ribbon.
 *
 * Read from the environment and never computed here: asking git would mean
 * importing `node:child_process`. The call lives in the `build` script, where a
 * shell is the natural tool.
 *
 * Empty when nobody supplied it, and the ribbon then warns without a version
 * line. Inventing a placeholder would put an identifier on screen that
 * corresponds to no commit at all.
 */
const BUILD_SHA = process.env.VITE_BUILD_SHA ?? ''

export default defineConfig({
  define: {
    __BUILD_SHA__: JSON.stringify(BUILD_SHA),
  },
  plugins: [react(), tailwindcss()],
  server: {
    host: '0.0.0.0',
    port: 3000,
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
