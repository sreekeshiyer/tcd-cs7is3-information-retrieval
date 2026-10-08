package cranfield;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

public final class CranfieldSearcher {
    private static final int RESULTS_PER_QUERY = 50;
    private static final String RUN_ID = "cranfield";

    private CranfieldSearcher() {
    }

    public static void main(String[] args) throws IOException, ParseException {
        if (args.length > 3) {
            System.err.println("Usage: java -cp target/cranfield-indexer-1.0.0.jar "
                    + "cranfield.CranfieldSearcher [query-file] [index-directory] [results-file]");
            System.exit(1);
        }

        Path queryPath = Paths.get(args.length > 0 ? args[0] : "cran.qry");
        Path indexPath = Paths.get(args.length > 1 ? args[1] : "index");
        Path resultsPath = Paths.get(args.length > 2 ? args[2] : "results.txt");
        if (!Files.isRegularFile(queryPath)) {
            throw new IOException("Query file not found: " + queryPath.toAbsolutePath());
        }
        if (!Files.isDirectory(indexPath)) {
            throw new IOException("Index directory not found: " + indexPath.toAbsolutePath());
        }

        List<CranfieldParser.Record> queries = new CranfieldParser().parse(queryPath);
        if (queries.isEmpty()) {
            throw new IOException("No queries found in: " + queryPath.toAbsolutePath());
        }

        Path resultsParent = resultsPath.toAbsolutePath().getParent();
        if (resultsParent != null) {
            Files.createDirectories(resultsParent);
        }

        try (Analyzer analyzer = new StandardAnalyzer();
             Directory directory = FSDirectory.open(indexPath)) {
            if (!DirectoryReader.indexExists(directory)) {
                throw new IOException("No Lucene index found in: " + indexPath.toAbsolutePath());
            }

            try (DirectoryReader reader = DirectoryReader.open(directory);
                 BufferedWriter results = Files.newBufferedWriter(resultsPath, StandardCharsets.UTF_8,
                         StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                         StandardOpenOption.WRITE)) {
                IndexSearcher searcher = new IndexSearcher(reader);
                QueryParser parser = new QueryParser("contents", analyzer);
                int resultCount = 0;
                int queryNumber = 0;

                for (CranfieldParser.Record queryRecord : queries) {
                    queryNumber++;
                    String queryText = getQueryText(queryRecord);
                    Query query = parser.parse(QueryParser.escape(queryText));
                    TopDocs topDocs = searcher.search(query, RESULTS_PER_QUERY);

                    for (int i = 0; i < topDocs.scoreDocs.length; i++) {
                        ScoreDoc scoreDoc = topDocs.scoreDocs[i];
                        Document document = searcher.doc(scoreDoc.doc);
                        results.write(Integer.toString(queryNumber));
                        results.write(" Q0 ");
                        results.write(document.get("id"));
                        results.write(" ");
                        results.write(Integer.toString(i + 1));
                        results.write(" ");
                        results.write(Float.toString(scoreDoc.score));
                        results.write(" ");
                        results.write(RUN_ID);
                        results.newLine();
                        resultCount++;
                    }
                }

                System.out.println("Ranked " + queries.size() + " queries against "
                        + reader.numDocs() + " documents; wrote " + resultCount
                        + " results to " + resultsPath.toAbsolutePath());
            }
        }
    }

    private static String getQueryText(CranfieldParser.Record record) throws IOException {
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, String> field : record.getFields().entrySet()) {
            if (text.length() > 0) {
                text.append(' ');
            }
            text.append(field.getValue());
        }
        if (text.length() == 0) {
            throw new IOException("Query " + record.getId() + " has no searchable text");
        }
        return text.toString();
    }
}
