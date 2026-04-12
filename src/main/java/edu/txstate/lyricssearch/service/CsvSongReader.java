package edu.txstate.lyricssearch.service;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import edu.txstate.lyricssearch.model.Song;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
@Service
public class CsvSongReader {
    public List<Song> readSongs(String csvPath) throws IOException, CsvValidationException {
        List<Song> songs = new ArrayList<>();
        try (Reader fileReader = Files.newBufferedReader(Path.of(csvPath), StandardCharsets.ISO_8859_1);
             CSVReader reader = new CSVReader(fileReader)) {
            String[] header = reader.readNext();
            System.out.println("CSV HEADER = " + Arrays.toString(header));
            String[] row;
            // Generate a simple unique ID for each song for Lucene indexing
            int idCounter = 1;
            while ((row = reader.readNext()) != null) {
                // Map each CSV column to the specific Song model fields
                String rank = safe(row, 0);
                String title = safe(row, 1);
                 String artist = safe(row, 2);
                String year = safe(row, 3);              
                String lyrics = safe(row, 4);
                songs.add(new Song(
                        String.valueOf(idCounter++),
                        rank,
                        title,
                        year,
                        artist,
                        lyrics
                ));
            }
        }
        return songs;
    }
    //To Prevent the app from crashing if a row is missing a column or has empty data.
    private String safe(String[] row, int index) {
        if (index >= row.length || row[index] == null) {
            return "";
        }
        return row[index].trim();
    }
}