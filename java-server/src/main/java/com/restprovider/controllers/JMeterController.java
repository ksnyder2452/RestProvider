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

        if ("GET".equalsIgnoreCase(method) && "version".equalsIgnoreCase(route)) {
            String output = commandRunner.run("jmeter", "-v");
            respondJson(response, HttpStatus.SC_OK, "{\"result\":\"" + JsonUtil.escape(output) + "\"}");
            return;
        }

        if ("GET".equalsIgnoreCase(method) && "servers".equalsIgnoreCase(route)) {
            String output = commandRunner.run("jmeter", "-n -R");
            respondJson(response, HttpStatus.SC_OK, "{\"result\":\"" + JsonUtil.escape(output) + "\"}");
            return;
        }

        if ("POST".equalsIgnoreCase(method) && "".equals(route)) {
            String projectName = HttpRequestUtil.headerValue(request, "projectName");
            String testcaseName = safeName(HttpRequestUtil.headerValue(request, "testcaseName"));
            String scriptName = HttpRequestUtil.headerValue(request, "scriptName");
            String resultFile = HttpRequestUtil.headerValue(request, "resultFile");
            String jmeterLogFile = HttpRequestUtil.headerValue(request, "jmeterLogFile");

            String rootDir = System.getProperty("user.dir");
            String scriptPath = Path.of(rootDir, "data_files", scriptName).toString();
            String tempPrefix = Path.of(rootDir, "data_files", "temp", projectName, testcaseName).toString();
            String resolvedResultFile = tempPrefix + resultFile;
            String jmeterLogArg = "";
            if (jmeterLogFile != null && !jmeterLogFile.isBlank()) {
                jmeterLogArg = " -j " + tempPrefix + jmeterLogFile;
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

    private static void respondJson(ClassicHttpResponse response, int code, String body) {
        response.setCode(code);
        response.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));
    }

    @FunctionalInterface
    public interface CommandRunner {
        String run(String command, String args);
    }
}
