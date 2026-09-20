package run.runnable.numfeelservice.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.reactive.config.CorsRegistry;
import org.springframework.web.reactive.config.ResourceHandlerRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;

import java.io.File;
import java.net.URI;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;

/**
 * WebFlux configuration: CORS and static resources.
 * <p>
 * The previous Vert.x implementation opened all origins with
 * {@code CorsHandler.create("*")} and served {@code pages/} and {@code components/}
 * through StaticHandler using paths relative to the working directory.
 * This configuration maps the same external paths and directory layout to filesystem
 * locations so the 8MB frontend assets do not need to be packaged inside the jar.
 * The static locations can be changed with {@code static.pages-location} /
 * {@code static.components-location}; when a directory does not exist, such as in an
 * API-only deployment where the frontend is hosted separately on numfeel.996.ninja,
 * the static mapping is skipped automatically.
 */
@Configuration
public class WebConfig implements WebFluxConfigurer {

    @Value("${static.pages-location:file:../pages/}")
    private String pagesLocation;

    @Value("${static.components-location:file:../components/}")
    private String componentsLocation;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("Content-Type", "Authorization", "Accept", "Origin")
                .maxAge(3600);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        if (locationExists(pagesLocation)) {
            registry.addResourceHandler("/pages/**").addResourceLocations(pagesLocation);
        }
        if (locationExists(componentsLocation)) {
            registry.addResourceHandler("/components/**").addResourceLocations(componentsLocation);
        }
    }

    @Bean
    public RouterFunction<ServerResponse> pageRedirects() {
        return RouterFunctions.route(GET("/pages/rest-vs-graphql/"),
                request -> ServerResponse.status(HttpStatus.FOUND)
                        .location(URI.create("/pages/rest-vs-graphql/index.html"))
                        .build());
    }

    /** Only verify existence for file: prefixed local directories; register classpath and other forms as-is. */
    private boolean locationExists(String location) {
        if (location == null) {
            return false;
        }
        if (location.startsWith("file:")) {
            return new File(location.substring("file:".length())).isDirectory();
        }
        return true;
    }
}
