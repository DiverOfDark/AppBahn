package eu.appbahn.platform.workspace.service;

import eu.appbahn.platform.api.model.CreateEnvironmentTokenRequest;
import eu.appbahn.platform.api.model.CreateEnvironmentTokenResponse;
import eu.appbahn.platform.api.model.EnvironmentToken;
import eu.appbahn.platform.common.audit.AuditLogService;
import eu.appbahn.platform.common.exception.NotFoundException;
import eu.appbahn.platform.common.security.AuthContext;
import eu.appbahn.platform.workspace.entity.EnvironmentEntity;
import eu.appbahn.platform.workspace.entity.EnvironmentTokenEntity;
import eu.appbahn.platform.workspace.entity.ProjectEntity;
import eu.appbahn.platform.workspace.repository.EnvironmentRepository;
import eu.appbahn.platform.workspace.repository.EnvironmentTokenRepository;
import eu.appbahn.platform.workspace.repository.ProjectRepository;
import eu.appbahn.shared.model.MemberRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class EnvironmentTokenService {

    private static final String TOKEN_PREFIX = "abp_";
    private static final int TOKEN_RANDOM_LENGTH = 40;
    private static final String ALPHANUMERIC = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final EnvironmentRepository environmentRepository;
    private final EnvironmentTokenRepository tokenRepository;
    private final ProjectRepository projectRepository;
    private final PermissionService permissionService;
    private final AuditLogService auditLogService;

    public EnvironmentTokenService(
            EnvironmentRepository environmentRepository,
            EnvironmentTokenRepository tokenRepository,
            ProjectRepository projectRepository,
            PermissionService permissionService,
            AuditLogService auditLogService
    ) {
        this.environmentRepository = environmentRepository;
        this.tokenRepository = tokenRepository;
        this.projectRepository = projectRepository;
        this.permissionService = permissionService;
        this.auditLogService = auditLogService;
    }

    public List<EnvironmentToken> listTokens(String slug, AuthContext ctx) {
        var env = findEnvironment(slug);
        requireWorkspaceAdmin(env, ctx);
        return tokenRepository.findByEnvironmentId(env.getId()).stream()
                .map(this::toApi)
                .collect(Collectors.toList());
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
        entity.setRole(req.getRole().getValue());
        entity.setCreatedBy(ctx.userId());

        if (req.getExpiresInDays() != null && req.getExpiresInDays() > 0) {
            entity.setExpiresAt(Instant.now().plus(req.getExpiresInDays(), ChronoUnit.DAYS));
        }

        tokenRepository.save(entity);

        var project = projectRepository.findById(env.getProjectId()).orElse(null);
        auditLogService.log(ctx, "environment_token.created", "environment", env.getSlug(),
                project != null ? project.getWorkspaceId() : null,
                Map.of("tokenName", req.getName()));

        var resp = new CreateEnvironmentTokenResponse();
        resp.setId(entity.getId());
        resp.setName(entity.getName());
        resp.setToken(rawToken); // Only returned once
        if (entity.getExpiresAt() != null) {
            resp.setExpiresAt(entity.getExpiresAt().atOffset(ZoneOffset.UTC));
        }
        return resp;
    }

    @Transactional
    public void deleteToken(String slug, UUID tokenId, AuthContext ctx) {
        var env = findEnvironment(slug);
        requireWorkspaceAdmin(env, ctx);

        var entity = tokenRepository.findById(tokenId)
                .orElseThrow(() -> new NotFoundException("Token not found"));
        tokenRepository.delete(entity);

        var project = projectRepository.findById(env.getProjectId()).orElse(null);
        auditLogService.log(ctx, "environment_token.deleted", "environment", env.getSlug(),
                project != null ? project.getWorkspaceId() : null, null);
    }

    private EnvironmentToken toApi(EnvironmentTokenEntity entity) {
        var dto = new EnvironmentToken();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setRole(EnvironmentToken.RoleEnum.fromValue(entity.getRole()));
        if (entity.getExpiresAt() != null) {
            dto.setExpiresAt(entity.getExpiresAt().atOffset(ZoneOffset.UTC));
        }
        if (entity.getLastUsedAt() != null) {
            dto.setLastUsedAt(entity.getLastUsedAt().atOffset(ZoneOffset.UTC));
        }
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreatedAt(entity.getCreatedAt().atOffset(ZoneOffset.UTC));
        return dto;
    }

    private void requireWorkspaceAdmin(EnvironmentEntity env, AuthContext ctx) {
        var project = projectRepository.findById(env.getProjectId())
                .orElseThrow(() -> new NotFoundException("Project not found"));
        permissionService.requireWorkspaceRole(ctx, project.getWorkspaceId(), MemberRole.ADMIN);
    }

    private EnvironmentEntity findEnvironment(String slug) {
        return environmentRepository.findBySlug(slug)
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
