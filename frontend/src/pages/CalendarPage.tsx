import { useMemo, useState } from 'react'
import { ArrowLeft, ArrowRight, CalendarDays, ChevronDown, Clock3, Filter, Move } from 'lucide-react'
import type { Subject, Task } from '../types'
import { EmptyState, formatDate, formatTime, localDateKey, PageHeading } from '../components/ui'

export function CalendarPage({ tasks, subjects, onMove }: { tasks: Task[]; subjects: Subject[]; onMove?: (task: Task, date: string, startTime: string, duration: number) => Promise<void> }) {
  const [offset, setOffset] = useState(0)
  const [monthOffset, setMonthOffset] = useState(0)
  const [view, setView] = useState<'WEEK'|'MONTH'>('WEEK')
  const [subject, setSubject] = useState('ALL')
  const [status, setStatus] = useState('ALL')
  const [movingId, setMovingId] = useState('')
  const [moveError, setMoveError] = useState('')
  const monday = useMemo(() => {
    const date = new Date()
    const weekdayOffset = (date.getDay() + 6) % 7
    date.setDate(date.getDate() - weekdayOffset + offset * 7)
    date.setHours(0, 0, 0, 0)
    return date
  }, [offset])
  const weekDays = Array.from({ length: 7 }, (_, index) => {
    const date = new Date(monday)
    date.setDate(date.getDate() + index)
    return date
  })
  const monthDays = useMemo(() => {
    const todayDate = new Date(); todayDate.setDate(1); todayDate.setMonth(todayDate.getMonth()+monthOffset)
    const first = new Date(todayDate); first.setDate(1-((first.getDay()+6)%7))
    return Array.from({length:42},(_,index)=>{const date=new Date(first);date.setDate(first.getDate()+index);return date})
  },[monthOffset])
  const days = view==='WEEK' ? weekDays : monthDays
  const firstDay = localDateKey(days[0])
  const lastDay = localDateKey(days[6])
  const today = localDateKey(new Date())
  const filtered = tasks.filter(task => !['ARCHIVED', 'MOVED', 'PROPOSED'].includes(task.status)
    && (subject === 'ALL' || task.subjectId === subject)
    && (status === 'ALL' || task.status === status))
  const weekTasks = filtered.filter(task => task.scheduledDate >= firstDay && task.scheduledDate <= lastDay)
  const weekHasExams = subjects.some(item => item.examDate && item.examDate >= firstDay && item.examDate <= lastDay && (subject === 'ALL' || item.id === subject))

  return <div className="calendar-page">
    <PageHeading eyebrow="A WEEK YOU CAN SEE" title="Study calendar" description="Review sessions, exam dates, and open time in one place."
      action={<div className="calendar-controls"><button className="icon-btn" aria-label={`Previous ${view==='WEEK'?'week':'month'}`} onClick={() => view==='WEEK'?setOffset(offset-1):setMonthOffset(monthOffset-1)}><ArrowLeft size={16} /></button><button className="btn btn-subtle" onClick={() => {setOffset(0);setMonthOffset(0)}}>Today</button><button className="icon-btn" aria-label={`Next ${view==='WEEK'?'week':'month'}`} onClick={() => view==='WEEK'?setOffset(offset+1):setMonthOffset(monthOffset+1)}><ArrowRight size={16} /></button></div>} />
    {moveError && <div className="inline-alert error-alert" role="alert">{moveError}<button className="icon-btn" aria-label="Dismiss move error" onClick={()=>setMoveError('')}>×</button></div>}
    <div className="calendar-toolbar"><div className="calendar-range"><CalendarDays size={16} /><strong>{view==='WEEK'?`${formatDate(firstDay, { month: 'long', day: 'numeric' })} – ${formatDate(lastDay, { month: 'long', day: 'numeric', year: 'numeric' })}`:formatDate(localDateKey(new Date(new Date().getFullYear(),new Date().getMonth()+monthOffset,1)),{month:'long',year:'numeric'})}</strong></div><div className="filter-controls"><div className="calendar-view-toggle" role="group" aria-label="Calendar view"><button className={view==='WEEK'?'active':''} aria-pressed={view==='WEEK'} onClick={()=>setView('WEEK')}>Week</button><button className={view==='MONTH'?'active':''} aria-pressed={view==='MONTH'} onClick={()=>setView('MONTH')}>Month</button></div>
      <label className="select-filter"><Filter size={13} /><select aria-label="Filter by subject" value={subject} onChange={event => setSubject(event.target.value)}><option value="ALL">All subjects</option>{subjects.map(item => <option key={item.id} value={item.id}>{item.name}</option>)}</select><ChevronDown size={12} /></label>
      <label className="select-filter"><select aria-label="Filter by task status" value={status} onChange={event => setStatus(event.target.value)}><option value="ALL">All task states</option><option value="PLANNED">Planned</option><option value="MISSED">Missed</option><option value="COMPLETED">Completed</option></select></label>
    </div></div>
    {weekTasks.length === 0 && !weekHasExams && tasks.length === 0
      ? <div className="panel calendar-empty"><EmptyState icon={<CalendarDays size={20} />} title="Your study week starts here" text="Add subjects and topics, then generate a draft plan to fill your calendar." /></div>
      : <div className={`calendar-grid ${view==='MONTH'?'month-view':''}`} role="grid" aria-label={`${view==='WEEK'?'Weekly':'Monthly'} study calendar`}>{days.map(day => {
        const dateKey = localDateKey(day)
        const dayTasks = weekTasks.filter(task => task.scheduledDate === dateKey).sort((a, b) => (a.startTime ?? '').localeCompare(b.startTime ?? ''))
        const dayExams = subjects.filter(item => item.examDate === dateKey && (subject === 'ALL' || item.id === subject))
        return <section className={`calendar-day ${dateKey === today ? 'is-today' : ''} ${view==='MONTH'&&day.getMonth()!==new Date(new Date().getFullYear(),new Date().getMonth()+monthOffset,1).getMonth()?'outside-month':''} ${movingId?'accept-drop':''}`} key={dateKey}
          onDragOver={event=>{if(movingId){event.preventDefault();event.dataTransfer.dropEffect='move'}}}
          onDrop={event=>{event.preventDefault();const taskId=event.dataTransfer.getData('text/studyflow-task')||movingId;const task=tasks.find(item=>item.id===taskId);setMovingId('');if(task&&onMove){void onMove(task,dateKey,task.startTime??'17:00:00',task.durationMinutes).catch(cause=>setMoveError(cause instanceof Error?cause.message:'Could not move this session.'))}}}>
          <div className="calendar-day-head"><span>{day.toLocaleDateString('en', { weekday: 'short' }).toUpperCase()}</span><b>{day.getDate()}</b><small>{day.toLocaleDateString('en', { month: 'short' })}</small></div>
          <div className="day-event-list">{dayExams.map(item => <article key={`exam-${item.id}`} className="calendar-exam-event"><span>EXAM</span><b>{item.examName}</b><small>{item.name}</small></article>)}{dayTasks.map(task => <article key={task.id} draggable={!!onMove&&task.status==='PLANNED'} onDragStart={event=>{event.dataTransfer.setData('text/studyflow-task',task.id);event.dataTransfer.effectAllowed='move';setMovingId(task.id)}} onDragEnd={()=>setMovingId('')} title={onMove&&task.status==='PLANNED'?'Drag to another day to reschedule':''} className={`calendar-event ${task.status.toLowerCase()} ${movingId===task.id?'is-dragging':''}`} style={{ '--subject-color': task.subjectColor } as React.CSSProperties}>
            <span className="event-time">{formatTime(task.startTime)}</span><b>{task.title}</b><span>{task.subjectName} · {task.durationMinutes}m</span>{onMove&&task.status==='PLANNED'&&<Move className="calendar-drag-hint" size={11}/>}<i className="event-status-dot" />
          </article>)}{dayTasks.length === 0 && dayExams.length === 0 && <span className="calendar-open">Open time</span>}</div>
        </section>
      })}</div>}
    <div className="calendar-legend"><span><i className="legend-violet" /> Study session</span><span><i className="legend-cyan" /> Completed</span><span><i className="legend-amber" /> Missed</span><small><Clock3 size={12} /> Dates reflect saved plan tasks.</small></div>
  </div>
}
