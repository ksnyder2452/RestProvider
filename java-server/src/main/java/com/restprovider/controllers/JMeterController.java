package com.restprovider.controllers;

import com.restprovider.core.HttpRequestUtil;
import com.restprovider.core.ProcessUtil;
import com.restprovider.util.JsonUtil;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import com.restprovider.core.BaseController;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.StringEntity;

public class JMeterController extends BaseController {
    private final CommandRunner commandRunner;

    public JMeterController() {
        this(ProcessUtil::run);
    }

    public JMeterController(CommandRunner commandRunner) {
        super("JMeter");
        this.commandRunner = commandRunner;
    }

    @Override
    public void handle(ClassicHttpRequest request, ClassicHttpResponse response, String subPath)
            throws IOException, HttpException {
        logger.debug("Handling request controller={} method={} subPath={}", getName(), request.getMethod(), subPath);
        String route = normalizeRoute(subPath);
        String method = request.getMethod();
        Map<String, String> query = HttpRequestUtil.queryParams(HttpRequestUtil.requestUri(request));

        if ("GET".equalsIgnoreCase(method)
                && ("version".equalsIgnoreCase(route) || "info/version".equalsIgnoreCase(route))) {
            String output = commandRunner.run("jmeter", "-v");
            respondJson(response, HttpStatus.SC_OK, "{\"result\":\"" + JsonUtil.escape(output) + "\"}");
            return;
        }

        if ("GET".equalsIgnoreCase(method)
                && ("servers".equalsIgnoreCase(route) || "server/list".equalsIgnoreCase(route))) {
            String output = commandRunner.run("jmeter", "-n -R");
            respondJson(response, HttpStatus.SC_OK, "{\"result\":\"" + JsonUtil.escape(output) + "\"}");
            return;
        }

        if (("POST".equalsIgnoreCase(method) || "GET".equalsIgnoreCase(method))
                && ("".equals(route) || "run".equalsIgnoreCase(route))) {
            String projectName = defaultValue(readValue(request, query, "projectName", "project"), "jmeter");
            String testcaseName = safeName(defaultValue(readValue(request, query, "testcaseName", "testcase", "testCase"), "run"));
            String scriptName = readValue(request, query, "scriptName", "script", "testPlan");
            String resultFile = defaultValue(readValue(request, query, "resultFile", "outputFile", "result"), "result.jtl");
            String jmeterLogFile = readValue(request, query, "jmeterLogFile", "logFile", "jmeterLog");

            if (!require(response, "scriptName", scriptName)) {
                return;
            }

            String rootDir = System.getProperty("user.dir");
            Path scriptPath = Path.of(rootDir, "data_files", scriptName);
            Path tempFolder = Path.of(rootDir, "data_files", "temp", projectName);
            String resolvedResultFile = tempFolder.resolve(testcaseName + "_" + safeName(resultFile)).toString();
            String jmeterLogArg = "";
            if (jmeterLogFile != null && !jmeterLogFile.isBlank()) {
                jmeterLogArg = " -j " + tempFolder.resolve(testcaseName + "_" + safeName(jmeterLogFile));
            }

            String args = "-n -t " + scriptPath + " -e -l " + resolvedResultFile + jmeterLogArg;
            String output = commandRunner.run("jmeter", args);
            respondJson(response, HttpStatus.SC_OK, "{\"result\":\"" + JsonUtil.escape(output) + "\"}");
            return;
        }

        super.handle(request, response, subPath);
    }

    private static String normalizeRoute(String subPath) {
        String route = subPath == null ? "" : subPath;
        if (route.startsWith("jmeter/")) {
            route = route.substring("jmeter/".length());
        }
        if ("jmeter".equalsIgnoreCase(route)) {
            return "";
        }
        return route;
    }

    private static String safeName(String name) {
        return (name == null ? "" : name).replace(" ", "_");
    }

    private static String readValue(ClassicHttpRequest request, Map<String, String> query, String... keys) {
        for (String key : keys) {
            String headerValue = HttpRequestUtil.headerValue(request, key);
            if (headerValue != null && !headerValue.isBlank()) {
                return headerValue;
            }
        }
        for (String key : keys) {
            for (Map.Entry<String, String> entry : query.entrySet()) {
                if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(key)) {
                    String value = entry.getValue();
                    if (value != null && !value.isBlank()) {
                        return value;
                    }
                }
            }
        }
        return "";
    }

    private static String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static boolean require(ClassicHttpResponse response, String field, String value) {
        if (value != null && !value.isBlank()) {
            return true;
        }
        respondJson(response, HttpStatus.SC_BAD_REQUEST,
                "{\"error\":\"" + JsonUtil.escape("Missing required parameter: " + field) + "\"}");
        return false;
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
