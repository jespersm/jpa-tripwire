package io.github.jespersm.twca.test.entity;

import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;

/**
 * Teacher entity
 * Demonstrates: One-to-Many relationship with Classes
 */
@Entity
@Table(name = "teachers")
public class Teacher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Column(nullable = false)
    private String email;

    @Column(name = "employee_id", unique = true)
    private String employeeId;

    private String department;

    // One teacher can teach many classes
    @OneToMany(mappedBy = "teacher")
    private Set<SchoolClass> classes = new HashSet<>();

    // One teacher can advise many students
    @OneToMany(mappedBy = "advisor")
    private Set<Student> advisees = new HashSet<>();

    // Constructors
    public Teacher() {
    }

    public Teacher(String firstName, String lastName, String email, String employeeId, String department) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.employeeId = employeeId;
        this.department = department;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(String employeeId) {
        this.employeeId = employeeId;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public Set<SchoolClass> getClasses() {
        return classes;
    }

    public void setClasses(Set<SchoolClass> classes) {
        this.classes = classes;
    }

    public Set<Student> getAdvisees() {
        return advisees;
    }

    public void setAdvisees(Set<Student> advisees) {
        this.advisees = advisees;
    }
}

