package edu.txstate.lyricssearch.model;
// Represents a single song entry from the lyrics.csv file. This model is used to map raw CSV data into Java objects for indexing.
public class Song {
    private String id;
    private String rank;
    private String title;
    private String year;
    private String artist;
    private String lyrics;
    // Default constructor required for certain frameworks or serialization
    public Song() {
    }
    // Main constructor used by CsvSongReader to create song objects during import
    public Song(String id, String rank, String title, String year, String artist, String lyrics) {
        this.id = id;
        this.rank = rank;
        this.title = title;
        this.year = year;
        this.artist = artist;
        this.lyrics = lyrics;
    }
    // Getters and Setters to allow services to read and update song data
    public String getId() {
        return id;
    }
    public String getRank() {
        return rank;
    }
    public String getTitle() {
        return title;
    }
    public String getYear() {
        return year;
    }
    public String getArtist() {
        return artist;
    }
    public String getLyrics() {
        return lyrics;
    }
    public void setId(String id) {
        this.id = id;
    }
    public void setRank(String rank) {
        this.rank = rank;
    }
    public void setTitle(String title) {
        this.title = title;
    }
    public void setYear(String year) {
        this.year = year;
    }
    public void setArtist(String artist) {
        this.artist = artist;
    }
    public void setLyrics(String lyrics) {
        this.lyrics = lyrics;
    }
}