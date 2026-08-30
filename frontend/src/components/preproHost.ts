/**
 * The two domains the apps live under while none of them has a domain of its
 * own. When TokenMeter moves, the notice disappears by itself: there is no
 * environment variable to remember and no special deploy to run.
 */
const PREPRO_DOMAINS = ['diegobarrioh.dev', 'backendtothefuture.com']

/**
 * Compared label by label rather than with `endsWith`.
 *
 * `endsWith` would accept `notdiegobarrioh.dev`, which belongs to someone
 * else. The check demands either the exact domain or a dot right before it —
 * which is what separates a subdomain of ours from a stranger's domain that
 * happens to end the same way.
 */
export function isPreproHost(hostname: string): boolean {
  return PREPRO_DOMAINS.some(
    (domain) => hostname === domain || hostname.endsWith(`.${domain}`),
  )
}
