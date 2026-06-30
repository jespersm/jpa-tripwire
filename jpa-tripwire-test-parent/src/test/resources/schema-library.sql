-- Library Lending Tripwire Schema
-- This schema intentionally omits indexes and selected unique constraints so the tools have real findings.

DROP TABLE IF EXISTS loans CASCADE;
DROP TABLE IF EXISTS book_authors CASCADE;
DROP TABLE IF EXISTS book_copies CASCADE;
DROP TABLE IF EXISTS books CASCADE;
DROP TABLE IF EXISTS authors CASCADE;
DROP TABLE IF EXISTS borrowers CASCADE;
DROP TABLE IF EXISTS borrower_profiles CASCADE;
DROP TABLE IF EXISTS library_branches CASCADE;

CREATE TABLE library_branches (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    city VARCHAR(255) NOT NULL
);

CREATE TABLE authors (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL
);

-- INTENTIONAL ISSUE: isbn is unique in JPA but not unique in the schema.
CREATE TABLE books (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    isbn VARCHAR(255) NOT NULL,
    genre VARCHAR(255) NOT NULL
);

CREATE TABLE book_authors (
    book_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    PRIMARY KEY (book_id, author_id),
    FOREIGN KEY (book_id) REFERENCES books(id),
    FOREIGN KEY (author_id) REFERENCES authors(id)
);

CREATE TABLE borrower_profiles (
    id BIGSERIAL PRIMARY KEY,
    membership_level VARCHAR(255) NOT NULL,
    home_postal_code VARCHAR(255) NOT NULL
);

-- INTENTIONAL ISSUE: card_number is unique in JPA but not unique in the schema.
-- INTENTIONAL ISSUE: No index on profile_id (owning OneToOne).
CREATE TABLE borrowers (
    id BIGSERIAL PRIMARY KEY,
    full_name VARCHAR(255) NOT NULL,
    card_number VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    profile_id BIGINT NOT NULL,
    FOREIGN KEY (profile_id) REFERENCES borrower_profiles(id)
);

-- INTENTIONAL ISSUE: barcode is unique in JPA but not unique in the schema.
-- INTENTIONAL ISSUE: No indexes on book_id, branch_id, or the declared (branch_id, status) index.
CREATE TABLE book_copies (
    id BIGSERIAL PRIMARY KEY,
    barcode VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,
    book_id BIGINT NOT NULL,
    branch_id BIGINT NOT NULL,
    FOREIGN KEY (book_id) REFERENCES books(id),
    FOREIGN KEY (branch_id) REFERENCES library_branches(id)
);

-- INTENTIONAL ISSUE: No indexes on copy_id, borrower_id, or the declared (borrower_id, returned_at) index.
CREATE TABLE loans (
    id BIGSERIAL PRIMARY KEY,
    borrowed_at DATE NOT NULL,
    due_at DATE,
    returned_at DATE,
    copy_id BIGINT NOT NULL,
    borrower_id BIGINT NOT NULL,
    FOREIGN KEY (copy_id) REFERENCES book_copies(id),
    FOREIGN KEY (borrower_id) REFERENCES borrowers(id)
);
