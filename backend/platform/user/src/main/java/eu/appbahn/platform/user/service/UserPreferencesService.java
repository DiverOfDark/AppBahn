package eu.appbahn.platform.user.service;

import eu.appbahn.platform.user.entity.UserPreferencesEntity;
import eu.appbahn.platform.user.repository.UserPreferencesRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserPreferencesService {

    private final UserPreferencesRepository repository;

    public UserPreferencesService(UserPreferencesRepository repository) {
        this.repository = repository;
    }

    public UserPreferencesEntity getOrEmpty(UUID userId) {
        return repository.findByUserId(userId).orElseGet(() -> {
            var prefs = new UserPreferencesEntity();
            prefs.setUserId(userId);
            prefs.setUpdatedAt(Instant.now());
            return prefs;
        });
    }

    @Transactional
    public UserPreferencesEntity upsert(UUID userId, String defaultWorkspaceSlug) {
        var prefs = repository.findByUserId(userId).orElseGet(() -> {
            var p = new UserPreferencesEntity();
            p.setUserId(userId);
            p.setUpdatedAt(Instant.now());
            return p;
        });
        prefs.setDefaultWorkspaceSlug(defaultWorkspaceSlug);
        prefs.setUpdatedAt(Instant.now());
        return repository.save(prefs);
    }
}
