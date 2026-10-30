package ie.tcd.cs7is3.engine;

import ie.tcd.cs7is3.model.CranfieldDocument;
import ie.tcd.cs7is3.model.CranfieldQuery;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Core Search Engine implementation using Apache Lucene.
 * Handles:
 * 1. Document indexing (storing and tokenizing Cranfield abstracts).
 * 2. Query execution with single-field and multi-field weighted scoring.
 * 3. TREC run file generation for trec_eval scoring.
 * 4. Interactive querying for live demonstration.
 */
public class SearchEngine implements AutoCloseable {

    private final Analyzer analyzer;
    private final Similarity similarity;
    private final Directory directory;
    private DirectoryReader directoryReader;
    private IndexSearcher indexSearcher;

    // Field boosting configurations
    private boolean useMultiField = false;
    private Map<String, Float> fieldBoosts;
    private String[] searchFields;

    /**
     * Initializes the search engine with an in-memory directory (ideal for rapid batch evaluations).
     */
    public SearchEngine(Analyzer analyzer, Similarity similarity) {
        this.analyzer = analyzer;
        this.similarity = similarity;
        this.directory = new ByteBuffersDirectory();
        configureDefaultMultiFieldBoosts();
    }

    /**
     * Initializes the search engine with a persistent disk-backed directory.
     */
    public SearchEngine(Analyzer analyzer, Similarity similarity, Path indexPath) throws IOException {
        this.analyzer = analyzer;
        this.similarity = similarity;
        this.directory = FSDirectory.open(indexPath);
        configureDefaultMultiFieldBoosts();
    }

    private void configureDefaultMultiFieldBoosts() {
        // Only search Title and Words. We deliberately EXCLUDE Author because Cranfield queries
        // are scientific aerodynamics questions with no author terms; including Author causes
        // common query words (e.g., "speed", "flow", "young") to match author surnames.
        this.searchFields = new String[]{"title", "words"};
        this.fieldBoosts = new HashMap<>();
        // Balanced boost: moderate 1.5x boost to title for topical emphasis without overpowering the abstract
        this.fieldBoosts.put("title", 1.5f);
        this.fieldBoosts.put("words", 1.0f);
    }

    public void setUseMultiField(boolean useMultiField) {
        this.useMultiField = useMultiField;
    }

    public void setCustomFieldBoosts(float titleBoost, float wordsBoost, float authorBoost) {
        this.fieldBoosts.put("title", titleBoost);
        this.fieldBoosts.put("words", wordsBoost);
        this.fieldBoosts.put("author", authorBoost);
    }

    /**
     * Builds the Lucene index from a list of Cranfield documents.
     */
    public void buildIndex(List<CranfieldDocument> documents) throws IOException {
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        config.setSimilarity(similarity);
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE);

        try (IndexWriter writer = new IndexWriter(directory, config)) {
            for (CranfieldDocument doc : documents) {
                Document luceneDoc = new Document();

                // "id" stored verbatim as StringField for exact lookup in evaluation
                luceneDoc.add(new StringField("id", doc.getId(), Field.Store.YES));

                // Individual analyzed and stored fields
                luceneDoc.add(new TextField("title", doc.getTitle(), Field.Store.YES));
                luceneDoc.add(new TextField("author", doc.getAuthor(), Field.Store.YES));
                luceneDoc.add(new TextField("bibliography", doc.getBibliography(), Field.Store.YES));
                luceneDoc.add(new TextField("words", doc.getWords(), Field.Store.YES));

                // Combined content field for single-field baseline querying
                luceneDoc.add(new TextField("content", doc.getAllContent(), Field.Store.NO));

                writer.addDocument(luceneDoc);
            }
            writer.commit();
        }

        // Open searcher
        if (directoryReader != null) {
            directoryReader.close();
        }
        this.directoryReader = DirectoryReader.open(directory);
        this.indexSearcher = new IndexSearcher(directoryReader);
        this.indexSearcher.setSimilarity(similarity);
    }

    /**
     * Parses a query string into a Lucene Query object.
     */
    public Query parseQuery(String queryText) throws ParseException {
        if (useMultiField) {
            MultiFieldQueryParser multiParser = new MultiFieldQueryParser(searchFields, analyzer, fieldBoosts);
            multiParser.setPhraseSlop(2);
            return multiParser.parse(queryText);
        } else {
            QueryParser parser = new QueryParser("content", analyzer);
            parser.setPhraseSlop(2);
            return parser.parse(queryText);
        }
    }

    /**
     * Executes all Cranfield queries and writes results in standard TREC run format:
     *   <query_id> Q0 <doc_id> <rank> <score> <run_tag>
     *
     * @param queries List of queries to execute
     * @param outputFile Path to output run file
     * @param runTag System run identifier (e.g., BM25_ENGLISH_BOOSTED)
     * @param maxHits Maximum documents to retrieve per query (typically 1000 for TREC recall)
     * @throws IOException If writing fails
     */
    public int executeBatchQueries(List<CranfieldQuery> queries, String outputFile, String runTag, int maxHits) throws IOException {
        if (indexSearcher == null) {
            throw new IllegalStateException("Index has not been built yet. Call buildIndex() first.");
        }

        File outFile = new File(outputFile);
        if (outFile.getParentFile() != null && !outFile.getParentFile().exists()) {
            outFile.getParentFile().mkdirs();
        }

        int linesWritten = 0;
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outFile))) {
            for (CranfieldQuery q : queries) {
                String cleanQuery = q.getSanitizedText();
                if (cleanQuery.isEmpty()) continue;

                try {
                    Query query = parseQuery(cleanQuery);
                    TopDocs topDocs = indexSearcher.search(query, maxHits);
                    ScoreDoc[] hits = topDocs.scoreDocs;

                    for (int rank = 0; rank < hits.length; rank++) {
                        ScoreDoc hit = hits[rank];
                        Document doc = indexSearcher.storedFields().document(hit.doc);
                        String docId = doc.get("id");

                        // TREC format: <qid> Q0 <doc_id> <rank> <score> <run_tag>
                        writer.write(String.format("%s Q0 %s %d %.6f %s\n",
                                q.getId(), docId, (rank + 1), hit.score, runTag));
                        linesWritten++;
                    }
                } catch (ParseException e) {
                    System.err.println("Warning: Skipping malformed query " + q.getId() + ": " + e.getMessage());
                }
            }
            writer.flush();
        }
        return linesWritten;
    }

    /**
     * Interactive search for demonstration.
     */
    public void searchInteractive(String userQuery, int topN) throws ParseException, IOException {
        if (indexSearcher == null) {
            System.out.println("Error: Index is not built.");
            return;
        }

        String sanitized = userQuery.replaceAll("[^a-zA-Z0-9\\s]", " ").trim();
        if (sanitized.isEmpty()) {
            System.out.println("Empty query entered.");
            return;
        }

        Query query = parseQuery(sanitized);
        TopDocs topDocs = indexSearcher.search(query, topN);
        ScoreDoc[] hits = topDocs.scoreDocs;

        System.out.println("\n------------------------------------------------------------");
        System.out.printf("Search Results for: \"%s\" (Found %d matches, showing top %d)\n",
                userQuery, topDocs.totalHits.value(), Math.min(topN, hits.length));
        System.out.println("------------------------------------------------------------");

        if (hits.length == 0) {
            System.out.println("No matching documents found.");
            return;
        }

        for (int i = 0; i < hits.length; i++) {
            Document doc = indexSearcher.storedFields().document(hits[i].doc);
            String title = doc.get("title");
            String author = doc.get("author");
            String words = doc.get("words");

            if (title == null) title = "N/A";
            if (author == null) author = "N/A";
            if (words != null && words.length() > 180) {
                words = words.substring(0, 177) + "...";
            }

            System.out.printf("[%d] Doc ID: %s | Score: %.4f\n", (i + 1), doc.get("id"), hits[i].score);
            System.out.printf("    Title:  %s\n", title);
            System.out.printf("    Author: %s\n", author);
            System.out.printf("    Words:  %s\n\n", words);
        }
    }

    public IndexSearcher getIndexSearcher() {
        return indexSearcher;
    }

    @Override
    public void close() throws IOException {
        if (directoryReader != null) {
            directoryReader.close();
        }
        if (directory != null) {
            directory.close();
        }
    }
}
