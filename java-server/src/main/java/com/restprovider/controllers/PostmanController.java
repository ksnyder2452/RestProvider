package com.restprovider.controllers;

import com.restprovider.core.HttpRequestUtil;
import com.restprovider.core.ProcessUtil;
import com.restprovider.util.JsonUtil;
import java.io.IOException;
import java.nio.file.Path;
import com.restprovider.core.BaseController;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.StringEntity;

public class PostmanController extends BaseController {
    private final CommandRunner commandRunner;

    public PostmanController() {
        this(ProcessUtil::run);
    }

    public PostmanController(CommandRunner commandRunner) {
        super("Postman");
        this.commandRunner = commandRunner;
    }

    @Override
    public void handle(ClassicHttpRequest request, ClassicHttpResponse response, String subPath)
            throws IOException, HttpException {
        logger.debug("Handling request controller={} method={} subPath={}", getName(), request.getMethod(), subPath);
        String route = normalizeRoute(subPath);
        String method = request.getMethod();

        if ("GET".equalsIgnoreCase(method) && "status".equalsIgnoreCase(route)) {
            String postmanUser = HttpRequestUtil.headerValue(request, "postmanUser");
            String login = postmanLogin();
            int statusCode = (login.contains("Logged in using api key of user: " + postmanUser)
                    || login.contains("Logged in successfully"))
                    ? HttpStatus.SC_OK
                    : HttpStatus.SC_NOT_ACCEPTABLE;
            respondJson(response, statusCode, "{\"Content\":\"" + JsonUtil.escape(login) + "\"}");
            return;
        }

        if ("POST".equalsIgnoreCase(method) && "run/id".equalsIgnoreCase(route)) {
            String collectionId = HttpRequestUtil.headerValue(request, "collectionId");
            String login = postmanLogin();
            if (!login.contains("Logged in successfully")
                    && !login.contains("Logged in using api key")) {
                respondJson(response, HttpStatus.SC_NOT_ACCEPTABLE,
                        "{\"Content\":\"" + JsonUtil.escape(login) + "\"}");
                return;
            }

            Thread thread = new Thread(() -> commandRunner.run("postman", "collection run " + collectionId));
            thread.setDaemon(true);
            thread.start();

            respondJson(response, HttpStatus.SC_OK, "{\"Content\":\"" + JsonUtil.escape(login) + "\"}");
            return;
        }

        if ("POST".equalsIgnoreCase(method) && "run/file".equalsIgnoreCase(route)) {
            String projectName = HttpRequestUtil.headerValue(request, "projectName");
            String collectionPath = HttpRequestUtil.headerValue(request, "collectionPath");
            String login = postmanLogin();
            if (!login.contains("Logged in successfully")
                    && !login.contains("Logged in using api key")) {
                respondJson(response, HttpStatus.SC_NOT_ACCEPTABLE,
                        "{\"Content\":\"" + JsonUtil.escape(login) + "\"}");
                return;
            }

            String rootDir = System.getProperty("user.dir");
            String fullPath = Path.of(rootDir, "data_files", "temp", projectName, collectionPath).toString();
            Thread thread = new Thread(() -> commandRunner.run("postman", "collection run " + fullPath));
            thread.setDaemon(true);
            thread.start();

            respondJson(response, HttpStatus.SC_OK, "{\"Content\":\"" + JsonUtil.escape(login) + "\"}");
            return;
        }

        super.handle(request, response, subPath);
    }

    private String postmanLogin() {
        String apiKey = System.getenv("postman_key");
        return commandRunner.run("postman", "login --with-api-key " + (apiKey == null ? "" : apiKey));
    }

    private static String normalizeRoute(String subPath) {
        String route = subPath == null ? "" : subPath;
        if (route.startsWith("postman/")) {
            route = route.substring("postman/".length());
        }
        return route;
    }

    private static void respondJson(ClassicHttpResponse response, int code, String body) {
        response.setCode(code);
        response.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));
    }

    @FunctionalInterface
    public interface CommandRunner {
        String run(String command, String args);
    }
}
