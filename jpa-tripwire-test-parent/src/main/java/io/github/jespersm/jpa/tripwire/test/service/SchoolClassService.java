package io.github.jespersm.jpa.tripwire.test.service;

import io.github.jespersm.jpa.tripwire.test.entity.SchoolClass;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.springframework.stereotype.Service;

@Service
public record SchoolClassService(EntityManager entityManager) {
    public SchoolClass loadByQuery(String code) {
        return entityManager.createQuery(
                        "select sc from SchoolClass sc where sc.courseCode = :courseCode",
                        SchoolClass.class
                )
                .setParameter("courseCode", code)
                .getSingleResult();
    }

    public SchoolClass loadByCriteriaBuilder(String code) {

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<SchoolClass> q = cb.createQuery(SchoolClass.class);
        Root<SchoolClass> root = q.from(SchoolClass.class);
        q.select(root).where(cb.equal(root.get("courseCode"), code));
        return entityManager.createQuery(q).getSingleResult();
    }
}
