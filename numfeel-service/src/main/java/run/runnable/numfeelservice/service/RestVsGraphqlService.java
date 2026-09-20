package run.runnable.numfeelservice.service;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import run.runnable.numfeelservice.controller.dto.BookDetailDTO;
import run.runnable.numfeelservice.controller.dto.BookStoreStatusDTO;
import run.runnable.numfeelservice.controller.dto.CatalogItemDTO;
import run.runnable.numfeelservice.controller.dto.CatalogResponseDTO;
import run.runnable.numfeelservice.controller.dto.ReviewDTO;
import run.runnable.numfeelservice.web.ApiException;

import java.util.List;

/**
 * REST vs GraphQL comparison demo - REST-side business logic.
 * <p>
 * Provides three REST query shapes:
 * <ul>
 *   <li>Full payload: returns every catalog field to show generic DTO over-fetching</li>
 *   <li>Slim endpoint: returns only the fields this page needs</li>
 *   <li>Single book detail plus reviews</li>
 * </ul>
 */
@Service
public class RestVsGraphqlService {

    private static final int MAX_LIMIT = 100;

    private final DatabaseClient db;
    private final BookStoreDataInitializer dataInitializer;

    public RestVsGraphqlService(DatabaseClient db, BookStoreDataInitializer dataInitializer) {
        this.db = db;
        this.dataInitializer = dataInitializer;
    }

    /**
     * Query the catalog. {@code full=true} returns all fields; {@code full=false} returns the slim shape.
     *
     * @param full  whether to use the full payload shape
     * @param limit result limit, capped at {@link #MAX_LIMIT}
     * @return catalog response with server latency and SQL call count
     */
    public Mono<CatalogResponseDTO> catalog(boolean full, int limit) {
        if (!dataInitializer.isReady()) {
            return Mono.error(new ApiException(503, "Bookstore data is still initializing. Please retry shortly."));
        }
        int n = Math.max(1, Math.min(limit, MAX_LIMIT));
        String columns = full
                ? "b.id, b.title, a.name AS author, b.isbn, b.category, b.price, b.rating, "
                        + "b.pages, b.stock, b.published_year, b.description"
                : "b.id, b.title, a.name AS author, b.price, b.rating";
        String sql = "SELECT " + columns + " FROM bookstore_books b "
                + "JOIN bookstore_authors a ON a.id = b.author_id "
                + "ORDER BY b.rating DESC, b.id LIMIT :limit";

        long start = System.nanoTime();
        return db.sql(sql).bind("limit", n)
                .map((row, meta) -> toCatalogItem(full, row))
                .all()
                .collectList()
                .map(items -> {
                    long elapsedMs = (System.nanoTime() - start) / 1_000_000;
                    return new CatalogResponseDTO(items.size(), items, elapsedMs, 1);
                });
    }

    /**
     * Query one book plus up to 10 reviews.
     *
     * @param id book ID
     * @return book detail DTO; emits 404 when the book does not exist
     */
    public Mono<BookDetailDTO> book(int id) {
        if (!dataInitializer.isReady()) {
            return Mono.error(new ApiException(503, "Bookstore data is still initializing. Please retry shortly."));
        }
        if (id <= 0) {
            return Mono.error(ApiException.badRequest("Invalid book ID"));
        }
        String bookSql = "SELECT b.id, b.title, a.name AS author, b.isbn, b.category, b.price, b.rating, "
                + "b.pages, b.stock, b.published_year, b.description "
                + "FROM bookstore_books b JOIN bookstore_authors a ON a.id = b.author_id WHERE b.id = :id";

        return db.sql(bookSql).bind("id", id)
                .map((row, meta) -> toCatalogItem(true, row))
                .one()
                .switchIfEmpty(Mono.error(new ApiException(404, "Book not found")))
                .flatMap(book -> db.sql("SELECT id, rating, content, reviewer, created_at "
                                + "FROM bookstore_reviews WHERE book_id = :bookId ORDER BY created_at DESC LIMIT 10")
                        .bind("bookId", id)
                        .map((row, meta) -> new ReviewDTO(
                                ((Number) row.get("id")).longValue(),
                                ((Number) row.get("rating")).intValue(),
                                row.get("content", String.class),
                                row.get("reviewer", String.class),
                                ((Number) row.get("created_at")).longValue()))
                        .all()
                        .collectList()
                        .map(reviews -> new BookDetailDTO(book, reviews)));
    }

    /**
     * Query dataset initialization status.
     *
     * @return row counts for the three bookstore tables and readiness flag
     */
    public Mono<BookStoreStatusDTO> status() {
        return count("bookstore_authors")
                .zipWith(count("bookstore_books"))
                .zipWith(count("bookstore_reviews"))
                .map(t -> new BookStoreStatusDTO(
                        t.getT1().getT1() >= BookStoreDataInitializer.AUTHOR_COUNT
                                && t.getT1().getT2() >= BookStoreDataInitializer.BOOK_COUNT,
                        t.getT1().getT1(),
                        t.getT1().getT2(),
                        t.getT2()));
    }

    /** Count rows in one table. */
    private Mono<Long> count(String table) {
        return db.sql("SELECT COUNT(*) AS c FROM " + table)
                .map((row, meta) -> ((Number) row.get("c")).longValue())
                .first()
                .defaultIfEmpty(0L);
    }

    /** Build a catalog item DTO from one SQL row. */
    private CatalogItemDTO toCatalogItem(boolean full, io.r2dbc.spi.Row row) {
        if (full) {
            return new CatalogItemDTO(
                    ((Number) row.get("id")).intValue(),
                    row.get("title", String.class),
                    row.get("author", String.class),
                    row.get("isbn", String.class),
                    row.get("category", String.class),
                    ((Number) row.get("price")).doubleValue(),
                    ((Number) row.get("rating")).doubleValue(),
                    ((Number) row.get("pages")).intValue(),
                    ((Number) row.get("stock")).intValue(),
                    ((Number) row.get("published_year")).intValue(),
                    row.get("description", String.class));
        }
        return new CatalogItemDTO(
                ((Number) row.get("id")).intValue(),
                row.get("title", String.class),
                row.get("author", String.class),
                null, null,
                ((Number) row.get("price")).doubleValue(),
                ((Number) row.get("rating")).doubleValue(),
                null, null, null, null);
    }
}
