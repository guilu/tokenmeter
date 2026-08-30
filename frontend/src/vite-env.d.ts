/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Google Analytics 4 Measurement ID (e.g. G-XXXXXXXXXX). When unset, GA is never loaded. */
  readonly VITE_GA_MEASUREMENT_ID?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

/**
 * Short commit of the build, injected by `vite.config.ts`.
 *
 * Empty when the build could not work it out — inside the image `.git` is in
 * the .dockerignore and node:alpine ships no git, so there it arrives through
 * `VITE_BUILD_SHA` or not at all.
 */
declare const __BUILD_SHA__: string
