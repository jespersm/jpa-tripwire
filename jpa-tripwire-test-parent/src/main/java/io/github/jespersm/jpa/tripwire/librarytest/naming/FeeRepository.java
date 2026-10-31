package io.github.jespersm.jpa.tripwire.librarytest.naming;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FeeRepository extends JpaRepository<Fee, Long> {

    Optional<Fee> findByIdAndPaidIsFalse(Long id);

    List<Fee> findAllByPaidIsFalse();
}
