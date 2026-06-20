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
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.restprovider.core.BaseController;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;

public class FileController extends BaseController {
    private final PasscodeValidator passcodeValidator;
    private final CommandRunner commandRunner;

    public FileController() {
        this(new EnvPasscodeValidator(), ProcessUtil::run);
    }

    public FileController(PasscodeValidator passcodeValidator, CommandRunner commandRunner) {
        super("File");
        this.passcodeValidator = passcodeValidator;
        this.commandRunner = commandRunner;
    }

    @Override
    public void handle(ClassicHttpRequest request, ClassicHttpResponse response, String subPath)
            throws IOException, HttpException {
        logger.debug("Handling request controller={} method={} subPath={}", getName(), request.getMethod(), subPath);
        String route = normalizeRoute(subPath);
        Map<String, String> query = HttpRequestUtil.queryParams(HttpRequestUtil.requestUri(request));

        if (!validatePassCode(request, response, query)) {
            return;
        }

        if ("GET".equalsIgnoreCase(request.getMethod()) && "exists".equalsIgnoreCase(route)) {
            String project = readValue(request, query, "projectName", "project");
            String filePath = readValue(request, query, "filePath", "path", "folder");
            String fileName = readValue(request, query, "fileName", "file");
            if (!require(response, "fileName", fileName)) {
                return;
            }
            if ("YES".equalsIgnoreCase(readValue(request, query, "isForInsightLink", "insightLink"))) {
                String group = rbGroup();
                filePath = filePath == null || filePath.isBlank() ? group : appendSlash(filePath) + group;
            }
            Path path = safeTempPath(project, filePath, fileName);
            boolean exists = Files.exists(path);
            String content = exists ? fileName + " was found" : fileName + " was not found";
            respondJson(response, exists ? HttpStatus.SC_OK : HttpStatus.SC_NOT_ACCEPTABLE,
                    "{\"Content\":\"" + JsonUtil.escape(content) + "\"}");
            return;
        }

        if ("POST".equalsIgnoreCase(request.getMethod()) && "sort".equalsIgnoreCase(route)) {
            handleSort(request, response);
            return;
        }

        if ("POST".equalsIgnoreCase(request.getMethod()) && "sort/rows".equalsIgnoreCase(route)) {
            handleSortRows(request, response);
            return;
        }

        if ("POST".equalsIgnoreCase(request.getMethod()) && "sort/columns".equalsIgnoreCase(route)) {
            handleSortColumns(request, response, false);
            return;
        }

        if ("POST".equalsIgnoreCase(request.getMethod()) && "sort/columns/existinglist".equalsIgnoreCase(route)) {
            handleSortColumns(request, response, true);
            return;
        }

        if ("PUT".equalsIgnoreCase(request.getMethod()) && "".equals(route)) {
            String project = HttpRequestUtil.headerValue(request, "projectName");
            Path from = safeTempPath(project,
                    HttpRequestUtil.headerValue(request, "originalFilePath"),
                    HttpRequestUtil.headerValue(request, "originalFileName"));
            Path to = safeTempPath(project,
                    HttpRequestUtil.headerValue(request, "newFilePath"),
                    HttpRequestUtil.headerValue(request, "newFileName"));
            Files.createDirectories(to.getParent());
            boolean replace = "YES".equalsIgnoreCase(HttpRequestUtil.headerValue(request, "replaceExistingFile"));
            if (replace) {
                Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.copy(from, to);
            }
            respondJson(response, HttpStatus.SC_OK, "{\"newFileName\":\"" + JsonUtil.escape(to.getFileName().toString()) + "\"}");
            return;
        }

        if ("DELETE".equalsIgnoreCase(request.getMethod()) && "".equals(route)) {
            String project = HttpRequestUtil.headerValue(request, "projectName");
            Path file = safeTempPath(project,
                    HttpRequestUtil.headerValue(request, "filePath"),
                    HttpRequestUtil.headerValue(request, "fileName"));
            Files.deleteIfExists(file);
            respondJson(response, HttpStatus.SC_OK, "{\"result\":\"" + JsonUtil.escape(file.getFileName().toString() + " has been deleted") + "\"}");
            return;
        }

        if ("DELETE".equalsIgnoreCase(request.getMethod()) && "folder".equalsIgnoreCase(route)) {
            String project = HttpRequestUtil.headerValue(request, "projectName");
            String folderName = HttpRequestUtil.headerValue(request, "folderName");
            Path folder = safeTempPath(project, folderName, "");
            deleteRecursively(folder);
            respondJson(response, HttpStatus.SC_OK, "{\"result\":\"" + JsonUtil.escape(folderName + " has been deleted") + "\"}");
            return;
        }

        if ("GET".equalsIgnoreCase(request.getMethod()) && "".equals(route)) {
            String project = HttpRequestUtil.headerValue(request, "projectName");
            Path file = safeTempPath(project,
                    HttpRequestUtil.headerValue(request, "filePath"),
                    HttpRequestUtil.headerValue(request, "fileName"));
            String result = Files.readString(file, StandardCharsets.UTF_8);
            respondJson(response, HttpStatus.SC_OK, "{\"result\":\"" + JsonUtil.escape(result) + "\"}");
            return;
        }

        if ("GET".equalsIgnoreCase(request.getMethod()) && "json".equalsIgnoreCase(route)) {
            String project = HttpRequestUtil.headerValue(request, "projectName");
            Path file = safeTempPath(project,
                    HttpRequestUtil.headerValue(request, "filePath"),
                    HttpRequestUtil.headerValue(request, "fileName"));
            String json = Files.readString(file, StandardCharsets.UTF_8).trim().replace("\\n  \\", "");
            respondJson(response, HttpStatus.SC_OK, "{\"json\":\"" + JsonUtil.escape(json) + "\"}");
            return;
        }

        if ("POST".equalsIgnoreCase(request.getMethod()) && "".equals(route)) {
            String project = HttpRequestUtil.headerValue(request, "projectName");
            Path file = safeTempPath(project,
                    HttpRequestUtil.headerValue(request, "filePath"),
                    HttpRequestUtil.headerValue(request, "fileName"));
            Files.createDirectories(file.getParent());
            String contents = HttpRequestUtil.headerValue(request, "contents");
            boolean deleteIfExists = "Yes".equalsIgnoreCase(HttpRequestUtil.headerValue(request, "deleteIfExists"));
            if (deleteIfExists) {
                Files.deleteIfExists(file);
            }
            Files.writeString(file, contents == null ? "" : contents, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            respondJson(response, HttpStatus.SC_OK, "{\"result\":\"" + JsonUtil.escape(file.getFileName().toString() + " has been written to") + "\"}");
            return;
        }

        if ("DELETE".equalsIgnoreCase(request.getMethod()) && "line".equalsIgnoreCase(route)) {
            String project = HttpRequestUtil.headerValue(request, "projectName");
            Path file = safeTempPath(project,
                    HttpRequestUtil.headerValue(request, "filePath"),
                    HttpRequestUtil.headerValue(request, "fileName"));
            String match = HttpRequestUtil.headerValue(request, "matchedStringInLine");
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            List<String> filtered = new ArrayList<>();
            for (String line : lines) {
                if (!line.contains(match)) {
                    filtered.add(line);
                }
            }
            Files.write(file, filtered, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
            respondJson(response, HttpStatus.SC_OK, "{\"result\":\"" + JsonUtil.escape(file.getFileName().toString() + " has been modified") + "\"}");
            return;
        }

        if ("GET".equalsIgnoreCase(request.getMethod()) && "line".equalsIgnoreCase(route)) {
            String project = HttpRequestUtil.headerValue(request, "projectName");
            Path file = safeTempPath(project,
                    HttpRequestUtil.headerValue(request, "filePath"),
                    HttpRequestUtil.headerValue(request, "fileName"));
            String match = HttpRequestUtil.headerValue(request, "matchStringInFile");
            boolean found = Files.readAllLines(file, StandardCharsets.UTF_8).stream().anyMatch(l -> l.contains(match));
            respondJson(response, found ? HttpStatus.SC_OK : HttpStatus.SC_NOT_ACCEPTABLE,
                    "{\"Content\":\"" + (found ? "Found String" : "String not found") + "\"}");
            return;
        }

        if ("PUT".equalsIgnoreCase(request.getMethod()) && "line".equalsIgnoreCase(route)) {
            String project = HttpRequestUtil.headerValue(request, "projectName");
            Path file = safeTempPath(project,
                    HttpRequestUtil.headerValue(request, "filePath"),
                    HttpRequestUtil.headerValue(request, "fileName"));
            String textLine = HttpRequestUtil.headerValue(request, "textLine");
            Files.writeString(file, textLine == null ? "" : textLine, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            respondJson(response, HttpStatus.SC_OK,
                    "{\"result\":\"" + JsonUtil.escape(file.getFileName().toString() + " has been appended to") + "\"}");
            return;
        }

        if ("POST".equalsIgnoreCase(request.getMethod()) && "line".equalsIgnoreCase(route)) {
            String project = HttpRequestUtil.headerValue(request, "projectName");
            Path file = safeTempPath(project,
                    HttpRequestUtil.headerValue(request, "filePath"),
                    HttpRequestUtil.headerValue(request, "fileName"));
            String replaceWith = HttpRequestUtil.headerValue(request, "replaceWith");
            String matchLine = HttpRequestUtil.headerValue(request, "matchLine");
            String matchStarts = HttpRequestUtil.headerValue(request, "matchStartsWithString");
            String matchEnds = HttpRequestUtil.headerValue(request, "matchEndsWithString");
            String matchContains = HttpRequestUtil.headerValue(request, "matchContainsString");
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                boolean matched = (!matchLine.isBlank() && line.equals(matchLine))
                        || (!matchStarts.isBlank() && line.startsWith(matchStarts))
                        || (!matchEnds.isBlank() && line.endsWith(matchEnds))
                        || (!matchContains.isBlank() && line.contains(matchContains));
                if (matched) {
                    lines.set(i, replaceWith);
                }
            }
            Files.write(file, lines, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
            respondJson(response, HttpStatus.SC_OK,
                    "{\"result\":\"" + JsonUtil.escape(file.getFileName().toString() + " has been modified") + "\"}");
            return;
        }

        if ("PUT".equalsIgnoreCase(request.getMethod()) && "column".equalsIgnoreCase(route)) {
            String project = HttpRequestUtil.headerValue(request, "projectName");
            Path file = safeTempPath(project,
                    HttpRequestUtil.headerValue(request, "filePath"),
                    HttpRequestUtil.headerValue(request, "fileName"));
            String columnDefinition = HttpRequestUtil.headerValue(request, "columnDefinition");
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            List<String> out = new ArrayList<>();
            for (String line : lines) {
                out.add(line + columnDefinition);
            }
            Files.write(file, out, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
            respondJson(response, HttpStatus.SC_OK,
                    "{\"result\":\"" + JsonUtil.escape(file.getFileName().toString() + " has been modified") + "\"}");
            return;
        }

        if ("DELETE".equalsIgnoreCase(request.getMethod()) && "column".equalsIgnoreCase(route)) {
            String project = HttpRequestUtil.headerValue(request, "projectName");
            Path file = safeTempPath(project,
                    HttpRequestUtil.headerValue(request, "filePath"),
                    HttpRequestUtil.headerValue(request, "fileName"));
            int column = parseIntOrDefault(HttpRequestUtil.headerValue(request, "columnNumber"), 0);
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            List<String> out = new ArrayList<>();
            for (String line : lines) {
                String[] arr = line.split(",", -1);
                List<String> list = new ArrayList<>(List.of(arr));
                if (column >= 0 && column < list.size()) {
                    list.remove(column);
                }
                out.add(String.join(",", list));
            }
            Files.write(file, out, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
            respondJson(response, HttpStatus.SC_OK,
                    "{\"result\":\"" + JsonUtil.escape(file.getFileName().toString() + " has been modified") + "\"}");
            return;
        }

        if ("GET".equalsIgnoreCase(request.getMethod()) && "diff".equalsIgnoreCase(route)) {
            String project = HttpRequestUtil.headerValue(request, "projectName");
            Path file1 = safeTempPath(project, "", HttpRequestUtil.headerValue(request, "file1"));
            Path file2 = safeTempPath(project, "", HttpRequestUtil.headerValue(request, "file2"));
            String diff = diffFiles(file1, file2);
            boolean matched = diff.isEmpty();
            respondJson(response, matched ? HttpStatus.SC_OK : HttpStatus.SC_NOT_ACCEPTABLE,
                    "{\"Content\":\"" + JsonUtil.escape(diff) + "\"}");
            return;
        }

        if ("GET".equalsIgnoreCase(request.getMethod()) && "names".equalsIgnoreCase(route)) {
            String project = HttpRequestUtil.headerValue(request, "projectName");
            String folderName = HttpRequestUtil.headerValue(request, "folderName");
            boolean recursive = "YES".equalsIgnoreCase(HttpRequestUtil.headerValue(request, "useRecursion"));
            Path folder = safeTempPath(project, folderName, "");
            List<String> names = listFiles(folder, recursive);
            respondJson(response, HttpStatus.SC_OK, "{\"fileNames\":\"" + JsonUtil.escape(String.join(",", names)) + "\"}");
            return;
        }

        if ("GET".equalsIgnoreCase(request.getMethod()) && "folder/names".equalsIgnoreCase(route)) {
            String project = HttpRequestUtil.headerValue(request, "projectName");
            String folderName = HttpRequestUtil.headerValue(request, "folderName");
            boolean recursive = "YES".equalsIgnoreCase(HttpRequestUtil.headerValue(request, "useRecursion"));
            Path folder = safeTempPath(project, folderName, "");
            List<String> names = listDirectories(folder, recursive);
            respondJson(response, HttpStatus.SC_OK, "{\"folderNames\":\"" + JsonUtil.escape(String.join(",", names)) + "\"}");
            return;
        }

        if ("PUT".equalsIgnoreCase(request.getMethod()) && "folder".equalsIgnoreCase(route)) {
            String project = HttpRequestUtil.headerValue(request, "projectName");
            String folderName = HttpRequestUtil.headerValue(request, "folderName");
            Path newFolder = safeTempPath(project, folderName, "");
            Files.createDirectories(newFolder);
            respondJson(response, HttpStatus.SC_OK, "{\"result\":\"Folders have been created\"}");
            return;
        }

        if ("PUT".equalsIgnoreCase(request.getMethod()) && "2".equals(route)) {
            handleCopyFromTemplate(request, response);
            return;
        }

        if ("PUT".equalsIgnoreCase(request.getMethod()) && "3".equals(route)) {
            handleCopyTemplateFolder(request, response);
            return;
        }

        if ("POST".equalsIgnoreCase(request.getMethod()) && "unzip".equalsIgnoreCase(route)) {
            handleUnzip(request, response, false);
            return;
        }

        if ("POST".equalsIgnoreCase(request.getMethod()) && "unzip2".equalsIgnoreCase(route)) {
            handleUnzip(request, response, true);
            return;
        }

        if ("POST".equalsIgnoreCase(request.getMethod()) && "zip2".equalsIgnoreCase(route)) {
            handleZip(request, response);
            return;
        }

        if ("GET".equalsIgnoreCase(request.getMethod()) && "sftp".equalsIgnoreCase(route)) {
            handleSftp(request, response, true);
            return;
        }

        if ("POST".equalsIgnoreCase(request.getMethod()) && "sftp".equalsIgnoreCase(route)) {
            handleSftp(request, response, false);
            return;
        }

        if ("PUT".equalsIgnoreCase(request.getMethod()) && "diff/binary".equalsIgnoreCase(route)) {
            handleBinaryDiff(request, response);
            return;
        }

        if ("GET".equalsIgnoreCase(request.getMethod()) && ("local".equalsIgnoreCase(route)
                || "download/local".equalsIgnoreCase(route))) {
            handleLocalDownload(request, response, query);
            return;
        }

        if ("POST".equalsIgnoreCase(request.getMethod()) && ("local".equalsIgnoreCase(route)
                || "upload/local".equalsIgnoreCase(route))) {
            handleLocalUpload(request, response, query);
            return;
        }

        super.handle(request, response, subPath);
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

    private void handleSort(ClassicHttpRequest request, ClassicHttpResponse response) {
        String project = HttpRequestUtil.headerValue(request, "projectName");
        String parentFile = HttpRequestUtil.headerValue(request, "parentFile");
        String childFile = HttpRequestUtil.headerValue(request, "childFile");
        String tempFolder = Path.of(System.getProperty("user.dir"), "data_files", "temp", project).toString();
        String shellFolder = Path.of(System.getProperty("user.dir"), "ShellCommand").toString();

        String parentSorted = parentFile + "_sorted";
        String childSorted = childFile + "_sorted";
        String result = commandRunner.run(shellFolder + "/sortCSVRows.sh", "\"" + tempFolder + "/" + parentFile + "\" \""
                + tempFolder + "/" + parentSorted + "\"");
        result = commandRunner.run(shellFolder + "/sortCSVRows.sh", "\"" + tempFolder + "/" + childFile + "\" \""
                + tempFolder + "/" + childSorted + "\"");
        respondJson(response, HttpStatus.SC_OK, "{\"result\":\"" + JsonUtil.escape(result) + "\"}");
    }

    private void handleSortRows(ClassicHttpRequest request, ClassicHttpResponse response) {
        String project = HttpRequestUtil.headerValue(request, "projectName");
        String originalFile = HttpRequestUtil.headerValue(request, "originalFile");
        String newFile = HttpRequestUtil.headerValue(request, "newFile");
        String tempFolder = Path.of(System.getProperty("user.dir"), "data_files", "temp", project).toString();
        String shell = Path.of(System.getProperty("user.dir"), "ShellCommand", "sortCSVRows.sh").toString();
        String result = commandRunner.run(shell, "\"" + tempFolder + "/" + originalFile + "\" \"" + tempFolder + "/"
                + newFile + "\"");
        respondJson(response, HttpStatus.SC_OK, "{\"result\":\"" + JsonUtil.escape(result) + "\"}");
    }

    private void handleSortColumns(ClassicHttpRequest request, ClassicHttpResponse response, boolean existingList) {
        String project = HttpRequestUtil.headerValue(request, "projectName");
        String originalFile = HttpRequestUtil.headerValue(request, "originalFileName");
        String newFile = HttpRequestUtil.headerValue(request, "newFileName");
        String tempFolder = Path.of(System.getProperty("user.dir"), "data_files", "temp", project).toString();
        String shellName = existingList ? "sortCSVColumnsExistingList.sh" : "sortCSVColumns.sh";
        String shell = Path.of(System.getProperty("user.dir"), "ShellCommand", shellName).toString();
        String args = originalFile + " " + newFile + " \"" + tempFolder + "\"";
        if (existingList) {
            args = args + " " + HttpRequestUtil.headerValue(request, "existingListFileName");
        } else {
            String columns = HttpRequestUtil.headerValue(request, "columnList");
            if (!columns.isBlank()) {
                args = args + " \"" + columns + "\"";
            }
        }
        String result = commandRunner.run(shell, args);
        respondJson(response, HttpStatus.SC_OK, "{\"result\":\"" + JsonUtil.escape(result) + "\"}");
    }

    private void handleCopyFromTemplate(ClassicHttpRequest request, ClassicHttpResponse response) throws IOException {
        String group = HttpRequestUtil.headerValue(request, "group");
        String originalFileName = HttpRequestUtil.headerValue(request, "originalFileName");
        String newFilePath = HttpRequestUtil.headerValue(request, "newFilePath");
        String newFileName = HttpRequestUtil.headerValue(request, "newFileName");
        String accountName = envOr("rb_account", envOr("rbAccount", ""));

        Path template = Path.of(System.getProperty("user.dir"), "data_files", "templates", "insightlink", group, originalFileName);
        Path target = Path.of(System.getProperty("user.dir"), "data_files", "temp", "insightlink", accountName,
                appendSlash(newFilePath), newFileName).normalize();
        Files.createDirectories(target.getParent());
        Files.copy(template, target, StandardCopyOption.REPLACE_EXISTING);
        respondJson(response, HttpStatus.SC_OK,
                "{\"newFileName\":\"" + JsonUtil.escape(newFileName) + "\"}");
    }

    private void handleCopyTemplateFolder(ClassicHttpRequest request, ClassicHttpResponse response) throws IOException {
        String projectName = HttpRequestUtil.headerValue(request, "projectName");
        String accountName = HttpRequestUtil.headerValue(request, "accountName");
        String newFilePath = appendSlash(HttpRequestUtil.headerValue(request, "newFilePath"));
        boolean deleteIfExists = "YES".equalsIgnoreCase(HttpRequestUtil.headerValue(request, "deleteIfExists"));

        Path sourceFolder = Path.of(System.getProperty("user.dir"), "data_files", "templates", "insightlink", rbGroup());
        Path targetFolder = Path.of(System.getProperty("user.dir"), "data_files", "temp", projectName, accountName, newFilePath);
        Files.createDirectories(targetFolder);
        if (Files.exists(sourceFolder)) {
            try (var files = Files.list(sourceFolder)) {
                files.filter(Files::isRegularFile).forEach(src -> {
                    try {
                        Path dest = targetFolder.resolve(src.getFileName().toString());
                        if (deleteIfExists) {
                            Files.deleteIfExists(dest);
                        }
                        Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException ignored) {
                    }
                });
            }
        }
        respondJson(response, HttpStatus.SC_OK, "{\"result\":\"Folders have been created\"}");
    }

    private void handleUnzip(ClassicHttpRequest request, ClassicHttpResponse response, boolean encryptedFlow) {
        String project = HttpRequestUtil.headerValue(request, "projectName");
        String fromFolder = appendSlash(HttpRequestUtil.headerValue(request, "folderNameFrom"));
        String fileName = HttpRequestUtil.headerValue(request, "fileName");
        String toFolder = HttpRequestUtil.headerValue(request, "folderNameTo");
        if (encryptedFlow && "YES".equalsIgnoreCase(HttpRequestUtil.headerValue(request, "isForInsightLink"))) {
            toFolder = appendSlash(rbGroup()) + toFolder;
        }
        String zipPassword = HttpRequestUtil.headerValue(request, "zipPassword");
        String tempFolder = Path.of(System.getProperty("user.dir"), "data_files", "temp", project).toString();
        String args = "x \"" + tempFolder + "/" + fromFolder + fileName + "\" -o\"" + tempFolder + "/" + toFolder + "\"";
        if (!zipPassword.isBlank()) {
            args = args + " -p" + zipPassword;
        }
        String result = commandRunner.run("7z", args);
        String key = encryptedFlow ? "resultOutput" : "result";
        if (!encryptedFlow) {
            result = "Unzipped " + fileName;
        }
        respondJson(response, HttpStatus.SC_OK, "{\"" + key + "\":\"" + JsonUtil.escape(result) + "\"}");
    }

    private void handleZip(ClassicHttpRequest request, ClassicHttpResponse response) {
        String project = HttpRequestUtil.headerValue(request, "projectName");
        String fromFolder = appendSlash(HttpRequestUtil.headerValue(request, "folderNameFrom"));
        String inputFiles = HttpRequestUtil.headerValue(request, "inputFileName");
        String zipFileName = HttpRequestUtil.headerValue(request, "zipFileName");
        String toFolder = appendSlash(HttpRequestUtil.headerValue(request, "folderNameTo"));
        String tempFolder = Path.of(System.getProperty("user.dir"), "data_files", "temp", project).toString();

        StringBuilder filesArg = new StringBuilder();
        for (String token : inputFiles.split("\\s+")) {
            if (!token.isBlank()) {
                filesArg.append(" \"").append(tempFolder).append("/").append(fromFolder).append(token).append("\"");
            }
        }
        String args = "a \"" + tempFolder + "/" + toFolder + zipFileName + "\"" + filesArg;
        String result = commandRunner.run("7z", args);
        respondJson(response, HttpStatus.SC_OK, "{\"resultOutput\":\"" + JsonUtil.escape(result) + "\"}");
    }

    private void handleSftp(ClassicHttpRequest request, ClassicHttpResponse response, boolean download) {
        String project = HttpRequestUtil.headerValue(request, "projectName");
        String serverIp = HttpRequestUtil.headerValue(request, "serverIP");
        String remoteRef = HttpRequestUtil.headerValue(request, "remoteFileReference");
        String localName = HttpRequestUtil.headerValue(request, "localFileName");
        String user = envOr("sftpUser", "");
        String tempFolder = Path.of(System.getProperty("user.dir"), "data_files", "temp", project).toString();
        String local = tempFolder + "/" + localName;

        String args = download
                ? "-q " + user + "@" + serverIp + ":\"" + remoteRef + "\" \"" + local + "\""
                : "-q \"" + local + "\" " + user + "@" + serverIp + ":\"" + remoteRef + "\"";
        String result = commandRunner.run("scp", args);
        if (download && !Files.exists(Path.of(local))) {
            try {
                Files.createDirectories(Path.of(local).getParent());
                Files.writeString(Path.of(local), "", StandardCharsets.UTF_8);
            } catch (IOException ignored) {
            }
        }
        respondJson(response, HttpStatus.SC_OK,
                "{\"Content\":\"" + JsonUtil.escape(result.isBlank() ? (localName + (download ? " has been downloaded" : " has been uploaded")) : result) + "\"}");
    }

    private void handleBinaryDiff(ClassicHttpRequest request, ClassicHttpResponse response) throws IOException {
        String project = HttpRequestUtil.headerValue(request, "projectName");
        Path file1 = safeTempPath(project, "", HttpRequestUtil.headerValue(request, "file1"));
        Path file2 = safeTempPath(project, "", HttpRequestUtil.headerValue(request, "file2"));
        byte[] left = Files.readAllBytes(file1);
        byte[] right = Files.readAllBytes(file2);
        boolean match = java.util.Arrays.equals(left, right);
        int code = match ? HttpStatus.SC_OK : HttpStatus.SC_NOT_ACCEPTABLE;
        respondJson(response, code, "{\"Content\":\"" + (match ? "match" : "no match") + "\"}");
    }

    private void handleLocalDownload(ClassicHttpRequest request, ClassicHttpResponse response, Map<String, String> query)
            throws IOException {
        String project = readValue(request, query, "projectName", "project");
        String fileName = readValue(request, query, "fileName", "file");
        if (!require(response, "fileName", fileName)) {
            return;
        }
        String folder = "";
        if ("YES".equalsIgnoreCase(readValue(request, query, "isForInsightLink", "insightLink"))) {
            folder = rbGroup();
        }
        Path file = safeTempPath(project, folder, fileName);
        if (!Files.exists(file)) {
            response.setCode(HttpStatus.SC_FORBIDDEN);
            response.setEntity(new StringEntity("Forbidden", ContentType.TEXT_PLAIN));
            return;
        }
        response.setCode(HttpStatus.SC_OK);
        response.setEntity(new ByteArrayEntity(Files.readAllBytes(file), ContentType.APPLICATION_OCTET_STREAM));
    }

    private void handleLocalUpload(ClassicHttpRequest request, ClassicHttpResponse response, Map<String, String> query)
            throws IOException, ParseException {
        String project = readValue(request, query, "projectName", "project");
        String fileName = readValue(request, query, "fileName", "file");
        if (!require(response, "fileName", fileName)) {
            return;
        }
        Path file = safeTempPath(project, "", fileName);
        Files.createDirectories(file.getParent());
        byte[] payload = request.getEntity() == null ? new byte[0] : EntityUtils.toByteArray(request.getEntity());
        Files.write(file, payload, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        respondJson(response, HttpStatus.SC_OK,
                "{\"Content\":\"" + JsonUtil.escape("Uploaded file " + fileName) + "\"}");
    }

    private static String normalizeRoute(String subPath) {
        String route = subPath == null ? "" : subPath;
        if (route.startsWith("file/")) {
            route = route.substring("file/".length());
        }
        return route;
    }

    private static String appendSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.endsWith("/") ? value : value + "/";
    }

    private static String rbGroup() {
        return envOr("rb_group", envOr("rbGroup", ""));
    }

    private static String envOr(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null ? defaultValue : value;
    }

    private static Path safeTempPath(String project, String relativePath, String fileName) {
        String rel = relativePath == null ? "" : relativePath;
        String name = fileName == null ? "" : fileName;
        String combined = (rel.endsWith("/") || rel.isBlank()) ? rel + name : rel + "/" + name;
        if (combined.contains("..")) {
            throw new IllegalArgumentException("Invalid folder reference");
        }
        return Path.of(System.getProperty("user.dir"), "data_files", "temp", project == null ? "" : project, combined).normalize();
    }

    private static void deleteRecursively(Path path) {
        try {
            if (!Files.exists(path)) {
                return;
            }
            try (var walk = Files.walk(path)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {
                    }
                });
            }
        } catch (IOException ignored) {
        }
    }

    private static List<String> listFiles(Path folder, boolean recursive) {
        List<String> names = new ArrayList<>();
        try {
            if (!Files.exists(folder)) {
                return names;
            }
            if (recursive) {
                try (var walk = Files.walk(folder)) {
                    walk.filter(Files::isRegularFile).forEach(p -> names.add(folder.relativize(p).toString().replace("\\", "/")));
                }
            } else {
                try (var stream = Files.list(folder)) {
                    stream.filter(Files::isRegularFile).forEach(p -> names.add(p.getFileName().toString()));
                }
            }
        } catch (IOException ignored) {
        }
        return names;
    }

    private static List<String> listDirectories(Path folder, boolean recursive) {
        List<String> names = new ArrayList<>();
        try {
            if (!Files.exists(folder)) {
                return names;
            }
            if (recursive) {
                try (var walk = Files.walk(folder)) {
                    walk.filter(Files::isDirectory)
                            .filter(p -> !p.equals(folder))
                            .forEach(p -> names.add(folder.relativize(p).toString().replace("\\", "/")));
                }
            } else {
                try (var stream = Files.list(folder)) {
                    stream.filter(Files::isDirectory).forEach(p -> names.add(p.getFileName().toString()));
                }
            }
        } catch (IOException ignored) {
        }
        return names;
    }

    private static String diffFiles(Path file1, Path file2) {
        try {
            List<String> a = Files.readAllLines(file1, StandardCharsets.UTF_8);
            List<String> b = Files.readAllLines(file2, StandardCharsets.UTF_8);
            StringBuilder sb = new StringBuilder();
            int max = Math.max(a.size(), b.size());
            for (int i = 0; i < max; i++) {
                String left = i < a.size() ? a.get(i) : "";
                String right = i < b.size() ? b.get(i) : "";
                if (!left.equals(right)) {
                    sb.append("< ").append(left).append(" | > ").append(right).append(System.lineSeparator());
                }
            }
            return sb.toString().trim();
        } catch (IOException ex) {
            return "Diff failed: " + ex.getMessage();
        }
    }

    private static int parseIntOrDefault(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ex) {
            return defaultValue;
        }
    }

    private static String readValue(ClassicHttpRequest request, Map<String, String> query, String... names) {
        for (String name : names) {
            String headerValue = HttpRequestUtil.headerValue(request, name);
            if (headerValue != null && !headerValue.isBlank()) {
                return headerValue;
            }
            String queryValue = query.get(name);
            if (queryValue != null && !queryValue.isBlank()) {
                return queryValue;
            }
        }
        return "";
    }

    private static boolean require(ClassicHttpResponse response, String fieldName, String value) {
        if (value != null && !value.isBlank()) {
            return true;
        }
        respondJson(response, HttpStatus.SC_BAD_REQUEST,
                "{\"error\":\"Missing required field: " + JsonUtil.escape(fieldName) + "\"}");
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

