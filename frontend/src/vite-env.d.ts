/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Google Analytics 4 Measurement ID (e.g. G-XXXXXXXXXX). When unset, GA is never loaded. */
  readonly VITE_GA_MEASUREMENT_ID?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
