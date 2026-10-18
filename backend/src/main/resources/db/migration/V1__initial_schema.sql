CREATE TABLE students (
  id UUID PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  email VARCHAR(254) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  is_demo BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE student_preferences (
  student_id UUID PRIMARY KEY REFERENCES students(id) ON DELETE CASCADE,
  daily_study_minutes INTEGER NOT NULL CHECK (daily_study_minutes BETWEEN 30 AND 720),
  session_minutes INTEGER NOT NULL CHECK (session_minutes BETWEEN 20 AND 180),
  break_minutes INTEGER NOT NULL CHECK (break_minutes BETWEEN 0 AND 60),
  preferred_days VARCHAR(32) NOT NULL,
  day_start TIME NOT NULL,
  day_end TIME NOT NULL,
  revision_preference VARCHAR(32) NOT NULL
);
CREATE TABLE subjects (
  id UUID PRIMARY KEY,
  student_id UUID NOT NULL REFERENCES students(id) ON DELETE CASCADE,
  name VARCHAR(120) NOT NULL,
  color VARCHAR(16) NOT NULL,
  priority INTEGER NOT NULL CHECK (priority BETWEEN 1 AND 5),
  created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_subject_student ON subjects(student_id);
CREATE TABLE syllabus_topics (
  id UUID PRIMARY KEY,
  subject_id UUID NOT NULL REFERENCES subjects(id) ON DELETE CASCADE,
  title VARCHAR(180) NOT NULL,
  unit VARCHAR(120),
  difficulty VARCHAR(16) NOT NULL,
  estimated_minutes INTEGER NOT NULL CHECK (estimated_minutes BETWEEN 15 AND 720),
  approved BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE INDEX idx_topic_subject ON syllabus_topics(subject_id);
CREATE TABLE syllabi (
  id UUID PRIMARY KEY,
  student_id UUID NOT NULL REFERENCES students(id) ON DELETE CASCADE,
  subject_id UUID NOT NULL REFERENCES subjects(id) ON DELETE CASCADE,
  source_name VARCHAR(180) NOT NULL,
  source_type VARCHAR(16) NOT NULL,
  topic_count INTEGER NOT NULL CHECK (topic_count > 0),
  status VARCHAR(16) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_syllabus_student_created ON syllabi(student_id, created_at DESC);
CREATE TABLE examinations (
  id UUID PRIMARY KEY,
  student_id UUID NOT NULL REFERENCES students(id) ON DELETE CASCADE,
  subject_id UUID NOT NULL REFERENCES subjects(id) ON DELETE CASCADE,
  name VARCHAR(120) NOT NULL,
  exam_date DATE NOT NULL
);
CREATE INDEX idx_exam_student_date ON examinations(student_id, exam_date);
CREATE TABLE study_plans (
  id UUID PRIMARY KEY,
  student_id UUID NOT NULL REFERENCES students(id) ON DELETE CASCADE,
  title VARCHAR(120) NOT NULL,
  status VARCHAR(24) NOT NULL,
  ai_mode VARCHAR(24) NOT NULL,
  summary VARCHAR(1400),
  created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_plan_student_status ON study_plans(student_id, status);
CREATE TABLE study_tasks (
  id UUID PRIMARY KEY,
  plan_id UUID NOT NULL REFERENCES study_plans(id) ON DELETE CASCADE,
  student_id UUID NOT NULL REFERENCES students(id) ON DELETE CASCADE,
  subject_id UUID NOT NULL REFERENCES subjects(id) ON DELETE CASCADE,
  topic_id UUID REFERENCES syllabus_topics(id) ON DELETE SET NULL,
  title VARCHAR(180) NOT NULL,
  description VARCHAR(1000),
  scheduled_date DATE NOT NULL,
  start_time TIME,
  end_time TIME,
  duration_minutes INTEGER NOT NULL CHECK (duration_minutes BETWEEN 10 AND 360),
  priority VARCHAR(12) NOT NULL,
  status VARCHAR(16) NOT NULL,
  exam_date DATE,
  completed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT ck_task_time_order CHECK (start_time IS NULL OR end_time > start_time)
);
CREATE INDEX idx_task_student_date ON study_tasks(student_id, scheduled_date);
CREATE INDEX idx_task_plan_status ON study_tasks(plan_id, status);
CREATE TABLE agent_runs (
  id UUID PRIMARY KEY,
  student_id UUID NOT NULL REFERENCES students(id) ON DELETE CASCADE,
  stage VARCHAR(20) NOT NULL,
  status VARCHAR(20) NOT NULL,
  mode VARCHAR(24) NOT NULL,
  summary VARCHAR(1600),
  stage_history VARCHAR(200) NOT NULL DEFAULT 'OBSERVE',
  created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_agent_student_created ON agent_runs(student_id, created_at DESC);
CREATE TABLE tool_executions (
  id UUID PRIMARY KEY,
  run_id UUID NOT NULL REFERENCES agent_runs(id) ON DELETE CASCADE,
  student_id UUID NOT NULL REFERENCES students(id) ON DELETE CASCADE,
  tool_name VARCHAR(64) NOT NULL,
  status VARCHAR(20) NOT NULL,
  input_summary VARCHAR(1000),
  output_summary VARCHAR(1400),
  error_message VARCHAR(800),
  created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_tool_student_created ON tool_executions(student_id, created_at DESC);
CREATE TABLE schedule_revisions (
  id UUID PRIMARY KEY,
  student_id UUID NOT NULL REFERENCES students(id) ON DELETE CASCADE,
  plan_id UUID NOT NULL REFERENCES study_plans(id) ON DELETE CASCADE,
  status VARCHAR(20) NOT NULL,
  reason VARCHAR(1400) NOT NULL,
  proposed_json TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  decided_at TIMESTAMPTZ
);
CREATE INDEX idx_revision_student_created ON schedule_revisions(student_id, created_at DESC);

