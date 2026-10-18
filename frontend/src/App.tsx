import { lazy, Suspense, useCallback, useEffect, useMemo, useState } from 'react'
import { AlertCircle, LoaderCircle, Sparkles, X } from 'lucide-react'
import { authApi, studyApi } from './api'
import { AppShell, type Screen } from './components/AppShell'
import type { AgentRun, Plan, Progress, Revision, Student, Subject, Task } from './types'
import { LandingPage } from './pages/LandingPage'
import { AuthPage } from './pages/AuthPage'
import { Onboarding } from './pages/Onboarding'
const Dashboard=lazy(()=>import('./pages/Dashboard').then(m=>({default:m.Dashboard})))
const TasksPage=lazy(()=>import('./pages/TasksPage').then(m=>({default:m.TasksPage})))
const CalendarPage=lazy(()=>import('./pages/CalendarPage').then(m=>({default:m.CalendarPage})))
const SyllabusPage=lazy(()=>import('./pages/SyllabusPage').then(m=>({default:m.SyllabusPage})))
const AgentPage=lazy(()=>import('./pages/AgentPage').then(m=>({default:m.AgentPage})))
const AnalyticsPage=lazy(()=>import('./pages/AnalyticsPage').then(m=>({default:m.AnalyticsPage})))
const RevisionsPage=lazy(()=>import('./pages/RevisionsPage').then(m=>({default:m.RevisionsPage})))
const SettingsPage=lazy(()=>import('./pages/SettingsPage').then(m=>({default:m.SettingsPage})))
const PlansPage=lazy(()=>import('./pages/PlansPage').then(m=>({default:m.PlansPage})))
const SubjectsPage=lazy(()=>import('./pages/SubjectsPage').then(m=>({default:m.SubjectsPage})))

type AuthMode='login'|'register'|null
export default function App(){
  const [student,setStudent]=useState<Student|null>(null);const [authMode,setAuthMode]=useState<AuthMode>(null);const [authLoading,setAuthLoading]=useState(true);const [authBusy,setAuthBusy]=useState(false);const [authError,setAuthError]=useState('');const [connectionError,setConnectionError]=useState('')
  const [screen,setScreen]=useState<Screen>('dashboard');const [subjects,setSubjects]=useState<Subject[]>([]);const [tasks,setTasks]=useState<Task[]>([]);const [progress,setProgress]=useState<Progress|null>(null);const [plan,setPlan]=useState<Plan|null>(null);const [draftPlan,setDraftPlan]=useState<Plan|null>(null);const [allPlans,setAllPlans]=useState<Plan[]>([]);const [activity,setActivity]=useState<AgentRun[]>([]);const [revisions,setRevisions]=useState<Revision[]>([])
  const [workspaceLoading,setWorkspaceLoading]=useState(false);const [workspaceError,setWorkspaceError]=useState('');const [agentBusy,setAgentBusy]=useState(false);const [revisionBusy,setRevisionBusy]=useState(false);const [toast,setToast]=useState('');const [onboardingDismissed,setOnboardingDismissed]=useState(false)
  useEffect(()=>{authApi.me().then(({student:s})=>setStudent(s)).catch((e:Error)=>{if(!e.message.includes('401')&&!e.message.toLowerCase().includes('sign in'))setConnectionError(e.message)}).finally(()=>setAuthLoading(false))},[])
  const refresh=useCallback(async()=>{if(!student)return;setWorkspaceLoading(true);setWorkspaceError('');try{
    const [s,t,p,allPlans,a,r,pr]=await Promise.all([studyApi.subjects(),studyApi.tasks(),studyApi.activePlan(),studyApi.plans(),studyApi.activity(),studyApi.revisions(),studyApi.progress()]);
    setSubjects(s);setTasks(t);setPlan(p);setAllPlans(allPlans);setDraftPlan(allPlans.find(x=>x.status==='DRAFT')??null);setActivity(a);setRevisions(r);setProgress(pr)
  }catch(e){const message=e instanceof Error?e.message:'Could not load your workspace';setWorkspaceError(message);if(message.toLowerCase().includes('sign in'))setStudent(null)}finally{setWorkspaceLoading(false)}},[student])
  useEffect(()=>{if(student)void refresh()},[student,refresh])
  useEffect(()=>{if(!toast)return;const timer=window.setTimeout(()=>setToast(''),4200);return()=>window.clearTimeout(timer)},[toast])
  const handleAuth=async(mode:'login'|'register',name:string,email:string,password:string)=>{setAuthBusy(true);setAuthError('');setConnectionError('');try{const {student:s}=mode==='register'?await authApi.register(name,email,password):await authApi.login(email,password);setStudent(s);setAuthMode(null);setScreen('dashboard');setOnboardingDismissed(false)}catch(e){setAuthError(e instanceof Error?e.message:'Unable to authenticate. Check that the backend is running.')}finally{setAuthBusy(false)}}
  const demoLogin=()=>void handleAuth('login','','demo@planner.local','DemoPass123!')
  const logout=async()=>{try{await authApi.logout()}catch{/* local sign out still clears the view */}setStudent(null);setSubjects([]);setTasks([]);setProgress(null);setPlan(null);setDraftPlan(null);setActivity([]);setRevisions([]);setAuthMode(null)}
  const onTaskStatus=async(task:Task,status:string)=>{if(status==='MISSED'&&!window.confirm(`Mark “${task.title}” as missed? You can ask the planner to adapt around it.`))return;try{await studyApi.updateTask(task.id,status);setToast(status==='COMPLETED'?'Session marked complete.':'Task status updated.');await refresh()}catch(e){setToast(e instanceof Error?e.message:'Could not update this task.')}}
  const runAgent=async(intent:string,note?:string)=>{setAgentBusy(true);setWorkspaceError('');try{const result=await studyApi.runAgent(intent,note);setToast(result.summary);setScreen(intent==='ADAPT_PLAN'?'revisions':'agent');await refresh()}catch(e){setWorkspaceError(e instanceof Error?e.message:'Agent run failed.');setScreen('agent')}finally{setAgentBusy(false)}}
  const decideRevision=async(id:string,decision:string)=>{const revision=revisions.find(r=>r.id===id);if(decision==='APPROVE'&&!window.confirm(`Apply this revision to ${revision?.proposedTasks.length??0} unfinished sessions? Completed study history will be preserved.`))return;setRevisionBusy(true);setWorkspaceError('');try{await studyApi.decideRevision(id,decision);setToast(decision==='APPROVE'?'Approved revision applied to your schedule.':'Revision rejected; your schedule remains unchanged.');await refresh()}catch(e){setWorkspaceError(e instanceof Error?e.message:'Could not update this revision.')}finally{setRevisionBusy(false)}}
  const approvePlan=async(candidate:Plan)=>{if(!candidate.id||!window.confirm(`Activate “${candidate.title}” with ${candidate.tasks.length} draft sessions?`))return;try{await studyApi.approvePlan(candidate.id);setToast('Study plan approved. Its tasks are now active.');await refresh()}catch(e){setWorkspaceError(e instanceof Error?e.message:'Could not approve the study plan.')}}
  const changePlanStatus=async(id:string,status:string)=>{try{await studyApi.updatePlanStatus(id,status);setToast(status==='PAUSED'?'Plan paused.':status==='ACTIVE'?'Plan resumed.':'Plan archived. Completed history is retained.');await refresh()}catch(e){setWorkspaceError(e instanceof Error?e.message:'Could not update this plan.')}}
  const moveTask=async(task:Task,date:string,start:string,duration:number)=>{await studyApi.rescheduleTask(task.id,date,start,duration);setToast(`Moved “${task.title}” to ${date}.`);await refresh()}
  const onboardingKey=student?`studyflow-onboarding-dismissed-${student.id}`:''
  const shouldOnboard=!!student&&subjects.length===0&&!onboardingDismissed&&!!onboardingKey&&sessionStorage.getItem(onboardingKey)!=='true'&&!workspaceLoading
  const afterOnboarding=()=>{setOnboardingDismissed(true);if(onboardingKey)sessionStorage.setItem(onboardingKey,'true');void refresh();setScreen('dashboard')}
  const content=useMemo(()=>{
    if(!student)return null
    switch(screen){
      case 'dashboard':return <Dashboard student={student} subjects={subjects} tasks={tasks} progress={progress} plan={plan} draftPlan={draftPlan} activity={activity} onAction={intent=>void runAgent(intent)} onApprovePlan={()=>draftPlan&&void approvePlan(draftPlan)} onTaskStatus={(t,s)=>void onTaskStatus(t,s)} onNavigate={setScreen}/>
      case 'plans':return <PlansPage plans={allPlans} subjects={subjects} onRefresh={()=>void refresh()} onApprove={candidate=>void approvePlan(candidate)} onStatus={changePlanStatus}/>
      case 'subjects':return <SubjectsPage subjects={subjects} onRefresh={()=>void refresh()}/>
      case 'tasks':return <TasksPage tasks={tasks} subjects={subjects} draftPlan={draftPlan} onUpdate={(t,s)=>void onTaskStatus(t,s)} onGenerate={()=>void runAgent(plan?'ADAPT_PLAN':'GENERATE_PLAN')} onApprovePlan={()=>draftPlan&&void approvePlan(draftPlan)}/>
      case 'calendar':return <CalendarPage tasks={tasks} subjects={subjects} onMove={moveTask}/>
      case 'syllabus':return <SyllabusPage subjects={subjects} onRefresh={()=>void refresh()}/>
      case 'agent':return <AgentPage activity={activity} plan={plan} onRun={(intent,note)=>void runAgent(intent,note)} busy={agentBusy}/>
      case 'analytics':return <AnalyticsPage progress={progress}/>
      case 'revisions':return <RevisionsPage revisions={revisions} plan={plan} tasks={tasks} onTaskStatus={(t,s)=>void onTaskStatus(t,s)} onRun={()=>void runAgent('ADAPT_PLAN')} onDecision={(id,d)=>void decideRevision(id,d)} busy={agentBusy||revisionBusy}/>
      case 'settings':return <SettingsPage student={student} subjects={subjects} onRefresh={()=>void refresh()}/>
    }
  },[student,screen,subjects,tasks,progress,plan,draftPlan,allPlans,activity,agentBusy,revisions,revisionBusy])
  if(authLoading)return <div className="full-loading"><span className="loading-mark"><Sparkles size={19}/></span><span>Preparing your workspace</span><LoaderCircle size={16} className="spin-icon"/></div>
  if(!student){return authMode?<AuthPage initialMode={authMode} onBack={()=>{setAuthMode(null);setAuthError('')}} onSubmit={handleAuth} loading={authBusy} error={authError}/>:<><LandingPage onLogin={()=>{setAuthMode('login');setAuthError('')}} onRegister={()=>{setAuthMode('register');setAuthError('')}} onDemo={demoLogin}/>{(connectionError||authError)&&<div className="connection-banner"><AlertCircle size={16}/><span>{connectionError||authError}</span><button className="icon-btn" onClick={()=>setConnectionError('')}><X size={14}/></button></div>}</>}
  if(shouldOnboard)return <Onboarding onComplete={afterOnboarding} onSkip={()=>{setOnboardingDismissed(true);if(onboardingKey)sessionStorage.setItem(onboardingKey,'true')}}/>
  return <AppShell student={student} screen={screen} onScreen={setScreen} onLogout={()=>void logout()} onCreatePlan={()=>setScreen('plans')} pendingRevisionCount={tasks.filter(task=>task.sessionType==='REVISION'&&task.status==='PLANNED'&&task.scheduledDate<=new Date().toISOString().slice(0,10)).length+revisions.filter(revision=>revision.status==='PENDING').length}>{workspaceLoading&&subjects.length===0?<div className="workspace-loading"><LoaderCircle size={18} className="spin-icon"/>Loading your saved study space…</div>:<>{workspaceError&&<div className="workspace-alert"><AlertCircle size={15}/><span>{workspaceError}</span><button className="icon-btn" aria-label="Dismiss error" onClick={()=>setWorkspaceError('')}><X size={14}/></button></div>}<Suspense fallback={<div className="workspace-loading"><LoaderCircle size={18} className="spin-icon"/>Loading this view…</div>}>{content}</Suspense></>}{toast&&<div role="status" className="toast-message"><CheckIcon/>{toast}<button aria-label="Dismiss notification" onClick={()=>setToast('')}><X size={14}/></button></div>}</AppShell>
}
function CheckIcon(){return <span className="toast-check">✓</span>}

