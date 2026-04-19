package edu.txstate.lyricssearch.service;

import edu.txstate.lyricssearch.model.SearchResultItem;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.queryparser.classic.QueryParserBase;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.uhighlight.UnifiedHighlighter;
import org.apache.lucene.store.FSDirectory;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SearchService {

    // Keep snippets crisp and readable on a search results page
    private static final int MAX_SNIPPET_LENGTH = 220;

    
     // Executes a search across song titles, artists, and lyrics.
     
    public List<SearchResultItem> search(String userQuery) throws Exception {
        List<SearchResultItem> results = new ArrayList<>();

        // Using try-with-resources ensures the reader closes even if an error occurs
        try (DirectoryReader reader = DirectoryReader.open(FSDirectory.open(Path.of("data/index")))) {
            IndexSearcher searcher = new IndexSearcher(reader);
            StandardAnalyzer analyzer = new StandardAnalyzer();

            // We prioritize matches in titles and artist names over the actual lyrics
            Map<String, Float> boosts = new HashMap<>();
            boosts.put("title", 3.0f);
            boosts.put("artist", 2.0f);
            boosts.put("lyrics", 1.0f);

            MultiFieldQueryParser parser = new MultiFieldQueryParser(
                new String[]{"title", "artist", "lyrics"}, 
                analyzer, 
                boosts
            );

            // Require all terms to match by default to keep results relevant
            parser.setDefaultOperator(QueryParser.Operator.AND);
            
            Query query = parser.parse(QueryParserBase.escape(userQuery));
            
            // Limit results to the top 20 matches
            TopDocs topDocs = searcher.search(query, 20);

            // Set up the highlighter to pull relevant snippets from the lyrics
            UnifiedHighlighter highlighter = UnifiedHighlighter.builder(searcher, analyzer).build();
            String[] rawSnippets = highlighter.highlight("lyrics", query, topDocs, 1);

            StoredFields storedFields = searcher.storedFields();
            for (int i = 0; i < topDocs.scoreDocs.length; i++) {
                ScoreDoc scoreDoc = topDocs.scoreDocs[i];
                Document doc = storedFields.document(scoreDoc.doc);
                
                String lyrics = doc.get("lyrics");
                String snippet = normalizeSnippet(rawSnippets[i]);

                // If the automatic highlighter failed to find a good spot, we manually create a fallback snippet around the search terms
                if (snippet.isBlank()) {
                    snippet = buildFallbackSnippet(lyrics, userQuery);
                }

                results.add(new SearchResultItem(
                    doc.get("id"),
                    doc.get("title"),
                    doc.get("rank"),
                    doc.get("year"),
                    doc.get("artist"),
                    snippet
                ));
            }
        }
        return results;
    }

    
     // Fetch a single song's details using its unique ID.
     
    public Document getSongById(String id) throws Exception {
        try (DirectoryReader reader = DirectoryReader.open(FSDirectory.open(Path.of("data/index")))) {
            IndexSearcher searcher = new IndexSearcher(reader);
            
            // Exact match lookup for the ID field
            Query query = new org.apache.lucene.search.TermQuery(
                new org.apache.lucene.index.Term("id", id)
            );

            TopDocs hits = searcher.search(query, 1);
            if (hits.scoreDocs.length == 0) {
                return null;
            }
            
            return searcher.storedFields().document(hits.scoreDocs[0].doc);
        }
    }

    
     // Cleans up formatting and enforces length limits on snippets.
     
    private String normalizeSnippet(String snippet) {
        if (snippet == null) return "";

        String cleaned = snippet.replaceAll("\\s+", " ").trim();
        
        if (cleaned.isBlank()) return "";

        if (cleaned.length() <= MAX_SNIPPET_LENGTH) {
            return cleaned;
        }
        return cleaned.substring(0, MAX_SNIPPET_LENGTH).trim() + "...";
    }

    
     // Finds the first occurrence of a search term in the lyrics and builds a context window around it.
     
    private String buildFallbackSnippet(String lyrics, String userQuery) {
        if (lyrics == null || lyrics.isBlank()) return "";

        String normalizedLyrics = lyrics.replaceAll("\\s+", " ").trim();
        String lowerLyrics = normalizedLyrics.toLowerCase();
        String[] queryTerms = userQuery.toLowerCase().split("\\s+");

        int firstMatchIndex = -1;
        for (String term : queryTerms) {
            firstMatchIndex = lowerLyrics.indexOf(term);
            if (firstMatchIndex >= 0) break;
        }

        // If no term is found in the text, just return the beginning of the lyrics
        if (firstMatchIndex < 0) {
            return normalizedLyrics.length() <= MAX_SNIPPET_LENGTH
                ? normalizedLyrics
                : normalizedLyrics.substring(0, MAX_SNIPPET_LENGTH).trim() + "...";
        }

        // Grab text before and after the match to provide context
        int start = Math.max(0, firstMatchIndex - 70);
        int end = Math.min(normalizedLyrics.length(), firstMatchIndex + 150);
        
        String snippet = normalizedLyrics.substring(start, end).trim();
        
        if (start > 0) snippet = "..." + snippet;
        if (end < normalizedLyrics.length()) snippet = snippet + "...";

        return snippet;
    }
}