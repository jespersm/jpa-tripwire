-- Sample data for the library lending Tripwire tests.

INSERT INTO library_branches (name, city) VALUES
('Central Library', 'Copenhagen'),
('Harbor Branch', 'Aarhus');

INSERT INTO authors (name) VALUES
('Ursula K. Le Guin'),
('Octavia E. Butler'),
('Kim Stanley Robinson');

INSERT INTO books (title, isbn, genre) VALUES
('The Left Hand of Darkness', '9780441478125', 'Science Fiction'),
('Parable of the Sower', '9780446675505', 'Science Fiction'),
('The Ministry for the Future', '9780316300131', 'Climate Fiction');

INSERT INTO book_authors (book_id, author_id) VALUES
(1, 1),
(2, 2),
(3, 3);

INSERT INTO borrower_profiles (membership_level, home_postal_code) VALUES
('Resident', '2100'),
('Researcher', '8000'),
('Student', '2200');

INSERT INTO borrowers (full_name, card_number, email, profile_id) VALUES
('Nora Holm', 'CARD-1001', 'nora.holm@example.test', 1),
('Mads Kirk', 'CARD-1002', 'mads.kirk@example.test', 2),
('Ida Berg', 'CARD-1003', 'ida.berg@example.test', 3);

INSERT INTO book_copies (barcode, status, book_id, branch_id) VALUES
('COPY-0001', 'AVAILABLE', 1, 1),
('COPY-0002', 'ON_LOAN', 1, 1),
('COPY-0003', 'ON_LOAN', 2, 1),
('COPY-0004', 'AVAILABLE', 2, 2),
('COPY-0005', 'ON_LOAN', 3, 2);

INSERT INTO loans (borrowed_at, due_at, returned_at, copy_id, borrower_id) VALUES
('2024-09-01', '2024-09-22', NULL, 2, 1),
('2024-09-02', '2024-09-23', NULL, 3, 1),
('2024-08-15', '2024-09-05', '2024-08-30', 1, 2),
('2024-09-03', '2024-09-24', NULL, 5, 3);
