package ie.tcd.cs7is3.engine;

import ie.tcd.cs7is3.analysis.AnalyzerFactory;
import ie.tcd.cs7is3.model.CranfieldDocument;
import ie.tcd.cs7is3.model.CranfieldQuery;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.search.similarities.Similarity;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Automates the execution of the entire experimental benchmark matrix.
 * Compares:
 * 1. Analyzers: Standard, English (Porter), Whitespace, Custom (KStem + domain stopwords).
 * 2. Scoring Models: Classic VSM (TF-IDF), BM25, LM Dirichlet.
 * 3. Retrieval Strategies: Single-field vs Multi-field with Title boosting.
 */
public class ExperimentRunner {

    public static class ExperimentConfig {
        public final String name;
        public final AnalyzerFactory.AnalyzerType analyzerType;
        public final SimilarityFactory.SimilarityType similarityType;
        public final boolean useMultiField;
        public final String outputFile;

        public ExperimentConfig(String name, AnalyzerFactory.AnalyzerType analyzerType,
                                SimilarityFactory.SimilarityType similarityType, boolean useMultiField,
                                String outputFile) {
            this.name = name;
            this.analyzerType = analyzerType;
            this.similarityType = similarityType;
            this.useMultiField = useMultiField;
            this.outputFile = outputFile;
        }
    }

    public static List<ExperimentConfig> getDefaultExperiments(String resultsDir) {
        List<ExperimentConfig> experiments = new ArrayList<>();

        // 1. Baseline: Vector Space Model (TF-IDF) + Standard Analyzer
        experiments.add(new ExperimentConfig(
                "VSM_Standard",
                AnalyzerFactory.AnalyzerType.STANDARD,
                SimilarityFactory.SimilarityType.CLASSIC_VSM,
                false,
                resultsDir + "/run_vsm_standard.txt"
        ));

        // 2. Vector Space Model + English Analyzer (Porter Stemming)
        experiments.add(new ExperimentConfig(
                "VSM_English_Porter",
                AnalyzerFactory.AnalyzerType.ENGLISH,
                SimilarityFactory.SimilarityType.CLASSIC_VSM,
                false,
                resultsDir + "/run_vsm_english.txt"
        ));

        // 3. BM25 + Standard Analyzer (Testing BM25 vs VSM)
        experiments.add(new ExperimentConfig(
                "BM25_Standard",
                AnalyzerFactory.AnalyzerType.STANDARD,
                SimilarityFactory.SimilarityType.BM25,
                false,
                resultsDir + "/run_bm25_standard.txt"
        ));

        // 4. BM25 + English Analyzer (Porter Stemming)
        experiments.add(new ExperimentConfig(
                "BM25_English_Porter",
                AnalyzerFactory.AnalyzerType.ENGLISH,
                SimilarityFactory.SimilarityType.BM25,
                false,
                resultsDir + "/run_bm25_english.txt"
        ));

        // 5. BM25 + English Analyzer + Multi-Field Title Boosting 
        experiments.add(new ExperimentConfig(
                "BM25_English_Boosted",
                AnalyzerFactory.AnalyzerType.ENGLISH,
                SimilarityFactory.SimilarityType.BM25,
                true,
                resultsDir + "/run_bm25_english_boosted.txt"
        ));

        // 6. BM25 Parameter Tuning + English Analyzer + Multi-Field Boosting
        // Cranfield abstracts are short and homogeneous; lower b prevents penalizing detailed abstracts.
        experiments.add(new ExperimentConfig(
                "BM25_Tuned_English_Boosted",
                AnalyzerFactory.AnalyzerType.ENGLISH,
                SimilarityFactory.SimilarityType.BM25_TUNED,
                true,
                resultsDir + "/run_bm25_tuned_english_boosted.txt"
        ));

        // 7. Dictionary-Based Stemming: Custom Cranfield Analyzer (KStem + Domain Stopwords) + BM25 + Multi-Field Boosting
        // Tests Krovetz dictionary morphological stemming against algorithmic Porter stemming.
        experiments.add(new ExperimentConfig(
                "Custom_KStem_BM25_Boosted",
                AnalyzerFactory.AnalyzerType.CUSTOM,
                SimilarityFactory.SimilarityType.BM25,
                true,
                resultsDir + "/run_custom_kstem_boosted.txt"
        ));

        // 8. Peak Optimized Pipeline: Custom KStem Analyzer + Tuned BM25 (k1=1.2, b=0.65) + Multi-Field Boosting
        // Combines morphological stemming, domain stopword filtering, length normalization tuning, and title boosting.
        experiments.add(new ExperimentConfig(
                "Custom_KStem_BM25_Tuned_Boosted",
                AnalyzerFactory.AnalyzerType.CUSTOM,
                SimilarityFactory.SimilarityType.BM25_TUNED,
                true,
                resultsDir + "/run_custom_kstem_bm25_tuned_boosted.txt"
        ));

        return experiments;
    }

    /**
     * Executes all configured experiments on the provided corpus and queries.
     */
    public static void runAll(List<CranfieldDocument> documents, List<CranfieldQuery> queries,
                              String resultsDir) throws IOException {
        File dir = new File(resultsDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        List<ExperimentConfig> experiments = getDefaultExperiments(resultsDir);
        System.out.println("================================================================================");
        System.out.printf("Starting Benchmark Suite: %d experiments over %d documents & %d queries\n",
                experiments.size(), documents.size(), queries.size());
        System.out.println("================================================================================");

        long totalStartTime = System.currentTimeMillis();

        for (int i = 0; i < experiments.size(); i++) {
            ExperimentConfig config = experiments.get(i);
            long expStartTime = System.currentTimeMillis();

            System.out.printf("[%d/%d] Running experiment: %-30s ... ", (i + 1), experiments.size(), config.name);

            Analyzer analyzer = AnalyzerFactory.createAnalyzer(config.analyzerType);
            Similarity similarity = SimilarityFactory.createSimilarity(config.similarityType);

            int linesWritten = 0;
            try (SearchEngine engine = new SearchEngine(analyzer, similarity)) {
                engine.setUseMultiField(config.useMultiField);
                engine.buildIndex(documents);
                linesWritten = engine.executeBatchQueries(queries, config.outputFile, config.name, 1000);
            }

            long expDuration = System.currentTimeMillis() - expStartTime;
            System.out.printf("DONE in %d ms -> Saved to: %s (%d lines)\n", expDuration, config.outputFile, linesWritten);
        }

        long totalDuration = System.currentTimeMillis() - totalStartTime;
        System.out.println("================================================================================");
        System.out.printf("All experiments completed successfully in %.2f seconds!\n", totalDuration / 1000.0);
        System.out.println("Run files are ready for trec_eval evaluation in: " + resultsDir);
        System.out.println("================================================================================");
    }
}
