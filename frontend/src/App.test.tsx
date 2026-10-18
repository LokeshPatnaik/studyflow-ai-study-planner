import { describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'
import { AuthPage } from './pages/AuthPage'
import { TasksPage } from './pages/TasksPage'
import { RevisionsPage } from './pages/RevisionsPage'
import { CalendarPage } from './pages/CalendarPage'
import { localDateKey } from './components/ui'
import type { Revision, Task } from './types'

describe('authentication form', () => {
  it('validates email and password before submitting', async () => {
    const submit = vi.fn()
    render(<AuthPage initialMode="login" onBack={() => {}} onSubmit={submit} error="" loading={false} />)
    fireEvent.click(screen.getByRole('button', { name: /log in/i }))
    expect(await screen.findByRole('alert')).toHaveTextContent('valid email address')
    expect(submit).not.toHaveBeenCalled()
  })
})

describe('task list', () => {
  it('exposes completion action for a planned task', () => {
    const task: Task = { id:'task-1',planId:'plan-1',subjectId:'subject-1',topicId:null,subjectName:'Algorithms',subjectColor:'#9b8cff',title:'Graph traversal',description:'Practice BFS',scheduledDate:'2026-10-12',startTime:'17:00:00',endTime:'17:50:00',durationMinutes:50,priority:'HIGH',status:'PLANNED',examDate:'2026-10-20' }
    const update=vi.fn()
    render(<TasksPage tasks={[task]} subjects={[]} draftPlan={null} onUpdate={update} onGenerate={()=>{}} onApprovePlan={()=>{}} />)
    fireEvent.click(screen.getByRole('button',{name:'Mark Graph traversal completed'}))
    expect(update).toHaveBeenCalledWith(task,'COMPLETED')
  })
})

describe('schedule revision approval', () => {
  it('submits approval for the selected proposal', () => {
    const revision: Revision = {
      id:'revision-1', planId:'plan-1', status:'PENDING', reason:'Recover missed study time', createdAt:'2026-10-11T08:00:00Z',
      originalTasks:[{id:'task-1',planId:'plan-1',subjectId:'subject-1',topicId:null,subjectName:'Algorithms',subjectColor:'#9b8cff',title:'Graph traversal',description:'Practice BFS',scheduledDate:'2026-10-12',startTime:'17:00:00',endTime:'17:50:00',durationMinutes:50,priority:'HIGH',status:'MISSED',examDate:'2026-10-20'}],
      proposedTasks:[{taskId:'task-1',subjectId:'subject-1',topicId:null,subjectName:'Algorithms',title:'Graph traversal',description:'Practice BFS',scheduledDate:'2026-10-13',startTime:'17:00:00',endTime:'17:50:00',durationMinutes:50,priority:'HIGH',examDate:'2026-10-20'}]
    }
    const decide=vi.fn()
    render(<RevisionsPage revisions={[revision]} plan={null} tasks={[]} onTaskStatus={()=>{}} onRun={()=>{}} onDecision={decide} busy={false}/>)
    fireEvent.click(screen.getByRole('button',{name:/approve and apply/i}))
    expect(decide).toHaveBeenCalledWith('revision-1','APPROVE')
  })
})

describe('study calendar', () => {
  it('keeps the last day of the week visible using local calendar dates', () => {
    const sunday = new Date()
    sunday.setDate(sunday.getDate() + ((7 - sunday.getDay()) % 7))
    const subject = { id: 'subject-1', name: 'Algorithms', color: '#9b8cff', priority: 4, examName: 'Midterm', examDate: null, topicCount: 1, completedTopics: 0 }
    const task: Task = { id: 'task-sun', planId: 'plan-1', subjectId: subject.id, topicId: null, subjectName: subject.name, subjectColor: subject.color, title: 'Sunday study block', description: 'Review sorting', scheduledDate: localDateKey(sunday), startTime: '17:00:00', endTime: '17:50:00', durationMinutes: 50, priority: 'MEDIUM', status: 'PLANNED', examDate: null }

    render(<CalendarPage tasks={[task]} subjects={[subject]} />)

    expect(screen.getByText('Sunday study block')).toBeInTheDocument()
  })

  it('shows subject exams on the calendar even when there are no study tasks', () => {
    const sunday = new Date()
    sunday.setDate(sunday.getDate() + ((7 - sunday.getDay()) % 7))
    const subject = { id: 'subject-2', name: 'Database Systems', color: '#57c8d2', priority: 4, examName: 'Database final', examDate: localDateKey(sunday), topicCount: 0, completedTopics: 0 }

    render(<CalendarPage tasks={[]} subjects={[subject]} />)

    expect(screen.getByText('Database final')).toBeInTheDocument()
    expect(screen.getByText('EXAM')).toBeInTheDocument()
  })
})

