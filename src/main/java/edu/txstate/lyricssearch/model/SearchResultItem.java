package edu.txstate.lyricssearch.model;
public class SearchResultItem {
    private final String id;
    private final String title;
    private final String rank;
    private final String year;
    private final String artist;
    private final String snippet;
    // Data model that holds a single search result. It carries the song info and the highlighted snippet to the UI.
    public SearchResultItem(String id, String title, String rank, String year, String artist, String 
snippet) {
        this.id = id;
        this.title = title;
        this.rank = rank;
        this.year = year;
        this.artist = artist;
        this.snippet = snippet;
    }
    // Standard getters used by Thymeleaf to display data in index.html
    public String getId() {
        return id;
    }
    public String getTitle() {
        return title;
    }
    public String getRank() {
        return rank;
    }
    public String getYear() {
        return year;
    }
    public String getArtist() {
        return artist;
    }
    public String getSnippet() {
        return snippet;
    }
}