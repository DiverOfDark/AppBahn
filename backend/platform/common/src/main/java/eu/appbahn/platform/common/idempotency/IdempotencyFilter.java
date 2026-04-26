package eu.appbahn.platform.common.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.appbahn.platform.common.security.AppBahnAuthenticationToken;
import eu.appbahn.platform.common.security.AuthContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Generic dedup filter for mutating endpoints. When the client sends an
 * {@link IdempotencyConstants#HEADER_NAME} header on a {@code POST/PUT/PATCH/DELETE},
 * the response (status, content-type, body) is captured on first success and replayed
 * verbatim for any retry from the same actor within {@link IdempotencyConstants#TTL}.
 *
 * <p>Replays are short-circuited: the controller is not invoked. A retry whose request
 * fingerprint differs from the original gets {@code 422 idempotency_key_reused} instead
 * of being silently merged. Rows past the TTL behave as missing — the cleanup job removes
 * them eventually but lookups also filter on age so a stale row can never spuriously match.
 *
 * <p>Skipped paths: GET/HEAD/OPTIONS, missing key, multipart uploads, SSE, request body
 * over {@link IdempotencyConstants#MAX_BODY_BYTES}, unauthenticated callers, handlers
 * marked {@link IdempotencyOptOut}, and non-2xx responses (which are never cached so the
 * client can retry with a corrected request).
 */
@Component
public class IdempotencyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyFilter.class);

    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final String CONTENT_TYPE_HEADER = "Content-Type";

    private final IdempotencyRecordRepository repository;
    private final RequestMappingHandlerMapping handlerMapping;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public IdempotencyFilter(
            IdempotencyRecordRepository repository,
            @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping,
            ObjectMapper objectMapper) {
        this(repository, handlerMapping, objectMapper, Clock.systemUTC());
    }

    IdempotencyFilter(
            IdempotencyRecordRepository repository,
            RequestMappingHandlerMapping handlerMapping,
            ObjectMapper objectMapper,
            Clock clock) {
        this.repository = repository;
        this.handlerMapping = handlerMapping;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        if (!MUTATING_METHODS.contains(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        String key = request.getHeader(IdempotencyConstants.HEADER_NAME);
        if (key == null || key.isBlank()) {
            chain.doFilter(request, response);
            return;
        }

        UUID actorId = currentActorId();
        if (actorId == null) {
            chain.doFilter(request, response);
            return;
        }

        String contentType = request.getContentType();
        if (contentType != null && contentType.toLowerCase().startsWith("multipart/")) {
            response.setHeader(IdempotencyConstants.REPLAYED_HEADER, "false");
            chain.doFilter(request, response);
            return;
        }

        byte[] body;
        try {
            body = request.getInputStream().readAllBytes();
        } catch (IOException e) {
            log.warn("Idempotency: failed to read request body, passing through", e);
            chain.doFilter(request, response);
            return;
        }

        if (body.length > IdempotencyConstants.MAX_BODY_BYTES) {
            log.debug(
                    "Idempotency: request body {} bytes exceeds cap {}, skipping cache",
                    body.length,
                    IdempotencyConstants.MAX_BODY_BYTES);
            response.setHeader(IdempotencyConstants.REPLAYED_HEADER, "false");
            chain.doFilter(new CachedBodyHttpServletRequest(request, body), response);
            return;
        }

        var wrappedRequest = new CachedBodyHttpServletRequest(request, body);

        if (isOptedOut(wrappedRequest)) {
            chain.doFilter(wrappedRequest, response);
            return;
        }

        byte[] fingerprint = fingerprint(request, body);

        Optional<IdempotencyRecordEntity> existing = repository.findByIdempotencyKeyAndActorId(key, actorId);
        Instant now = clock.instant();
        if (existing.isPresent()) {
            IdempotencyRecordEntity row = existing.get();
            boolean fresh = row.getCreatedAt().isAfter(now.minus(IdempotencyConstants.TTL));
            if (fresh) {
                if (Arrays.equals(row.getRequestHash(), fingerprint)) {
                    replay(row, response);
                } else {
                    writeKeyReused(response);
                }
                return;
            }
        }

        var responseWrapper = new ContentCachingResponseWrapper(response);
        try {
            chain.doFilter(wrappedRequest, responseWrapper);
        } finally {
            int status = responseWrapper.getStatus();
            byte[] capturedBody = responseWrapper.getContentAsByteArray();
            // Always copy first so the client sees the body regardless of caching outcome.
            responseWrapper.copyBodyToResponse();

            if (status >= 200 && status < 300 && capturedBody.length <= IdempotencyConstants.MAX_BODY_BYTES) {
                persist(key, actorId, request, fingerprint, status, capturedBody, responseWrapper, now);
            } else if (capturedBody.length > IdempotencyConstants.MAX_BODY_BYTES) {
                log.debug(
                        "Idempotency: response body {} bytes exceeds cap {}, not caching",
                        capturedBody.length,
                        IdempotencyConstants.MAX_BODY_BYTES);
                response.setHeader(IdempotencyConstants.REPLAYED_HEADER, "false");
            }
        }
    }

    private boolean isOptedOut(HttpServletRequest request) {
        try {
            HandlerExecutionChain handler = handlerMapping.getHandler(request);
            if (handler != null && handler.getHandler() instanceof HandlerMethod method) {
                return method.getMethodAnnotation(IdempotencyOptOut.class) != null;
            }
        } catch (Exception e) {
            log.debug("Idempotency: handler resolution failed, treating as not opted-out", e);
        }
        return false;
    }

    private static UUID currentActorId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof AppBahnAuthenticationToken token) {
            AuthContext ctx = token.getPrincipal();
            return ctx != null ? ctx.userId() : null;
        }
        return null;
    }

    private static byte[] fingerprint(HttpServletRequest request, byte[] body) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandatory in every JDK distribution; this branch is unreachable.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
        digest.update(request.getMethod().getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
        digest.update(request.getRequestURI().getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
        String query = request.getQueryString();
        if (query != null) {
            digest.update(query.getBytes(StandardCharsets.UTF_8));
        }
        digest.update((byte) 0);
        digest.update(body);
        return digest.digest();
    }

    private static void replay(IdempotencyRecordEntity row, HttpServletResponse response) throws IOException {
        response.setStatus(row.getResponseStatus());
        if (row.getResponseHeaders() != null) {
            row.getResponseHeaders().forEach(response::setHeader);
        }
        response.setHeader(IdempotencyConstants.REPLAYED_HEADER, "true");
        if (row.getResponseBody() != null) {
            response.getOutputStream().write(row.getResponseBody());
            response.getOutputStream().flush();
        }
    }

    private void writeKeyReused(HttpServletResponse response) throws IOException {
        response.setStatus(422);
        response.setContentType("application/json");
        objectMapper.writeValue(
                response.getOutputStream(),
                Map.of(
                        "error",
                        "idempotency_key_reused",
                        "message",
                        "Idempotency-Key was previously used with a different request"));
    }

    private void persist(
            String key,
            UUID actorId,
            HttpServletRequest request,
            byte[] fingerprint,
            int status,
            byte[] body,
            ContentCachingResponseWrapper responseWrapper,
            Instant now) {
        var entity = new IdempotencyRecordEntity();
        entity.setIdempotencyKey(key);
        entity.setActorId(actorId);
        entity.setRequestMethod(request.getMethod());
        entity.setRequestPath(request.getRequestURI());
        entity.setRequestHash(fingerprint);
        entity.setResponseStatus((short) status);
        entity.setResponseHeaders(captureResponseHeaders(responseWrapper));
        entity.setResponseBody(body.length == 0 ? null : body);
        entity.setCreatedAt(now);
        try {
            repository.save(entity);
        } catch (RuntimeException e) {
            // A concurrent request may have inserted the same (key, actor) — that's a logical
            // duplicate, not a failure. Log at debug; the next retry will replay either row.
            log.debug("Idempotency: failed to persist record for key=*** actor={}: {}", actorId, e.getMessage());
        }
    }

    private static Map<String, String> captureResponseHeaders(ContentCachingResponseWrapper response) {
        Map<String, String> headers = new LinkedHashMap<>();
        String contentType = response.getContentType();
        if (contentType != null) {
            headers.put(CONTENT_TYPE_HEADER, contentType);
        }
        return headers;
    }
}
