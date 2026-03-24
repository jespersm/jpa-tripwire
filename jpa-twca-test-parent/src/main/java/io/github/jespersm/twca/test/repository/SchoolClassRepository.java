package io.github.jespersm.twca.test.repository;

import io.github.jespersm.twca.test.entity.SchoolClass;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SchoolClassRepository extends JpaRepository<SchoolClass, Long> {

    List<SchoolClass> findAllByCourseNameStartsWith(String name);

    Optional<SchoolClass> findByCourseCode(String courseCode);

    Optional<SchoolClass> findByMaxStudentsGreaterThanAndSemester(int limit, String semester);
}

