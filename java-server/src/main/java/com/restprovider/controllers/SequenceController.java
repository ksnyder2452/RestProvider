package com.restprovider.controllers;

import com.restprovider.core.HttpRequestUtil;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import com.restprovider.core.BaseController;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

/**
 * Controller for the Sequence integration endpoints.
 *
 * <p>This class maps controller routes, validates request input aliases, and
 * returns API responses aligned with RestProvider automation behavior.</p>
 */
public class SequenceController extends BaseController {
    /**
     * Creates a controller with default runtime dependencies.
     */
    public SequenceController() {
        super("Sequence");
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

        if ("POST".equals(method) && ("".equals(route) || "create".equalsIgnoreCase(route))) {
            String project = readValue(request, query, "projectName", "project");
            String sequenceName = readValue(request, query, "sequenceName", "name");
            String recreate = readValue(request, query, "recreateIfExists", "recreate");
            int minVal = parseIntOrDefault(readValue(request, query, "minVal", "startValue"), 0);
            int incrementBy = parseIntOrDefault(readValue(request, query, "incrementBy", "step"), 1);
            int maxVal = parseIntOrDefault(readValue(request, query, "maxVal", "endValue"), 999999999);
            boolean shared = "YES".equalsIgnoreCase(readValue(request, query, "share", "shared"));

            if (!require(response, "sequenceName", sequenceName)) {
                return;
            }

            Path seqFile = sequenceFile(project, sequenceName, shared);
            Files.createDirectories(seqFile.getParent());
            if (Files.exists(seqFile) && "YES".equalsIgnoreCase(recreate)) {
                Files.deleteIfExists(seqFile);
            } else if (Files.exists(seqFile) && "NO".equalsIgnoreCase(recreate)) {
                respondText(response, HttpStatus.SC_OK, "Sequence " + sequenceName + " already exists.");
                return;
            }

            writeSequence(seqFile, minVal, maxVal, incrementBy, "No", minVal);
            respondText(response, HttpStatus.SC_OK, "Sequence " + sequenceName + " has been created.");
            return;
        }

        if ("DELETE".equals(method) && ("".equals(route) || "delete".equalsIgnoreCase(route))) {
            String project = readValue(request, query, "projectName", "project");
            String sequenceName = readValue(request, query, "sequenceName", "name");
            if (!require(response, "sequenceName", sequenceName)) {
                return;
            }
            Path local = sequenceFile(project, sequenceName, false);
            Path global = sequenceFile(project, sequenceName, true);
            Files.deleteIfExists(local);
            if (Files.exists(global)) {
                Files.deleteIfExists(global);
            }
            respondText(response, HttpStatus.SC_OK, "Sequence " + sequenceName + " has been deleted.");
            return;
        }

        if ("GET".equals(method) && ("nextval".equalsIgnoreCase(route) || "next".equalsIgnoreCase(route))) {
            String project = readValue(request, query, "projectName", "project");
            String sequenceName = readValue(request, query, "sequenceName", "name");
            if (!require(response, "sequenceName", sequenceName)) {
                return;
            }
            Path seqFile = resolveSequenceFile(project, sequenceName);
            Map<String, String> values = readSequence(seqFile);
            int currVal = parseIntOrDefault(values.get("currVal"), 0);
            int incrementBy = parseIntOrDefault(values.get("incrementBy"), 1);
            int minVal = parseIntOrDefault(values.get("minVal"), 0);
            int maxVal = parseIntOrDefault(values.get("maxVal"), 999999999);
            String share = values.getOrDefault("share", "No");

            int newVal = currVal + incrementBy;
            writeSequence(seqFile, minVal, maxVal, incrementBy, share, newVal);
            respondText(response, HttpStatus.SC_OK, String.valueOf(newVal));
            return;
        }

        if ("GET".equals(method) && ("currval".equalsIgnoreCase(route) || "current".equalsIgnoreCase(route))) {
            String project = readValue(request, query, "projectName", "project");
            String sequenceName = readValue(request, query, "sequenceName", "name");
            if (!require(response, "sequenceName", sequenceName)) {
                return;
            }
            Path seqFile = resolveSequenceFile(project, sequenceName);
            Map<String, String> values = readSequence(seqFile);
            respondText(response, HttpStatus.SC_OK, values.getOrDefault("currVal", "0"));
            return;
        }

        super.handle(request, response, subPath);
    }

    private static String normalizeRoute(String subPath) {
        String route = subPath == null ? "" : subPath;
        if (route.startsWith("sequence/")) {
            route = route.substring("sequence/".length());
        }
        if ("sequence".equalsIgnoreCase(route)) {
            return "";
        }
        return route;
    }

    private static Path sequenceFile(String projectName, String sequenceName, boolean shared) {
        String normalized = (sequenceName == null ? "sequence" : sequenceName).replace(" ", "_") + ".xml";
        Path root = Path.of(System.getProperty("user.dir"), "data_files", "temp");
        return shared
                ? root.resolve(normalized)
                : root.resolve(projectName == null ? "" : projectName).resolve(normalized);
    }

    private static Path resolveSequenceFile(String projectName, String sequenceName) {
        Path local = sequenceFile(projectName, sequenceName, false);
        if (Files.exists(local)) {
            return local;
        }
        return sequenceFile(projectName, sequenceName, true);
    }

    private static Map<String, String> readSequence(Path path) {
        try {
            String xml = Files.readString(path, StandardCharsets.UTF_8);
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                    .parse(new InputSource(new StringReader(xml)));
            Map<String, String> out = new HashMap<>();
            out.put("minVal", nodeText(doc, "minVal", "0"));
            out.put("maxVal", nodeText(doc, "maxVal", "999999999"));
            out.put("incrementBy", nodeText(doc, "incrementBy", "1"));
            out.put("share", nodeText(doc, "share", "No"));
            out.put("currVal", nodeText(doc, "currVal", "0"));
            return out;
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to read sequence file: " + ex.getMessage(), ex);
        }
    }

    private static void writeSequence(Path path, int minVal, int maxVal, int incrementBy, String share, int currVal) {
        try {
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
            Element root = doc.createElement("Sequence_Definition");
            doc.appendChild(root);
            appendNode(doc, root, "minVal", String.valueOf(minVal));
            appendNode(doc, root, "maxVal", String.valueOf(maxVal));
            appendNode(doc, root, "incrementBy", String.valueOf(incrementBy));
            appendNode(doc, root, "share", share);
            appendNode(doc, root, "currVal", String.valueOf(currVal));

            var transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.setOutputProperty(OutputKeys.INDENT, "no");
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(doc), new StreamResult(writer));
            Files.writeString(path, writer.toString(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to write sequence file: " + ex.getMessage(), ex);
        }
    }

    private static String nodeText(Document doc, String tag, String defaultValue) {
        var list = doc.getElementsByTagName(tag);
        if (list.getLength() == 0) {
            return defaultValue;
        }
        String v = list.item(0).getTextContent();
        return v == null || v.isBlank() ? defaultValue : v;
    }

    private static void appendNode(Document doc, Element root, String name, String value) {
        Element e = doc.createElement(name);
        e.appendChild(doc.createTextNode(value));
        root.appendChild(e);
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
        response.setCode(HttpStatus.SC_BAD_REQUEST);
        response.setEntity(new StringEntity("Missing required field: " + fieldName, ContentType.TEXT_PLAIN));
        return false;
    }

    private static void respondText(ClassicHttpResponse response, int code, String body) {
        response.setCode(code);
        response.setEntity(new StringEntity(body, ContentType.TEXT_PLAIN));
    }
}


