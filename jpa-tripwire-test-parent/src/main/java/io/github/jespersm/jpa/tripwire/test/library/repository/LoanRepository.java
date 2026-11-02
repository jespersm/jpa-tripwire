package io.github.jespersm.jpa.tripwire.test.library.repository;

import io.github.jespersm.jpa.tripwire.test.library.entity.Loan;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LoanRepository extends JpaRepository<Loan, Long> {

    List<Loan> findAllByBorrowerCardNumberAndReturnedAtIsNull(String cardNumber);

    List<Loan> findAllByReturnedAtIsNull();
}
