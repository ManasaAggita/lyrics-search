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
 private static final int MAX_SNIPPET_LENGTH = 220;
 public List<SearchResultItem> search(String userQuery) throws Exception {
 List<SearchResultItem> results = new ArrayList<>();
 try (DirectoryReader reader = DirectoryReader.open(FSDirectory.open(Path.of("data/index")))) {
 IndexSearcher searcher = new IndexSearcher(reader);
 StandardAnalyzer analyzer = new StandardAnalyzer();
 Map<String, Float> boosts = new HashMap<>();
 boosts.put("title", 3.0f);
 boosts.put("artist", 2.0f);
 boosts.put("lyrics", 1.0f);
 MultiFieldQueryParser parser =
 new MultiFieldQueryParser(new String[]{"title", "artist", "lyrics"}, analyzer, boosts);
 parser.setDefaultOperator(QueryParser.Operator.AND);
 Query query = parser.parse(QueryParserBase.escape(userQuery));
 TopDocs topDocs = searcher.search(query, 20);
 UnifiedHighlighter highlighter = UnifiedHighlighter.builder(searcher, analyzer).build();
 String[] rawSnippets = highlighter.highlight("lyrics", query, topDocs, 1);
 StoredFields storedFields = searcher.storedFields();
 for (int i = 0; i < topDocs.scoreDocs.length; i++) {
 ScoreDoc scoreDoc = topDocs.scoreDocs[i];
Page 2
 Document doc = storedFields.document(scoreDoc.doc);
 String lyrics = doc.get("lyrics");
 String snippet = normalizeSnippet(rawSnippets[i]);
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
 public Document getSongById(String id) throws Exception {
 try (DirectoryReader reader = DirectoryReader.open(FSDirectory.open(Path.of("data/index")))) {
 IndexSearcher searcher = new IndexSearcher(reader);
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
 private String normalizeSnippet(String snippet) {
 if (snippet == null) {
 return "";
 }
 String cleaned = snippet.replaceAll("\\s+", " ").trim();
 if (cleaned.isBlank()) {
 return "";
 }
 if (cleaned.length() <= MAX_SNIPPET_LENGTH) {
 return cleaned;
 }
 return cleaned.substring(0, MAX_SNIPPET_LENGTH).trim() + "...";
 }
 private String buildFallbackSnippet(String lyrics, String userQuery) {
 if (lyrics == null || lyrics.isBlank()) {
 return "";
 }
 String normalizedLyrics = lyrics.replaceAll("\\s+", " ").trim();
 String lowerLyrics = normalizedLyrics.toLowerCase();
 String[] queryTerms = userQuery.toLowerCase().split("\\s+");
 int firstMatchIndex = -1;
 for (String term : queryTerms) {
 firstMatchIndex = lowerLyrics.indexOf(term);
 if (firstMatchIndex >= 0) {
 break;
 }
 }
Page 3
 if (firstMatchIndex < 0) {
 return normalizedLyrics.length() <= MAX_SNIPPET_LENGTH
 ? normalizedLyrics
 : normalizedLyrics.substring(0, MAX_SNIPPET_LENGTH).trim() + "...";
 }
 int start = Math.max(0, firstMatchIndex - 70);
 int end = Math.min(normalizedLyrics.length(), firstMatchIndex + 150);
 String snippet = normalizedLyrics.substring(start, end).trim();
 if (start > 0) {
 snippet = "..." + snippet;
 }
 if (end < normalizedLyrics.length()) {
 snippet = snippet + "...";
 }
 return snippet;
 }
}
