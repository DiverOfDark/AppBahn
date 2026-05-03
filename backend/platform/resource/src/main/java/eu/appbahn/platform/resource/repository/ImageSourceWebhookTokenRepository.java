package eu.appbahn.platform.resource.repository;

import eu.appbahn.platform.resource.entity.ImageSourceWebhookTokenEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImageSourceWebhookTokenRepository extends JpaRepository<ImageSourceWebhookTokenEntity, String> {

    Optional<ImageSourceWebhookTokenEntity> findByToken(String token);

    Optional<ImageSourceWebhookTokenEntity> findByImageSourceSlug(String imageSourceSlug);

    void deleteByImageSourceSlug(String imageSourceSlug);
}
