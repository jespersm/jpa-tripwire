package io.github.jespersm.jpa.tripwire.test.students.repository;

import io.github.jespersm.jpa.tripwire.test.students.entity.Teacher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TeacherRepository extends JpaRepository<Teacher, Long> {

    Optional<Teacher> findByEmail(String email);
}

