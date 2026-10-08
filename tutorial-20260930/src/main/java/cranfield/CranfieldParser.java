package cranfield;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CranfieldParser {
    private static final Pattern DOCUMENT_START = Pattern.compile("^\\.I\\s+(.+)$");
    private static final Pattern FIELD_MARKER = Pattern.compile("^\\.([A-Z])(?:\\s.*)?$");

    public List<Record> parse(Path path) throws IOException {
        List<Record> records = new ArrayList<>();
        Record currentRecord = null;
        String currentField = null;

        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmedLine = line.trim();
                Matcher documentStart = DOCUMENT_START.matcher(trimmedLine);
                if (documentStart.matches()) {
                    if (currentRecord != null) {
                        records.add(currentRecord);
                    }
                    String id = Integer.toString(Integer.parseInt(documentStart.group(1).trim()));
                    currentRecord = new Record(id);
                    currentField = null;
                    continue;
                }

                Matcher fieldMarker = FIELD_MARKER.matcher(trimmedLine);
                if (fieldMarker.matches()) {
                    currentField = fieldMarker.group(1);
                } else if (currentRecord != null && currentField != null && !trimmedLine.isEmpty()) {
                    currentRecord.append(currentField, trimmedLine);
                }
            }
        }

        if (currentRecord != null) {
            records.add(currentRecord);
        }
        return records;
    }

    public static final class Record {
        private final String id;
        private final Map<String, StringBuilder> fields = new LinkedHashMap<>();

        private Record(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        public Map<String, String> getFields() {
            Map<String, String> result = new LinkedHashMap<>();
            for (Map.Entry<String, StringBuilder> field : fields.entrySet()) {
                result.put(field.getKey(), field.getValue().toString());
            }
            return Collections.unmodifiableMap(result);
        }

        private void append(String field, String value) {
            StringBuilder text = fields.get(field);
            if (text == null) {
                text = new StringBuilder();
                fields.put(field, text);
            }
            if (text.length() > 0) {
                text.append(' ');
            }
            text.append(value);
        }
    }
}