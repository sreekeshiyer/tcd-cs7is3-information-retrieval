package ie.tcd.cs7is3.model;

/**
 * Represents an individual document (abstract) from the Cranfield collection.
 * The Cranfield collection includes:
 * - .I: Document ID
 * - .T: Title of the document
 * - .A: Author(s)
 * - .B: Bibliographic information
 * - .W: Words / Abstract / Main body
 */
public class CranfieldDocument {
    private String id;
    private String title;
    private String author;
    private String bibliography;
    private String words;

    public CranfieldDocument() {
        this.title = "";
        this.author = "";
        this.bibliography = "";
        this.words = "";
    }

    public CranfieldDocument(String id, String title, String author, String bibliography, String words) {
        this.id = id;
        this.title = title != null ? title : "";
        this.author = author != null ? author : "";
        this.bibliography = bibliography != null ? bibliography : "";
        this.words = words != null ? words : "";
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getBibliography() {
        return bibliography;
    }

    public void setBibliography(String bibliography) {
        this.bibliography = bibliography;
    }

    public String getWords() {
        return words;
    }

    public void setWords(String words) {
        this.words = words;
    }

    /**
     * Combined textual content (Title + Author + Words) used for single-field baseline indexing.
     */
    public String getAllContent() {
        StringBuilder sb = new StringBuilder();
        if (!title.isEmpty()) sb.append(title).append(" ");
        if (!words.isEmpty()) sb.append(words);
        return sb.toString().trim();
    }

    @Override
    public String toString() {
        return "CranfieldDocument{" +
                "id='" + id + '\'' +
                ", title='" + (title.length() > 50 ? title.substring(0, 47) + "..." : title) + '\'' +
                '}';
    }
}
