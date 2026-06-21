package com.restprovider.controllers;

import com.restprovider.core.BaseController;
import com.restprovider.core.ProcessUtil;
import com.restprovider.util.JsonUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.StringEntity;

/**
 * Controller for generating and publishing project Javadocs.
 *
 * <p>This controller exposes endpoints to generate API documentation using Maven and
 * serve the generated files from the running HTTP server.</p>
 */
public class JavadocController extends BaseController {
    private final CommandRunner commandRunner;
    private final Path docsRoot;

    /**
     * Creates a controller with default runtime dependencies.
     */
    public JavadocController() {
        this(ProcessUtil::run, defaultDocsRoot());
    }

    /**
     * Creates a controller with injected dependencies for testability and customization.
     */
    public JavadocController(CommandRunner commandRunner, Path docsRoot) {
        super("Javadoc");
        this.commandRunner = commandRunner;
        this.docsRoot = docsRoot;
    }

    /**
     * Handles incoming HTTP requests for this controller's route surface.
     *
     * @param request inbound HTTP request
     * @param response outbound HTTP response
     * @param subPath controller-specific route segment after /api/{controller}/
     * @throws IOException when I/O work fails
     * @throws HttpException when request handling fails at HTTP protocol level
     */
    @Override
    public void handle(ClassicHttpRequest request, ClassicHttpResponse response, String subPath)
            throws IOException, HttpException {
        logger.debug("Handling request controller={} method={} subPath={}", getName(), request.getMethod(), subPath);
        String route = normalizeRoute(subPath);
        String method = request.getMethod();

        if ("GET".equalsIgnoreCase(method)
                && ("".equals(route) || "generate".equalsIgnoreCase(route) || "build".equalsIgnoreCase(route))) {
            String output = commandRunner.run("mvn", "-q -DskipTests javadoc:javadoc");
            Path index = docsRoot.resolve("index.html");
            if (Files.exists(index)) {
                respondJson(response, HttpStatus.SC_OK,
                        "{\"message\":\"Javadocs generated\","
                                + "\"docsEndpoint\":\"/api/javadoc/docs\","
                                + "\"result\":\"" + JsonUtil.escape(output) + "\"}");
            } else {
                respondJson(response, HttpStatus.SC_INTERNAL_SERVER_ERROR,
                        "{\"error\":\"Javadoc generation failed\","
                                + "\"result\":\"" + JsonUtil.escape(output) + "\"}");
            }
            return;
        }

        if ("GET".equalsIgnoreCase(method) && ("docs".equalsIgnoreCase(route) || route.startsWith("docs/"))) {
            String relativePath = "docs".equalsIgnoreCase(route) ? "index.html" : route.substring("docs/".length());
            Path normalizedRoot = docsRoot.normalize();
            Path resolved = normalizedRoot.resolve(relativePath).normalize();
            if (!resolved.startsWith(normalizedRoot)) {
                respondJson(response, HttpStatus.SC_BAD_REQUEST, "{\"error\":\"Invalid documentation path\"}");
                return;
            }
            if (!Files.exists(resolved) || Files.isDirectory(resolved)) {
                respondJson(response, HttpStatus.SC_NOT_FOUND, "{\"error\":\"Documentation file not found\"}");
                return;
            }

            ContentType contentType = resolveContentType(resolved);
            byte[] body = Files.readAllBytes(resolved);
            response.setCode(HttpStatus.SC_OK);
            response.setEntity(new ByteArrayEntity(body, contentType));
            return;
        }

        super.handle(request, response, subPath);
    }

    private static String normalizeRoute(String subPath) {
        String route = subPath == null ? "" : subPath;
        if (route.startsWith("javadoc/")) {
            route = route.substring("javadoc/".length());
        }
        if ("javadoc".equalsIgnoreCase(route)) {
            return "";
        }
        return route;
    }

    private static Path defaultDocsRoot() {
        return Path.of(System.getProperty("user.dir"), "target", "reports", "apidocs");
    }

    private static ContentType resolveContentType(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        if (name.endsWith(".html") || name.endsWith(".htm")) {
            return ContentType.TEXT_HTML;
        }
        if (name.endsWith(".css")) {
            return ContentType.parse("text/css");
        }
        if (name.endsWith(".js")) {
            return ContentType.parse("application/javascript");
        }
        if (name.endsWith(".json")) {
            return ContentType.APPLICATION_JSON;
        }
        if (name.endsWith(".txt")) {
            return ContentType.TEXT_PLAIN;
        }
        return ContentType.APPLICATION_OCTET_STREAM;
    }

    private static void respondJson(ClassicHttpResponse response, int code, String body) {
        response.setCode(code);
        response.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));
    }

    /**
     * Functional contract used to abstract external operations for this controller.
     */
    @FunctionalInterface
    public interface CommandRunner {
        String run(String command, String args);
    }
}
