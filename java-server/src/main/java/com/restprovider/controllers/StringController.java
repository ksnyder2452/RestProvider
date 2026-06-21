package com.restprovider.controllers;

import com.restprovider.core.HttpRequestUtil;
import com.restprovider.util.JsonUtil;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.StringEntity;
import com.restprovider.core.BaseController;

/**
 * Controller for the String integration endpoints.
 *
 * <p>This class maps controller routes, validates request input aliases, and
 * returns API responses aligned with RestProvider automation behavior.</p>
 */
public class StringController extends BaseController {
    /**
     * Creates a controller with default runtime dependencies.
     */
    public StringController() {
        super("String");
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
        String normalized = normalizeSubPath(subPath);
        Map<String, String> query = HttpRequestUtil.queryParams(HttpRequestUtil.requestUri(request));
        if ("GET".equalsIgnoreCase(request.getMethod()) && normalized.startsWith("echo/")) {
            String echo = normalized.substring("echo/".length());
            response.setCode(HttpStatus.SC_OK);
            response.setEntity(new StringEntity(echo, ContentType.TEXT_PLAIN));
            return;
        }

        if ("GET".equalsIgnoreCase(request.getMethod()) && normalized.startsWith("echo2/")) {
            String echo = normalized.substring("echo2/".length());
            response.setCode(HttpStatus.SC_OK);
            response.setEntity(new StringEntity(
                    "{\"echoMe\":\"" + JsonUtil.escape(echo) + "\"}",
                    ContentType.APPLICATION_JSON));
            return;
        }

        if ("GET".equalsIgnoreCase(request.getMethod())
                && ("isnumber".equalsIgnoreCase(normalized) || "is-number".equalsIgnoreCase(normalized))) {
            String value = readValue(request, query, "stringVal", "value", "input");
            String positiveValidation = readValue(request, query, "positiveValidation", "expectNumber");
            if (!require(response, "stringVal", value)) {
                return;
            }
            boolean expectNumber = "YES".equalsIgnoreCase(positiveValidation);
            boolean isNumber = isInteger(value);
            String message;
            if (expectNumber) {
                message = isNumber
                        ? "PASSED: " + value + " is an integer"
                        : "FAILED: " + value + " is not an integer";
            } else {
                message = isNumber
                        ? "FAILED: " + value + " is an integer"
                        : "PASSED: " + value + " is not an integer";
            }
            response.setCode(HttpStatus.SC_OK);
            response.setEntity(new StringEntity(message, ContentType.TEXT_PLAIN));
            return;
        }

        if ("GET".equalsIgnoreCase(request.getMethod()) && "compare".equalsIgnoreCase(normalized)) {
            String s1 = readValue(request, query, "string1", "left", "expected");
            String s2 = readValue(request, query, "string2", "right", "actual");
            String ignoreCase = readValue(request, query, "ignoreCase", "caseInsensitive");
            if (!require(response, "string1", s1) || !require(response, "string2", s2)) {
                return;
            }
            boolean matched = "No".equalsIgnoreCase(ignoreCase) ? s1.equals(s2) : s1.equalsIgnoreCase(s2);
            response.setCode(matched ? HttpStatus.SC_OK : HttpStatus.SC_NOT_ACCEPTABLE);
            response.setEntity(new StringEntity(
                    "{\"matched\":\"" + (matched ? "Yes" : "No") + "\"}",
                    ContentType.APPLICATION_JSON));
            return;
        }

        if ("GET".equalsIgnoreCase(request.getMethod())
                && ("array/match".equalsIgnoreCase(normalized) || "array/compare".equalsIgnoreCase(normalized))) {
            String stringArray = readValue(request, query, "stringArray", "values");
            String stringToMatch = readValue(request, query, "stringToMatch", "matchValue");
            boolean ignoreCase = "YES".equalsIgnoreCase(readValue(request, query, "ignoreCase", "caseInsensitive"));
            boolean similarMatch = "YES".equalsIgnoreCase(readValue(request, query, "similarMatch", "fuzzyMatch"));
            int matchDistance = parseIntOrDefault(readValue(request, query, "matchDistance", "distance"), 1);
            if (!require(response, "stringArray", stringArray) || !require(response, "stringToMatch", stringToMatch)) {
                return;
            }

            List<String> values = splitCsv(stringArray);
            List<String> states = new ArrayList<>();
            int statusCode = HttpStatus.SC_OK;
            for (String current : values) {
                String left = ignoreCase ? current.toUpperCase(Locale.ROOT) : current;
                String right = ignoreCase ? stringToMatch.toUpperCase(Locale.ROOT) : stringToMatch;
                if (!similarMatch) {
                    if (left.equals(right)) {
                        states.add("YES");
                    } else {
                        states.add("NO");
                        statusCode = HttpStatus.SC_NOT_ACCEPTABLE;
                    }
                } else {
                    int dist = levenshtein(left, right);
                    if (dist < matchDistance) {
                        states.add("YES");
                    } else if (dist < matchDistance + 1) {
                        states.add("MAYBE");
                    } else {
                        states.add("NO");
                        statusCode = HttpStatus.SC_NOT_ACCEPTABLE;
                    }
                }
            }

            String result = String.join(",", states);
            response.setCode(statusCode);
            response.setEntity(new StringEntity(
                    "{\"result\":\"" + JsonUtil.escape(result) + "\"}",
                    ContentType.APPLICATION_JSON));
            return;
        }

        if ("GET".equalsIgnoreCase(request.getMethod())
                && ("json/to/array".equalsIgnoreCase(normalized) || "json/array".equalsIgnoreCase(normalized))) {
            String projectName = readValue(request, query, "projectName", "project");
            String elementName = readValue(request, query, "elementName", "jsonElement");
            String fileName = readValue(request, query, "fileName", "file");
            String filePath = readValue(request, query, "filePath", "path", "folder");
            if (!require(response, "elementName", elementName) || !require(response, "fileName", fileName)) {
                return;
            }

            String cleanPath = filePath.endsWith("/") ? filePath : filePath + "/";
            Path source = Path.of(System.getProperty("user.dir"), "data_files", "temp", projectName, cleanPath, fileName);
            String json = Files.readString(source, StandardCharsets.UTF_8);

            String splitStr = "\"" + elementName + "\":";
            String[] segments = json.split(splitStr);
            List<String> extracted = new ArrayList<>();
            for (int i = 1; i < segments.length; i++) {
                String s = segments[i];
                int commaIdx = s.indexOf(",");
                if (commaIdx > 1) {
                    extracted.add(s.substring(1, commaIdx));
                }
            }
            String result = String.join(",", extracted);
            response.setCode(HttpStatus.SC_OK);
            response.setEntity(new StringEntity(
                    "{\"result\":\"" + JsonUtil.escape(result) + "\"}",
                    ContentType.APPLICATION_JSON));
            return;
        }

        if ("GET".equalsIgnoreCase(request.getMethod()) && "encrypt".equalsIgnoreCase(normalized)) {
            String clear = readValue(request, query, "clearTextString", "value", "plainText");
            if (!require(response, "clearTextString", clear)) {
                return;
            }
            String passphrase = System.getenv("restprovider_pwd");
            if (passphrase == null || passphrase.isBlank()) {
                response.setCode(HttpStatus.SC_NOT_IMPLEMENTED);
                response.setEntity(new StringEntity(
                        "Encryption requires restprovider_pwd to be configured",
                        ContentType.TEXT_PLAIN));
                return;
            }
            String encrypted = encryptString(clear, passphrase);
            response.setCode(HttpStatus.SC_OK);
            response.setEntity(new StringEntity(encrypted, ContentType.TEXT_PLAIN));
            return;
        }

        if ("GET".equalsIgnoreCase(request.getMethod())
                && ("hash".equalsIgnoreCase(normalized) || "sha256".equalsIgnoreCase(normalized))) {
            String clear = readValue(request, query, "clearTextString", "value", "plainText");
            if (!require(response, "clearTextString", clear)) {
                return;
            }
            String hashed = sha256(clear);
            response.setCode(HttpStatus.SC_OK);
            response.setEntity(new StringEntity(hashed, ContentType.TEXT_PLAIN));
            return;
        }

        super.handle(request, response, subPath);
    }

    private static String normalizeSubPath(String subPath) {
        String normalized = subPath == null ? "" : subPath;
        if (normalized.startsWith("string/")) {
            normalized = normalized.substring("string/".length());
        }
        return normalized;
    }

    private static boolean isInteger(String value) {
        try {
            Integer.parseInt(value);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private static int parseIntOrDefault(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ex) {
            return defaultValue;
        }
    }

    private static List<String> splitCsv(String csv) {
        List<String> values = new ArrayList<>();
        if (csv == null || csv.isBlank()) {
            return values;
        }
        for (String token : csv.split(",")) {
            values.add(token.trim());
        }
        return values;
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
        response.setCode(HttpStatus.SC_BAD_REQUEST);
        response.setEntity(new StringEntity(
                "{\"error\":\"Missing required field: " + JsonUtil.escape(fieldName) + "\"}",
                ContentType.APPLICATION_JSON));
        return false;
    }

    private static int levenshtein(String s, String t) {
        int n = s.length();
        int m = t.length();
        if (n == 0) {
            return m;
        }
        if (m == 0) {
            return n;
        }

        int[][] d = new int[n + 1][m + 1];
        for (int i = 0; i <= n; i++) {
            d[i][0] = i;
        }
        for (int j = 0; j <= m; j++) {
            d[0][j] = j;
        }

        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                int cost = s.charAt(i - 1) == t.charAt(j - 1) ? 0 : 1;
                d[i][j] = Math.min(
                        Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1),
                        d[i - 1][j - 1] + cost);
            }
        }
        return d[n][m];
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to hash input", ex);
        }
    }

    private static String encryptString(String plainText, String passphrase) {
        try {
            byte[] iv = "pemgail9uzpgzl88".getBytes(StandardCharsets.UTF_8);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] key = digest.digest(passphrase.getBytes(StandardCharsets.UTF_8));
            SecretKeySpec secret = new SecretKeySpec(key, "AES");
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, secret, new IvParameterSpec(iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to encrypt input", ex);
        }
    }
}


