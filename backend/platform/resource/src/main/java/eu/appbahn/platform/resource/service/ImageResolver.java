package eu.appbahn.platform.resource.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import eu.appbahn.platform.common.exception.ValidationException;
import eu.appbahn.shared.Labels;
import eu.appbahn.shared.crd.CredentialRef;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Resolves {@code image:tag} references to their registry manifest digest via Docker Registry HTTP
 * API v2: {@code HEAD /v2/<repo>/manifests/<tag>} returns a {@code Docker-Content-Digest} header
 * pointing at the immutable manifest. Pinning the Pod spec to {@code image@sha256:digest}
 * guarantees that a moved tag cannot change what runs.
 *
 * <p>Handles the standard Bearer token challenge: when a registry replies {@code 401} with a
 * {@code Www-Authenticate: Bearer realm=...,service=...,scope=...} header, the resolver fetches
 * an anonymous token from that realm and retries the manifest HEAD with the bearer credential.
 * That covers Docker Hub and GHCR public images, both of which require a token even for
 * unauthenticated reads.
 *
 * <p>Authenticated registries (private credentials via {@code DockerSource.credentialRef}) need
 * the user-supplied secret to be loaded and exchanged for a scoped token; resolution is skipped
 * for those until the secret-loading plumbing lands.
 */
@Service
public class ImageResolver {

    private static final Logger log = LoggerFactory.getLogger(ImageResolver.class);

    static final String DEFAULT_DOCKER_REGISTRY = "registry-1.docker.io";
    static final String DEFAULT_DOCKER_REGISTRY_LIBRARY_NAMESPACE = "library";
    static final String DOCKER_CONTENT_DIGEST_HEADER = "Docker-Content-Digest";

    private static final String DOCKER_MANIFEST_V2_MEDIA_TYPE = "application/vnd.docker.distribution.manifest.v2+json";
    private static final String OCI_MANIFEST_V1_MEDIA_TYPE = "application/vnd.oci.image.manifest.v1+json";
    private static final String DOCKER_MANIFEST_LIST_V2_MEDIA_TYPE =
            "application/vnd.docker.distribution.manifest.list.v2+json";
    private static final String OCI_INDEX_V1_MEDIA_TYPE = "application/vnd.oci.image.index.v1+json";

    private static final String MANIFEST_ACCEPT_HEADER = String.join(
            ", ",
            OCI_MANIFEST_V1_MEDIA_TYPE,
            DOCKER_MANIFEST_V2_MEDIA_TYPE,
            OCI_INDEX_V1_MEDIA_TYPE,
            DOCKER_MANIFEST_LIST_V2_MEDIA_TYPE);

    private final RestClient restClient;

    public ImageResolver() {
        this(RestClient.builder());
    }

    /** Test seam: lets MockRestServiceServer bind to the underlying client builder. */
    ImageResolver(RestClient.Builder builder) {
        this.restClient = builder.build();
    }

    /**
     * Parses {@code image} (which may include a registry prefix) plus a separate {@code tag}, and
     * an optional {@code registryUrlOverride} from {@code DockerSource.registryUrl}. Returns the
     * canonicalised parts (registry/repo/tag) without contacting the network.
     */
    public static ResolvedImage parse(String image, String tag, String registryUrlOverride) {
        if (image == null || image.isBlank()) {
            throw new ValidationException("Docker source: image must not be empty");
        }
        String effectiveTag = (tag == null || tag.isBlank()) ? Labels.DEFAULT_IMAGE_TAG : tag;
        String registry;
        String repo;

        // A registry component is recognised only when the prefix before the first '/' contains a
        // '.' or ':' or equals 'localhost' — otherwise it's a Docker Hub user/org namespace.
        int slashIdx = image.indexOf('/');
        String head = slashIdx >= 0 ? image.substring(0, slashIdx) : "";
        boolean headLooksLikeRegistry =
                slashIdx >= 0 && (head.contains(".") || head.contains(":") || head.equals("localhost"));

        if (headLooksLikeRegistry) {
            registry = head;
            repo = image.substring(slashIdx + 1);
        } else if (slashIdx >= 0) {
            // user/repo form on Docker Hub
            registry = DEFAULT_DOCKER_REGISTRY;
            repo = image;
        } else {
            // bare name (e.g. "nginx") — implicit Docker Hub library namespace
            registry = DEFAULT_DOCKER_REGISTRY;
            repo = DEFAULT_DOCKER_REGISTRY_LIBRARY_NAMESPACE + "/" + image;
        }

        if (registryUrlOverride != null && !registryUrlOverride.isBlank()) {
            registry = stripScheme(registryUrlOverride);
        }

        return new ResolvedImage(registry, repo, effectiveTag, null);
    }

    /**
     * Resolves the manifest digest for {@code image:tag}. Returns a {@link ResolvedImage} with the
     * digest populated when the registry is reachable and the manifest exists; returns
     * {@code digest == null} (parsed-only) on any registry error so the caller can fall back to
     * {@code image:tag} and let the kubelet surface the failure later. Pinning is a best-effort
     * upgrade — temporary registry hiccups, missing tags, or auth-only mirrors must not block
     * a deploy from being triggered.
     *
     * @param credentialRef when non-null, signals an authenticated registry — currently
     *     unsupported and resolution is skipped (returns a {@link ResolvedImage} with
     *     {@code digest == null}). Authenticated resolution is a follow-up.
     */
    public ResolvedImage resolve(String image, String tag, String registryUrlOverride, CredentialRef credentialRef) {
        ResolvedImage parsed = parse(image, tag, registryUrlOverride);

        if (credentialRef != null && credentialRef.getSecretName() != null) {
            log.info(
                    "Skipping digest resolution for {}: credentialRef secret {} requires a user-supplied secret (not yet wired)",
                    image,
                    credentialRef.getSecretName());
            return parsed;
        }

        String url = "https://" + parsed.registry() + "/v2/" + parsed.repo() + "/manifests/" + parsed.tag();
        try {
            HeadResult first = headManifest(url, null);
            String digest = first.digest;
            if (digest == null && first.bearerChallenge != null) {
                String token = fetchAnonymousToken(first.bearerChallenge);
                digest = headManifest(url, token).digest;
            }
            if (digest == null || digest.isBlank()) {
                log.warn(
                        "Registry response for {}:{} missing {} header — proceeding without digest pin",
                        image,
                        parsed.tag(),
                        DOCKER_CONTENT_DIGEST_HEADER);
                return parsed;
            }
            log.info("Resolved {}:{} to {}", image, parsed.tag(), digest);
            return new ResolvedImage(parsed.registry(), parsed.repo(), parsed.tag(), digest);
        } catch (RestClientException e) {
            log.warn(
                    "Failed to resolve {}:{} against {} — proceeding without digest pin: {}",
                    image,
                    parsed.tag(),
                    parsed.registry(),
                    e.getMessage());
            return parsed;
        }
    }

    /**
     * Issues the manifest HEAD. On 200, {@link HeadResult#digest} carries the digest. On a 401
     * Bearer challenge during the unauthenticated attempt, {@link HeadResult#bearerChallenge}
     * carries the challenge header so the caller can exchange it for a token; on any other error
     * the {@link HttpClientErrorException} bubbles up.
     */
    private HeadResult headManifest(String url, String bearerToken) {
        try {
            HttpHeaders responseHeaders = restClient
                    .head()
                    .uri(url)
                    .header(HttpHeaders.ACCEPT, MANIFEST_ACCEPT_HEADER)
                    .headers(h -> {
                        if (bearerToken != null) {
                            h.setBearerAuth(bearerToken);
                        }
                    })
                    .retrieve()
                    .toBodilessEntity()
                    .getHeaders();
            return new HeadResult(responseHeaders.getFirst(DOCKER_CONTENT_DIGEST_HEADER), null);
        } catch (HttpClientErrorException e) {
            if (bearerToken == null && e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                String challenge = e.getResponseHeaders() != null
                        ? e.getResponseHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)
                        : null;
                if (challenge != null) {
                    return new HeadResult(null, challenge);
                }
            }
            throw e;
        }
    }

    /**
     * Reads the {@code Www-Authenticate: Bearer realm=...,service=...,scope=...} challenge and
     * exchanges it for an anonymous token. Docker Hub and GHCR both expose the realm at
     * {@code auth.docker.io} / {@code ghcr.io/token} and return {@code {"token": "…"}}
     * (or {@code "access_token"}) for unauthenticated callers carrying the right service+scope.
     */
    private String fetchAnonymousToken(String challengeHeader) {
        Map<String, String> challenge = parseBearerChallenge(challengeHeader);
        String realm = challenge.get("realm");
        if (realm == null || realm.isBlank()) {
            throw new ValidationException("Registry's Www-Authenticate did not advertise a realm: " + challengeHeader);
        }
        StringBuilder tokenUrl = new StringBuilder(realm);
        boolean hasQuery = realm.contains("?");
        for (var entry : challenge.entrySet()) {
            if ("realm".equals(entry.getKey())) {
                continue;
            }
            tokenUrl.append(hasQuery ? '&' : '?');
            hasQuery = true;
            tokenUrl.append(entry.getKey())
                    .append('=')
                    .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }

        TokenResponse resp =
                restClient.get().uri(URI.create(tokenUrl.toString())).retrieve().body(TokenResponse.class);
        if (resp == null) {
            throw new ValidationException("Empty token response from " + tokenUrl);
        }
        String token = resp.token != null ? resp.token : resp.accessToken;
        if (token == null || token.isBlank()) {
            throw new ValidationException("Token response from " + tokenUrl + " did not include a token");
        }
        return token;
    }

    /**
     * Parses a {@code Bearer realm="…",service="…",scope="…"} header into a parameter map. The
     * registry spec quotes every value, but a couple of older proxies omit the quotes — accept both.
     */
    static Map<String, String> parseBearerChallenge(String header) {
        if (header == null) {
            return Map.of();
        }
        String trimmed = header.trim();
        if (trimmed.regionMatches(true, 0, "Bearer", 0, 6)) {
            trimmed = trimmed.substring(6).trim();
        }
        Map<String, String> out = new LinkedHashMap<>();
        int i = 0;
        while (i < trimmed.length()) {
            int eq = trimmed.indexOf('=', i);
            if (eq < 0) {
                break;
            }
            String key = trimmed.substring(i, eq).trim();
            int valStart = eq + 1;
            int valEnd;
            String value;
            if (valStart < trimmed.length() && trimmed.charAt(valStart) == '"') {
                valStart++;
                valEnd = trimmed.indexOf('"', valStart);
                if (valEnd < 0) {
                    break;
                }
                value = trimmed.substring(valStart, valEnd);
                i = valEnd + 1;
            } else {
                valEnd = trimmed.indexOf(',', valStart);
                if (valEnd < 0) {
                    valEnd = trimmed.length();
                }
                value = trimmed.substring(valStart, valEnd).trim();
                i = valEnd;
            }
            out.put(key, value);
            while (i < trimmed.length() && (trimmed.charAt(i) == ',' || Character.isWhitespace(trimmed.charAt(i)))) {
                i++;
            }
        }
        return out;
    }

    private static String stripScheme(String url) {
        int idx = url.indexOf("://");
        return idx >= 0 ? url.substring(idx + 3) : url;
    }

    private record HeadResult(String digest, String bearerChallenge) {}

    record TokenResponse(
            @JsonProperty("token") String token,
            @JsonProperty("access_token") String accessToken) {}
}
