package eu.appbahn.platform;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Forwards SPA routes to index.html so Vue Router handles client-side routing.
 * Static assets (JS/CSS/images) are served directly by Spring Boot's default
 * static resource handling from classpath:/static/.
 */
@Controller
public class SpaForwardingController {

    @GetMapping(value = {"/", "/console", "/console/**", "/login", "/auth/complete"})
    public String forward() {
        return "forward:/index.html";
    }
}
