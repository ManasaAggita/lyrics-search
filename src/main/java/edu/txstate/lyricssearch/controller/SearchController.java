package edu.txstate.lyricssearch.controller;
import edu.txstate.lyricssearch.service.SearchService;
import org.apache.lucene.document.Document;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
@Controller
public class SearchController {
    private final SearchService searchService;
    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }
    @GetMapping("/")
    public String home(@RequestParam(name = "q", required = false) String q, Model model) {
       // Keeps the user's search text in the input box after the page reloads
        model.addAttribute("q", q == null ? "" : q);
        if (q != null && !q.isBlank()) {
            try {
                  // Only triggers the Lucene search if the user actually typed something
                model.addAttribute("results", searchService.search(q));
            } catch (Exception e) {
                   // Passes the error message to the UI instead of crashing the app
                model.addAttribute("error", e.getMessage());
            }
        }
        return "index";
    }
    @GetMapping("/song/{id}")
    public String songPage(@PathVariable String id, Model model) {
        try {
            // Looks up a specific song by its ID when a user clicks a search result
            Document doc = searchService.getSongById(id);
            if (doc == null) {
                model.addAttribute("error", "Song not found.");
                return "song";
            }
            // Passes all song details to the song.html template for display
            model.addAttribute("title", doc.get("title"));
            model.addAttribute("rank", doc.get("rank"));
            model.addAttribute("year", doc.get("year"));
            model.addAttribute("artist", doc.get("artist"));
            model.addAttribute("lyrics", doc.get("lyrics"));
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
        }
        return "song";
    }
}