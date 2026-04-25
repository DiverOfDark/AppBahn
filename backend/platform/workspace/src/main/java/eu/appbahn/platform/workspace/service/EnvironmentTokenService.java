package eu.appbahn.platform.workspace.service;

import eu.appbahn.platform.api.AuditAction;
import eu.appbahn.platform.api.AuditTargetType;
import eu.appbahn.platform.api.EnvironmentToken;
import eu.appbahn.platform.api.environment.CreateEnvironmentTokenRequest;
import eu.appbahn.platform.api.environment.CreateEnvironmentTokenResponse;
import eu.appbahn.platform.common.audit.AuditLogService;
import eu.appbahn.platform.common.exception.NotFoundException;
import eu.appbahn.platform.common.exception.ValidationException;
import eu.appbahn.platform.common.security.AuthContext;
import eu.appbahn.platform.user.entity.UserEntity;
import eu.appbahn.platform.user.repository.UserRepository;
import eu.appbahn.platform.workspace.entity.EnvironmentEntity;
import eu.appbahn.platform.workspace.entity.EnvironmentTokenEntity;
import eu.appbahn.platform.workspace.repository.EnvironmentRepository;
import eu.appbahn.platform.workspace.repository.EnvironmentTokenRepository;
import eu.appbahn.platform.workspace.repository.ProjectRepository;
import eu.appbahn.shared.model.MemberRole;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EnvironmentTokenService {

    private static final String TOKEN_PREFIX = "abp_";
    private static final int TOKEN_RANDOM_LENGTH = 40;
    private static final String ALPHANUMERIC = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final EnvironmentRepository environmentRepository;
    private final EnvironmentTokenRepository tokenRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final PermissionService permissionService;
    private final AuditLogService auditLogService;

    @Value("${platform.tokens.max-lifetime-days:365}")
    private int maxLifetimeDays;

    public EnvironmentTokenService(
            EnvironmentRepository environmentRepository,
            EnvironmentTokenRepository tokenRepository,
            ProjectRepository projectRepository,
            UserRepository userRepository,
            PermissionService permissionService,
            AuditLogService auditLogService) {
        this.environmentRepository = environmentRepository;
        this.tokenRepository = tokenRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.permissionService = permissionService;
        this.auditLogService = auditLogService;
    }

    public List<EnvironmentToken> listTokens(String slug, AuthContext ctx) {
        var env = findEnvironment(slug);
        requireWorkspaceAdmin(env, ctx);

        var tokens = tokenRepository.findByEnvironmentId(env.getId());
        // Batch-fetch creator users to avoid N+1
        var creatorIds = tokens.stream()
                .map(EnvironmentTokenEntity::getCreatedBy)
                .filter(id -> id != null)
                .distinct()
                .toList();
        var usersById =
                userRepository.findAllById(creatorIds).stream().collect(Collectors.toMap(u -> u.getId(), u -> u));

        return tokens.stream().map(t -> toApi(t, usersById)).collect(Collectors.toList());
    }

    @Transactional
    public CreateEnvironmentTokenResponse createToken(String slug, CreateEnvironmentTokenRequest req, AuthContext ctx) {
        var env = findEnvironment(slug);
        requireWorkspaceAdmin(env, ctx);

        String rawToken = TOKEN_PREFIX + generateRandomString(TOKEN_RANDOM_LENGTH);
        String hash = hashToken(rawToken);

        var entity = new EnvironmentTokenEntity();
        entity.setEnvironmentId(env.getId());
        entity.setName(req.getName());
        entity.setTokenHash(hash);
        entity.setRole(req.getRole().name());
        entity.setCreatedBy(ctx.userId());

        if (req.getExpiresInDays() == null || req.getExpiresInDays() < 1) {
            throw new ValidationException("expiresInDays is required and must be >= 1");
        }
        if (req.getExpiresInDays() > maxLifetimeDays) {
            throw new ValidationException("Token lifetime exceeds maximum allowed: " + maxLifetimeDays + " days");
        }
        entity.setExpiresAt(Instant.now().plus(req.getExpiresInDays(), ChronoUnit.DAYS));

        tokenRepository.save(entity);

        var project = projectRepository.findById(env.getProjectId()).orElse(null);
        auditLogService
                .audit(ctx, AuditAction.ENVIRONMENT_TOKEN_CREATED)
                .target(AuditTargetType.ENVIRONMENT, env.getSlug())
                .inWorkspace(project != null ? project.getWorkspaceId() : null)
                .inProject(env.getProjectId())
                .inEnvironment(env.getId())
                .detail("tokenName", req.getName())
                .save();

        var resp = new CreateEnvironmentTokenResponse();
        resp.setId(entity.getId());
        resp.setName(entity.getName());
        resp.setToken(rawToken); // Only returned once
        resp.setRole(MemberRole.valueOf(entity.getRole()));
        resp.setExpiresAt(entity.getExpiresAt().atOffset(ZoneOffset.UTC));
        return resp;
    }

    @Transactional
    public void deleteToken(String slug, UUID tokenId, AuthContext ctx) {
        var env = findEnvironment(slug);
        requireWorkspaceAdmin(env, ctx);

        var entity = tokenRepository.findById(tokenId).orElseThrow(() -> new NotFoundException("Token not found"));
        tokenRepository.delete(entity);

        var project = projectRepository.findById(env.getProjectId()).orElse(null);
        auditLogService
                .audit(ctx, AuditAction.ENVIRONMENT_TOKEN_DELETED)
                .target(AuditTargetType.ENVIRONMENT, env.getSlug())
                .inWorkspace(project != null ? project.getWorkspaceId() : null)
                .inProject(env.getProjectId())
                .inEnvironment(env.getId())
                .save();
    }

    private EnvironmentToken toApi(EnvironmentTokenEntity entity, Map<UUID, UserEntity> usersById) {
        var dto = new EnvironmentToken();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setRole(MemberRole.valueOf(entity.getRole()));
        if (entity.getExpiresAt() != null) {
            dto.setExpiresAt(entity.getExpiresAt().atOffset(ZoneOffset.UTC));
        }
        if (entity.getLastUsedAt() != null) {
            dto.setLastUsedAt(entity.getLastUsedAt().atOffset(ZoneOffset.UTC));
        }
        if (entity.getCreatedBy() != null) {
            var user = usersById.get(entity.getCreatedBy());
            if (user != null) {
                dto.setCreatedBy(user.getEmail());
            }
        }
        dto.setCreatedAt(entity.getCreatedAt().atOffset(ZoneOffset.UTC));
        return dto;
    }

    private void requireWorkspaceAdmin(EnvironmentEntity env, AuthContext ctx) {
        var project = projectRepository
                .findById(env.getProjectId())
                .orElseThrow(() -> new NotFoundException("Project not found"));
        permissionService.requireWorkspaceRole(ctx, project.getWorkspaceId(), MemberRole.ADMIN);
    }

    private EnvironmentEntity findEnvironment(String slug) {
        return environmentRepository
                .findBySlug(slug)
                .orElseThrow(() -> new NotFoundException("Environment not found: " + slug));
    }

    private static String generateRandomString(int length) {
        var sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHANUMERIC.charAt(RANDOM.nextInt(ALPHANUMERIC.length())));
        }
        return sb.toString();
    }

    static String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
