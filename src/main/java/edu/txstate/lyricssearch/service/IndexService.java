package edu.txstate.lyricssearch.service;
import com.opencsv.exceptions.CsvValidationException;
import edu.txstate.lyricssearch.model.Song;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.store.FSDirectory;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
@Service
public class IndexService {
    private final CsvSongReader csvSongReader;
    public IndexService(CsvSongReader csvSongReader) {
        this.csvSongReader = csvSongReader;
    }
    //This ensures we only spend time building the index if it doesn't already exist.
    public void buildIndexIfNeeded() throws IOException, CsvValidationException {
        Path indexPath = Path.of("data/index");
        if (!Files.exists(indexPath)) {
            Files.createDirectories(indexPath);
        }
        boolean indexEmpty;
        try (Stream<Path> files = Files.list(indexPath)) {
            indexEmpty = files.findAny().isEmpty();
        }
        if (indexEmpty) {
            rebuildIndex();
        }
    }
    public void rebuildIndex() throws IOException, CsvValidationException {
        Path indexPath = Path.of("data/index");
        // Pull the raw song data from the CSV file we bundled with the project.
        List<Song> songs = csvSongReader.readSongs("data/lyrics.csv");
        try (FSDirectory directory = FSDirectory.open(indexPath);
             IndexWriter writer = new IndexWriter(
                     directory,
                     new IndexWriterConfig(new StandardAnalyzer()).setOpenMode(OpenMode.CREATE)
             )) {
            for (Song song : songs) {
                writer.addDocument(toDocument(song));
            }
            writer.commit();
        }
    }
    private Document toDocument(Song song) {
        Document doc = new Document();
        // IDs and Titles are kept as-is, but Title is searchable (TextField) while ID is exact (StringField)
        doc.add(new StringField("id", song.getId(), Field.Store.YES));
        doc.add(new StoredField("rank", song.getRank()));
        doc.add(new TextField("title", song.getTitle(), Field.Store.YES));
        doc.add(new StoredField("year", song.getYear()));
        doc.add(new TextField("artist", song.getArtist(), Field.Store.YES));
        // We enable full term vectors and offsets here so Lucene can highlight search terms directly in the snippets on the results page.
        FieldType lyricsType = new FieldType();
        lyricsType.setStored(true);
        lyricsType.setTokenized(true);
        lyricsType.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
        lyricsType.setStoreTermVectors(true);
        lyricsType.setStoreTermVectorPositions(true);
        lyricsType.setStoreTermVectorOffsets(true);
        lyricsType.freeze();
        doc.add(new Field("lyrics", song.getLyrics(), lyricsType));
        return doc;
    }
}