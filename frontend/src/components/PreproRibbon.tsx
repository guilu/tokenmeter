import { isPreproHost } from './preproHost'

interface PreproRibbonProps {
  /** Deployed commit. Empty when the build could not work it out. */
  readonly sha?: string
  /** Tests only; in production the real host decides. */
  readonly hostname?: string
}

/**
 * Diagonal band warning that this is not production.
 *
 * It sits in the top bar, right of the logo, and takes the theme's colours
 * swapped over: `bg-text` on `text-bg`. Both tokens are redefined under `.dark`
 * in index.css, so the inversion holds in either mode without declaring a new
 * colour or writing a single `dark:` variant.
 *
 * The commit rather than a version: `package.json` still reads 0.0.0 from the
 * scaffold, so a version number here would state something untrue. The sha says
 * exactly what is deployed.
 *
 * The parallelogram comes from skewing the container and counter-skewing its
 * content instead of a `clip-path`, so the background stays an ordinary box
 * that inherits the theme colour.
 */
export function PreproRibbon({
  sha = __BUILD_SHA__,
  hostname,
}: PreproRibbonProps) {
  const host = hostname ?? window.location.hostname
  if (!isPreproHost(host)) return null

  return (
    <div
      role="status"
      aria-label={
        sha
          ? `Preproduction environment, version ${sha}`
          : 'Preproduction environment'
      }
      className="ml-4 flex h-16 select-none items-center justify-center bg-text px-5 text-bg [transform:skewX(-18deg)]"
    >
      <span
        aria-hidden="true"
        className="flex flex-col items-center gap-0.5 [transform:skewX(18deg)]"
      >
        <span className="text-sm leading-none font-bold tracking-widest">
          PREPRO
        </span>
        {sha ? (
          <span
            className="text-xs leading-none tracking-wide opacity-85"
            data-testid="prepro-sha"
          >
            {sha}
          </span>
        ) : null}
      </span>
    </div>
  )
}
