package io.github.jespersm.jpa.tripwire.test.library.naming;

import io.github.jespersm.jpa.tripwire.test.library.entity.Author;
import io.github.jespersm.jpa.tripwire.test.library.entity.Book;
import org.springframework.data.repository.Repository;

import java.util.List;

/**
 * Queries through a collection: the join goes via the {@code book_authors} join table, not a column in {@code books}.
 */
public interface BookSearchRepository extends Repository<Book, Long> {

    List<Book> findByGenreAndAuthorsContaining(String genre, Author author);
}
