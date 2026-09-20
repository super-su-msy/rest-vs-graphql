package run.runnable.numfeelservice.service;

import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingFieldSelectionSet;
import graphql.schema.SelectedField;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.RowsFetchSpec;
import io.r2dbc.spi.Row;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.runnable.numfeelservice.web.ApiException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link BookGraphqlService}.
 * <p>
 * The key assertion is that the selection set determines real DB cost: scalar fields use one call,
 * adding author becomes 1+N, and adding reviews becomes 1+N+N. This is the concrete evidence that
 * GraphQL server cost grows with client-selected nesting.
 */
@ExtendWith(MockitoExtension.class)
class BookGraphqlServiceTest {

    @Mock
    private DatabaseClient db;

    @Mock
    private BookStoreDataInitializer dataInitializer;

    private BookGraphqlService service;

    @BeforeEach
    void setUp() {
        service = new BookGraphqlService(db, dataInitializer);
        lenient().when(dataInitializer.isReady()).thenReturn(true);
    }

    @Test
    void queryShouldReturn503WhenNotReady() {
        when(dataInitializer.isReady()).thenReturn(false);
        DataFetchingEnvironment env = mockEnv(false, false);

        StepVerifier.create(service.query(env, 3))
                .expectErrorSatisfies(e -> {
                    assertInstanceOf(ApiException.class, e);
                    assertEquals(503, ((ApiException) e).status());
                })
                .verify();
    }

    @Test
    void scalarOnlyShouldCostSingleDbCall() {
        stubFlux("ORDER BY b.rating DESC", bookTable(3));
        DataFetchingEnvironment env = mockEnv(false, false);

        StepVerifier.create(service.query(env, 3))
                .assertNext(result -> {
                    assertEquals(1, result.meta().dbCalls());
                    assertEquals(3, result.meta().rowsLoaded());
                    assertEquals(3, result.books().size());
                    assertNull(result.books().get(0).author());
                    assertTrue(result.books().get(0).reviews().isEmpty());
                })
                .verifyComplete();
    }

    @Test
    void withAuthorShouldCostOnePlusN() {
        stubFlux("ORDER BY b.rating DESC", bookTable(3));
        stubOne("bookstore_authors WHERE id", authorTable());
        DataFetchingEnvironment env = mockEnv(true, false);

        StepVerifier.create(service.query(env, 3))
                .assertNext(result -> {
                    assertEquals(4, result.meta().dbCalls());
                    assertEquals(6, result.meta().rowsLoaded());
                    assertEquals("Stephen Hawking", result.books().get(0).author().name());
                })
                .verifyComplete();
    }

    @Test
    void withAuthorAndReviewsShouldExplodeCost() {
        stubFlux("ORDER BY b.rating DESC", bookTable(3));
        stubOne("bookstore_authors WHERE id", authorTable());
        // Two reviews per book: rowsLoaded = 3 books + 3 authors + 6 reviews.
        stubFlux("bookstore_reviews WHERE book_id", reviewTable(2));
        DataFetchingEnvironment env = mockEnv(true, true);

        StepVerifier.create(service.query(env, 3))
                .assertNext(result -> {
                    assertEquals(7, result.meta().dbCalls());
                    assertEquals(12, result.meta().rowsLoaded());
                    assertEquals(2, result.books().get(0).reviews().size());
                })
                .verifyComplete();
    }

    @Test
    void limitShouldBeClampedToMax() {
        ArgumentCaptor<Object> bound = ArgumentCaptor.forClass(Object.class);
        DatabaseClient.GenericExecuteSpec spec = stubFlux("ORDER BY b.rating DESC", bookTable(1));
        when(spec.bind(eq("limit"), bound.capture())).thenReturn(spec);
        DataFetchingEnvironment env = mockEnv(false, false);

        StepVerifier.create(service.query(env, 9999))
                .assertNext(result -> assertEquals(1, result.books().size()))
                .verifyComplete();

        assertEquals(100, bound.getValue(), "limit=9999 should be clamped to 100");
    }

    // Stub helpers

    /**
     * Stub a multi-row query by SQL fragment: capture the mapping function and drive it with mock rows.
     *
     * @return spec mock for additional assertions
     */
    @SuppressWarnings("unchecked")
    private DatabaseClient.GenericExecuteSpec stubFlux(String fragment, List<Map<String, Object>> table) {
        DatabaseClient.GenericExecuteSpec spec = mock(DatabaseClient.GenericExecuteSpec.class);
        when(db.sql(contains(fragment))).thenReturn(spec);
        lenient().when(spec.bind(anyString(), any())).thenReturn(spec);

        RowsFetchSpec<Object> rows = mock(RowsFetchSpec.class);
        BiFunction[] fn = new BiFunction[1];
        when(spec.map(any(BiFunction.class))).thenAnswer(inv -> {
            fn[0] = inv.getArgument(0);
            return rows;
        });
        when(rows.all()).thenAnswer(inv -> {
            List<Object> mapped = new ArrayList<>();
            for (Map<String, Object> data : table) {
                mapped.add(fn[0].apply(mockRow(data), null));
            }
            return Flux.fromIterable(mapped);
        });
        return spec;
    }

    /** Stub a single-row query by SQL fragment. */
    @SuppressWarnings("unchecked")
    private void stubOne(String fragment, Map<String, Object> row) {
        DatabaseClient.GenericExecuteSpec spec = mock(DatabaseClient.GenericExecuteSpec.class);
        when(db.sql(contains(fragment))).thenReturn(spec);
        lenient().when(spec.bind(anyString(), any())).thenReturn(spec);

        RowsFetchSpec<Object> rows = mock(RowsFetchSpec.class);
        BiFunction[] fn = new BiFunction[1];
        when(spec.map(any(BiFunction.class))).thenAnswer(inv -> {
            fn[0] = inv.getArgument(0);
            return rows;
        });
        when(rows.one()).thenAnswer(inv ->
                Mono.just(fn[0].apply(mockRow(row), null)));
    }

    /** Build a mock row whose values are resolved by column name. */
    @SuppressWarnings("unchecked")
    private Row mockRow(Map<String, Object> data) {
        Row row = mock(Row.class);
        when(row.get(anyString())).thenAnswer(inv -> data.get(inv.getArgument(0, String.class)));
        when(row.get(anyString(), any())).thenAnswer(inv -> {
            Object v = data.get(inv.getArgument(0, String.class));
            return v == null ? null : v.toString();
        });
        return row;
    }

    /** Build a mock DataFetchingEnvironment that marks whether author/reviews are requested. */
    private DataFetchingEnvironment mockEnv(boolean withAuthor, boolean withReviews) {
        DataFetchingEnvironment env = mock(DataFetchingEnvironment.class);

        DataFetchingFieldSelectionSet inner = mock(DataFetchingFieldSelectionSet.class);
        lenient().when(inner.contains("author")).thenReturn(withAuthor);
        lenient().when(inner.contains("reviews")).thenReturn(withReviews);

        SelectedField booksField = mock(SelectedField.class);
        lenient().when(booksField.getSelectionSet()).thenReturn(inner);

        DataFetchingFieldSelectionSet outer = mock(DataFetchingFieldSelectionSet.class);
        lenient().when(outer.getFields("books")).thenReturn(List.of(booksField));
        lenient().when(env.getSelectionSet()).thenReturn(outer);
        return env;
    }

    /** Build n rows of base book data. */
    private List<Map<String, Object>> bookTable(int n) {
        List<Map<String, Object>> table = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", i);
            r.put("title", "Book " + i);
            r.put("author_id", 100 + i);
            r.put("isbn", "9787" + i);
            r.put("category", "Popular Science");
            r.put("price", 45.0 + i);
            r.put("rating", 4.5);
            r.put("pages", 300);
            r.put("stock", 20);
            r.put("published_year", 2020);
            r.put("description", "This is the description for book " + i + ", and it is fairly long.");
            table.add(r);
        }
        return table;
    }

    /** Build an author row. */
    private Map<String, Object> authorTable() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", 101);
        r.put("name", "Stephen Hawking");
        r.put("country", "United Kingdom");
        r.put("bio", "Theoretical physicist.");
        return r;
    }

    /** Build n review rows. */
    private List<Map<String, Object>> reviewTable(int n) {
        List<Map<String, Object>> table = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", 9000 + i);
            r.put("rating", 5);
            r.put("content", "Great book " + i);
            r.put("reviewer", "Reader " + i);
            table.add(r);
        }
        return table;
    }
}
