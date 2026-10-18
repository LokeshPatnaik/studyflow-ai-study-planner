ALTER TABLE syllabus_topics ADD COLUMN description VARCHAR(1000);
ALTER TABLE syllabus_topics ADD COLUMN chapter VARCHAR(120);
ALTER TABLE syllabus_topics ADD COLUMN importance INTEGER NOT NULL DEFAULT 3 CHECK (importance BETWEEN 1 AND 5);
ALTER TABLE syllabus_topics ADD COLUMN familiarity INTEGER NOT NULL DEFAULT 3 CHECK (familiarity BETWEEN 1 AND 5);
ALTER TABLE syllabus_topics ADD COLUMN included BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE syllabus_topics ADD COLUMN original_title VARCHAR(180);
ALTER TABLE syllabus_topics ADD COLUMN original_unit VARCHAR(120);
ALTER TABLE syllabus_topics ADD COLUMN original_chapter VARCHAR(120);
ALTER TABLE syllabus_topics ADD COLUMN original_description VARCHAR(1000);

UPDATE syllabus_topics
SET original_title = title,
    original_unit = unit;

ALTER TABLE study_tasks ADD COLUMN session_type VARCHAR(24) NOT NULL DEFAULT 'LEARN';
ALTER TABLE study_tasks ADD COLUMN revision_number INTEGER NOT NULL DEFAULT 0;
ALTER TABLE study_tasks ADD COLUMN actual_minutes INTEGER;
ALTER TABLE study_tasks ADD COLUMN notes VARCHAR(1000);
ALTER TABLE study_tasks ADD COLUMN confidence INTEGER CHECK (confidence BETWEEN 1 AND 5);

CREATE INDEX idx_task_plan_topic_type
  ON study_tasks(plan_id, topic_id, session_type);
