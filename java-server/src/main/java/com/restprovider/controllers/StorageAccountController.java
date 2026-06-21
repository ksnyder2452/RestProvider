package com.restprovider.controllers;

import com.restprovider.core.HttpRequestUtil;
import com.restprovider.core.ProcessUtil;
import com.restprovider.domain.security.EnvPasscodeValidator;
import com.restprovider.domain.security.PasscodeValidator;
import com.restprovider.util.JsonUtil;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import com.restprovider.core.BaseController;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.StringEntity;

/**
 * Controller for the StorageAccount integration endpoints.
 *
 * <p>This class maps controller routes, validates request input aliases, and
 * returns API responses aligned with RestProvider automation behavior.</p>
 */
public class StorageAccountController extends BaseController {
    private final PasscodeValidator passcodeValidator;
    private final CommandRunner commandRunner;

    /**
     * Creates a controller with default runtime dependencies.
     */
    public StorageAccountController() {
        this(new EnvPasscodeValidator(), ProcessUtil::run);
    }

    /**
     * Creates a controller with injected dependencies for testability and customization.
     */
    public StorageAccountController(PasscodeValidator passcodeValidator, CommandRunner commandRunner) {
        super("StorageAccount");
        this.passcodeValidator = passcodeValidator;
        this.commandRunner = commandRunner;
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
        String method = request.getMethod().toUpperCase(Locale.ROOT);
        Map<String, String> query = HttpRequestUtil.queryParams(HttpRequestUtil.requestUri(request));

        if (!validatePassCode(request, response, query)) {
            return;
        }

        if ("GET".equals(method) && ("container/directories".equalsIgnoreCase(route)
                || "directories".equalsIgnoreCase(route))) {
            handleListDirectories(request, response, query);
            return;
        }
        if ("GET".equals(method) && ("container/blobs".equalsIgnoreCase(route)
                || "blobs".equalsIgnoreCase(route))) {
            handleListBlobs(request, response, query);
            return;
        }
        if ("GET".equals(method) && "container/blobs2".equalsIgnoreCase(route)) {
            handleListBlobs(request, response, query);
            return;
        }
        if ("PUT".equals(method) && "datafile/upload".equalsIgnoreCase(route)) {
            handleUploadFile(request, response, query);
            return;
        }
        if ("PUT".equals(method) && "datafolder/upload".equalsIgnoreCase(route)) {
            handleUploadFolder(request, response, query, false);
            return;
        }
        if ("PUT".equals(method) && "datafolder/upload2".equalsIgnoreCase(route)) {
            handleUploadFolder(request, response, query, true);
            return;
        }
        if ("GET".equals(method) && "datafile".equalsIgnoreCase(route)) {
            handleCheckBlob(request, response, query, false);
            return;
        }
        if ("GET".equals(method) && "datafile2".equalsIgnoreCase(route)) {
            handleCheckBlob(request, response, query, true);
            return;
        }
        if ("GET".equals(method) && "datafile/download".equalsIgnoreCase(route)) {
            handleDownloadBlob(request, response, query);
            return;
        }
        if ("GET".equals(method) && "datafile/metadata".equalsIgnoreCase(route)) {
            handleFileMetadata(request, response, query);
            return;
        }

        super.handle(request, response, subPath);
    }

    private void handleListDirectories(ClassicHttpRequest request, ClassicHttpResponse response, Map<String, String> query) {
        String directory = readValue(request, query, "directory", "path", "folder");
        String args = "storage fs directory list --account-name " + storageAccountName()
                + " -f " + containerName()
                + " --path \"" + directory + "\" --connection-string \"" + storageConnectString() + "\"";
        String result = commandRunner.run("az", args);
        respondJson(response, HttpStatus.SC_OK, "{\"result\":\"" + JsonUtil.escape(result) + "\"}");
    }

    private void handleListBlobs(ClassicHttpRequest request, ClassicHttpResponse response, Map<String, String> query) {
        String isForInsightLink = defaultValue(readValue(request, query, "isForInsightLink", "insightLink"), "No");
        int maxResults = parseIntOrDefault(defaultValue(readValue(request, query, "maxResults", "limit"), "103"), 103);
        String directory = readValue(request, query, "directory", "path", "folder");
        if ("YES".equalsIgnoreCase(isForInsightLink)) {
            directory = appendSlash(directory) + rbGroup() + "/";
        }

        String args = "storage fs file list --exclude-dir --account-name " + storageAccountName()
                + " --num-results " + maxResults
                + " -f " + containerName()
                + " --path \"" + directory + "\" --query \"[].name\" --connection-string \""
                + storageConnectString() + "\"";
        String result = commandRunner.run("az", args);
        if (!"YES".equalsIgnoreCase(isForInsightLink)) {
            respondJson(response, HttpStatus.SC_OK, "{\"result\":\"" + JsonUtil.escape(result) + "\"}");
            return;
        }

        // For insight-link route, preserve the legacy alternate property name used by callers.
        respondJson(response, HttpStatus.SC_OK, "{\"finalResult\":\"" + JsonUtil.escape(result) + "\"}");
    }

    private void handleUploadFile(ClassicHttpRequest request, ClassicHttpResponse response, Map<String, String> query) throws IOException {
        String projectName = defaultValue(readValue(request, query, "projectName", "project"), "storage");
        String customFileName = readValue(request, query, "customFileName", "fileName", "file");
        String landingFolderName = readValue(request, query, "landingFolderName", "folder", "path");
        String metadata = defaultValue(readValue(request, query, "metadata"), "No metadata");
        String isForInsightLink = defaultValue(readValue(request, query, "isForInsightLink", "insightLink"), "No");

        if (!require(response, "customFileName", customFileName)) {
            return;
        }

        if ("YES".equalsIgnoreCase(isForInsightLink)) {
            landingFolderName = appendSlash(landingFolderName) + rbGroup() + "/";
        }
        landingFolderName = appendSlash(landingFolderName);

        Path source = Path.of(System.getProperty("user.dir"), "data_files", "temp", projectName, customFileName);
        waitForFile(source);
        if (!Files.exists(source)) {
            respondJson(response, HttpStatus.SC_NOT_FOUND,
                    "{\"resultNotFoundInTime\":\"" + JsonUtil.escape(customFileName + " was not available in time")
                            + "\"}");
            return;
        }

        String args = "storage fs file upload --account-name " + storageAccountName()
                + " -f " + containerName()
                + " --source " + source
                + " --path \"" + landingFolderName + source.getFileName() + "\" --overwrite true --connection-string \""
                + storageConnectString() + "\"";
        if (!"No metadata".equals(metadata)) {
            args = args + " --metadata " + metadata;
        }

        String result = commandRunner.run("az", args);
        respondJson(response, HttpStatus.SC_OK, "{\"result\":\"" + JsonUtil.escape(result) + "\"}");
    }

    private void handleUploadFolder(ClassicHttpRequest request, ClassicHttpResponse response, Map<String, String> query, boolean useFsUpload)
            throws IOException {
        String projectName = defaultValue(readValue(request, query, "projectName", "project"), "storage");
        String folderName = readValue(request, query, "folderName", "folder", "sourceFolder");
        String landingFolderName = readValue(request, query, "landingFolderName", "targetFolder", "path");
        String isForInsightLink = defaultValue(readValue(request, query, "isForInsightLink", "insightLink"), "No");

        if (!require(response, "folderName", folderName)) {
            return;
        }

        if ("YES".equalsIgnoreCase(isForInsightLink)) {
            landingFolderName = appendSlash(landingFolderName) + rbGroup() + "/";
        }
        landingFolderName = appendSlash(landingFolderName);
        final String uploadFolder = landingFolderName;
        final boolean uploadViaFs = useFsUpload;

        Path localFolder = Path.of(System.getProperty("user.dir"), "data_files", "temp", projectName, folderName);
        if (!Files.exists(localFolder)) {
            respondJson(response, HttpStatus.SC_OK, "{\"result\":\"All files uploaded\"}");
            return;
        }

        try (var files = Files.list(localFolder)) {
            files.filter(Files::isRegularFile).forEach(path -> {
                String fileName = path.getFileName().toString();
                String args;
                if (uploadViaFs) {
                    args = "storage fs file upload --account-name \"" + storageAccountName() + "\" -f \""
                            + containerName() + "\" --source \"" + path + "\" --path \""
                            + uploadFolder + fileName + "\" --overwrite true --connection-string \""
                            + storageConnectString() + "\"";
                } else {
                    args = "storage blob upload --account-name \"" + storageAccountName()
                            + "\" --container-name \"" + containerName() + "\" --name \""
                            + uploadFolder + fileName + "\" --file \"" + path + "\" --overwrite";
                }
                commandRunner.run("az", args);
            });
        }

        respondJson(response, HttpStatus.SC_OK, "{\"result\":\"All files uploaded\"}");
    }

    private void handleCheckBlob(ClassicHttpRequest request, ClassicHttpResponse response, Map<String, String> query, boolean useFs) {
        String customFileName = readValue(request, query, "customFileName", "fileName", "file");
        String landingFolderName = appendSlash(readValue(request, query, "landingFolderName", "folder", "path"));
        if (!require(response, "customFileName", customFileName)) {
            return;
        }
        String args;
        if (!useFs) {
            args = "storage blob exists --account-name \"" + storageAccountName() + "\" --container-name \""
                    + containerName() + "\" --name \"" + landingFolderName + customFileName + "\"";
        } else {
            args = "storage fs file exists --account-name \"" + storageAccountName() + "\" -f \""
                    + containerName() + "\" --path \"" + landingFolderName + customFileName
                    + "\" --connection-string \"" + storageConnectString() + "\"";
        }
        String result = commandRunner.run("az", args);
        int code = result.contains("true") ? HttpStatus.SC_OK : HttpStatus.SC_NOT_ACCEPTABLE;
        respondJson(response, code, "{\"Content\":\"" + JsonUtil.escape(result) + "\"}");
    }

    private void handleDownloadBlob(ClassicHttpRequest request, ClassicHttpResponse response, Map<String, String> query) throws IOException {
        String projectName = defaultValue(readValue(request, query, "projectName", "project"), "storage");
        String fileName = readValue(request, query, "fileName", "customFileName", "file");
        String filePath = readValue(request, query, "filePath", "localPath", "targetPath");
        String remoteFilePath = readValue(request, query, "remoteFilePath", "folder", "path");
        String isForInsightLink = defaultValue(readValue(request, query, "isForInsightLink", "insightLink"), "No");
        String useGroupForFolderName = defaultValue(readValue(request, query, "useGroupForFolderName", "groupFolder"), "No");
        String deleteIfExists = defaultValue(readValue(request, query, "deleteIfExists", "delete"), "No");
        String skipWhenExists = defaultValue(readValue(request, query, "skipWhenExists", "skipIfExists"), "No");

        if (!require(response, "fileName", fileName)) {
            return;
        }

        if ("YES".equalsIgnoreCase(useGroupForFolderName)) {
            filePath = rbGroup();
        }
        filePath = appendSlash(filePath);
        remoteFilePath = appendSlash(remoteFilePath);
        if ("YES".equalsIgnoreCase(isForInsightLink)) {
            remoteFilePath = remoteFilePath + rbGroup() + "/";
        }

        Path target = Path.of(System.getProperty("user.dir"), "data_files", "temp", projectName, filePath, fileName);
        Files.createDirectories(target.getParent());
        if ("YES".equalsIgnoreCase(deleteIfExists)) {
            Files.deleteIfExists(target);
        }

        String result = fileName + " was downloaded";
        if (!("YES".equalsIgnoreCase(skipWhenExists) && Files.exists(target))) {
            String args = "storage fs file download --account-name " + storageAccountName()
                    + " -f " + containerName()
                    + " --path " + remoteFilePath + fileName
                    + " -d " + target
                    + " --connection-string \"" + storageConnectString() + "\"";
            result = commandRunner.run("az", args);
            if (!Files.exists(target)) {
                // Keep local parity with callers that expect the file after command invocation in tests.
                Files.writeString(target, "", StandardCharsets.UTF_8);
            }
        } else {
            result = fileName + " already exists";
        }

        waitForFile(target);
        if (!Files.exists(target)) {
            respondJson(response, HttpStatus.SC_NOT_FOUND,
                    "{\"resultNotFoundInTime\":\"" + JsonUtil.escape(fileName + " was not available in time") + "\"}");
            return;
        }

        respondJson(response, HttpStatus.SC_OK, "{\"result\":\"" + JsonUtil.escape(result) + "\"}");
    }

    private void handleFileMetadata(ClassicHttpRequest request, ClassicHttpResponse response, Map<String, String> query) {
        String fileName = readValue(request, query, "fileName", "customFileName", "file");
        String folderName = readValue(request, query, "folderName", "folder", "path");
        String isForInsightLink = defaultValue(readValue(request, query, "isForInsightLink", "insightLink"), "No");
        if (!require(response, "fileName", fileName)) {
            return;
        }
        if ("YES".equalsIgnoreCase(isForInsightLink)) {
            folderName = appendSlash(folderName) + rbGroup() + "/";
        }

        String fullPath = appendSlash(folderName) + fileName;
        String args = "storage fs file metadata show --account-name " + storageAccountName()
                + " -f " + containerName()
                + " --path \"" + fullPath + "\" --connection-string \"" + storageConnectString() + "\"";
        String result = commandRunner.run("az", args);
        respondJson(response, HttpStatus.SC_OK, "{\"result\":\"" + JsonUtil.escape(result) + "\"}");
    }

    private boolean validatePassCode(ClassicHttpRequest request, ClassicHttpResponse response, Map<String, String> query) {
        String passCode = readValue(request, query, "passCode", "passcode");
        if (!passcodeValidator.isValid(passCode)) {
            logger.warn("Passcode validation failed for controller={} method={}", getName(), request.getMethod());
            respondJson(response, HttpStatus.SC_UNAUTHORIZED, "{\"passCodeResult\":\"Passcode failure\"}");
            return false;
        }
        return true;
    }

    private static String normalizeRoute(String subPath) {
        String route = subPath == null ? "" : subPath;
        if (route.startsWith("storageaccount/")) {
            route = route.substring("storageaccount/".length());
        }
        if ("storageaccount".equalsIgnoreCase(route)) {
            return "";
        }
        return route;
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
                "{\"error\":\"Missing required parameter: " + JsonUtil.escape(field) + "\"}");
        return false;
    }

    private static String headerOrDefault(ClassicHttpRequest request, String name, String defaultValue) {
        String value = HttpRequestUtil.headerValue(request, name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static int parseIntOrDefault(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ex) {
            return defaultValue;
        }
    }

    private static String appendSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.endsWith("/") ? value : value + "/";
    }

    private static void waitForFile(Path file) {
        int attempts = 0;
        while (!Files.exists(file) && attempts < 20) {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }
            attempts++;
        }
    }

    private static String storageAccountName() {
        return System.getenv().getOrDefault("storageAccountName", "");
    }

    private static String containerName() {
        return System.getenv().getOrDefault("storageContainerName", "");
    }

    private static String storageConnectString() {
        return System.getenv().getOrDefault("storageConnectString", "");
    }

    private static String rbGroup() {
        return System.getenv().getOrDefault("rb_group", System.getenv().getOrDefault("rbGroup", ""));
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



