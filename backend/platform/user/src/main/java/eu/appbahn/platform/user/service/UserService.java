package eu.appbahn.platform.user.service;

import eu.appbahn.platform.user.entity.UserEntity;
import eu.appbahn.platform.user.event.UserCreatedEvent;
import eu.appbahn.platform.user.repository.UserRepository;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    public UserService(UserRepository userRepository, ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Two concurrent first-time requests for the same OIDC subject (common when the SPA fires
     * several API calls right after login) would both miss the {@code findByOidcSubjectId} check
     * and race into {@code save} — the loser then hits the {@code users_oidc_subject_id_key}
     * unique constraint and bubbles a 500 that the SPA interprets as auth failure. Guard with
     * a catch-and-refetch: each repository call runs in its own transaction (Spring Data's
     * {@code SimpleJpaRepository} wraps them), so the failed insert's rollback doesn't taint
     * the subsequent find.
     */
    public UserEntity findOrCreateFromJwt(Jwt jwt) {
        String subject = jwt.getSubject();
        var existing = userRepository.findByOidcSubjectId(subject);
        if (existing.isPresent()) {
            return existing.get();
        }
        try {
            var user = new UserEntity();
            user.setOidcSubjectId(subject);
            user.setEmail(jwt.getClaimAsString("email"));
            var saved = userRepository.save(user);
            eventPublisher.publishEvent(new UserCreatedEvent(saved.getId(), saved.getEmail()));
            return saved;
        } catch (DataIntegrityViolationException concurrentInsert) {
            return userRepository
                    .findByOidcSubjectId(subject)
                    .orElseThrow(() -> new IllegalStateException(
                            "User row for " + subject + " vanished after a concurrent insert conflict",
                            concurrentInsert));
        }
    }

    public UserEntity findById(UUID id) {
        return userRepository.findById(id).orElse(null);
    }
}
