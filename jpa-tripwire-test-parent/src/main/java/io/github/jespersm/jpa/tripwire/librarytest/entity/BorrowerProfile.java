package io.github.jespersm.jpa.tripwire.librarytest.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "borrower_profiles")
public class BorrowerProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String membershipLevel;

    @Column(nullable = false)
    private String homePostalCode;

    @OneToOne(mappedBy = "profile")
    private Borrower borrower;

    public BorrowerProfile() {
    }

    public BorrowerProfile(String membershipLevel, String homePostalCode) {
        this.membershipLevel = membershipLevel;
        this.homePostalCode = homePostalCode;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMembershipLevel() {
        return membershipLevel;
    }

    public void setMembershipLevel(String membershipLevel) {
        this.membershipLevel = membershipLevel;
    }

    public String getHomePostalCode() {
        return homePostalCode;
    }

    public void setHomePostalCode(String homePostalCode) {
        this.homePostalCode = homePostalCode;
    }

    public Borrower getBorrower() {
        return borrower;
    }

    public void setBorrower(Borrower borrower) {
        this.borrower = borrower;
    }
}
