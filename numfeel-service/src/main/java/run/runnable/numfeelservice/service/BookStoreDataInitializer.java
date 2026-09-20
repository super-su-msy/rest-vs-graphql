package run.runnable.numfeelservice.service;

import lombok.extern.slf4j.Slf4j;
import net.datafaker.Faker;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

/**
 * Data initializer for the REST vs GraphQL comparison demo.
 * <p>
 * On startup, this idempotently seeds {@code bookstore_authors / bookstore_books /
 * bookstore_reviews} with bookstore-style demo data: 200 authors, 10,000 books, and roughly
 * 25,000-30,000 reviews. If the tables are missing, under-filled, or still contain the older
 * Chinese seed data, the three tables are truncated and rebuilt together so relationships stay
 * consistent.
 * <p>
 * This component creates its own tables first so it does not race the global
 * {@code SchemaInitializer}.
 */
@Slf4j
@Component
public class BookStoreDataInitializer {

    /** Author count. */
    static final int AUTHOR_COUNT = 200;

    /** Book count: large enough to make payload and query-cost differences visible. */
    static final int BOOK_COUNT = 10_000;

    /** Maximum reviews per book: 0-5, averaging about 2.5 reviews per book. */
    private static final int REVIEWS_PER_BOOK_MAX = 5;

    /** Insert batch size, keeping placeholder counts manageable. */
    private static final int BATCH_SIZE = 500;

    private static final Faker EN = new Faker();

    private static final String[] COUNTRIES = {
            "United States", "United Kingdom", "Canada", "Australia", "Germany", "France",
            "Japan", "India", "Brazil", "South Africa"
    };

    private static final String[] CATEGORIES = {
            "Science Fiction", "History", "Literature", "Psychology", "Economics",
            "Popular Science", "Mystery", "Lifestyle", "Art", "Philosophy"
    };

    private static final String[] SUBJECTS = {
            "Quantum Mechanics", "Deep Learning", "A Brief History of Time", "Interstellar Travel",
            "Human History", "Poetry", "Insects", "Renaissance Art", "Wine", "Coffee Roasting",
            "Game Theory", "Urban Maps", "Lost Civilizations", "Space Elevators", "Blockchain",
            "Neuroscience", "Classical Music", "Detective Archives", "Wilderness Survival",
            "Deep Sea Exploration", "Artificial Intelligence", "Topology", "Weather Systems",
            "Archaeology", "Robotics", "Vaccines", "Mathematical Beauty", "Cosmic Dust",
            "Cryptography", "Cooking Science", "Dreams", "Ancient Rome", "Rainforest Ecology",
            "Transistors", "Faster-than-Light Travel", "Behavioral Economics"
    };

    private static final String[] MODIFIERS = {
            "", "New", "Illustrated", "Compact", "Ultimate", "Pocket", "Cambridge",
            "Oxford", "Midnight", "Field"
    };

    private static final String[] FORMS = {
            "Introduction", "Short History", "Guide", "Notes", "Mystery", "Collected Essays",
            "Primer", "Twelve Lectures", "Study", "Conversations", "Atlas", "Handbook",
            "Meditations", "Field Guide", "Reader"
    };

    private static final String[] DESC_SENTENCES = {
            "The book starts with fundamentals and gradually moves toward advanced questions without losing the reader.",
            "The author replaces dry formulas with concrete field examples, making it approachable for beginners.",
            "The third section has especially strong experimental design and practically invites you to reproduce it.",
            "If you only read one chapter, read chapter seven; it condenses the book's method into a compact case study.",
            "The terminology is consistent, the prose is clear, and the diagrams carry real explanatory weight.",
            "The further-reading list at the end of each chapter is unusually practical and well sourced.",
            "Data and charts cite their sources, making the argument easy to audit.",
            "The production quality is excellent for this price range, especially the typography and layout.",
            "The appendices by practitioners are almost worth the price on their own.",
            "It took a full weekend to read, but the information density never felt punishing.",
            "The book handles contested topics carefully, arguing from evidence rather than posture.",
            "It works both as a course companion and as a desk reference.",
            "The first three chapters are a little slow, but the later payoff is worth it.",
            "Many readers treat it as an introduction, but it works even better as a second book on the topic.",
            "The open questions in the final chapter remain active research problems."
    };

    private static final String[] BIO_TEMPLATES = {
            "Born in %s in %s, this author has spent decades researching and writing about %s.",
            "Born in %s in %s, this author worked in %s before turning to nonfiction.",
            "A %s-born writer whose work on %s has been translated into multiple languages.",
            "Born in %s in %s, this award-winning author now writes from a small coastal town.",
            "Born in %s, this former journalist and editor became known for accessible books about %s."
    };

    private static final String[] REVIEW_TEMPLATES = {
            "Read it in one sitting and immediately recommended it to a teammate.",
            "Great layout. The content is a little light, but it works well as an introduction.",
            "The chapter-three case study is strong enough to revisit.",
            "Thicker than expected, but it reads quickly.",
            "Clear writing, clean examples, and enough depth to stay useful.",
            "The author is restrained and does not oversell the conclusion.",
            "A few sections feel repetitive, but the examples are worth it.",
            "The rating may be a little generous; I would call it three and a half stars.",
            "Every dataset is sourced, which earned my trust.",
            "Paused halfway through and plan to return when I have more time.",
            "Works well as a desk reference.",
            "The ending feels rushed, almost like the manuscript was cut short.",
            "Paper and printing quality improve the reading experience.",
            "Bought it on a friend's recommendation and was not disappointed.",
            "Better after one introductory book; as a first read it may feel dense.",
            "Worth rereading once a year.",
            "The ebook annotations are more convenient than the print edition.",
            "A little expensive, but worth it."
    };

    private final DatabaseClient db;

    /** Whether data is ready; endpoints return 503 until initialization avoids rebuild-window errors. */
    private final java.util.concurrent.atomic.AtomicBoolean dataReady =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    public BookStoreDataInitializer(DatabaseClient db) {
        this.db = db;
    }

    /**
     * Run after startup: create tables, check shape/counts, and rebuild when needed.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        createTables()
                .then(countAll())
                .flatMap(counts -> {
                    boolean countReady = counts[0] >= AUTHOR_COUNT && counts[1] >= BOOK_COUNT && counts[2] > 0;
                    return englishDatasetReady()
                            .flatMap(englishReady -> {
                                if (countReady && englishReady) {
                                    log.info("BookStore data ready: {} authors, {} books, {} reviews",
                                            counts[0], counts[1], counts[2]);
                                    return Mono.empty();
                                }
                                return truncateAll()
                                        .then(seedAuthors())
                                        .then(seedBooks())
                                        .then(seedReviews())
                                        .then(countAll())
                                        .doOnNext(fresh -> log.info(
                                                "BookStore data seeded: {} authors, {} books, {} reviews",
                                                fresh[0], fresh[1], fresh[2]))
                                        .then();
                            });
                })
                .doOnSuccess(v -> dataReady.set(true))
                .doOnError(e -> log.warn("BookStore data init failed (service continues): {}", e.getMessage()))
                .onErrorComplete()
                .subscribe();
    }

    /**
     * Whether the dataset is ready.
     *
     * @return true when the three tables are initialized and safe to query
     */
    public boolean isReady() {
        return dataReady.get();
    }

    /**
     * Create this module's three tables independently of the global schema initializer.
     *
     * @return completion signal
     */
    private Mono<Void> createTables() {
        return Mono.fromCallable(() -> List.of(
                "CREATE TABLE IF NOT EXISTS bookstore_authors (\n" +
                        "    id         INT AUTO_INCREMENT PRIMARY KEY,\n" +
                        "    name       VARCHAR(64)  NOT NULL COMMENT 'Author name',\n" +
                        "    country    VARCHAR(32)  NOT NULL COMMENT 'Country',\n" +
                        "    birth_year SMALLINT     NOT NULL COMMENT 'Birth year',\n" +
                        "    bio        VARCHAR(512) NOT NULL COMMENT 'Author bio',\n" +
                        "    INDEX idx_bs_author_name (name)\n" +
                        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",
                "CREATE TABLE IF NOT EXISTS bookstore_books (\n" +
                        "    id             INT AUTO_INCREMENT PRIMARY KEY,\n" +
                        "    title          VARCHAR(128) NOT NULL COMMENT 'Book title',\n" +
                        "    author_id      INT          NOT NULL COMMENT 'Author ID',\n" +
                        "    isbn           VARCHAR(20)  NOT NULL COMMENT 'ISBN',\n" +
                        "    category       VARCHAR(32)  NOT NULL COMMENT 'Category',\n" +
                        "    price          DOUBLE       NOT NULL COMMENT 'Price',\n" +
                        "    rating         DOUBLE       NOT NULL COMMENT 'Rating 1.0-5.0',\n" +
                        "    pages          SMALLINT     NOT NULL COMMENT 'Page count',\n" +
                        "    stock          INT          NOT NULL COMMENT 'Stock',\n" +
                        "    published_year SMALLINT     NOT NULL COMMENT 'Published year',\n" +
                        "    description    VARCHAR(512) NOT NULL COMMENT 'Book description',\n" +
                        "    INDEX idx_bs_book_author (author_id),\n" +
                        "    INDEX idx_bs_book_category (category)\n" +
                        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",
                "CREATE TABLE IF NOT EXISTS bookstore_reviews (\n" +
                        "    id         BIGINT AUTO_INCREMENT PRIMARY KEY,\n" +
                        "    book_id    INT          NOT NULL COMMENT 'Book ID',\n" +
                        "    rating     TINYINT      NOT NULL COMMENT 'Rating 1-5',\n" +
                        "    content    VARCHAR(256) NOT NULL COMMENT 'Review content',\n" +
                        "    reviewer   VARCHAR(64)  NOT NULL COMMENT 'Reviewer name',\n" +
                        "    created_at BIGINT       NOT NULL COMMENT 'Review timestamp in ms',\n" +
                        "    INDEX idx_bs_review_book (book_id)\n" +
                        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"))
                .flatMapMany(Flux::fromIterable)
                .concatMap(sql -> db.sql(sql).fetch().rowsUpdated().onErrorResume(e -> Mono.empty()))
                .then();
    }

    /**
     * Count rows in the three tables.
     *
     * @return {@code [authors, books, reviews]} row counts
     */
    private Mono<long[]> countAll() {
        return count("bookstore_authors")
                .zipWith(count("bookstore_books"))
                .zipWith(count("bookstore_reviews"))
                .map(t -> new long[]{t.getT1().getT1(), t.getT1().getT2(), t.getT2()});
    }

    /** Count rows in one table. */
    private Mono<Long> count(String table) {
        return db.sql("SELECT COUNT(*) AS c FROM " + table)
                .map((row, meta) -> ((Number) row.get("c")).longValue())
                .first()
                .defaultIfEmpty(0L);
    }

    /**
     * Check whether the current rows look like the English seed dataset. This catches older local
     * databases that already have enough rows but were seeded with the previous Chinese data.
     */
    private Mono<Boolean> englishDatasetReady() {
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < CATEGORIES.length; i++) {
            if (i > 0) {
                placeholders.append(',');
            }
            placeholders.append(":c").append(i);
        }
        var spec = db.sql("SELECT COUNT(*) AS c FROM bookstore_books WHERE category IN (" + placeholders + ")");
        for (int i = 0; i < CATEGORIES.length; i++) {
            spec = spec.bind("c" + i, CATEGORIES[i]);
        }
        return spec.map((row, meta) -> ((Number) row.get("c")).longValue())
                .first()
                .map(count -> count >= BOOK_COUNT)
                .defaultIfEmpty(false);
    }

    /** Truncate the three tables, used only during startup rebuilds. */
    private Mono<Void> truncateAll() {
        return Flux.just("bookstore_reviews", "bookstore_books", "bookstore_authors")
                .concatMap(t -> db.sql("TRUNCATE TABLE " + t).fetch().rowsUpdated())
                .then();
    }

    /** Generate and insert 200 authors. */
    private Mono<Void> seedAuthors() {
        List<Object[]> rows = new ArrayList<>(AUTHOR_COUNT);
        for (int i = 0; i < AUTHOR_COUNT; i++) {
            String name = EN.name().fullName();
            String country = pick(COUNTRIES);
            int birthYear = 1920 + EN.random().nextInt(76);
            String subject = pick(SUBJECTS);
            String bio = String.format(pick(BIO_TEMPLATES), birthYear, country, subject);
            rows.add(new Object[]{name, country, birthYear, bio});
        }
        return insertBatches("bookstore_authors", "name, country, birth_year, bio", rows, 4);
    }

    /** Generate and insert 10,000 books. */
    private Mono<Void> seedBooks() {
        List<Object[]> rows = new ArrayList<>(BOOK_COUNT);
        for (int i = 0; i < BOOK_COUNT; i++) {
            int authorId = 1 + EN.random().nextInt(AUTHOR_COUNT);
            String title = randomTitle();
            String isbn = EN.code().isbn13();
            String category = pick(CATEGORIES);
            double price = Math.round((19 + EN.random().nextDouble() * 180) * 10.0) / 10.0;
            double rating = Math.round((1.0 + EN.random().nextDouble() * 4.0) * 10.0) / 10.0;
            int pages = 80 + EN.random().nextInt(720);
            int stock = EN.random().nextInt(500);
            int publishedYear = 1960 + EN.random().nextInt(66);
            String description = randomDescription();
            rows.add(new Object[]{title, authorId, isbn, category, price, rating, pages, stock, publishedYear, description});
        }
        return insertBatches("bookstore_books",
                "title, author_id, isbn, category, price, rating, pages, stock, published_year, description", rows, 10);
    }

    /** Generate 0-5 reviews per book, about 25,000 reviews total. */
    private Mono<Void> seedReviews() {
        List<Object[]> rows = new ArrayList<>(BOOK_COUNT * 3);
        long now = System.currentTimeMillis();
        long twoYears = 2L * 365 * 24 * 3600 * 1000;
        for (int bookId = 1; bookId <= BOOK_COUNT; bookId++) {
            int reviewCount = EN.random().nextInt(REVIEWS_PER_BOOK_MAX + 1);
            for (int r = 0; r < reviewCount; r++) {
                long createdAt = now - (long) (EN.random().nextDouble() * twoYears);
                rows.add(new Object[]{
                        bookId,
                        1 + EN.random().nextInt(5),
                        pick(REVIEW_TEMPLATES),
                        EN.name().fullName(),
                        createdAt
                });
            }
        }
        return insertBatches("bookstore_reviews", "book_id, rating, content, reviewer, created_at", rows, 5);
    }

    /**
     * Build and execute batched multi-row INSERT statements.
     *
     * @param table   target table
     * @param columns comma-separated column list
     * @param rows    row data; each row length must match {@code cols}
     * @param cols    column count
     * @return completion signal
     */
    private Mono<Void> insertBatches(String table, String columns, List<Object[]> rows, int cols) {
        List<List<Object[]>> batches = new ArrayList<>();
        for (int i = 0; i < rows.size(); i += BATCH_SIZE) {
            batches.add(rows.subList(i, Math.min(i + BATCH_SIZE, rows.size())));
        }
        return Flux.fromIterable(batches)
                .concatMap(batch -> {
                    var spec = db.sql(buildInsertSql(table, columns, batch.size(), cols));
                    int idx = 0;
                    for (Object[] row : batch) {
                        for (int c = 0; c < cols; c++) {
                            spec = spec.bind(idx++, row[c]);
                        }
                    }
                    return spec.fetch().rowsUpdated();
                })
                .then();
    }

    /** Build SQL in the form {@code INSERT INTO t (...) VALUES (?,?,..),(?,?,..)}. */
    private String buildInsertSql(String table, String columns, int rowCount, int cols) {
        StringBuilder sb = new StringBuilder("INSERT INTO ").append(table)
                .append(" (").append(columns).append(") VALUES ");
        for (int r = 0; r < rowCount; r++) {
            sb.append('(');
            for (int c = 0; c < cols; c++) {
                if (c > 0) {
                    sb.append(',');
                }
                sb.append('?');
            }
            sb.append(')');
            if (r < rowCount - 1) {
                sb.append(',');
            }
        }
        return sb.toString();
    }

    /** Pick one random element. */
    private String pick(String[] arr) {
        return arr[EN.random().nextInt(arr.length)];
    }

    /** Generate an English title from modifier + subject + form. */
    private String randomTitle() {
        String subject = pick(SUBJECTS);
        String modifier = pick(MODIFIERS);
        String form = pick(FORMS);
        return modifier.isEmpty() ? subject + " " + form : modifier + " " + subject + " " + form;
    }

    /** Compose a 2-3 sentence description. */
    private String randomDescription() {
        int sentences = 2 + EN.random().nextInt(2);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sentences; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(pick(DESC_SENTENCES));
        }
        return sb.toString();
    }
}
