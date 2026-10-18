import { useEffect, useState } from 'react'
import { Activity, BarChart3, CalendarDays, CheckSquare2, ChevronDown, ChevronLeft, ChevronRight, Command, GraduationCap, LayoutDashboard, LogOut, Menu, Moon, Settings2, Sparkles, Sun, UploadCloud, X, BookOpen, Layers3 } from 'lucide-react'
import type { ReactNode } from 'react'
import type { Student } from '../types'

export type Screen = 'dashboard' | 'plans' | 'subjects' | 'tasks' | 'calendar' | 'syllabus' | 'agent' | 'analytics' | 'revisions' | 'settings'
const nav: { id: Screen; label: string; icon: typeof LayoutDashboard; group: string }[] = [
  { id: 'dashboard', label: 'Overview', icon: LayoutDashboard, group: 'Workspace' },
  { id: 'plans', label: 'My study plans', icon: Layers3, group: 'Workspace' },
  { id: 'syllabus', label: 'Syllabus library', icon: UploadCloud, group: 'Workspace' },
  { id: 'calendar', label: 'Study calendar', icon: CalendarDays, group: 'Workspace' },
  { id: 'tasks', label: "Today's tasks", icon: CheckSquare2, group: 'Workspace' },
  { id: 'revisions', label: 'Revision center', icon: Activity, group: 'Workspace' },
  { id: 'subjects', label: 'Subjects & topics', icon: BookOpen, group: 'Workspace' },
  { id: 'analytics', label: 'Analytics', icon: BarChart3, group: 'Intelligence' },
  { id: 'agent', label: 'Agent activity', icon: Sparkles, group: 'Intelligence' },
  { id: 'settings', label: 'Settings', icon: Settings2, group: 'Personalize' },
]
const titles: Record<Screen, string> = { dashboard: 'Overview', plans: 'My study plans', subjects: 'Subjects & topics', tasks: "Today's tasks", calendar: 'Study calendar', syllabus: 'Syllabus library', agent: 'Agent activity', revisions: 'Revision center', analytics: 'Analytics', settings: 'Settings' }

export function Brand({ compact = false }: { compact?: boolean }) {
  return <div className={`brand ${compact ? 'brand-compact' : ''}`}><span className="brand-mark"><GraduationCap size={18} strokeWidth={2.1} /></span>{!compact && <span className="brand-name">studyflow<span className="brand-dot">.</span></span>}</div>
}

export function AppShell({ student, screen, onScreen, onLogout, onCreatePlan, pendingRevisionCount = 0, children }: { student: Student; screen: Screen; onScreen: (s: Screen) => void; onLogout: () => void; onCreatePlan?: () => void; pendingRevisionCount?: number; children: ReactNode }) {
  const [collapsed, setCollapsed] = useState(() => { try { return localStorage.getItem('studyflow-sidebar-collapsed') === 'true' } catch { return false } })
  const [theme, setTheme] = useState<'dark'|'light'>(() => { try { return localStorage.getItem('studyflow-theme') === 'light' ? 'light' : 'dark' } catch { return 'dark' } })
  const [mobileOpen, setMobileOpen] = useState(false)
  const [profileOpen, setProfileOpen] = useState(false)
  const focusTaskSearch = () => {
    onScreen('tasks')
    window.setTimeout(() => document.getElementById('task-search-input')?.focus(), 0)
  }
  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault()
        focusTaskSearch()
      }
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [onScreen])
  useEffect(() => {
    document.documentElement.dataset.theme = theme
    try { localStorage.setItem('studyflow-theme', theme) } catch { /* theme still applies for this session */ }
  }, [theme])
  useEffect(() => { try { localStorage.setItem('studyflow-sidebar-collapsed', String(collapsed)) } catch { /* ignore unavailable storage */ } }, [collapsed])
  const navItems = (group: string) => nav.filter((item) => item.group === group).map(({ id, label, icon: Icon }) => (
    <button key={id} className={`nav-item ${screen === id ? 'active' : ''}`} onClick={() => { onScreen(id); setMobileOpen(false) }} aria-current={screen === id ? 'page' : undefined}>
      <Icon size={17} strokeWidth={1.8} /><span>{label}</span>{id === 'revisions' && pendingRevisionCount > 0 && <i className="nav-count">{pendingRevisionCount}</i>}
    </button>
  ))
  return <div className={`app-shell ${collapsed ? 'sidebar-collapsed' : ''}`}>
    {mobileOpen && <button className="mobile-scrim" aria-label="Close menu" onClick={() => setMobileOpen(false)} />}
    <aside className={`sidebar ${mobileOpen ? 'mobile-open' : ''}`}>
      <div className="sidebar-brand"><Brand /><button className="icon-btn collapse-toggle" aria-label={collapsed?'Expand sidebar':'Collapse sidebar'} onClick={() => setCollapsed(!collapsed)}><ChevronLeft size={16} /></button><button className="icon-btn mobile-close" aria-label="Close menu" onClick={() => setMobileOpen(false)}><X size={17} /></button></div>
      <div className="workspace-switcher"><span className="workspace-avatar">{student.name.slice(0, 1).toUpperCase()}</span><span className="workspace-label"><strong>My workspace</strong><small>{student.demo ? 'Demo account' : 'Personal workspace'}</small></span><ChevronDown size={14} /></div>
      <nav className="sidebar-nav" aria-label="Main navigation">
        <div className="nav-group"><span className="nav-label">WORKSPACE</span>{navItems('Workspace')}</div>
        <div className="nav-group"><span className="nav-label">INTELLIGENCE</span>{navItems('Intelligence')}</div>
        <div className="nav-group"><span className="nav-label">PERSONALIZE</span>{navItems('Personalize')}</div>
      </nav>
      <div className="sidebar-bottom">
        <div className="agent-status"><span className="status-orb" /><div><strong>Planner ready</strong><small>{student.demo ? 'Sample workspace' : 'Connected to your account'}</small></div><ChevronRight size={14} /></div>
        <button className="profile-row" onClick={() => setProfileOpen(!profileOpen)} aria-expanded={profileOpen}>
          <span className="avatar">{student.name.split(' ').map((s) => s[0]).join('').slice(0, 2).toUpperCase()}</span><span className="profile-text"><strong>{student.name}</strong><small>{student.email}</small></span><ChevronDown size={14} />
        </button>
        {profileOpen && <div className="profile-menu"><button onClick={onLogout}><LogOut size={15} /> Sign out</button></div>}
      </div>
    </aside>
    <main className="main-area">
      <header className="topbar"><div className="topbar-title"><button className="icon-btn mobile-menu" aria-label="Open navigation" onClick={() => setMobileOpen(true)}><Menu size={19} /></button><span className="breadcrumb-muted">Workspace</span><ChevronRight size={13} /><strong>{titles[screen]}</strong></div><div className="topbar-actions"><button className="theme-toggle" aria-label={`Switch to ${theme==='dark'?'light':'dark'} theme`} title={`Switch to ${theme==='dark'?'light':'dark'} theme`} onClick={()=>setTheme(theme==='dark'?'light':'dark')}>{theme==='dark'?<Sun size={16}/>:<Moon size={16}/>}</button><button className="btn btn-primary create-plan-top" onClick={onCreatePlan??(()=>onScreen('plans'))}><Sparkles size={14}/>Create plan</button><button className="command-hint" aria-label="Search study tasks" title="Search study tasks (Ctrl/⌘ K)" onClick={focusTaskSearch}><Command size={12} /><span>K</span><small>Search</small></button><span className="topbar-divider" /><span className="today-label">{new Intl.DateTimeFormat('en', { weekday: 'short', month: 'short', day: 'numeric' }).format(new Date())}</span><button className="topbar-avatar" aria-label="Profile" onClick={() => onScreen('settings')}>{student.name.slice(0, 1).toUpperCase()}</button></div></header>
      {student.demo && <div className="demo-banner"><span className="demo-pulse" /> DEMONSTRATION DATA <span>Changes are saved to this local demo account.</span></div>}
      <div className="page-content">{children}</div>
    </main>
  </div>
}

