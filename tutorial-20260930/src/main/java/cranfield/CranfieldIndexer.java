package cranfield;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

public final class CranfieldIndexer {
    private CranfieldIndexer() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length > 2) {
            System.err.println("Usage: java -jar cranfield-indexer-1.0.0.jar [corpus-file] [index-directory]");
            System.exit(1);
        }

        Path corpusPath = Paths.get(args.length > 0 ? args[0] : "cran.all.1400");
        Path indexPath = Paths.get(args.length > 1 ? args[1] : "index");
        if (!Files.isRegularFile(corpusPath)) {
            throw new IOException("Corpus file not found: " + corpusPath.toAbsolutePath());
        }

        List<CranfieldParser.Record> records = new CranfieldParser().parse(corpusPath);
        Files.createDirectories(indexPath);

        try (Analyzer analyzer = new StandardAnalyzer();
             Directory directory = FSDirectory.open(indexPath);
             IndexWriter writer = new IndexWriter(directory,
                     new IndexWriterConfig(analyzer).setOpenMode(IndexWriterConfig.OpenMode.CREATE))) {
            for (CranfieldParser.Record record : records) {
                addDocument(writer, record);
            }
        }

        System.out.println("Indexed " + records.size() + " documents from " + corpusPath.toAbsolutePath());
        System.out.println("Index directory: " + indexPath.toAbsolutePath());
    }

    private static void addDocument(IndexWriter writer, CranfieldParser.Record record) throws IOException {
        Document document = new Document();
        document.add(new StringField("id", record.getId(), Field.Store.YES));

        StringBuilder contents = new StringBuilder();
        for (Map.Entry<String, String> field : record.getFields().entrySet()) {
            String fieldName = field.getKey().toLowerCase(Locale.ROOT);
            document.add(new TextField(fieldName, field.getValue(), Field.Store.YES));
            if (contents.length() > 0) {
                contents.append(' ');
            }
            contents.append(field.getValue());
        }
        document.add(new TextField("contents", contents.toString(), Field.Store.NO));
        writer.addDocument(document);
    }
}