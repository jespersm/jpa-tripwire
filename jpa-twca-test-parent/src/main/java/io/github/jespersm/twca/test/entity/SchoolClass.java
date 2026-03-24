package io.github.jespersm.twca.test.entity;

import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;

/**
 * SchoolClass entity (named SchoolClass to avoid conflict with java.lang.Class)
 * Demonstrates:
 * - ManyToOne relationship with Teacher (MISSING INDEX - intentional issue!)
 * - ManyToMany relationship with Students
 * - Course code with different column name in DB
 */
@Entity
@Table(name = "classes")
public class SchoolClass {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String courseName;

    @Column(unique = true, nullable = false, name = "code")
    private String courseCode;

    private String semester;

    private Integer maxStudents;

    // Many classes can be taught by one teacher
    // INTENTIONAL ISSUE: No @Index on teacher_id column!
    @ManyToOne
    @JoinColumn(name = "teacher_id", nullable = false)
    private Teacher teacher;

    // Many-to-many relationship with students
    @ManyToMany
    @JoinTable(
            name = "class_enrollments",
            joinColumns = @JoinColumn(name = "class_id"),
            inverseJoinColumns = @JoinColumn(name = "student_id")
    )
    private Set<Student> students = new HashSet<>();

    // Constructors
    public SchoolClass() {
    }

    public SchoolClass(String courseName, String courseCode, String semester, Integer maxStudents, Teacher teacher) {
        this.courseName = courseName;
        this.courseCode = courseCode;
        this.semester = semester;
        this.maxStudents = maxStudents;
        this.teacher = teacher;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCourseName() {
        return courseName;
    }

    public void setCourseName(String courseName) {
        this.courseName = courseName;
    }

    public String getCourseCode() {
        return courseCode;
    }

    public void setCourseCode(String courseCode) {
        this.courseCode = courseCode;
    }

    public String getSemester() {
        return semester;
    }

    public void setSemester(String semester) {
        this.semester = semester;
    }

    public Integer getMaxStudents() {
        return maxStudents;
    }

    public void setMaxStudents(Integer maxStudents) {
        this.maxStudents = maxStudents;
    }

    public Teacher getTeacher() {
        return teacher;
    }

    public void setTeacher(Teacher teacher) {
        this.teacher = teacher;
    }

    public Set<Student> getStudents() {
        return students;
    }

    public void setStudents(Set<Student> students) {
        this.students = students;
    }

    public void addStudent(Student student) {
        this.students.add(student);
        student.getClasses().add(this);
    }

    public void removeStudent(Student student) {
        this.students.remove(student);
        student.getClasses().remove(this);
    }
}

