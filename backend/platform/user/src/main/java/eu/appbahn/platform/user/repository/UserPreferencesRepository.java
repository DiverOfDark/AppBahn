package eu.appbahn.platform.user.repository;

import eu.appbahn.platform.user.entity.UserPreferencesEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPreferencesRepository extends JpaRepository<UserPreferencesEntity, UUID> {

    Optional<UserPreferencesEntity> findByUserId(UUID userId);
}
