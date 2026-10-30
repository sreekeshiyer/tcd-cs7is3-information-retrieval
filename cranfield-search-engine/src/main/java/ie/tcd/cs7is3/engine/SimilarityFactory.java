package ie.tcd.cs7is3.engine;

import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.BooleanSimilarity;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.search.similarities.LMDirichletSimilarity;
import org.apache.lucene.search.similarities.LMJelinekMercerSimilarity;
import org.apache.lucene.search.similarities.Similarity;

/**
 * Factory for creating different Lucene scoring approaches (Similarities).
 * 
 * Includes:
 * 1. BM25Similarity: Modern probabilistic ranking function (Best Match 25).
 * 2. ClassicSimilarity: Traditional Vector Space Model (TF-IDF with cosine normalization).
 * 3. BooleanSimilarity: Binary relevance matching (no term frequency or document length weighting).
 */
public class SimilarityFactory {

    public enum SimilarityType {
        BM25,
        BM25_TUNED,
        CLASSIC_VSM,
        BOOLEAN
    }

    /**
     * Creates a Similarity scoring model instance.
     */
    public static Similarity createSimilarity(SimilarityType type) {
        switch (type) {
            case BM25:
                // Default parameters: k1 = 1.2 and b = 0.75 
                // k1 - term frequency saturation parameter (typically between 1.0 and 2.0)
                // b - balanced document length penalty preventing suppression of detailed abstracts)
                return new BM25Similarity();
            case BM25_TUNED:
                // Tuned parameters for short Cranfield abstracts
                return new BM25Similarity(1.5f, 0.7f);
            case CLASSIC_VSM:
                // Classic Vector Space Model / TF-IDF
                return new ClassicSimilarity();
            case BOOLEAN:
                // Unweighted boolean similarity
                return new BooleanSimilarity();
            default:
                throw new IllegalArgumentException("Unsupported similarity type: " + type);
        }
    }


    // BM25 with custom parameters for fine-tuning retrieval performance.
    public static Similarity createBM25(float k1, float b) {
        return new BM25Similarity(k1, b);
    }

    // Resolves a similarity model by name (case-insensitive).
    public static Similarity fromString(String name) {
        if (name == null) return new BM25Similarity();
        String lower = name.trim().toLowerCase();
        if (lower.contains("bm25")) {
            return createSimilarity(SimilarityType.BM25);
        } else if (lower.contains("vsm") || lower.contains("classic") || lower.contains("tfidf")) {
            return createSimilarity(SimilarityType.CLASSIC_VSM);
        } else if (lower.contains("boolean")) {
            return createSimilarity(SimilarityType.BOOLEAN);
        } else {
            System.err.println("Unknown similarity name: '" + name + "'. Defaulting to BM25.");
            return createSimilarity(SimilarityType.BM25);
        }
    }
}
