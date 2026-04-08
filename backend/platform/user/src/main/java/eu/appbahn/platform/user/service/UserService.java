package eu.appbahn.platform.user.service;

import eu.appbahn.platform.user.entity.UserEntity;
import eu.appbahn.platform.user.event.UserCreatedEvent;
import eu.appbahn.platform.user.repository.UserRepository;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    public UserService(UserRepository userRepository, ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public UserEntity findOrCreateFromJwt(Jwt jwt) {
        String subject = jwt.getSubject();
        return userRepository.findByOidcSubjectId(subject).orElseGet(() -> {
            var user = new UserEntity();
            user.setOidcSubjectId(subject);
            user.setEmail(jwt.getClaimAsString("email"));
            var saved = userRepository.save(user);
            eventPublisher.publishEvent(new UserCreatedEvent(saved.getId(), saved.getEmail()));
            return saved;
        });
    }

    public UserEntity findById(UUID id) {
        return userRepository.findById(id).orElse(null);
    }
}
