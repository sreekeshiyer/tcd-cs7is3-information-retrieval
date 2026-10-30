package ie.tcd.cs7is3;

import ie.tcd.cs7is3.analysis.AnalyzerFactory;
import ie.tcd.cs7is3.engine.ExperimentRunner;
import ie.tcd.cs7is3.engine.SearchEngine;
import ie.tcd.cs7is3.engine.SimilarityFactory;
import ie.tcd.cs7is3.model.CranfieldDocument;
import ie.tcd.cs7is3.model.CranfieldQuery;
import ie.tcd.cs7is3.parser.CranfieldParser;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.search.similarities.Similarity;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Scanner;

/**
 * Main application entry point for the CS7IS3 Cranfield Search Engine.
 * 
 * Supports both interactive CLI mode (for classroom demonstration)
 * and batch execution mode (for automated TREC run generation).
 */
public class App {

    private static final String DEFAULT_DATA_DIR = "data/cran";
    private static final String DEFAULT_RESULTS_DIR = "results";

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("   CS7IS3 Information Retrieval & Web Search - Apache Lucene Search Engine      ");
        System.out.println("   Cranfield Collection Evaluation & Retrieval Benchmark                        ");
        System.out.println("================================================================================\n");

        if (args.length > 0) {
            handleCliArguments(args);
            return;
        }

        // Interactive Menu Mode
        runInteractiveMenu();
    }

    private static void handleCliArguments(String[] args) {
        String command = args[0].toLowerCase();
        try {
            switch (command) {
                case "--batch":
                case "-b":
                    String dataDir = args.length > 1 ? args[1] : locateDataDirectory();
                    String resultsDir = args.length > 2 ? args[2] : DEFAULT_RESULTS_DIR;
                    runBatchPipeline(dataDir, resultsDir);
                    break;

                case "--convert-qrels":
                case "-c":
                    String inputQrels = args.length > 1 ? args[1] : locateDataDirectory() + "/cranqrel";
                    String outputQrels = args.length > 2 ? args[2] : "data/QRelsCorrectedforTRECeval";
                    convertQrels(inputQrels, outputQrels);
                    break;

                case "--search":
                case "-s":
                    String analyzerName = args.length > 1 ? args[1] : "english";
                    String similarityName = args.length > 2 ? args[2] : "bm25";
                    boolean multiField = args.length <= 3 || Boolean.parseBoolean(args[3]);
                    startSearchLoop(locateDataDirectory(), analyzerName, similarityName, multiField);
                    break;

                case "--help":
                case "-h":
                default:
                    printHelp();
                    break;
            }
        } catch (Exception e) {
            System.err.println("Execution error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void runInteractiveMenu() {
        Scanner scanner = new Scanner(System.in);
        String dataDir = locateDataDirectory();

        while (true) {
            System.out.println("\nSelect an action:");
            System.out.println("  [1] Run All Experiments (Generates 7 TREC run files for trec_eval)");
            System.out.println("  [2] Convert cranqrel to TREC format (QRelsCorrectedforTRECeval)");
            System.out.println("  [3] Interactive Search Engine Demo (Search queries live)");
            System.out.println("  [4] Run Single Experiment with Custom Settings");
            System.out.println("  [0] Exit");
            System.out.print("\nEnter choice (0-4): ");

            String input = scanner.nextLine().trim();
            switch (input) {
                case "1":
                    try {
                        runBatchPipeline(dataDir, DEFAULT_RESULTS_DIR);
                    } catch (Exception e) {
                        System.err.println("Failed to run batch experiments: " + e.getMessage());
                    }
                    break;
                case "2":
                    try {
                        convertQrels(dataDir + "/cranqrel", "data/QRelsCorrectedforTRECeval");
                    } catch (Exception e) {
                        System.err.println("Failed to convert qrels: " + e.getMessage());
                    }
                    break;
                case "3":
                    interactiveSearchSetup(scanner, dataDir);
                    break;
                case "4":
                    runCustomExperiment(scanner, dataDir);
                    break;
                case "0":
                case "q":
                case "exit":
                    System.out.println("Exiting search engine. Goodbye!");
                    return;
                default:
                    System.out.println("Invalid selection. Please enter 0-4.");
            }
        }
    }

    private static void runBatchPipeline(String dataDir, String resultsDir) throws IOException {
        String docsPath = locateDocFile(dataDir);
        String queriesPath = dataDir + "/cran.qry";
        String qrelsPath = dataDir + "/cranqrel";

        if (!new File(docsPath).exists()) {
            System.err.println("Error: Document file not found at " + docsPath);
            System.err.println("Run 'bash scripts/download_cranfield.sh' to download the dataset.");
            return;
        }

        System.out.println("Parsing Cranfield documents from: " + docsPath);
        List<CranfieldDocument> documents = CranfieldParser.parseDocuments(docsPath);
        System.out.printf("Successfully loaded %d documents.\n", documents.size());
        if (documents.isEmpty()) {
            System.err.println("Error: 0 documents parsed. Please verify the document file contents.");
            return;
        }

        System.out.println("Parsing Cranfield queries from: " + queriesPath);
        List<CranfieldQuery> queries = CranfieldParser.parseQueries(queriesPath);
        System.out.printf("Successfully loaded %d queries.\n", queries.size());
        if (queries.isEmpty()) {
            System.err.println("Error: 0 queries parsed. Please verify " + queriesPath);
            return;
        }

        ExperimentRunner.runAll(documents, queries, resultsDir);
    }

    private static void convertQrels(String inputQrels, String outputQrels) throws IOException {
        File infile = new File(inputQrels);
        if (!infile.exists()) {
            System.err.println("Error: Input qrels not found at: " + inputQrels);
            return;
        }
        System.out.println("Converting " + inputQrels + " -> " + outputQrels + " ...");
        int count = CranfieldParser.convertQrelsToTrec(inputQrels, outputQrels, true);
        System.out.printf("Success! Wrote %d relevance records to %s\n", count, outputQrels);
    }

    private static void interactiveSearchSetup(Scanner scanner, String dataDir) {
        System.out.println("\n--- Search Engine Demo Configuration ---");
        System.out.print("Select Analyzer [standard / english / whitespace / custom] (default: english): ");
        String analyzerName = scanner.nextLine().trim();
        if (analyzerName.isEmpty()) analyzerName = "english";

        System.out.print("Select Similarity [bm25 / vsm / dirichlet] (default: bm25): ");
        String simName = scanner.nextLine().trim();
        if (simName.isEmpty()) simName = "bm25";

        System.out.print("Enable Multi-Field Title Boosting (3.0x Title)? [y/n] (default: y): ");
        String boostChoice = scanner.nextLine().trim().toLowerCase();
        boolean multiField = !boostChoice.equals("n") && !boostChoice.equals("no");

        try {
            startSearchLoop(dataDir, analyzerName, simName, multiField);
        } catch (Exception e) {
            System.err.println("Interactive demo error: " + e.getMessage());
        }
    }

    private static void startSearchLoop(String dataDir, String analyzerName, String simName, boolean multiField) throws Exception {
        String docsPath = dataDir + "/cran.all.1400";
        if (!new File(docsPath).exists()) {
            System.err.println("Error: Document file not found at " + docsPath);
            return;
        }

        System.out.println("Loading documents and building in-memory index...");
        List<CranfieldDocument> documents = CranfieldParser.parseDocuments(docsPath);

        Analyzer analyzer = AnalyzerFactory.fromString(analyzerName);
        Similarity similarity = SimilarityFactory.fromString(simName);

        try (SearchEngine engine = new SearchEngine(analyzer, similarity)) {
            engine.setUseMultiField(multiField);
            engine.buildIndex(documents);

            System.out.printf("\nSearch Engine Ready! (Analyzer: %s | Similarity: %s | MultiField: %b)\n",
                    analyzerName, simName, multiField);
            System.out.println("Type your search query below (or ':q' to return to menu):");

            Scanner scanner = new Scanner(System.in);
            while (true) {
                System.out.print("\nSearch Query >>> ");
                String query = scanner.nextLine().trim();
                if (query.equalsIgnoreCase(":q") || query.equalsIgnoreCase("exit") || query.equalsIgnoreCase("quit")) {
                    break;
                }
                if (query.isEmpty()) continue;

                engine.searchInteractive(query, 5);
            }
        }
    }

    private static void runCustomExperiment(Scanner scanner, String dataDir) {
        System.out.println("\n--- Custom Single Experiment ---");
        System.out.print("Enter Analyzer [standard / english / whitespace / custom]: ");
        String anName = scanner.nextLine().trim();

        System.out.print("Enter Similarity [bm25 / vsm / dirichlet / jm / boolean]: ");
        String simName = scanner.nextLine().trim();

        System.out.print("Enable Multi-Field Boosting? [y/n]: ");
        boolean multiField = scanner.nextLine().trim().equalsIgnoreCase("y");

        System.out.print("Output run file path (e.g. results/custom_run.txt): ");
        String outPath = scanner.nextLine().trim();
        if (outPath.isEmpty()) outPath = "results/custom_run.txt";

        try {
            List<CranfieldDocument> docs = CranfieldParser.parseDocuments(dataDir + "/cran.all.1400");
            List<CranfieldQuery> queries = CranfieldParser.parseQueries(dataDir + "/cran.qry");

            Analyzer analyzer = AnalyzerFactory.fromString(anName);
            Similarity similarity = SimilarityFactory.fromString(simName);

            try (SearchEngine engine = new SearchEngine(analyzer, similarity)) {
                engine.setUseMultiField(multiField);
                System.out.println("Building index and executing 225 queries...");
                engine.buildIndex(docs);
                engine.executeBatchQueries(queries, outPath, "CUSTOM_RUN", 1000);
            }
            System.out.println("Run complete! Results saved to: " + outPath);
        } catch (Exception e) {
            System.err.println("Experiment failed: " + e.getMessage());
        }
    }

    private static String locateDocFile(String dataDir) {
        if (new File(dataDir, "cran.all.1400").exists()) {
            return new File(dataDir, "cran.all.1400").getPath();
        } else if (new File(dataDir, "cran.all").exists()) {
            return new File(dataDir, "cran.all").getPath();
        } else if (new File(dataDir, "cran.1400").exists()) {
            return new File(dataDir, "cran.1400").getPath();
        }
        return dataDir + "/cran.all.1400";
    }

    private static String locateDataDirectory() {
        String[] candidateDirs = {"data/cran", "data", "cran", "."};
        String[] candidateDocFiles = {"cran.all.1400", "cran.all", "cran.1400"};

        for (String dir : candidateDirs) {
            for (String docFile : candidateDocFiles) {
                if (new File(dir, docFile).exists()) {
                    return dir;
                }
            }
        }
        return DEFAULT_DATA_DIR;
    }

    private static void printHelp() {
        System.out.println("Usage:");
        System.out.println("  mvn exec:java                               Run interactive menu");
        System.out.println("  mvn exec:java -Dexec.args=\"--batch\"          Run all 7 benchmark experiments");
        System.out.println("  mvn exec:java -Dexec.args=\"--convert-qrels\"  Convert cranqrel to TREC format");
        System.out.println("  mvn exec:java -Dexec.args=\"--search\"         Launch directly into search shell");
    }
}
