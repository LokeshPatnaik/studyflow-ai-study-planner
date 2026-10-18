import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { PlansPage } from './PlansPage'
import { SubjectsPage } from './SubjectsPage'
import { SyllabusPage } from './SyllabusPage'
import type { Plan, Preference, Subject, Topic } from '../types'

const apiMocks = vi.hoisted(() => ({
  preferences: vi.fn(), savePreferences: vi.fn(), generatePlan: vi.fn(), topics: vi.fn(), updateTopic: vi.fn(), addSubject: vi.fn(), addTopics: vi.fn(), parsePdf: vi.fn(), deleteTopic: vi.fn(), deleteSubject: vi.fn(), deletePlan: vi.fn(),
}))
vi.mock('../api', () => ({ studyApi: apiMocks }))

const preference: Preference = { dailyStudyMinutes:120, sessionMinutes:50, breakMinutes:10, preferredDays:['MON','TUE','WED','THU','FRI','SAT'], dayStart:'17:00:00', dayEnd:'21:00:00', revisionPreference:'SPACED' }
const subject: Subject = { id:'subject-1', name:'Algorithms', color:'#9182f5', priority:4, examName:'Final', examDate:'2026-11-12', topicCount:4, completedTopics:0 }
const topic: Topic = { id:'topic-1', subjectId:subject.id, title:'Binary search', unit:'Unit 2', chapter:'Search', description:'Find a value in a sorted sequence', difficulty:'MEDIUM', estimatedMinutes:70, importance:3, familiarity:3, included:true, approved:true, originalTitle:'Binary search', originalUnit:'Unit 2' }
const draft: Plan = { id:'plan-1', title:'November exams', status:'DRAFT', aiMode:'RULE_BASED', summary:'A candidate timetable for review.', feasible:true, conflicts:[], tasks:[] }

beforeEach(() => {
  vi.clearAllMocks()
  apiMocks.preferences.mockResolvedValue(preference)
  apiMocks.savePreferences.mockResolvedValue(preference)
  apiMocks.generatePlan.mockResolvedValue(draft)
  apiMocks.topics.mockResolvedValue([topic])
  apiMocks.updateTopic.mockImplementation(async (_subjectId: string, value: Topic) => value)
})
afterEach(() => cleanup())

describe('plan builder', () => {
  it('saves availability and asks the backend for a scoped schedule draft', async () => {
    const refresh = vi.fn()
    render(<PlansPage plans={[]} subjects={[subject]} onRefresh={refresh} onApprove={()=>{}} onStatus={()=>{}} />)
    fireEvent.click(screen.getByRole('checkbox', { name:/Algorithms/ }))
    fireEvent.click(screen.getByRole('button', { name:/Continue/ }))
    fireEvent.click(screen.getByRole('button', { name:/Continue/ }))
    fireEvent.click(screen.getByRole('button', { name:/Generate preview/ }))

    expect(await screen.findByText('November exams')).toBeInTheDocument()
    expect(apiMocks.savePreferences).toHaveBeenCalledWith(expect.objectContaining({ preferredDays:preference.preferredDays, dayStart:'17:00:00' }))
    expect(apiMocks.generatePlan).toHaveBeenCalledWith('Exam preparation plan', ['subject-1'], expect.objectContaining({ unavailableDates:[], commitments:[], timeZone:expect.any(String) }))
    expect(refresh).toHaveBeenCalled()
  })
})

describe('syllabus review', () => {
  it('lets a student split a grouped topic by its original headings and merge the related pieces', async () => {
    apiMocks.parsePdf.mockResolvedValue({
      parsed:true, requiresOcr:false, message:'Review these grouped syllabus topics.', extractedText:'',
      topics:[
        { title:'Graph traversal', unit:'Unit 1', chapter:'Algorithms', description:'Queues and stacks', difficulty:'MEDIUM', estimatedMinutes:90, importance:3, familiarity:3, included:true,
          sourceContent:[{text:'Unit 1: Algorithms',page:1,heading:true},{text:'1.1 Breadth-first search',page:1,heading:true},{text:'Use a queue to traverse graph levels.',page:1,heading:false},{text:'1.2 Depth-first search',page:2,heading:true},{text:'Use a stack for depth-first exploration.',page:2,heading:false}] },
        { title:'Graph traversal analysis', unit:'Unit 1', chapter:'Algorithms', description:'Complexity', difficulty:'MEDIUM', estimatedMinutes:45, importance:3, familiarity:3, included:true,
          sourceContent:[{text:'1.3 Traversal complexity',page:2,heading:true}] },
      ],
    })
    render(<SyllabusPage subjects={[subject]} onRefresh={()=>{}} />)
    const upload = document.querySelector('input[type="file"]') as HTMLInputElement
    fireEvent.change(upload, { target:{ files:[new File(['syllabus'], 'syllabus.pdf', { type:'application/pdf' })] } })
    fireEvent.click(screen.getByRole('button', { name:/Extract topics for review/ }))

    await screen.findByText('Suggested topics')
    expect(screen.getByText(/pages 1, 2/)).toBeInTheDocument()
    fireEvent.click(screen.getAllByRole('button', { name:'Split' })[0])
    expect(screen.getByDisplayValue('Breadth-first search')).toBeInTheDocument()
    expect(screen.getByDisplayValue('Depth-first search')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('checkbox', { name:'Select Breadth-first search' }))
    fireEvent.click(screen.getByRole('checkbox', { name:'Select Depth-first search' }))
    fireEvent.click(screen.getByRole('button', { name:/Merge selected/ }))
    expect(screen.getByDisplayValue(/Breadth-first search \+ 1 related topics/)).toBeInTheDocument()
  })
})

describe('topic editor', () => {
  it('persists bulk exclusion while keeping the topic in the saved syllabus', async () => {
    const refresh = vi.fn()
    render(<SubjectsPage subjects={[subject]} onRefresh={refresh} />)
    await screen.findByDisplayValue('Binary search')
    expect(screen.getByText('1h 10m')).toBeInTheDocument()
    expect(screen.getByRole('spinbutton', { name:'Saved estimate for Binary search' })).toHaveAttribute('readonly')
    fireEvent.click(screen.getByRole('checkbox', { name:'Select all topics' }))
    fireEvent.click(screen.getByRole('button', { name:/Exclude/ }))

    await waitFor(() => expect(apiMocks.updateTopic).toHaveBeenCalledWith(subject.id, expect.objectContaining({ id:topic.id, included:false })))
    expect(refresh).toHaveBeenCalled()
    expect(screen.getByDisplayValue('Binary search')).toBeInTheDocument()
  })

  it('requires confirmation and refreshes after deleting a subject with an impact summary', async () => {
    const refresh = vi.fn()
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true)
    apiMocks.deleteSubject.mockResolvedValue(undefined)
    const view = render(<SubjectsPage subjects={[subject]} onRefresh={refresh} />)
    await screen.findByDisplayValue('Binary search')

    fireEvent.click(within(view.container).getByRole('button', { name:/Delete subject/ }))

    await waitFor(() => expect(apiMocks.deleteSubject).toHaveBeenCalledWith(subject.id))
    expect(confirm).toHaveBeenCalledWith(expect.stringContaining(subject.name))
    expect(confirm).toHaveBeenCalledWith(expect.stringContaining('syllabus and topics, exams, study sessions (including completed history), and spaced revision tasks'))
    expect(await screen.findByRole('status')).toHaveTextContent('was deleted')
    expect(refresh).toHaveBeenCalled()
  })
})

describe('saved plan deletion', () => {
  it('requires confirmation, explains dependent records, and refreshes after deletion', async () => {
    const refresh = vi.fn()
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true)
    apiMocks.preferences.mockResolvedValue(preference)
    apiMocks.deletePlan.mockResolvedValue(undefined)
    render(<PlansPage plans={[draft]} subjects={[subject]} onRefresh={refresh} onApprove={()=>{}} onStatus={()=>{}} />)

    fireEvent.click(screen.getByRole('button', { name:/Delete plan/ }))

    await waitFor(() => expect(apiMocks.deletePlan).toHaveBeenCalledWith(draft.id))
    expect(confirm).toHaveBeenCalledWith(expect.stringContaining(draft.title))
    expect(confirm).toHaveBeenCalledWith(expect.stringContaining('completed history and spaced revision tasks'))
    expect(confirm).toHaveBeenCalledWith(expect.stringContaining('Subjects and other plans will remain'))
    expect(await screen.findByRole('status')).toHaveTextContent('were deleted')
    expect(refresh).toHaveBeenCalled()
  })
})
