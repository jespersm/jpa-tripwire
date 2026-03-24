-- Indexinator Demo Schema
-- This schema INTENTIONALLY omits indexes on foreign key columns
-- to demonstrate Indexinator's detection capabilities!

-- Drop tables if they exist (for clean restart)
DROP TABLE IF EXISTS class_enrollments CASCADE;
DROP TABLE IF EXISTS classes CASCADE;
DROP TABLE IF EXISTS students CASCADE;
DROP TABLE IF EXISTS teachers CASCADE;

-- Teachers table
CREATE TABLE teachers (
    id BIGSERIAL PRIMARY KEY,
    first_name VARCHAR(255) NOT NULL,
    last_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    employee_id VARCHAR(255) UNIQUE,
    department VARCHAR(255)
);

-- Students table
-- INTENTIONAL ISSUE: No index on advisor_id (FK to teachers)
CREATE TABLE students (
    id BIGSERIAL PRIMARY KEY,
    first_name VARCHAR(255) NOT NULL,
    last_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    student_number VARCHAR(255) NOT NULL UNIQUE,
    enrollment_date DATE,
    major VARCHAR(255),
    advisor_id BIGINT,
    FOREIGN KEY (advisor_id) REFERENCES teachers(id)
);
-- Note: PostgreSQL would normally auto-create an index on advisor_id, but we're
-- explicitly avoiding it by using NO INDEX strategy in our FK definition approach

-- Classes table
-- INTENTIONAL ISSUE: No index on teacher_id (FK to teachers)
CREATE TABLE classes (
    id BIGSERIAL PRIMARY KEY,
    course_name VARCHAR(255) NOT NULL,
    code VARCHAR(255) NOT NULL, -- Intentional forgot this should be UNIQUE !
    semester VARCHAR(255),
    max_students INTEGER,
    teacher_id BIGINT NOT NULL,
    FOREIGN KEY (teacher_id) REFERENCES teachers(id)
);

-- Class enrollments (many-to-many join table)
CREATE TABLE class_enrollments (
    class_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    PRIMARY KEY (class_id, student_id),
    FOREIGN KEY (class_id) REFERENCES classes(id),
    FOREIGN KEY (student_id) REFERENCES students(id)
);

-- Note: We intentionally DO NOT create indexes on:
-- - students.advisor_id
-- - classes.teacher_id
--
-- These are the exact issues that Indexinator should detect!
--
-- To fix these issues, you would run:
-- CREATE INDEX idx_students_advisor_id ON students(advisor_id);
-- CREATE INDEX idx_classes_teacher_id ON classes(teacher_id);

