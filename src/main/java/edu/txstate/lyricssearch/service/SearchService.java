package edu.txstate.lyricssearch.service;
import edu.txstate.lyricssearch.model.SearchResultItem;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.queryparser.classic.QueryParserBase;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.index.StoredFields;
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
    public List<SearchResultItem> search(String userQuery) throws Exception {
        List<SearchResultItem> results = new ArrayList<>();
        try (DirectoryReader reader = DirectoryReader.open(FSDirectory.open(Path.of("data/index")))) {
            IndexSearcher searcher = new IndexSearcher(reader);
            StandardAnalyzer analyzer = new StandardAnalyzer();
            // We prioritize matches in the Title and Artist over general Lyrics to make sure the most relevant songs appear at the top
            Map<String, Float> boosts = new HashMap<>();
            boosts.put("title", 3.0f);
            boosts.put("artist", 2.0f);
            boosts.put("lyrics", 1.0f);
            MultiFieldQueryParser parser =
                    new MultiFieldQueryParser(new String[]{"title", "artist", "lyrics"}, analyzer, boosts);
                    // Using AND operator so we find songs that contain all the user's search words
            parser.setDefaultOperator(QueryParser.Operator.AND);
            Query query = parser.parse(QueryParserBase.escape(userQuery));
            TopDocs topDocs = searcher.search(query, 20);
            // UnifiedHighlighter creates those snippets where the search term is highlighted
            UnifiedHighlighter highlighter = UnifiedHighlighter.builder(searcher, analyzer).build();
            String[] snippets = highlighter.highlight("lyrics", query, topDocs, 1);
            StoredFields storedFields = searcher.storedFields();
            for (int i = 0; i < topDocs.scoreDocs.length; i++) {
                ScoreDoc scoreDoc = topDocs.scoreDocs[i];
                Document doc = storedFields.document(scoreDoc.doc);
                String snippet = snippets[i];
                // If the highlighter can't find a good snippet, we show the start of the lyrics so the result card isn't empty
                if (snippet == null || snippet.isBlank()) {
                    String lyrics = doc.get("lyrics");
                    if (lyrics == null) {
                        snippet = "";
                    } else {
                        snippet = lyrics.substring(0, Math.min(160, lyrics.length())) + "...";
                    }
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
    public Document getSongById(String id) throws Exception {
    try (DirectoryReader reader = DirectoryReader.open(FSDirectory.open(Path.of("data/index")))) {
        IndexSearcher searcher = new IndexSearcher(reader);
        // Using a TermQuery for a direct lookup by ID, which is the fastest way to load a specific song's page
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
}