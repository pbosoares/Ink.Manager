package ink.manager.api.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Serve o login e o painel (app.html) em /app, separados da landing publica em /. */
@Controller
public class AppPageController {
    @GetMapping({"/app", "/app/"})
    public String abrir() {
        return "forward:/app.html";
    }
}
