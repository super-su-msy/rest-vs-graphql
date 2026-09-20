package run.runnable.numfeelservice.controller;

import graphql.schema.DataFetchingEnvironment;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Mono;
import run.runnable.numfeelservice.service.BookGraphqlService;

/**
 * REST vs GraphQL comparison demo - GraphQL controller.
 * <p>
 * Exposes the single {@code POST /graphql} endpoint through Spring for GraphQL.
 * The client selects fields and nesting depth in the query, while the server reports the real
 * cost through the {@code meta} field.
 */
@Controller
public class BookGraphqlController {

    private final BookGraphqlService service;

    public BookGraphqlController(BookGraphqlService service) {
        this.service = service;
    }

    /**
     * {@code Query.books(limit)} entry point: resolve the query according to client-selected fields.
     *
     * @param limit number of books to return; defaults to 10 and is clamped to 1-100
     * @param env   GraphQL execution context used to inspect the selection set
     * @return book list plus cost metadata
     */
    @QueryMapping
    public Mono<BookGraphqlService.BookListResult> books(
            @Argument Integer limit, DataFetchingEnvironment env) {
        return service.query(env, limit);
    }
}
