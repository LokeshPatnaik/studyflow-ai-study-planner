import type { AgentRun, Plan, Preference, Progress, Revision, Student, Subject, SyllabusResult, Task, Topic } from './types'

async function ensureCsrf() {
  const readCookie = () => {
    const entry = document.cookie.split(';').map(value => value.trim()).find(value => value.startsWith('XSRF-TOKEN='))
    return entry ? decodeURIComponent(entry.slice('XSRF-TOKEN='.length)) : ''
  }
  const existing = readCookie()
  if (existing) return existing

  const response = await fetch('/api/v1/auth/csrf', { credentials: 'include' })
  if (!response.ok) throw new Error(`Unable to initialize CSRF protection (${response.status})`)
  const token = readCookie()
  if (!token) throw new Error('The server did not set the CSRF cookie. Check that API cookies are enabled.')
  return token
}
export async function api<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers)
  if (options.body && !(options.body instanceof FormData)) headers.set('Content-Type', 'application/json')
  if (options.method && !['GET', 'HEAD', 'OPTIONS'].includes(options.method.toUpperCase())) headers.set('X-XSRF-TOKEN', await ensureCsrf())
  const response = await fetch(`/api/v1${path}`, { ...options, headers, credentials: 'include' })
  if (!response.ok) {
    let message = `Request failed (${response.status})`
    try { const body = await response.json(); message = body.detail || body.message || body.error || message } catch { /* use status message */ }
    throw new Error(message)
  }
  if (response.status === 204) return null as T
  return response.json() as Promise<T>
}
export const authApi = {
  me: () => api<{ student: Student }>('/auth/me'),
  login: (email: string, password: string) => api<{ student: Student }>('/auth/login', { method: 'POST', body: JSON.stringify({ email, password }) }),
  register: (name: string, email: string, password: string) => api<{ student: Student }>('/auth/register', { method: 'POST', body: JSON.stringify({ name, email, password }) }),
  logout: () => api('/auth/logout', { method: 'POST' }),
}
export const studyApi = {
  subjects: () => api<Subject[]>('/subjects'),
  topics: (subjectId: string) => api<Topic[]>(`/subjects/${subjectId}/topics`),
  addSubject: (body: { name: string; color: string; priority: number; examName: string; examDate: string }) => api<Subject>('/subjects', { method: 'POST', body: JSON.stringify(body) }),
  deleteSubject: (subjectId: string) => api<void>(`/subjects/${subjectId}`, { method: 'DELETE' }),
  addTopics: (subjectId: string, topics: { title: string; unit?: string | null; chapter?: string | null; description?: string | null; difficulty: string; estimatedMinutes: number; importance?: number; familiarity?: number; included?: boolean; sourceContent?: Topic['sourceContent'] }[], source='MANUAL', sourceName?: string) => api<Topic[]>('/syllabus/manual', { method: 'POST', body: JSON.stringify({ subjectId, source, sourceName, topics }) }),
  updateTopic: (subjectId: string, topic: Topic) => api<Topic>(`/subjects/${subjectId}/topics/${topic.id}`, { method: 'PATCH', body: JSON.stringify({ subjectId, title: topic.title, unit: topic.unit, chapter: topic.chapter, description: topic.description, difficulty: topic.difficulty, estimatedMinutes: topic.estimatedMinutes, importance: topic.importance, familiarity: topic.familiarity, included: topic.included, sourceContent: topic.sourceContent }) }),
  deleteTopic: (subjectId: string, topicId: string) => api<void>(`/subjects/${subjectId}/topics/${topicId}`, { method: 'DELETE' }),
  parsePdf: (file: File) => { const data = new FormData(); data.append('file', file); return api<SyllabusResult>('/syllabus/upload', { method: 'POST', body: data }) },
  preferences: () => api<Preference>('/preferences'),
  savePreferences: (p: Preference) => api<Preference>('/preferences', { method: 'PUT', body: JSON.stringify(p) }),
  activePlan: () => api<Plan | null>('/plans/active'),
  plans: () => api<Plan[]>('/plans'),
  deletePlan: (planId: string) => api<void>(`/plans/${planId}`, { method: 'DELETE' }),
  generatePlan: (title?: string, subjectIds?: string[], options?: { unavailableDates?: string[]; commitments?: { date: string; startTime: string; endTime: string; label?: string }[]; timeZone?: string }) => api<Plan>('/plans/generate', { method: 'POST', body: JSON.stringify({ title, subjectIds, ...options }) }),
  approvePlan: (id: string) => api<Plan>(`/plans/${id}/approve`, { method: 'POST' }),
  updatePlanStatus: (id: string, status: string) => api<Plan>(`/plans/${id}/status`, { method: 'PATCH', body: JSON.stringify({ status }) }),
  tasks: (params = '') => api<Task[]>(`/tasks${params ? `?${params}` : ''}`),
  updateTask: (id: string, status: string) => api<Task>(`/tasks/${id}/status`, { method: 'PATCH', body: JSON.stringify({ status }) }),
  rescheduleTask: (id: string, scheduledDate: string, startTime: string, durationMinutes: number) => api<Task>(`/tasks/${id}/schedule`, { method: 'PATCH', body: JSON.stringify({ scheduledDate, startTime, durationMinutes }) }),
  progress: () => api<Progress>('/analytics'),
  runAgent: (intent: string, note?: string) => api<AgentRun>('/agent/run', { method: 'POST', body: JSON.stringify({ intent, note }) }),
  activity: () => api<AgentRun[]>('/agent/activity'),
  revisions: () => api<Revision[]>('/revisions'),
  proposeRevision: (reason?: string) => api<Revision>('/revisions/propose', { method: 'POST', body: JSON.stringify({ reason }) }),
  decideRevision: (id: string, decision: string) => api<Revision>(`/revisions/${id}/decision`, { method: 'POST', body: JSON.stringify({ decision }) }),
}

