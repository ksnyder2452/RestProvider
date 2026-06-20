package com.restprovider.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

public final class CsvUtil {
    private static final Pattern COLUMN_SEPARATOR = Pattern.compile("[\\s,]+");

    private CsvUtil() {
    }

    public static void sortRows(Path input, Path output) throws IOException {
        if (!hasContent(input)) {
            copy(input, output);
            return;
        }

        List<List<String>> rows = readRows(input);
        if (rows.isEmpty()) {
            copy(input, output);
            return;
        }

        List<String> header = rows.get(0);
        List<List<String>> dataRows = new ArrayList<>(rows.subList(1, rows.size()));
        dataRows.sort(CsvUtil::compareRows);

        writeRows(output, header, dataRows);
    }

    public static void sortColumns(Path input, Path output, String columnList) throws IOException {
        if (!hasContent(input)) {
            copy(input, output);
            return;
        }

        List<List<String>> rows = readRows(input);
        if (rows.isEmpty()) {
            copy(input, output);
            return;
        }

        List<String> header = rows.get(0);
        List<Integer> selectedColumns = resolveColumns(header, columnList);
        List<List<String>> projectedRows = new ArrayList<>();
        projectedRows.add(projectRow(header, selectedColumns));
        for (int i = 1; i < rows.size(); i++) {
            projectedRows.add(projectRow(rows.get(i), selectedColumns));
        }

        writeRows(output, projectedRows);
    }

    public static void sortColumnsFromListFile(Path input, Path output, Path listFile) throws IOException {
        String columnList = Files.exists(listFile)
                ? Files.readString(listFile, StandardCharsets.UTF_8).trim()
                : "";
        sortColumns(input, output, columnList);
    }

    private static boolean hasContent(Path input) throws IOException {
        return Files.exists(input) && Files.size(input) > 0;
    }

    private static void copy(Path input, Path output) throws IOException {
        Files.copy(input, output, StandardCopyOption.REPLACE_EXISTING);
    }

    private static List<List<String>> readRows(Path input) throws IOException {
        List<String> lines = Files.readAllLines(input, StandardCharsets.UTF_8);
        List<List<String>> rows = new ArrayList<>(lines.size());
        for (String line : lines) {
            rows.add(parseRow(line));
        }
        return rows;
    }

    private static void writeRows(Path output, List<String> header, List<List<String>> rows) throws IOException {
        List<List<String>> allRows = new ArrayList<>(rows.size() + 1);
        allRows.add(header);
        allRows.addAll(rows);
        writeRows(output, allRows);
    }

    private static void writeRows(Path output, List<List<String>> rows) throws IOException {
        List<String> lines = new ArrayList<>(rows.size());
        for (List<String> row : rows) {
            lines.add(serializeRow(row));
        }
        Files.write(output, lines, StandardCharsets.UTF_8);
    }

    private static List<String> parseRow(String line) {
        List<String> values = new ArrayList<>();
        if (line == null) {
            values.add("");
            return values;
        }

        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(ch);
                }
            } else if (ch == ',') {
                values.add(current.toString());
                current.setLength(0);
            } else if (ch == '"') {
                inQuotes = true;
            } else {
                current.append(ch);
            }
        }
        values.add(current.toString());
        return values;
    }

    private static String serializeRow(List<String> row) {
        List<String> values = new ArrayList<>(row.size());
        for (String value : row) {
            values.add(serializeCell(value));
        }
        return String.join(",", values);
    }

    private static String serializeCell(String value) {
        String cell = value == null ? "" : value;
        boolean needsQuotes = cell.contains(",") || cell.contains("\"") || cell.contains("\n") || cell.contains("\r")
                || (!cell.isEmpty() && (Character.isWhitespace(cell.charAt(0)) || Character.isWhitespace(cell.charAt(cell.length() - 1))));
        if (!needsQuotes) {
            return cell;
        }
        return '"' + cell.replace("\"", "\"\"") + '"';
    }

    private static List<Integer> resolveColumns(List<String> header, String columnList) {
        List<Integer> indices = new ArrayList<>();
        if (columnList == null || columnList.isBlank()) {
            for (int i = 0; i < header.size(); i++) {
                indices.add(i);
            }
            return indices;
        }

        for (String token : COLUMN_SEPARATOR.split(columnList.trim())) {
            if (token == null || token.isBlank()) {
                continue;
            }

            Integer index = findHeaderIndex(header, token);
            if (index == null) {
                index = parseIndex(token);
            }

            if (index != null && index >= 0 && index < header.size() && !indices.contains(index)) {
                indices.add(index);
            }
        }

        if (indices.isEmpty()) {
            for (int i = 0; i < header.size(); i++) {
                indices.add(i);
            }
        }
        return indices;
    }

    private static Integer findHeaderIndex(List<String> header, String token) {
        for (int i = 0; i < header.size(); i++) {
            if (header.get(i).equals(token)) {
                return i;
            }
        }
        return null;
    }

    private static Integer parseIndex(String token) {
        try {
            int parsed = Integer.parseInt(token);
            return parsed > 0 ? parsed - 1 : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static List<String> projectRow(List<String> row, List<Integer> selectedColumns) {
        List<String> projected = new ArrayList<>(selectedColumns.size());
        for (Integer index : selectedColumns) {
            projected.add(index < row.size() ? row.get(index) : "");
        }
        return projected;
    }

    private static int compareRows(List<String> left, List<String> right) {
        int max = Math.max(left.size(), right.size());
        for (int i = 0; i < max; i++) {
            String leftValue = i < left.size() ? left.get(i) : "";
            String rightValue = i < right.size() ? right.get(i) : "";
            int comparison = leftValue.compareTo(rightValue);
            if (comparison != 0) {
                return comparison;
            }
        }
        return 0;
    }
}