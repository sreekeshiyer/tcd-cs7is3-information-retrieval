package ie.tcd.cs7is3.analysis;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;

/**
 * Factory for creating different Lucene Analyzers to compare content processing techniques.
 */
public class AnalyzerFactory {

    public enum AnalyzerType {
        STANDARD,
        ENGLISH,
        WHITESPACE,
        CUSTOM
    }

    /**
     * Creates an Analyzer corresponding to the specified type.
     *
     * @param type AnalyzerType enum or string representation
     * @return Initialized Lucene Analyzer instance
     */
    public static Analyzer createAnalyzer(AnalyzerType type) {
        switch (type) {
            case STANDARD:
                // StandardTokenizer + LowerCaseFilter + StopFilter (Default English stopwords)
                return new StandardAnalyzer();
            case ENGLISH:
                // EnglishAnalyzer: StandardTokenizer + Possessive + LowerCase + StopFilter + PorterStemFilter
                return new EnglishAnalyzer();
            case WHITESPACE:
                // WhitespaceTokenizer: Minimal baseline (no stemming, no stopwords, no lowercasing)
                return new WhitespaceAnalyzer();
            case CUSTOM:
                // CustomCranfieldAnalyzer: StandardTokenizer + Possessive + LowerCase + Domain Stopwords + KStemFilter
                return new CustomCranfieldAnalyzer();
            default:
                throw new IllegalArgumentException("Unsupported analyzer type: " + type);
        }
    }

    /**
     * Resolves an analyzer by name (case-insensitive).
     */
    public static Analyzer fromString(String name) {
        if (name == null) return new StandardAnalyzer();
        switch (name.trim().toLowerCase()) {
            case "standard":
                return createAnalyzer(AnalyzerType.STANDARD);
            case "english":
                return createAnalyzer(AnalyzerType.ENGLISH);
            case "whitespace":
                return createAnalyzer(AnalyzerType.WHITESPACE);
            case "custom":
                return createAnalyzer(AnalyzerType.CUSTOM);
            default:
                System.err.println("Unknown analyzer name: '" + name + "'. Defaulting to StandardAnalyzer.");
                return createAnalyzer(AnalyzerType.STANDARD);
        }
    }
}
