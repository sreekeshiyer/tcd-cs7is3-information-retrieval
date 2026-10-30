package ie.tcd.cs7is3.analysis;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.en.EnglishPossessiveFilter;
import org.apache.lucene.analysis.en.KStemFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;

import java.util.Arrays;
import java.util.List;

/**
 * Custom Lucene Analyzer tailored specifically for the Cranfield Collection.
 * 
 * Design Rationale:
 * 1. StandardTokenizer: Segments alphanumeric text and isolates acronyms/numbers cleanly.
 * 2. EnglishPossessiveFilter: Removes trailing apostrophe-s ("'s") to conflate possessives with nouns.
 * 3. LowerCaseFilter: Ensures case-insensitive indexing and query matching.
 * 4. StopFilter with Domain-Expanded Stopwords:
 *    Removes both standard English function words and conversational/query boilerplate words
 *    frequently appearing in Cranfield questions (e.g., "what", "similarity", "investigation",
 *    "determine", "paper", "method", "problem") that otherwise induce false-positive matches
 *    across academic paper abstracts.
 * 5. KStemFilter (Krovetz Morphological Stemmer):
 *    Unlike the Porter stemmer (which is aggressive and often chops technical aerospace terms into
 *    non-words or merges conceptually distinct terms), KStem is dictionary-assisted and conservative.
 */
public class CustomCranfieldAnalyzer extends Analyzer {

    private final CharArraySet stopWords;

    // Only filter conversational question boilerplate words that carry zero topical meaning.
    // CRITICAL: We do NOT remove domain words like "theory", "experiment", "method", "effect",
    // or "problem" because in Cranfield aerodynamics, these words define the core research topic.
    // Removing them was causing the "Text Processing Trade-off: Too much -> loose meaning" flaw (Slide 54).
    private static final List<String> DOMAIN_STOPWORDS = Arrays.asList(
            "what", "which", "where", "when", "why", "who", "how",
            "is", "are", "was", "were", "be", "been", "being",
            "can", "could", "would", "should", "may", "might", "must",
            "give", "given", "find", "describe", "discuss"
    );

    public CustomCranfieldAnalyzer() {
        // Start with Lucene's default English stop words
        CharArraySet combined = new CharArraySet(EnglishAnalyzer.ENGLISH_STOP_WORDS_SET, true);
        // Add expanded domain/query stopwords
        combined.addAll(DOMAIN_STOPWORDS);
        this.stopWords = CharArraySet.unmodifiableSet(combined);
    }

    public CustomCranfieldAnalyzer(CharArraySet customStopWords) {
        this.stopWords = customStopWords;
    }

    @Override
    protected TokenStreamComponents createComponents(String fieldName) {
        // Step 1: Tokenize on word boundaries
        final StandardTokenizer tokenizer = new StandardTokenizer();

        // Step 2: Strip possessives (e.g. Reynolds's -> Reynolds)
        TokenStream filter = new EnglishPossessiveFilter(tokenizer);

        // Step 3: Lowercase all tokens
        filter = new LowerCaseFilter(filter);

        // Step 4: Remove standard & query boilerplate stopwords
        filter = new StopFilter(filter, stopWords);

        // Step 5: Krovetz morphological stemmer
        filter = new KStemFilter(filter);

        return new TokenStreamComponents(tokenizer, filter);
    }

    public CharArraySet getStopWords() {
        return stopWords;
    }
}
