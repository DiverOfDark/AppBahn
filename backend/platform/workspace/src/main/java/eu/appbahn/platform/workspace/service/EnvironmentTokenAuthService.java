package eu.appbahn.platform.workspace.service;

import eu.appbahn.platform.common.security.AppBahnAuthenticationToken;
import eu.appbahn.platform.common.security.AuthContext;
import eu.appbahn.platform.workspace.entity.EnvironmentTokenEntity;
import eu.appbahn.platform.workspace.repository.EnvironmentTokenRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class EnvironmentTokenAuthService {

    private final EnvironmentTokenRepository tokenRepository;

    public EnvironmentTokenAuthService(EnvironmentTokenRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
    }

    /**
     * Validates an abp_ prefixed token and returns an Authentication if valid.
     */
    public Optional<Authentication> authenticate(String rawToken) {
        String hash = EnvironmentTokenService.hashToken(rawToken);
        var tokenOpt = tokenRepository.findByTokenHash(hash);
        if (tokenOpt.isEmpty()) {
            return Optional.empty();
        }

        EnvironmentTokenEntity token = tokenOpt.get();

        // Check expiry
        if (token.getExpiresAt() != null && token.getExpiresAt().isBefore(Instant.now())) {
            return Optional.empty();
        }

        // Update last used
        token.setLastUsedAt(Instant.now());
        tokenRepository.save(token);

        // Token-based auth: userId and email are null per spec (actor_source="token")
        var ctx = new AuthContext(null, null, List.of(), false);
        return Optional.of(new AppBahnAuthenticationToken(ctx));
    }
}
