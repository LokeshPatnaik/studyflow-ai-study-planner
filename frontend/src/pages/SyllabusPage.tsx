import { useEffect, useRef, useState } from 'react'
import { AlertCircle, Check, CheckSquare, ChevronDown, FileText, FileUp, GitMerge, LoaderCircle, Plus, Sparkles, Trash2, UploadCloud, X } from 'lucide-react'
import type { ParsedTopic, Subject, SyllabusResult, Topic } from '../types'
import { studyApi } from '../api'
import { EmptyState, PageHeading } from '../components/ui'

const freshTopic = (title: string): ParsedTopic => ({ title, unit: null, chapter: null, description: null, difficulty: 'MEDIUM', estimatedMinutes: 60, importance: 3, familiarity: 3, included: true, sourceContent: [] })
const headingTitle = (text: string) => text.replace(/^\s*(?:(?:\d+(?:\.\d+){0,4}|[A-Z]|[IVXLCDM]+)[.)]?\s+)/i, '').trim()
const isUnitHeading = (text: string) => /^(unit|module|chapter|part)\s+/i.test(text.trim())

export function SyllabusPage({ subjects, onRefresh }: { subjects: Subject[]; onRefresh: () => void }) {
  const fileInput = useRef<HTMLInputElement>(null)
  const [selected, setSelected] = useState(subjects[0]?.id ?? '')
  const [existing, setExisting] = useState<Topic[]>([])
  const [file, setFile] = useState<File | null>(null)
  const [result, setResult] = useState<SyllabusResult | null>(null)
  const [parsed, setParsed] = useState<ParsedTopic[]>([])
  const [selectedSuggestions, setSelectedSuggestions] = useState<number[]>([])
  const [newTopicName, setNewTopicName] = useState('')
  const [processing, setProcessing] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [manual, setManual] = useState('')
  const [difficulty, setDifficulty] = useState('MEDIUM')

  useEffect(() => { if (!selected && subjects[0]) setSelected(subjects[0].id) }, [subjects, selected])
  useEffect(() => {
    if (!selected) { setExisting([]); return }
    studyApi.topics(selected).then(setExisting).catch(() => setExisting([]))
  }, [selected])

  const handleFile = (next?: File) => { if (next) { setFile(next); setResult(null); setParsed([]); setSelectedSuggestions([]); setError('') } }
  const edit = <K extends keyof ParsedTopic>(index: number, key: K, value: ParsedTopic[K]) => setParsed(old => old.map((topic, i) => i === index ? { ...topic, [key]: value } : topic))
  const parse = async () => {
    if (!file) return
    setProcessing(true); setError('')
    try { const res = await studyApi.parsePdf(file); setResult(res); setParsed(res.topics.map(topic => ({ ...topic }))) }
    catch (cause) { setError(cause instanceof Error ? cause.message : 'Could not process this PDF.') }
    finally { setProcessing(false) }
  }
  const addManual = async () => {
    const lines = manual.split('\n').map(line => line.trim()).filter(Boolean)
    if (!selected || !lines.length) return
    setSaving(true); setError('')
    try {
      const created = await studyApi.addTopics(selected, lines.map(title => ({ title, unit: 'Manual entry', difficulty, estimatedMinutes: difficulty === 'HARD' ? 90 : difficulty === 'EASY' ? 40 : 60 })))
      setExisting(old => [...old, ...created]); setManual(''); onRefresh()
    } catch (cause) { setError(cause instanceof Error ? cause.message : 'Could not save topics.') }
    finally { setSaving(false) }
  }
  const approveExtracted = async () => {
    if (!selected || !parsed.length) return
    setSaving(true); setError('')
    try {
      const created = await studyApi.addTopics(selected, parsed.map(topic => ({
        title: topic.title, unit: topic.unit ?? undefined, chapter: topic.chapter ?? undefined, description: topic.description ?? undefined,
        difficulty: topic.difficulty, estimatedMinutes: topic.estimatedMinutes, importance: topic.importance, familiarity: topic.familiarity,
        included: topic.included, sourceContent: topic.sourceContent,
      })), 'PDF', file?.name)
      setExisting(old => [...old, ...created]); setResult(null); setParsed([]); setSelectedSuggestions([]); setFile(null); onRefresh()
    } catch (cause) { setError(cause instanceof Error ? cause.message : 'Could not save extracted topics.') }
    finally { setSaving(false) }
  }
  const addReviewTopic = () => {
    const title = newTopicName.trim()
    if (!title) return
    setParsed(old => [...old, freshTopic(title)]); setNewTopicName('')
  }
  const removeSuggestion = (index: number) => {
    setParsed(old => old.filter((_, i) => i !== index))
    setSelectedSuggestions(old => old.filter(i => i !== index).map(i => i > index ? i - 1 : i))
  }
  const mergeSuggestions = () => {
    const indexes = [...selectedSuggestions].sort((a, b) => a - b)
    if (indexes.length < 2) return
    const chosen = indexes.map(index => parsed[index])
    const sourceContent = chosen.flatMap(topic => topic.sourceContent ?? [])
    const merged: ParsedTopic = {
      ...chosen[0], title: `${chosen[0].title} + ${chosen.length - 1} related topics`.slice(0, 180),
      description: chosen.map(topic => topic.description).filter(Boolean).join('\n'),
      difficulty: chosen.some(topic => topic.difficulty === 'HARD') ? 'HARD' : chosen.some(topic => topic.difficulty === 'MEDIUM') ? 'MEDIUM' : 'EASY',
      estimatedMinutes: Math.min(720, chosen.reduce((sum, topic) => sum + topic.estimatedMinutes, 0)),
      importance: Math.max(...chosen.map(topic => topic.importance)), familiarity: Math.min(...chosen.map(topic => topic.familiarity)),
      included: chosen.every(topic => topic.included), sourceContent,
    }
    setParsed(old => [...old.filter((_, index) => !indexes.includes(index)), merged])
    setSelectedSuggestions([])
  }
  const splitSuggestion = (index: number) => {
    const topic = parsed[index]
    const groups: { heading: string; content: NonNullable<ParsedTopic['sourceContent']> }[] = []
    const inherited = (topic.sourceContent ?? []).filter(source => isUnitHeading(source.text))
    const leadingContent: NonNullable<ParsedTopic['sourceContent']> = []
    let current: { heading: string; content: NonNullable<ParsedTopic['sourceContent']> } | null = null
    for (const source of topic.sourceContent ?? []) {
      if (source.heading && isUnitHeading(source.text)) continue
      if (source.heading) {
        if (current) groups.push(current)
        current = { heading: headingTitle(source.text), content: [...inherited, ...leadingContent, source] }
        leadingContent.length = 0
      } else if (current) current.content.push(source)
      else leadingContent.push(source)
    }
    if (current) groups.push(current)
    if (groups.length < 2) { setError('This topic needs at least two original headings to split.'); return }
    const pieces = groups.map(group => ({ ...topic, title: group.heading.slice(0, 180), description: group.content.filter(item => !item.heading).map(item => item.text).join(' '), sourceContent: group.content }))
    setParsed(old => [...old.slice(0, index), ...pieces, ...old.slice(index + 1)])
    setSelectedSuggestions([]); setError('')
  }
  const deleteSaved = async (topic: Topic) => {
    if (!selected || !window.confirm(`Delete “${topic.title}” from this syllabus?`)) return
    setSaving(true); setError('')
    try { await studyApi.deleteTopic(selected, topic.id); setExisting(old => old.filter(item => item.id !== topic.id)); onRefresh() }
    catch (cause) { setError(cause instanceof Error ? cause.message : 'Could not delete topic.') }
    finally { setSaving(false) }
  }

  return <div className="syllabus-page">
    <PageHeading eyebrow="ONE PLACE FOR WHAT YOU NEED TO LEARN" title="Syllabus library" description="Extract topics from a text-based PDF or add them by hand. Review every suggestion before it becomes part of your plan." action={subjects.length > 0 && <label className="subject-select"><span>Subject</span><select value={selected} onChange={event => setSelected(event.target.value)}>{subjects.map(subject => <option value={subject.id} key={subject.id}>{subject.name}</option>)}</select><ChevronDown size={13}/></label>} />
    {subjects.length === 0 ? <div className="panel syllabus-no-subject"><EmptyState icon={<FileText size={21}/>} title="Add a subject first" text="A syllabus needs a subject and examination date before topics can be saved."/></div> : <div className="syllabus-layout">
      <section className="syllabus-left">
        <div className="panel upload-panel">
          <div className="panel-title-row"><span className="upload-icon"><UploadCloud size={18}/></span><div><h2>Import a syllabus</h2><p>PDF up to 10 MB · selectable text only</p></div></div>
          <div className={`drop-zone ${file ? 'has-file' : ''}`} onDragOver={event => { event.preventDefault(); event.currentTarget.classList.add('drag-over') }} onDragLeave={event => event.currentTarget.classList.remove('drag-over')} onDrop={event => { event.preventDefault(); event.currentTarget.classList.remove('drag-over'); handleFile(event.dataTransfer.files[0]) }}>
            <input ref={fileInput} type="file" accept="application/pdf,.pdf" hidden onChange={event => handleFile(event.target.files?.[0])}/>
            {file ? <><span className="file-badge"><FileText size={19}/></span><b>{file.name}</b><small>{(file.size / 1024 / 1024).toFixed(2)} MB · ready to process</small><button className="file-remove" onClick={() => { setFile(null); setResult(null); setParsed([]) }} aria-label="Remove selected file"><X size={14}/></button></> : <><span className="drop-icon"><FileUp size={21}/></span><b>Drop your syllabus here</b><span>or <button onClick={() => fileInput.current?.click()}>choose a PDF</button> from your device</span><small>Maximum file size 10 MB</small></>}
          </div>
          <button className="btn btn-primary full-btn" disabled={!file || processing} onClick={() => void parse()}>{processing ? <><LoaderCircle size={15} className="spin-icon"/> Extracting selectable text…</> : <><Sparkles size={15}/> Extract topics for review</>}</button>
          {error && <div className="inline-alert error-alert" role="alert"><AlertCircle size={14}/>{error}</div>}
          {result && <div className={`parse-result ${result.requiresOcr ? 'parse-warning' : ''}`}><span>{result.requiresOcr ? <AlertCircle size={15}/> : <Check size={15}/>}</span><p>{result.message}</p></div>}
          {result?.parsed && parsed.length > 0 && <div className="extracted-review">
            <div className="review-title"><div><b>Suggested topics</b><small>{parsed.filter(topic => topic.included).length} included of {parsed.length} · edit before saving</small></div><span className="review-chip">NOT SAVED</span></div>
            <p className="source-preservation-note">Original syllabus headings and page references are retained below. Split or merge suggestions to fit your course.</p>
            <div className="review-toolbar"><label><input type="checkbox" checked={selectedSuggestions.length === parsed.length && parsed.length > 0} onChange={event => setSelectedSuggestions(event.target.checked ? parsed.map((_, index) => index) : [])}/> Select all</label><span>{selectedSuggestions.length} selected</span><button type="button" className="btn btn-quiet" disabled={selectedSuggestions.length < 2} onClick={mergeSuggestions}><GitMerge size={13}/> Merge selected</button></div>
            <div className="extracted-list">{parsed.map((topic, index) => {
              const pages = [...new Set((topic.sourceContent ?? []).map(source => source.page).filter((page): page is number => page !== null))]
              const headings = (topic.sourceContent ?? []).filter(source => source.heading && !isUnitHeading(source.text))
              return <article className={`extracted-topic-card ${topic.included ? '' : 'excluded'}`} key={`${index}-${topic.title}`}>
                <div className="extracted-topic-top"><label><input type="checkbox" aria-label={`Select ${topic.title}`} checked={selectedSuggestions.includes(index)} onChange={event => setSelectedSuggestions(old => event.target.checked ? [...old, index] : old.filter(value => value !== index))}/><span>Topic {String(index + 1).padStart(2, '0')}</span></label><div><button type="button" className="btn btn-quiet" disabled={headings.length < 2} title="Split by original headings" onClick={() => splitSuggestion(index)}>Split</button><button type="button" className="icon-btn" aria-label={`Delete ${topic.title}`} onClick={() => removeSuggestion(index)}><Trash2 size={14}/></button></div></div>
                <div className="extracted-topic-fields"><label className="field-label">Topic name<input className="text-input" aria-label="Topic title" value={topic.title} onChange={event => edit(index, 'title', event.target.value)}/></label><label className="field-label">Unit / module<input className="text-input" value={topic.unit ?? ''} onChange={event => edit(index, 'unit', event.target.value || null)}/></label><label className="field-label">Chapter<input className="text-input" value={topic.chapter ?? ''} onChange={event => edit(index, 'chapter', event.target.value || null)}/></label><label className="field-label">Difficulty<select className="text-input" value={topic.difficulty} onChange={event => edit(index, 'difficulty', event.target.value)}><option>EASY</option><option>MEDIUM</option><option>HARD</option></select></label><label className="field-label">Initial study estimate<input className="text-input" value="Randomly assigned on save · 10–20 min" readOnly/></label><label className="field-label">Importance<select className="text-input" value={topic.importance} onChange={event => edit(index, 'importance', Number(event.target.value))}>{[1, 2, 3, 4, 5].map(value => <option key={value} value={value}>{value} / 5</option>)}</select></label><label className="field-label">Familiarity<select className="text-input" value={topic.familiarity} onChange={event => edit(index, 'familiarity', Number(event.target.value))}>{[1, 2, 3, 4, 5].map(value => <option key={value} value={value}>{value} / 5</option>)}</select></label><label className="review-include"><input type="checkbox" checked={topic.included} onChange={event => edit(index, 'included', event.target.checked)}/><CheckSquare size={14}/>{topic.included ? 'Include in plan' : 'Excluded from plan'}</label></div>
                {topic.description && <p className="extracted-description">{topic.description}</p>}
                <details className="extracted-source"><summary>{headings.length} original headings · pages {pages.length ? pages.join(', ') : 'not available'}</summary><ul>{(topic.sourceContent ?? []).map((source, sourceIndex) => <li key={`${sourceIndex}-${source.text}`}><span>p. {source.page ?? '—'}</span>{source.heading ? <b>{source.text}</b> : source.text}</li>)}</ul></details>
              </article>
            })}</div>
            <div className="add-review-topic"><input className="text-input" aria-label="New topic name" placeholder="Add a topic to this review" value={newTopicName} onChange={event => setNewTopicName(event.target.value)} onKeyDown={event => { if (event.key === 'Enter') { event.preventDefault(); addReviewTopic() } }}/><button type="button" className="btn btn-subtle" disabled={!newTopicName.trim()} onClick={addReviewTopic}><Plus size={14}/> Add topic</button></div>
            <button className="btn btn-primary full-btn" disabled={saving || !parsed.length} onClick={() => void approveExtracted()}>{saving ? <><LoaderCircle className="spin-icon" size={14}/> Saving topics…</> : <>Approve and save reviewed topics <Check size={14}/></>}</button>
          </div>}
        </div>
        <div className="panel manual-panel"><div className="panel-title-row"><span className="manual-icon"><Plus size={17}/></span><div><h2>Add topics manually</h2><p>One topic per line. Review the list before saving.</p></div></div><label className="field-label">Topic names<textarea className="text-input manual-topic-input" value={manual} onChange={event => setManual(event.target.value)} placeholder={'Graph traversal\nDynamic programming\nGreedy algorithms'}/></label><div className="manual-footer"><label className="field-label">Default difficulty<select className="text-input" value={difficulty} onChange={event => setDifficulty(event.target.value)}><option>EASY</option><option>MEDIUM</option><option>HARD</option></select></label><button className="btn btn-subtle" disabled={!manual.trim() || saving} onClick={() => void addManual()}>{saving ? <LoaderCircle className="spin-icon" size={14}/> : <Plus size={14}/>} Add topics</button></div></div>
      </section>
      <section className="panel saved-topics-panel"><div className="saved-topics-head"><div><span className="panel-overline">APPROVED SYLLABUS</span><h2>{subjects.find(subject => subject.id === selected)?.name ?? 'Subject'} topics</h2></div><span className="topic-count">{existing.length} topics</span></div>
        {existing.length === 0 ? <EmptyState icon={<FileText size={19}/>} title="No saved topics yet" text="Use the import or manual form. Extracted suggestions need your approval first."/> : <div className="saved-topic-list">{existing.map((topic, index) => <div className="saved-topic" key={topic.id}><span className="saved-topic-index">{String(index + 1).padStart(2, '0')}</span><div><b>{topic.title}</b><small>{topic.unit || 'Syllabus'} · {topic.estimatedMinutes} min · importance {topic.importance}/5</small>{topic.sourceContent?.length ? <small>{[...new Set(topic.sourceContent.map(source => source.page).filter(page => page !== null))].length} source pages retained</small> : null}</div><span className={`difficulty-pill ${topic.included ? topic.difficulty.toLowerCase() : ''}`}>{topic.included ? topic.difficulty : 'EXCLUDED'}</span><button className="icon-btn" disabled={saving} aria-label={`Delete ${topic.title}`} onClick={() => void deleteSaved(topic)}><Trash2 size={14}/></button></div>)}</div>}
        <div className="topics-footer"><span><Check size={12}/> Only included, approved topics enter a plan</span><span>OCR is not enabled</span></div>
      </section>
    </div>}
  </div>
}
