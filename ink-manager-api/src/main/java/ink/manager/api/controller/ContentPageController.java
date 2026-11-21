package ink.manager.api.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Serves the public content pages (one per business niche) at clean, indexable URLs. */
@Controller
public class ContentPageController {
    @GetMapping("/agenda-para-barbearia")
    public String barbearia() {
        return "forward:/agenda-para-barbearia.html";
    }

    @GetMapping("/agenda-para-manicure")
    public String manicure() {
        return "forward:/agenda-para-manicure.html";
    }
}
