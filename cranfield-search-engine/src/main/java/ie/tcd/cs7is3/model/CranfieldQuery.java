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
        // Remove Cranfield typographical artifact "-dash"
        String cleaned = rawText.replaceAll("-dash", " ");
        //Remove punctuation and Lucene special syntax characters
        cleaned = cleaned.replaceAll("[^a-zA-Z0-9\\s]", " ");
        // Remove conversational question boilerplate words that introduce false-positive matches 
        cleaned = cleaned.replaceAll("(?i)\\b(what|which|where|when|why|how|who|can|could|would|should|is|are|was|were|be|been|being|do|does|did|has|have|had)\\b", " ");
        cleaned = cleaned.replaceAll("(?i)\\b(papers|paper|studies|study|investigation|literature|work|criterion|criteria|dash)\\b", " ");
        cleaned = cleaned.replaceAll("(?i)\\b(possible|available|presently|found|give|given|describe|described|discuss|discussed|find|show|shown|solved|so far)\\b", " ");
        // 4. Normalize whitespace
        String result = cleaned.replaceAll("\\s+", " ").trim();
        // Fallback to basic sanitized text if all words were stripped
        if (result.isEmpty()) {
            return rawText.replaceAll("[^a-zA-Z0-9\\s]", " ").replaceAll("\\s+", " ").trim();
        }
        return result;
    }

    @Override
    public String toString() {
        return "CranfieldQuery{" +
                "id='" + id + '\'' +
                ", text='" + (rawText.length() > 60 ? rawText.substring(0, 57) + "..." : rawText) + '\'' +
                '}';
    }
}
