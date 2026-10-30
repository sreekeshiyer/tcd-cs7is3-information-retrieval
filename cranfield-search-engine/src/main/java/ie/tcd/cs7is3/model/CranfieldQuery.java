package ie.tcd.cs7is3.model;

/**
 * Represents an information request (query) from the cran.qry file.
 * The Cranfield queries are tagged with:
 * - .I: Query ID (1 to 225)
 * - .W: Natural language query text
 */
public class CranfieldQuery {
    private String id;
    private String rawText;

    public CranfieldQuery(String id, String rawText) {
        this.id = id;
        this.rawText = rawText != null ? rawText.trim() : "";
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRawText() {
        return rawText;
    }

    public void setRawText(String rawText) {
        this.rawText = rawText != null ? rawText.trim() : "";
    }

    /**
     * Sanitizes the query text by stripping punctuation and Lucene special syntax characters
     * (+ - && || ! ( ) { } [ ] ^ " ~ * ? : \ /) that cause syntax errors when passed into QueryParser.
     */
    public String getSanitizedText() {
        if (rawText == null || rawText.isEmpty()) {
            return "";
        }
        // Remove question marks, asterisks, brackets, quotes, slashes, etc.
        String cleaned = rawText.replaceAll("[^a-zA-Z0-9\\s]", " ");
        // Normalize whitespace
        return cleaned.replaceAll("\\s+", " ").trim();
    }

    @Override
    public String toString() {
        return "CranfieldQuery{" +
                "id='" + id + '\'' +
                ", text='" + (rawText.length() > 60 ? rawText.substring(0, 57) + "..." : rawText) + '\'' +
                '}';
    }
}
