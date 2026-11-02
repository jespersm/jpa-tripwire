package io.github.jespersm.jpa.tripwire.test.students.repository;

import io.github.jespersm.jpa.tripwire.test.students.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StudentRepository extends JpaRepository<Student, Long> {
}

