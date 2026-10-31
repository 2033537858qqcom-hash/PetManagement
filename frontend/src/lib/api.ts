let csrfToken = ''
let csrfHeader = ''
export class ApiError extends Error {
  constructor(message:string, public readonly status?:number) { super(message); this.name='ApiError' }
}
export async function initializeCsrf() {
  const result = await api<{ token: string; header: string }>('/auth/csrf')
  csrfToken = result.token; csrfHeader = result.header
}
export async function api<T>(path: string, options?: RequestInit): Promise<T> {
  let response: Response
  const headers = new Headers(options?.headers)
  if (csrfToken && csrfHeader && !['GET', 'HEAD', 'OPTIONS'].includes((options?.method || 'GET').toUpperCase())) headers.set(csrfHeader, csrfToken)
  try { response = await fetch('/api' + path, { ...options, headers }) } catch { throw new ApiError('暂时无法连接门店服务，请稍后重试。') }
  const body = await response.json().catch(() => null)
  if (response.status === 401 && !path.startsWith('/auth/')) window.dispatchEvent(new Event('session-expired'))
  if (!response.ok) throw new ApiError(body?.message || `请求失败（${response.status}），请重试。`, response.status)
  return body?.data as T
}
