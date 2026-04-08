package eu.appbahn.platform.user.service;

import eu.appbahn.platform.user.entity.UserEntity;
import eu.appbahn.platform.user.repository.UserRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public UserEntity findOrCreateFromJwt(Jwt jwt) {
        String subject = jwt.getSubject();
        return userRepository.findByOidcSubjectId(subject)
                .orElseGet(() -> {
                    var user = new UserEntity();
                    user.setOidcSubjectId(subject);
                    user.setEmail(jwt.getClaimAsString("email"));
                    return userRepository.save(user);
                });
    }

    public UserEntity findById(UUID id) {
        return userRepository.findById(id).orElse(null);
    }
}
