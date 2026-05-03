package eu.appbahn.platform.resource.controller;

import eu.appbahn.platform.api.imagesource.ImageSourceWebhookApi;
import eu.appbahn.platform.api.imagesource.ImageSourceWebhookSecret;
import eu.appbahn.platform.api.imagesource.ImageSourceWebhookView;
import eu.appbahn.platform.resource.service.ImageSourceWebhookService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ImageSourceWebhookController implements ImageSourceWebhookApi {

    private final ImageSourceWebhookService service;

    public ImageSourceWebhookController(ImageSourceWebhookService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<ImageSourceWebhookView> getImageSourceWebhook(String slug) {
        var view = service.view(slug);
        return ResponseEntity.ok(new ImageSourceWebhookView(view.url(), view.maskedSecret()));
    }

    @Override
    public ResponseEntity<ImageSourceWebhookSecret> rotateImageSourceWebhook(String slug) {
        var minted = service.rotate(slug);
        return ResponseEntity.ok(new ImageSourceWebhookSecret(minted.secret(), minted.url()));
    }
}
