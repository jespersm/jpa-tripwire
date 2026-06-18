package io.github.jespersm.jpa.tripwire.librarytest.repository;

import io.github.jespersm.jpa.tripwire.librarytest.entity.BookCopy;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BookCopyRepository extends JpaRepository<BookCopy, Long> {

    List<BookCopy> findAllByStatusAndBranchName(String status, String branchName);

    boolean existsByBarcode(String barcode);

    List<BookCopy> findAllByBookIsbn(String isbn);
}
