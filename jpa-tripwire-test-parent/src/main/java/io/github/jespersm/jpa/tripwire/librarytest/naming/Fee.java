package io.github.jespersm.jpa.tripwire.librarytest.naming;

import io.github.jespersm.jpa.tripwire.librarytest.entity.Borrower;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Root of a JOINED hierarchy. The join column is named in camelCase, which Spring's default naming
 * strategy turns into {@code borrower_id} in the schema.
 */
@Entity
@Inheritance(strategy = InheritanceType.JOINED)
@Table(name = "fees")
public class Fee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private boolean paid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "borrowerId", nullable = false)
    private Borrower borrower;

    public Long getId() {
        return id;
    }

    public boolean isPaid() {
        return paid;
    }

    public Borrower getBorrower() {
        return borrower;
    }
}
