import type { ReactNode } from 'react'
import { ArrowUpRight, ChevronRight } from 'lucide-react'
export function PageHeading({ eyebrow, title, description, action }: { eyebrow: string; title: string; description?: string; action?: ReactNode }) {
  return <div className="page-heading"><div><span className="page-eyebrow">{eyebrow}</span><h1>{title}</h1>{description&&<p>{description}</p>}</div>{action&&<div className="page-heading-action">{action}</div>}</div>
}
export function MetricCard({ label, value, suffix, detail, icon, tone = 'violet', trend }: { label: string; value: string | number; suffix?: string; detail: string; icon: ReactNode; tone?: string; trend?: string }) {
  return <article className="metric-card"><div className="metric-top"><span className={`metric-icon ${tone}`}>{icon}</span>{trend&&<span className="metric-trend">{trend}<ArrowUpRight size={12}/></span>}</div><span className="metric-label">{label}</span><strong className="metric-value">{value}<small>{suffix}</small></strong><span className="metric-detail">{detail}</span></article>
}
export function SectionHeading({ title, action, onAction }: { title: string; action?: string; onAction?: () => void }) {
  return <div className="section-heading"><h2>{title}</h2>{action&&<button className="text-action" onClick={onAction}>{action}<ChevronRight size={14}/></button>}</div>
}
export function EmptyState({ icon, title, text, action }: { icon: ReactNode; title: string; text: string; action?: ReactNode }) {
  return <div className="empty-state"><span className="empty-icon">{icon}</span><b>{title}</b><p>{text}</p>{action}</div>
}
export function localDateKey(date: Date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}
export function formatDate(value: string | null | undefined, opts?: Intl.DateTimeFormatOptions) {
  if (!value) return 'Date not set'
  return new Intl.DateTimeFormat('en', opts ?? { month: 'short', day: 'numeric' }).format(new Date(`${value.slice(0,10)}T12:00:00`))
}
export function formatTime(value: string | null | undefined) { return value ? new Intl.DateTimeFormat('en', { hour: 'numeric', minute: '2-digit' }).format(new Date(`2020-01-01T${value}`)) : '—' }

