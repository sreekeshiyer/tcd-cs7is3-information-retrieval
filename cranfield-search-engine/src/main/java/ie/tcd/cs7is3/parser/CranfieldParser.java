package ie.tcd.cs7is3.parser;

import ie.tcd.cs7is3.model.CranfieldDocument;
import ie.tcd.cs7is3.model.CranfieldQuery;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser for Cranfield Collection files:
 * - cran.all.1400: ~1400 scientific abstracts with tags .I, .T, .A, .B, .W
 * - cran.qry: ~225 information queries with tags .I, .W
 * - cranqrel: Relevance judgements mapping queries to documents
 */
public class CranfieldParser {

    /**
     * Parses the Cranfield document collection (cran.all.1400).
     *
     * @param filePath Path to cran.all.1400
     * @return List of parsed CranfieldDocument objects
     * @throws IOException If file reading fails
     */
    public static List<CranfieldDocument> parseDocuments(String filePath) throws IOException {
        List<CranfieldDocument> documents = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            CranfieldDocument currentDoc = null;
            String currentTag = null;
            StringBuilder textBuffer = new StringBuilder();

            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.startsWith(".I")) {
                    // Save previous document if exists
                    if (currentDoc != null) {
                        flushDocField(currentDoc, currentTag, textBuffer.toString().trim());
                        documents.add(currentDoc);
                    }
                    // Start new document
                    currentDoc = new CranfieldDocument();
                    String idPart = trimmed.substring(2).trim();
                    currentDoc.setId(idPart.replaceFirst("^0+(?!$)", ""));
                    currentTag = null;
                    textBuffer.setLength(0);
                } else if (trimmed.startsWith(".T") || trimmed.startsWith(".A") || 
                           trimmed.startsWith(".B") || trimmed.startsWith(".W")) {
                    // Flush accumulated text into previous tag
                    if (currentDoc != null && currentTag != null) {
                        flushDocField(currentDoc, currentTag, textBuffer.toString().trim());
                    }
                    currentTag = trimmed.substring(1, 2);
                    textBuffer.setLength(0);
                    // Check if content begins on the same line as the tag
                    if (trimmed.length() > 2) {
                        String remainder = trimmed.substring(2).trim();
                        if (!remainder.isEmpty()) {
                            textBuffer.append(remainder).append(" ");
                        }
                    }
                } else {
                    // Append line content to buffer
                    textBuffer.append(line).append(" ");
                }
            }

            // Flush the last document
            if (currentDoc != null) {
                if (currentTag != null) {
                    flushDocField(currentDoc, currentTag, textBuffer.toString().trim());
                }
                documents.add(currentDoc);
            }
        }

        return documents;
    }

    private static void flushDocField(CranfieldDocument doc, String tag, String content) {
        if (tag == null || content == null) return;
        switch (tag) {
            case "T":
                doc.setTitle(content);
                break;
            case "A":
                doc.setAuthor(content);
                break;
            case "B":
                doc.setBibliography(content);
                break;
            case "W":
                doc.setWords(content);
                break;
        }
    }

    /**
     * Parses the Cranfield query collection (cran.qry).
     *
     * @param filePath Path to cran.qry
     * @return List of parsed CranfieldQuery objects
     * @throws IOException If file reading fails
     */
    public static List<CranfieldQuery> parseQueries(String filePath) throws IOException {
        List<CranfieldQuery> queries = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            StringBuilder queryBuffer = new StringBuilder();
            boolean readingQuery = false;

            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.startsWith(".I")) {
                    if (queryBuffer.length() > 0) {
                        // NOTE: In the classic Cranfield dataset, cran.qry labels queries with gaps
                        // (e.g. 001, 002, 004, 008... up to 365), but cranqrel numbers the 225 queries
                        // sequentially from 1 to 225. We assign the sequential 1-based ID:
                        String seqId = String.valueOf(queries.size() + 1);
                        queries.add(new CranfieldQuery(seqId, queryBuffer.toString().trim()));
                    }
                    queryBuffer.setLength(0);
                    readingQuery = false;
                } else if (trimmed.startsWith(".W")) {
                    readingQuery = true;
                    // Check if query text starts on the same line as .W
                    if (trimmed.length() > 2) {
                        String remainder = trimmed.substring(2).trim();
                        if (!remainder.isEmpty()) {
                            queryBuffer.append(remainder).append(" ");
                        }
                    }
                } else if (readingQuery) {
                    queryBuffer.append(line).append(" ");
                }
            }

            // Flush final query
            if (queryBuffer.length() > 0) {
                String seqId = String.valueOf(queries.size() + 1);
                queries.add(new CranfieldQuery(seqId, queryBuffer.toString().trim()));
            }
        }

        return queries;
    }

    /**
     * Converts raw Cranfield relevance judgements (cranqrel) into official TREC qrels format.
     * Raw cranqrel typically has lines:
     *   <query_id> <doc_id> <relevance_grade> (or <query_id> 0 <doc_id> <relevance_grade>)
     * In Cranfield:
     *   Relevance scores range from 1 (most relevant) to 4 (minimum relevance), or -1 (not relevant).
     * trec_eval standard qrels format:
     *   <query_id> 0 <doc_id> <relevance>
     * Where relevance > 0 indicates relevant.
     *
     * @param inputQrelsPath Input path to raw cranqrel
     * @param outputTrecQrelsPath Output path for trec_eval compatible qrels file
     * @param binaryRelevance If true, maps any positive grade to 1, <=0 to 0. If false, preserves grade.
     * @return Number of relevance records converted
     * @throws IOException If I/O fails
     */
    public static int convertQrelsToTrec(String inputQrelsPath, String outputTrecQrelsPath, boolean binaryRelevance) throws IOException {
        int count = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(inputQrelsPath));
             BufferedWriter writer = new BufferedWriter(new FileWriter(outputTrecQrelsPath))) {

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                String[] parts = line.split("\\s+");
                String qid;
                String docId;
                int rel;

                if (parts.length >= 4) {
                    // Format: qid iter docId rel
                    qid = parts[0].replaceFirst("^0+(?!$)", "");
                    docId = parts[2].replaceFirst("^0+(?!$)", "");
                    rel = Integer.parseInt(parts[3]);
                } else if (parts.length == 3) {
                    // Format: qid docId rel
                    qid = parts[0].replaceFirst("^0+(?!$)", "");
                    docId = parts[1].replaceFirst("^0+(?!$)", "");
                    rel = Integer.parseInt(parts[2]);
                } else {
                    continue;
                }

                // In Cranfield: -1 means irrelevant. Positive numbers (1, 2, 3, 4) mean relevant.
                int trecRel;
                if (rel <= 0) {
                    trecRel = 0;
                } else if (binaryRelevance) {
                    trecRel = 1;
                } else {
                    // Graded relevance: 5 - rel inverts so that 1 (best) -> 4, 4 (marginal) -> 1
                    trecRel = (rel >= 1 && rel <= 4) ? (5 - rel) : 1;
                }

                // Output standard 4-column TREC format: <qid> 0 <doc_id> <relevance>
                writer.write(String.format("%s 0 %s %d\n", qid, docId, trecRel));
                count++;
            }
        }
        return count;
    }
}
