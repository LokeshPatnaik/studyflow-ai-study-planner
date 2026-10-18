import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import App from './App'

const { auth, study } = vi.hoisted(() => ({
  auth: { me: vi.fn(), login: vi.fn(), register: vi.fn(), logout: vi.fn() },
  study: {
    subjects: vi.fn(), tasks: vi.fn(), activePlan: vi.fn(), plans: vi.fn(), activity: vi.fn(), revisions: vi.fn(), progress: vi.fn(),
    preferences: vi.fn(), savePreferences: vi.fn(), addSubject: vi.fn(), addTopics: vi.fn(), parsePdf: vi.fn(), topics: vi.fn(),
    generatePlan: vi.fn(), approvePlan: vi.fn(), updateTask: vi.fn(), runAgent: vi.fn(), decideRevision: vi.fn(),
  },
}))

vi.mock('./api', () => ({ authApi: auth, studyApi: study }))

describe('account registration UI flow', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    auth.me.mockRejectedValue(new Error('Request failed (401)'))
    auth.register.mockResolvedValue({ student: { id: 'student-1', name: 'Taylor Student', email: 'taylor@example.test', demo: false } })
    study.subjects.mockResolvedValue([])
    study.tasks.mockResolvedValue([])
    study.activePlan.mockResolvedValue(null)
    study.plans.mockResolvedValue([])
    study.activity.mockResolvedValue([])
    study.revisions.mockResolvedValue([])
    study.progress.mockResolvedValue({ total: 0, completed: 0, pending: 0, missed: 0, completionPercent: 0, subjects: [], week: [] })
  })

  it('uses the authenticated registration response and opens the workspace without a second login request', async () => {
    const { unmount } = render(<App />)
    fireEvent.click(await screen.findByRole('button', { name: /Build my study plan/i }))
    fireEvent.change(screen.getByLabelText('Full name'), { target: { value: 'Taylor Student' } })
    fireEvent.change(screen.getByLabelText('Email address'), { target: { value: 'taylor@example.test' } })
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'StrongPass123!' } })
    fireEvent.click(screen.getByRole('button', { name: /Create account/i }))

    expect(await screen.findByRole('heading', { name: 'What are you preparing for?' })).toBeInTheDocument()
    expect(auth.register).toHaveBeenCalledWith('Taylor Student', 'taylor@example.test', 'StrongPass123!')
    expect(auth.login).not.toHaveBeenCalled()
    await waitFor(() => expect(study.progress).toHaveBeenCalled())
    unmount()
  })
})
