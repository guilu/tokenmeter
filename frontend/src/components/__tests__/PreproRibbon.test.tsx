import { render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { PreproRibbon } from '../PreproRibbon'
import { isPreproHost } from '../preproHost'

/** `window.location` is not writable in jsdom, so each case installs a stand-in. */
const realLocation = window.location
function atHost(hostname: string) {
  Object.defineProperty(window, 'location', {
    value: { ...realLocation, hostname },
    writable: true,
    configurable: true,
  })
}
afterEach(() => {
  Object.defineProperty(window, 'location', {
    value: realLocation,
    writable: true,
    configurable: true,
  })
})

describe('isPreproHost', () => {
  it('accepts both domains and their subdomains', () => {
    expect(isPreproHost('tokenmeter.backendtothefuture.com')).toBe(true)
    expect(isPreproHost('backendtothefuture.com')).toBe(true)
    expect(isPreproHost('forma.diegobarrioh.dev')).toBe(true)
  })

  it('rejects localhost and a production domain', () => {
    expect(isPreproHost('localhost')).toBe(false)
    expect(isPreproHost('tokenmeter.io')).toBe(false)
  })

  it('is not fooled by a domain that merely ends the same', () => {
    expect(isPreproHost('notdiegobarrioh.dev')).toBe(false)
    expect(isPreproHost('evil-backendtothefuture.com')).toBe(false)
  })
})

describe('PreproRibbon', () => {
  it('renders nothing outside preproduction', () => {
    atHost('tokenmeter.io')
    const { container } = render(<PreproRibbon sha="a91c034" />)
    expect(container).toBeEmptyDOMElement()
  })

  it('announces PREPRO and the deployed commit', () => {
    atHost('tokenmeter.backendtothefuture.com')
    render(<PreproRibbon sha="a91c034" />)
    expect(screen.getByText('PREPRO')).toBeInTheDocument()
    expect(screen.getByText('a91c034')).toBeInTheDocument()
  })

  it('keeps the warning and drops the second line when there is no sha', () => {
    atHost('tokenmeter.backendtothefuture.com')
    render(<PreproRibbon sha="" />)
    expect(screen.getByText('PREPRO')).toBeInTheDocument()
    expect(screen.queryByTestId('prepro-sha')).not.toBeInTheDocument()
  })
})
