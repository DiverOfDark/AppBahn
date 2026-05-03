package eu.appbahn.platform.resource.service;

import eu.appbahn.platform.common.exception.NotFoundException;
import eu.appbahn.platform.resource.entity.ImageSourceWebhookTokenEntity;
import eu.appbahn.platform.resource.repository.ImageSourceCacheRepository;
import eu.appbahn.platform.resource.repository.ImageSourceWebhookTokenRepository;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mints/rotates the per-ImageSource webhook capability token and projects it to public-facing
 * views. The plaintext token is only ever returned at rotate time — subsequent reads use the
 * masked form.
 */
@Service
public class ImageSourceWebhookService {

    private final ImageSourceWebhookTokenRepository tokenRepo;
    private final ImageSourceCacheRepository imageSourceCache;
    private final String publicBaseUrl;

    public ImageSourceWebhookService(
            ImageSourceWebhookTokenRepository tokenRepo,
            ImageSourceCacheRepository imageSourceCache,
            @Value("${platform.public-base-url:}") String publicBaseUrl) {
        this.tokenRepo = tokenRepo;
        this.imageSourceCache = imageSourceCache;
        this.publicBaseUrl = publicBaseUrl;
    }

    /** Mint or replace the token for an ImageSource. The plaintext token is returned exactly once. */
    @Transactional
    public Minted rotate(String slug) {
        if (imageSourceCache.findBySlug(slug).isEmpty()) {
            throw new NotFoundException("ImageSource not found: " + slug);
        }
        // Delete-then-insert (rather than update) so a new token is never confused with the
        // old one if the rotate retried after a partial failure: the old token is gone the
        // moment the new one is committed.
        tokenRepo.deleteByImageSourceSlug(slug);
        tokenRepo.flush();

        String token = WebhookTokenGenerator.generate();
        Instant now = Instant.now();
        var row = new ImageSourceWebhookTokenEntity();
        row.setToken(token);
        row.setImageSourceSlug(slug);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        tokenRepo.save(row);
        return new Minted(token, urlFor(token));
    }

    /** Project the existing token (if any) to its public URL + masked form. */
    @Transactional(readOnly = true)
    public View view(String slug) {
        if (imageSourceCache.findBySlug(slug).isEmpty()) {
            throw new NotFoundException("ImageSource not found: " + slug);
        }
        var row = tokenRepo
                .findByImageSourceSlug(slug)
                .orElseThrow(() -> new NotFoundException("No webhook token configured for: " + slug));
        return new View(urlFor(row.getToken()), WebhookTokenGenerator.mask(row.getToken()));
    }

    private String urlFor(String token) {
        String base = publicBaseUrl == null || publicBaseUrl.isBlank() ? "" : trimTrailingSlash(publicBaseUrl);
        return base + "/api/v1/webhooks/" + token;
    }

    private static String trimTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    public record Minted(String secret, String url) {}

    public record View(String url, String maskedSecret) {}
}
