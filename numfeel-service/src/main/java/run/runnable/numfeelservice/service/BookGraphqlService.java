package run.runnable.numfeelservice.service;

import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingFieldSelectionSet;
import graphql.schema.SelectedField;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.runnable.numfeelservice.web.ApiException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * REST vs GraphQL comparison demo - GraphQL-side business logic.
 * <p>
 * This intentionally uses one query entry point and then checks the client selection set to decide
 * whether to load authors or reviews. The deeper the requested selection, the more DB calls the
 * server performs. That is the intentional N+1 cost model the demo makes visible:
 * <ul>
 *   <li>Only scalar fields such as {@code price/title}: 1 SQL call</li>
 *   <li>Add {@code author}: one author query per book (1+N)</li>
 *   <li>Add {@code reviews}: one reviews query per book (1+N+N)</li>
 * </ul>
 * The real cost is exposed through {@link CostMeta} so the frontend can compare it with REST's
 * predictable one-query list endpoint.
 */
@Service
public class BookGraphqlService {

    private static final int MAX_LIMIT = 100;
    private static final int MAX_REVIEWS_PER_BOOK = 5;

    private final DatabaseClient db;
    private final BookStoreDataInitializer dataInitializer;

    public BookGraphqlService(DatabaseClient db, BookStoreDataInitializer dataInitializer) {
        this.db = db;
        this.dataInitializer = dataInitializer;
    }

    /**
     * Resolve one GraphQL request: load the books first, inspect the selection set, optionally load
     * authors/reviews, and report the real DB cost.
     *
     * @param env   GraphQL execution context used to inspect selected fields
     * @param limit client requested limit; defaults to 10 when null
     * @return book list plus cost metadata
     */
    public Mono<BookListResult> query(DataFetchingEnvironment env, Integer limit) {
        if (!dataInitializer.isReady()) {
            return Mono.error(new ApiException(503, "Bookstore data is still initializing. Please retry shortly."));
        }
        int n = clamp(limit != null ? limit : 10);
        DataFetchingFieldSelectionSet booksSelection = booksSelection(env);
        boolean needAuthor = contains(booksSelection, "author");
        boolean needReviews = contains(booksSelection, "reviews");

        long start = System.nanoTime();
        AtomicInteger dbCalls = new AtomicInteger(0);
        AtomicInteger rowsLoaded = new AtomicInteger(0);

        return loadBooks(n, dbCalls, rowsLoaded)
                .flatMap(books -> Flux.fromIterable(books)
                        .concatMap(book -> enrich(book, needAuthor, needReviews, dbCalls, rowsLoaded))
                        .collectList())
                .map(books -> new BookListResult(books, new CostMeta(
                        dbCalls.get(), rowsLoaded.get(), (int) ((System.nanoTime() - start) / 1_000_000))));
    }

    /** Enrich one book with author and reviews as requested, intentionally simulating N+1. */
    private Mono<BookData> enrich(BookData book, boolean needAuthor, boolean needReviews,
                                  AtomicInteger dbCalls, AtomicInteger rowsLoaded) {
        Mono<BookData> result = Mono.just(book);
        if (needAuthor) {
            result = result.flatMap(b -> loadAuthor(b, dbCalls, rowsLoaded));
        }
        if (needReviews) {
            result = result.flatMap(b -> loadReviews(b, dbCalls, rowsLoaded));
        }
        return result;
    }

    /** Load one author with one SQL query, intentionally simulating N+1. */
    private Mono<BookData> loadAuthor(BookData book, AtomicInteger dbCalls, AtomicInteger rowsLoaded) {
        dbCalls.incrementAndGet();
        return db.sql("SELECT id, name, country, bio FROM bookstore_authors WHERE id = :id")
                .bind("id", book.authorId())
                .map((row, meta) -> new AuthorData(
                        ((Number) row.get("id")).intValue(),
                        row.get("name", String.class),
                        row.get("country", String.class),
                        row.get("bio", String.class)))
                .one()
                .map(author -> {
                    rowsLoaded.incrementAndGet();
                    return rebuild(book, author, book.reviews());
                })
                .defaultIfEmpty(rebuild(book, null, book.reviews()));
    }

    /** Load reviews for one book with one SQL query, intentionally simulating N+1. */
    private Mono<BookData> loadReviews(BookData book, AtomicInteger dbCalls, AtomicInteger rowsLoaded) {
        dbCalls.incrementAndGet();
        return db.sql("SELECT id, rating, content, reviewer FROM bookstore_reviews "
                        + "WHERE book_id = :bookId ORDER BY created_at DESC LIMIT :limit")
                .bind("bookId", book.id())
                .bind("limit", MAX_REVIEWS_PER_BOOK)
                .map((row, meta) -> new BookReviewData(
                        ((Number) row.get("id")).intValue(),
                        ((Number) row.get("rating")).intValue(),
                        row.get("content", String.class),
                        row.get("reviewer", String.class)))
                .all()
                .collectList()
                .map(reviews -> {
                    rowsLoaded.addAndGet(reviews.size());
                    return rebuild(book, book.author(), reviews);
                });
    }

    /** Rebuild a book node with the loaded author and reviews. */
    private BookData rebuild(BookData book, AuthorData author, List<BookReviewData> reviews) {
        return new BookData(book.id(), book.title(), book.authorId(), author, book.isbn(), book.category(),
                book.price(), book.rating(), book.pages(), book.stock(), book.publishedYear(),
                book.description(), reviews);
    }

    /** Load the base book list without author/reviews: one SQL query, sorted by rating. */
    private Mono<List<BookData>> loadBooks(int limit, AtomicInteger dbCalls, AtomicInteger rowsLoaded) {
        dbCalls.incrementAndGet();
        return db.sql("SELECT b.id, b.title, b.author_id, b.isbn, b.category, b.price, b.rating, "
                        + "b.pages, b.stock, b.published_year, b.description "
                        + "FROM bookstore_books b ORDER BY b.rating DESC, b.id LIMIT :limit")
                .bind("limit", limit)
                .map((row, meta) -> new BookData(
                        ((Number) row.get("id")).intValue(),
                        row.get("title", String.class),
                        ((Number) row.get("author_id")).intValue(),
                        null,
                        row.get("isbn", String.class),
                        row.get("category", String.class),
                        ((Number) row.get("price")).doubleValue(),
                        ((Number) row.get("rating")).doubleValue(),
                        ((Number) row.get("pages")).intValue(),
                        ((Number) row.get("stock")).intValue(),
                        ((Number) row.get("published_year")).intValue(),
                        row.get("description", String.class),
                        new ArrayList<>()))
                .all()
                .collectList()
                .map(books -> {
                    rowsLoaded.addAndGet(books.size());
                    return books;
                });
    }

    /** Clamp the query limit. */
    private int clamp(int limit) {
        return Math.max(1, Math.min(limit, MAX_LIMIT));
    }

    /** Detect whether a field is requested in the selection set. */
    private boolean contains(DataFetchingFieldSelectionSet sel, String field) {
        if (sel == null) {
            return false;
        }
        if (sel.contains(field) || sel.contains(field + "/*")) {
            return true;
        }
        return sel.getFields().stream()
                .map(SelectedField::getName)
                .anyMatch(field::equals);
    }

    /** Extract the inner books selection set so author/reviews selection can be detected. */
    private DataFetchingFieldSelectionSet booksSelection(DataFetchingEnvironment env) {
        List<SelectedField> inner = env.getSelectionSet().getFields("books");
        if (inner != null && !inner.isEmpty() && inner.get(0).getSelectionSet() != null) {
            return inner.get(0).getSelectionSet();
        }
        return env.getSelectionSet();
    }

    // GraphQL return models

    /** Wrapper for the books query, including cost metadata. */
    public record BookListResult(List<BookData> books, CostMeta meta) {
    }

    /** Cost metadata for one query. */
    public record CostMeta(int dbCalls, int rowsLoaded, int elapsedMs) {
    }

    /** Book node; author/reviews are populated only when requested by the client. */
    public record BookData(
            Integer id,
            String title,
            Integer authorId,
            AuthorData author,
            String isbn,
            String category,
            Double price,
            Double rating,
            Integer pages,
            Integer stock,
            Integer publishedYear,
            String description,
            List<BookReviewData> reviews) {
    }

    /** Author node. */
    public record AuthorData(Integer id, String name, String country, String bio) {
    }

    /** Review node. */
    public record BookReviewData(Integer id, Integer rating, String content, String reviewer) {
    }
}
