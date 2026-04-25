package eu.appbahn.platform.api.tunnel;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Operator ↔ platform tunnel over REST + SSE.
 *
 * <ul>
 *   <li>Every call except {@code registerCluster} carries {@code Authorization: Bearer <JWT>}.
 *       The JWT is RS256-signed by the operator's private key; the platform verifies it
 *       against the stored public key for the cluster.</li>
 *   <li>{@code subscribeCommands} opens the only server-streaming endpoint (SSE). Each
 *       frame's {@code event:} field is the matching DTO's {@code EVENT_NAME} constant (e.g.
 *       {@link HelloAck#EVENT_NAME}); {@code data:} holds the JSON body of that DTO.</li>
 * </ul>
 *
 * <p>The {@code *_PATH} constants are the single source of truth for wire paths — the server's
 * {@code @RequestMapping} and the operator's client proxy both reference them.
 */
@Validated
@Tag(name = "Tunnel")
public interface TunnelApi {

    /** SSE {@code event:} name of heartbeat frames; body is an empty JSON object and operators ignore it. */
    String KEEPALIVE_EVENT = "keepalive";

    String REGISTER_PATH = "/tunnel/v1/register";
    String COMMANDS_PATH = "/tunnel/v1/commands";
    String EVENTS_PATH = "/tunnel/v1/events";
    String ACK_PATH = "/tunnel/v1/commands/{correlationId}/ack";
    String GOODBYE_PATH = "/tunnel/v1/goodbye";

    /** Unauthenticated bootstrap — the platform doesn't yet know the operator's public key. */
    @RequestMapping(
            method = RequestMethod.POST,
            value = REGISTER_PATH,
            produces = MediaType.APPLICATION_JSON_VALUE,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<RegisterClusterAck> registerCluster(@Valid @RequestBody RegisterClusterRequest request);

    /**
     * Opens the SSE stream that carries all platform→operator commands and snapshot pushes.
     * The first frame is always {@link HelloAck} ({@link HelloAck#EVENT_NAME}); subsequent frames carry
     * commands ({@code apply-resource}, {@code delete-resource}, {@code apply-namespace},
     * {@code delete-namespace}) and snapshot refreshes ({@code admin-config-push},
     * {@code quota-rbac-cache-push}) gated by content-addressed revisions.
     */
    @RequestMapping(method = RequestMethod.GET, value = COMMANDS_PATH, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter subscribeCommands(
            @RequestHeader(value = "Authorization", required = false) @Nullable String authorization,
            @RequestParam("clusterName") String clusterName,
            @RequestParam(value = "operatorInstanceId", required = false) @Nullable String operatorInstanceId,
            @RequestParam(value = "operatorVersion", required = false) @Nullable String operatorVersion,
            @RequestParam(value = "lastAdminConfigRevision", required = false, defaultValue = "0")
                    long lastAdminConfigRevision,
            @RequestParam(value = "lastQuotaRbacRevision", required = false, defaultValue = "0")
                    long lastQuotaRbacRevision);

    @RequestMapping(
            method = RequestMethod.POST,
            value = EVENTS_PATH,
            produces = MediaType.APPLICATION_JSON_VALUE,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<PushEventsAck> pushEvents(
            @RequestHeader(value = "Authorization", required = false) @Nullable String authorization,
            @Valid @RequestBody PushEventsRequest request);

    @RequestMapping(
            method = RequestMethod.POST,
            value = ACK_PATH,
            produces = MediaType.APPLICATION_JSON_VALUE,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> ackCommand(
            @RequestHeader(value = "Authorization", required = false) @Nullable String authorization,
            @PathVariable("correlationId") String correlationId,
            @Valid @RequestBody AckCommandRequest request);

    @RequestMapping(
            method = RequestMethod.POST,
            value = GOODBYE_PATH,
            produces = MediaType.APPLICATION_JSON_VALUE,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<GoodbyeAck> goodbye(
            @RequestHeader(value = "Authorization", required = false) @Nullable String authorization,
            @Valid @RequestBody GoodbyeRequest request);
}
