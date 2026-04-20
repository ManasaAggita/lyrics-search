package edu.txstate.lyricssearch.service;

import edu.txstate.lyricssearch.model.SearchResultItem;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.QueryParserBase;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.highlight.Highlighter;
import org.apache.lucene.search.highlight.QueryScorer;
import org.apache.lucene.search.highlight.SimpleFragmenter;
import org.apache.lucene.search.highlight.SimpleHTMLFormatter;
import org.apache.lucene.store.FSDirectory;
import org.springframework.stereotype.Service;

import java.io.StringReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

//This service handles all the heavy lifting for searching our Lucene index. It weights search results and generates the text snippets users see.
 
@Service
public class SearchService {

    // Controls how many songs we show and how long the preview text is.
    private static final int PAGE_SIZE = 50;
    private static final int SNIPPET_LENGTH = 180;

    
     //Searches for lyrics, titles, and artists based on the user's input.
     
    public List<SearchResultItem> search(String userQuery) throws Exception {
        List<SearchResultItem> results = new ArrayList<>();

        // Opening the index reader. We use try-with-resources to ensure it closes properly.
        try (DirectoryReader reader = DirectoryReader.open(FSDirectory.open(Path.of("data/index")))) {
            IndexSearcher searcher = new IndexSearcher(reader);
            StandardAnalyzer analyzer = new StandardAnalyzer();

            // Weighting: We care way more if the search term is in the Title than the Lyrics.
            Map<String, Float> boosts = new HashMap<>();
            boosts.put("title", 3.0f);
            boosts.put("artist", 2.0f);
            boosts.put("lyrics", 1.0f);

            // Set up a parser that looks across multiple fields at once.
            MultiFieldQueryParser parser =
                new MultiFieldQueryParser(new String[]{"title", "artist", "lyrics"}, analyzer, boosts);
            
            // Clean the query to prevent special characters from breaking the search.
            Query query = parser.parse(QueryParserBase.escape(userQuery));
            TopDocs topDocs = searcher.search(query, PAGE_SIZE);

            // Process each hit found in the index.
            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                Document doc = searcher.storedFields().document(scoreDoc.doc);
                String lyrics = doc.get("lyrics");

                // Try to get a smart highlight from Lucene, but fall back to a manual search if it fails.
                String snippet = buildSnippet(query, analyzer, lyrics, userQuery);

                if ("NA".equalsIgnoreCase(snippet)) {
                    snippet = "No lyric snippet available.";
                }

                // Map the Lucene Document into our frontend-friendly SearchResultItem.
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

   
     //Grabs a single song's full data when a user clicks on it.
     
    public Document getSongById(String id) throws Exception {
        try (DirectoryReader reader = DirectoryReader.open(FSDirectory.open(Path.of("data/index")))) {
            IndexSearcher searcher = new IndexSearcher(reader);
            
            // Look for an exact match on the unique ID field.
            Query query = new TermQuery(new Term("id", id));
            TopDocs hits = searcher.search(query, 1);

            if (hits.scoreDocs.length == 0) {
                return null;
            }
            return searcher.storedFields().document(hits.scoreDocs[0].doc);
        }
    }

    
     // Generates the "preview" text with <mark> tags around the search terms.
     
    private String buildSnippet(Query query, StandardAnalyzer analyzer, String lyrics, String userQuery) throws Exception {
        if (lyrics == null || lyrics.isBlank()) {
            return "No lyric snippet available.";
        }

        String cleanedLyrics = lyrics.replaceAll("\\s+", " ").trim();
        if (cleanedLyrics.equalsIgnoreCase("NA")) {
            return "No lyric snippet available.";
        }

        SimpleHTMLFormatter formatter = new SimpleHTMLFormatter("<mark>", "</mark>");
        Highlighter highlighter = new Highlighter(formatter, new QueryScorer(query, "lyrics"));
        highlighter.setTextFragmenter(new SimpleFragmenter(SNIPPET_LENGTH));

        // Use Lucene's token stream to find the best place in the lyrics to show the user.
        try (TokenStream tokenStream = analyzer.tokenStream("lyrics", new StringReader(cleanedLyrics))) {
            String bestFragment = highlighter.getBestFragment(tokenStream, cleanedLyrics);
            if (bestFragment != null && !bestFragment.isBlank()) {
                return normalizeEdges(bestFragment);
            }
        }

        // If the highlighter couldn't find a match (e.g. search term only in Title), use our manual fallback.
        return fallbackSnippet(cleanedLyrics, userQuery);
    }

    
     //A manual search for the keyword if the official Lucene highlighter comes up empty.
     
    private String fallbackSnippet(String lyrics, String userQuery) {
        String lowerLyrics = lyrics.toLowerCase();
        String[] terms = userQuery.toLowerCase().split("\\s+");
        int firstMatchIndex = -1;
        String matchedTerm = null;

        // Find the first occurrence of any of the search words.
        for (String term : terms) {
            int index = lowerLyrics.indexOf(term);
            if (index >= 0) {
                firstMatchIndex = index;
                matchedTerm = term;
                break;
            }
        }

        // If no words match the lyrics, just show the first 180 characters.
        if (firstMatchIndex < 0) {
            return lyrics.length() <= SNIPPET_LENGTH
                ? lyrics
                : lyrics.substring(0, SNIPPET_LENGTH).trim() + "...";
        }

        // Window the text: show some context before and after the matched word.
        int start = Math.max(0, firstMatchIndex - 60);
        int end = Math.min(lyrics.length(), firstMatchIndex + 120);
        String snippet = lyrics.substring(start, end).trim();

        // Since this is a manual fallback, we have to manually add the <mark> tags too.
        if (matchedTerm != null) {
            snippet = snippet.replaceAll(
                "(?i)\\b" + java.util.regex.Pattern.quote(matchedTerm) + "\\b",
                "<mark>$0</mark>"
            );
        }

        if (start > 0) snippet = "..." + snippet;
        if (end < lyrics.length()) snippet = snippet + "...";

        return snippet;
    }

    
     //Ensures snippets always start and end with ellipses for visual consistency.
     
    private String normalizeEdges(String snippet) {
        String cleaned = snippet.replaceAll("\\s+", " ").trim();
        
        if (!cleaned.startsWith("...")) {
            cleaned = "..." + cleaned;
        }
        if (!cleaned.endsWith("...")) {
            cleaned = cleaned + "...";
        }
        return cleaned;
    }
}