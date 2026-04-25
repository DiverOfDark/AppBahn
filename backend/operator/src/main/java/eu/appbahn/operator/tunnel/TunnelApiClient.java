package eu.appbahn.operator.tunnel;

import eu.appbahn.operator.tunnel.client.ApiException;
import eu.appbahn.operator.tunnel.client.model.AckCommandRequest;
import eu.appbahn.operator.tunnel.client.model.GoodbyeRequest;
import eu.appbahn.operator.tunnel.client.model.PushEventsAck;
import eu.appbahn.operator.tunnel.client.model.PushEventsRequest;
import eu.appbahn.operator.tunnel.client.model.RegisterClusterAck;
import eu.appbahn.operator.tunnel.client.model.RegisterClusterRequest;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.springframework.stereotype.Service;

/**
 * Thin facade over the openapi-generator-produced
 * {@link eu.appbahn.operator.tunnel.client.api.TunnelApi}: mints the bearer JWT once per call
 * and rewraps {@link ApiException} as an unchecked {@link UncheckedIOException} so callers
 * don't have to catch it explicitly. Everything except {@link #registerCluster} sends the
 * operator's bearer JWT — the platform rejects authenticated endpoints without it.
 */
@Service
public class TunnelApiClient {

    private static final String BEARER_PREFIX = "Bearer ";

    private final eu.appbahn.operator.tunnel.client.api.TunnelApi generated;
    private final OperatorJwtMinter jwtMinter;

    public TunnelApiClient(eu.appbahn.operator.tunnel.client.api.TunnelApi generated, OperatorJwtMinter jwtMinter) {
        this.generated = generated;
        this.jwtMinter = jwtMinter;
    }

    public RegisterClusterAck registerCluster(RegisterClusterRequest request) {
        try {
            return generated.registerCluster(request);
        } catch (ApiException e) {
            throw unchecked(e);
        }
    }

    public PushEventsAck pushEvents(PushEventsRequest request) {
        try {
            return generated.pushEvents(request, bearer());
        } catch (ApiException e) {
            throw unchecked(e);
        }
    }

    public void ackCommand(String correlationId, AckCommandRequest request) {
        try {
            generated.ackCommand(correlationId, request, bearer());
        } catch (ApiException e) {
            throw unchecked(e);
        }
    }

    public void goodbye(GoodbyeRequest request) {
        try {
            generated.goodbye(request, bearer());
        } catch (ApiException e) {
            throw unchecked(e);
        }
    }

    private String bearer() {
        return BEARER_PREFIX + jwtMinter.mint();
    }

    private static UncheckedIOException unchecked(ApiException e) {
        return new UncheckedIOException(
                new IOException("tunnel RPC failed with status " + e.getCode() + ": " + e.getMessage(), e));
    }
}
