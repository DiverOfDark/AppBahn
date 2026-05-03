package eu.appbahn.platform.resource.service;

import eu.appbahn.platform.common.exception.NotFoundException;
import eu.appbahn.platform.resource.entity.ImageSourceWebhookTokenEntity;
import eu.appbahn.platform.resource.repository.ImageSourceWebhookTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Provider-agnostic webhook receiver. The path token is looked up in
 * {@code image_source_webhook_token}; on a hit the bound ImageSource is nudged via the tunnel.
 * On a miss a {@link NotFoundException} is raised so the controller can return {@code 404} —
 * indistinguishable from a missing ImageSource so the endpoint isn't a token-enumeration
 * oracle.
 */
@Service
public class WebhookReceiverService {

    private static final Logger log = LoggerFactory.getLogger(WebhookReceiverService.class);

    private final ImageSourceWebhookTokenRepository tokenRepo;
    private final ImageSourceNudger nudger;

    public WebhookReceiverService(ImageSourceWebhookTokenRepository tokenRepo, ImageSourceNudger nudger) {
        this.tokenRepo = tokenRepo;
        this.nudger = nudger;
    }

    @Transactional
    public void receive(String token) {
        ImageSourceWebhookTokenEntity row =
                tokenRepo.findByToken(token).orElseThrow(() -> new NotFoundException("Unknown webhook token"));
        nudger.nudge(row.getImageSourceSlug());
        log.info("Webhook accepted slug={}", row.getImageSourceSlug());
    }
}
