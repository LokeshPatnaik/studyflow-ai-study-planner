import { afterEach, describe, expect, it, vi } from 'vitest'
import { api } from './api'

const jsonResponse = (body: unknown, status = 200) => new Response(JSON.stringify(body), {
  status,
  headers: { 'Content-Type': 'application/json' },
})

describe('API CSRF handling', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    document.cookie = 'XSRF-TOKEN=; Max-Age=0; path=/'
  })

  it('sends the raw readable cookie value as the CSRF header', async () => {
    const calls: Array<[RequestInfo | URL, RequestInit | undefined]> = []
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      calls.push([input, init])
      if (String(input) === '/api/v1/auth/csrf') {
        document.cookie = 'XSRF-TOKEN=raw-cookie-token; path=/'
        return jsonResponse({ ready: true })
      }
      return jsonResponse({ saved: true })
    })
    vi.stubGlobal('fetch', fetchMock)

    await expect(api<{ saved: boolean }>('/auth/login', { method: 'POST', body: '{}' }))
      .resolves.toEqual({ saved: true })

    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(new Headers(calls[1][1]?.headers).get('X-XSRF-TOKEN')).toBe('raw-cookie-token')
    expect(calls[0][1]?.credentials).toBe('include')
    expect(calls[1][1]?.credentials).toBe('include')
  })

  it('uses the current cookie on each mutation instead of caching an older token', async () => {
    document.cookie = 'XSRF-TOKEN=first-token; path=/'
    const headers: string[] = []
    const fetchMock = vi.fn(async (_input: RequestInfo | URL, init?: RequestInit) => {
      headers.push(new Headers(init?.headers).get('X-XSRF-TOKEN') ?? '')
      return jsonResponse({ ok: true })
    })
    vi.stubGlobal('fetch', fetchMock)

    await api('/first', { method: 'POST', body: '{}' })
    document.cookie = 'XSRF-TOKEN=second-token; path=/'
    await api('/second', { method: 'POST', body: '{}' })

    expect(headers).toEqual(['first-token', 'second-token'])
  })

  it('returns null for an intentional no-content response', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response(null, { status: 204 })))

    await expect(api('/plans/active')).resolves.toBeNull()
  })

  it('surfaces structured validation and conflict messages from the backend', async () => {
    document.cookie = 'XSRF-TOKEN=valid-token; path=/'
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse({
      type: 'about:blank', title: 'Conflict', status: 409, detail: 'An account with this email already exists',
    }, 409)))

    await expect(api('/auth/register', { method: 'POST', body: '{}' }))
      .rejects.toThrow('An account with this email already exists')
  })
})
