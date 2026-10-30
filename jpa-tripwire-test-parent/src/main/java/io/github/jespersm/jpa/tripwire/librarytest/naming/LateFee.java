package io.github.jespersm.jpa.tripwire.librarytest.naming;

import io.github.jespersm.jpa.tripwire.librarytest.entity.Loan;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * JOINED subclass: {@code loan_id} lives in {@code late_fees}, while {@code borrower_id} lives in {@code fees}.
 */
@Entity
@Table(name = "late_fees")
public class LateFee extends Fee {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_id", nullable = false)
    private Loan loan;

    public Loan getLoan() {
        return loan;
    }
}
