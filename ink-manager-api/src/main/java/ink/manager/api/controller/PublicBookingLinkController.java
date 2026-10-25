package ink.manager.api.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Serves the existing booking page at a compact first-party URL. */
@Controller
public class PublicBookingLinkController {
    @GetMapping("/a/{code:[A-Za-z0-9_-]+}")
    public String abrir() {
        return "forward:/agendar.html";
    }
}
