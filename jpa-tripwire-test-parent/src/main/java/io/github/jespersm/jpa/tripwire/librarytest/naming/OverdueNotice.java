package io.github.jespersm.jpa.tripwire.librarytest.naming;

import io.github.jespersm.jpa.tripwire.librarytest.entity.Loan;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

/**
 * SINGLE_TABLE subclass without a table of its own.
 */
@Entity
public class OverdueNotice extends Notice {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loanId")
    private Loan loan;

    public Loan getLoan() {
        return loan;
    }
}
