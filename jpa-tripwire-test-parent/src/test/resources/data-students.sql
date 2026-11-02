-- Sample data for Indexinator Test - Spring Boot 3.5

-- Insert teachers
INSERT INTO teachers (first_name, last_name, email, employee_id, department) VALUES
('John', 'Smith', 'john.smith@school.edu', 'T001', 'Computer Science'),
('Sarah', 'Jones', 'sarah.jones@school.edu', 'T002', 'Mathematics'),
('Michael', 'Brown', 'michael.brown@school.edu', 'T003', 'Computer Science');

-- Insert students (with advisors)
INSERT INTO students (first_name, last_name, email, student_number, enrollment_date, major, advisor_id) VALUES
('Alice', 'Johnson', 'alice.j@student.edu', 'S001', '2023-09-01', 'Computer Science', 1),
('Bob', 'Williams', 'bob.w@student.edu', 'S002', '2023-09-01', 'Computer Science', 1),
('Charlie', 'Davis', 'charlie.d@student.edu', 'S003', '2023-09-01', 'Mathematics', 2),
('Diana', 'Miller', 'diana.m@student.edu', 'S004', '2024-01-15', 'Computer Science', 3),
('Eve', 'Wilson', 'eve.w@student.edu', 'S005', '2024-01-15', 'Mathematics', 2);

-- Insert classes
INSERT INTO classes (course_name, code, semester, max_students, teacher_id) VALUES
('Introduction to Programming', 'CS101', 'Fall 2024', 30, 1),
('Data Structures', 'CS201', 'Fall 2024', 25, 1),
('Calculus I', 'MATH101', 'Fall 2024', 35, 2),
('Database Systems', 'CS301', 'Fall 2024', 20, 3);

-- Insert class enrollments (many-to-many)
INSERT INTO class_enrollments (class_id, student_id) VALUES
-- CS101 enrollments
(1, 1),  -- Alice in CS101
(1, 2),  -- Bob in CS101
(1, 4),  -- Diana in CS101
-- CS201 enrollments
(2, 1),  -- Alice in CS201
(2, 2),  -- Bob in CS201
-- MATH101 enrollments
(3, 1),  -- Alice in MATH101
(3, 3),  -- Charlie in MATH101
(3, 5),  -- Eve in MATH101
-- CS301 enrollments
(4, 2),  -- Bob in CS301
(4, 4);  -- Diana in CS301

