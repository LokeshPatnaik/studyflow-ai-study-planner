import { useEffect, useMemo, useState } from 'react'
import { AlertCircle, BookOpen, Check, CheckSquare, ChevronDown, Clock3, Plus, RotateCcw, Save, Trash2, X } from 'lucide-react'
import { studyApi } from '../api'
import { EmptyState, formatDate, PageHeading } from '../components/ui'
import type { Subject, Topic } from '../types'

type Props = { subjects: Subject[]; onRefresh: () => void }
export function SubjectsPage({ subjects, onRefresh }: Props) {
  const [subjectId, setSubjectId] = useState(subjects[0]?.id ?? '')
  const [topics, setTopics] = useState<Topic[]>([])
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [deletedSubjectId, setDeletedSubjectId] = useState('')
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const [selected, setSelected] = useState<string[]>([])
  const [subjectName, setSubjectName] = useState('')
  const [examName, setExamName] = useState('Final examination')
  const [examDate, setExamDate] = useState('')
  const [priority, setPriority] = useState(3)
  const [newTopics, setNewTopics] = useState('')
  const activeSubject = subjects.find(subject => subject.id === subjectId)

  useEffect(() => {
    if (subjectId && !subjects.some(subject => subject.id === subjectId)) setSubjectId(subjects[0]?.id ?? '')
    else if (!subjectId && subjects[0]) setSubjectId(subjects[0].id)
  }, [subjects, subjectId])
  useEffect(() => {
    if (!subjectId) { setTopics([]); return }
    setLoading(true); setSelected([])
    studyApi.topics(subjectId).then(setTopics).catch(cause => setError(cause instanceof Error ? cause.message : 'Could not load topics.')).finally(() => setLoading(false))
  }, [subjectId])
  const included = topics.filter(topic => topic.included)
  const workload = included.reduce((total, topic) => total + topic.estimatedMinutes, 0)
  const workloadLabel = workload < 60 ? `${workload}m` : `${Math.floor(workload / 60)}h${workload % 60 ? ` ${workload % 60}m` : ''}`
  const grouped = useMemo(() => {
    return topics.reduce<Record<string, Topic[]>>((groups, topic) => {
      const label = [topic.unit, topic.chapter].filter(Boolean).join(' / ') || 'Unassigned topics'
      ;(groups[label] ??= []).push(topic)
      return groups
    }, {})
  }, [topics])
  const updateLocal = (id: string, field: keyof Topic, value: string | number | boolean) => setTopics(current => current.map(topic => topic.id === id ? { ...topic, [field]: value } : topic))
  const saveTopic = async (topic: Topic) => {
    setSaving(true); setError('')
    try { const saved = await studyApi.updateTopic(subjectId, topic); setTopics(current => current.map(item => item.id === saved.id ? saved : item)); onRefresh() }
    catch (cause) { setError(cause instanceof Error ? cause.message : 'Could not save this topic.') }
    finally { setSaving(false) }
  }
  const bulkInclude = async (value: boolean) => {
    const changed = topics.filter(topic => selected.includes(topic.id) && topic.included !== value)
    setSaving(true); setError('')
    try { const saved = await Promise.all(changed.map(topic => studyApi.updateTopic(subjectId, { ...topic, included: value }))); setTopics(current => current.map(topic => saved.find(item => item.id === topic.id) ?? topic)); setSelected([]); onRefresh() }
    catch (cause) { setError(cause instanceof Error ? cause.message : 'Could not update the selected topics.') }
    finally { setSaving(false) }
  }
  const addSubject = async (event: React.FormEvent) => {
    event.preventDefault(); setSaving(true); setError('')
    try { const subject = await studyApi.addSubject({ name: subjectName.trim(), examName: examName.trim(), examDate, priority, color: ['#9182f5','#4eb8c6','#e4a957','#67b893'][subjects.length % 4] }); setSubjectName(''); setExamName('Final examination'); setExamDate(''); setSubjectId(subject.id); onRefresh() }
    catch (cause) { setError(cause instanceof Error ? cause.message : 'Could not add this subject.') }
    finally { setSaving(false) }
  }
  const deleteSubject = async () => {
    if (!activeSubject || deleting || saving || loading) return
    const confirmed = window.confirm(`Delete “${activeSubject.name}”? This removes its syllabus and topics, exams, study sessions (including completed history), and spaced revision tasks. Any saved plans that include this subject will remain, as will their other subjects and sessions; revision proposals for this subject will be removed. This cannot be undone.`)
    if (!confirmed) return
    setDeleting(true); setError(''); setSuccess('')
    try {
      await studyApi.deleteSubject(activeSubject.id)
      setTopics([]); setSelected([])
      setDeletedSubjectId(activeSubject.id)
      const remaining = subjects.filter(subject => subject.id !== activeSubject.id)
      if (remaining[0]) setSubjectId(remaining[0].id)
      setSuccess(`“${activeSubject.name}” was deleted.`)
      onRefresh()
    } catch (cause) { setError(cause instanceof Error ? cause.message : 'Could not delete this subject.') }
    finally { setDeleting(false) }
  }
  const addManualTopics = async (event: React.FormEvent) => {
    event.preventDefault(); const names = newTopics.split('\n').map(line => line.trim()).filter(Boolean)
    if (!subjectId || !names.length) return
    setSaving(true); setError('')
    try { const created = await studyApi.addTopics(subjectId, names.map(title => ({ title, unit: 'Manual entry', difficulty: 'MEDIUM', estimatedMinutes: 60, importance: 3, familiarity: 3 }))); setTopics(current => [...current, ...created]); setNewTopics(''); onRefresh() }
    catch (cause) { setError(cause instanceof Error ? cause.message : 'Could not add these topics.') }
    finally { setSaving(false) }
  }

  return <div className="subjects-page">
    <PageHeading eyebrow="THE MATERIAL BEHIND YOUR PLAN" title="Subjects & topics" description="Keep your exam dates and syllabus organized. Edit extraction suggestions and choose what enters your next schedule." action={subjects.length>0&&<label className="subject-select"><span>Subject</span><select value={subjectId} onChange={event=>setSubjectId(event.target.value)}>{subjects.map(subject=><option key={subject.id} value={subject.id}>{subject.name}</option>)}</select><ChevronDown size={13}/></label>} />
    {error && <div className="inline-alert error-alert" role="alert"><AlertCircle size={14}/>{error}<button className="icon-btn" aria-label="Dismiss" onClick={()=>setError('')}><X size={13}/></button></div>}
    {success && <div className="inline-alert delete-success" role="status">{success}<button className="icon-btn" aria-label="Dismiss" onClick={()=>setSuccess('')}><X size={13}/></button></div>}
    <div className="subjects-layout">
      <section className="panel subject-editor-panel">
        {activeSubject ? <><div className="topic-editor-header"><div><span className="panel-overline">SUBJECT WORKSPACE</span><h2>{activeSubject.name}</h2><p>{activeSubject.examName} · {activeSubject.examDate ? formatDate(activeSubject.examDate,{month:'long',day:'numeric',year:'numeric'}) : 'Exam date not set'} · priority {activeSubject.priority}/5</p></div><div className="subject-header-actions"><span className="subject-header-dot" style={{background:activeSubject.color}}/><button className="btn btn-quiet delete-action" disabled={deleting||saving||loading||deletedSubjectId===activeSubject.id} onClick={()=>void deleteSubject()}><Trash2 size={14}/>{deleting?'Deleting…':deletedSubjectId===activeSubject.id?'Deleted':'Delete subject'}</button></div></div><div className="topic-summary-strip"><span><b>{included.length}</b><small>included</small></span><span><b>{topics.length-included.length}</b><small>excluded</small></span><span><b>{workloadLabel}</b><small>estimated workload</small></span><span><b>{topics.length ? Math.round(included.length*100/topics.length) : 0}%</b><small>coverage</small></span></div>
          <div className="topic-bulk-bar"><label><input type="checkbox" aria-label="Select all topics" checked={topics.length>0&&selected.length===topics.length} onChange={event=>setSelected(event.target.checked?topics.map(topic=>topic.id):[])}/> Select all</label><span>{selected.length} selected</span><button className="btn btn-quiet" disabled={!selected.length||saving} onClick={()=>void bulkInclude(true)}><Check size={13}/> Include</button><button className="btn btn-quiet" disabled={!selected.length||saving} onClick={()=>void bulkInclude(false)}><X size={13}/> Exclude</button></div>
          {loading ? <div className="workspace-loading">Loading saved topics…</div> : topics.length===0 ? <EmptyState icon={<BookOpen size={19}/>} title="No syllabus topics yet" text="Import a PDF in Syllabus Library or add a few topics below. Extracted items stay reviewable before they are saved."/> : <div className="topic-editor-list">{Object.entries(grouped).map(([group,items])=><section className="topic-group" key={group}><h3>{group}</h3>{items.map((topic,index)=><article className={`topic-edit-row ${topic.included?'':'excluded'}`} key={topic.id}><div className="topic-edit-main"><input type="checkbox" aria-label={`Select ${topic.title}`} checked={selected.includes(topic.id)} onChange={event=>setSelected(current=>event.target.checked?[...current,topic.id]:current.filter(id=>id!==topic.id))}/><span className="topic-row-index">{String(index+1).padStart(2,'0')}</span><div className="topic-edit-fields"><label className="topic-title-field"><span>Topic name</span><input value={topic.title} maxLength={180} onChange={event=>updateLocal(topic.id,'title',event.target.value)}/></label><label><span>Unit</span><input value={topic.unit??''} maxLength={120} onChange={event=>updateLocal(topic.id,'unit',event.target.value||null as unknown as string)}/></label><label><span>Chapter</span><input value={topic.chapter??''} maxLength={120} onChange={event=>updateLocal(topic.id,'chapter',event.target.value)}/></label><label className="topic-description-field"><span>Description</span><textarea value={topic.description??''} maxLength={1000} rows={2} onChange={event=>updateLocal(topic.id,'description',event.target.value)}/></label><label><span>Minutes (saved estimate)</span><input type="number" min={10} max={720} value={topic.estimatedMinutes} readOnly aria-label={`Saved estimate for ${topic.title}`}/></label><label><span>Difficulty</span><select value={topic.difficulty} onChange={event=>updateLocal(topic.id,'difficulty',event.target.value)}><option>EASY</option><option>MEDIUM</option><option>HARD</option></select></label><label><span>Importance</span><select value={topic.importance} onChange={event=>updateLocal(topic.id,'importance',Number(event.target.value))}>{[1,2,3,4,5].map(value=><option key={value} value={value}>{value}</option>)}</select></label><label><span>Familiarity</span><select value={topic.familiarity} onChange={event=>updateLocal(topic.id,'familiarity',Number(event.target.value))}>{[1,2,3,4,5].map(value=><option key={value} value={value}>{value}</option>)}</select></label></div></div><div className="topic-row-actions"><span className={`difficulty-pill ${topic.difficulty.toLowerCase()}`}>{topic.included?'IN PLAN':'EXCLUDED'}</span><button className="btn btn-quiet" aria-label={`Restore original ${topic.title}`} title="Restore extracted title and unit" onClick={()=>void saveTopic({...topic,title:topic.originalTitle??topic.title,unit:topic.originalUnit??topic.unit})}><RotateCcw size={13}/></button><button className="btn btn-subtle" disabled={saving} onClick={()=>void saveTopic(topic)}><Save size={13}/> Save</button><button className={`include-toggle ${topic.included?'on':''}`} aria-pressed={topic.included} onClick={()=>void saveTopic({...topic,included:!topic.included})}>{topic.included?'Included':'Excluded'}</button></div></article>)}</section>)}</div>}
          <div className="topic-editor-footer"><span><Clock3 size={13}/> Available preparation time is constrained by saved exam dates and preferences.</span><span>Source extraction is preserved for title and unit restore.</span></div>
        </> : <EmptyState icon={<BookOpen size={20}/>} title="Start with a subject" text="Add a subject and exam date. You can then upload a syllabus PDF or enter topics manually."/>}
      </section>
      <aside className="subjects-side-column">
        <form className="panel subject-create-card" onSubmit={event=>void addSubject(event)}><span className="panel-overline">EXAM TRACKING</span><h2>{subjects.length?'Add another subject':'Add a subject'}</h2><label className="field-label">Subject name<input className="text-input" required maxLength={120} value={subjectName} onChange={event=>setSubjectName(event.target.value)} placeholder="e.g. Algorithms"/></label><label className="field-label">Exam name<input className="text-input" required maxLength={120} value={examName} onChange={event=>setExamName(event.target.value)}/></label><label className="field-label">Exam date<input className="text-input" required type="date" min={new Date().toISOString().slice(0,10)} value={examDate} onChange={event=>setExamDate(event.target.value)}/></label><label className="field-label">Importance<select className="text-input" value={priority} onChange={event=>setPriority(Number(event.target.value))}>{[1,2,3,4,5].map(value=><option value={value} key={value}>{value} / 5</option>)}</select></label><button className="btn btn-primary full-btn" disabled={saving}><Plus size={14}/> Save subject</button></form>
        {activeSubject && <form className="panel add-topic-card" onSubmit={event=>void addManualTopics(event)}><span className="panel-overline">GROW THE SYLLABUS</span><h2>Add topics manually</h2><p>One topic per line. New topics can be edited before plan generation.</p><label className="field-label">Topic names<textarea className="text-input manual-topic-input" rows={5} value={newTopics} onChange={event=>setNewTopics(event.target.value)} placeholder={'Graph traversal\nDynamic programming\nNormalization'}/></label><button className="btn btn-primary full-btn" disabled={saving||!newTopics.trim()}><Plus size={14}/> Add topics</button></form>}
        <div className="panel topic-guidance"><span className="guidance-icon"><CheckSquare size={16}/></span><b>Your syllabus stays yours</b><p>Exclude items to keep them out of scheduling. Saved topics are preserved and can be included again.</p><p>Original extracted titles and units stay available for restoring your edits.</p></div>
      </aside>
    </div>
  </div>
}
