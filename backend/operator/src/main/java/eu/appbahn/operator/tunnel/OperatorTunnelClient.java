package eu.appbahn.operator.tunnel;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import eu.appbahn.tunnel.v1.PlatformMessage;
import eu.appbahn.tunnel.v1.SubscribeCommandsRequest;
import eu.appbahn.tunnel.wire.Envelope;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Service;

/**
 * OkHttp-backed Connect client for the operator side of the tunnel. Unary RPCs
 * are straight HTTPS POSTs with a JWT-bearing Authorization header and a JSON
 * body. SubscribeCommands (server-stream) is wired in a later phase.
 */
@Service
public class OperatorTunnelClient {

    private static final MediaType JSON = MediaType.parse("application/json");
    private static final String BASE_PATH = "/appbahn.tunnel.v1.OperatorTunnel";

    private final OkHttpClient http;
    private final OperatorJwtMinter jwtMinter;
    private final OperatorTunnelConfig config;
    private final JsonFormat.Parser jsonParser = JsonFormat.parser().ignoringUnknownFields();
    private final JsonFormat.Printer jsonPrinter = JsonFormat.printer().omittingInsignificantWhitespace();

    public OperatorTunnelClient(OperatorJwtMinter jwtMinter, OperatorTunnelConfig config) {
        this.jwtMinter = jwtMinter;
        this.config = config;
        // A long read timeout for the streaming subscribe path — the server holds the
        // connection open indefinitely while waiting for commands. Keepalive is the
        // server's responsibility (heartbeat frames); we exit on any read failure.
        this.http = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .build();
    }

    public <B extends Message.Builder> Message unary(String rpc, Message request, B responseBuilder)
            throws IOException {
        return unary(rpc, request, responseBuilder, /* withAuth */ true);
    }

    /** Variant for the pre-registration {@code RegisterCluster} call: the platform doesn't yet
     * know the operator's public key, so it can't verify a JWT. All other RPCs send one. */
    public <B extends Message.Builder> Message unaryUnauthenticated(String rpc, Message request, B responseBuilder)
            throws IOException {
        return unary(rpc, request, responseBuilder, /* withAuth */ false);
    }

    private <B extends Message.Builder> Message unary(String rpc, Message request, B responseBuilder, boolean withAuth)
            throws IOException {
        String body = jsonPrinter.print(request);
        Request.Builder reqBuilder = new Request.Builder()
                .url(config.platformBaseUrl() + BASE_PATH + "/" + rpc)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(body, JSON));
        if (withAuth) {
            reqBuilder.header("Authorization", "Bearer " + jwtMinter.mint());
        }
        Request httpReq = reqBuilder.build();

        try (Response response = http.newCall(httpReq).execute()) {
            if (response.code() < 200 || response.code() >= 300) {
                throw new TunnelRpcException(
                        response.code(),
                        response.body() != null ? response.body().string() : "");
            }
            String responseBody = response.body() != null ? response.body().string() : "{}";
            try {
                jsonParser.merge(responseBody, responseBuilder);
                return responseBuilder.build();
            } catch (InvalidProtocolBufferException e) {
                throw new IOException("Malformed response JSON: " + responseBody, e);
            }
        }
    }

    /**
     * Opens the {@code SubscribeCommands} server-stream. Blocks on each {@link PlatformMessage}
     * frame and invokes {@code handler}. Returns when the stream ends cleanly; throws on transport
     * error so the caller can decide whether to reconnect.
     */
    public void subscribe(SubscribeCommandsRequest request, Consumer<PlatformMessage> handler) throws IOException {
        String body = jsonPrinter.print(request);
        Request httpReq = new Request.Builder()
                .url(config.platformBaseUrl() + BASE_PATH + "/SubscribeCommands")
                .header("Authorization", "Bearer " + jwtMinter.mint())
                .header("Content-Type", "application/json")
                .post(RequestBody.create(body, JSON))
                .build();

        try (Response response = http.newCall(httpReq).execute()) {
            if (response.code() < 200 || response.code() >= 300) {
                throw new TunnelRpcException(
                        response.code(),
                        response.body() != null ? response.body().string() : "");
            }
            InputStream in = response.body().byteStream();
            while (true) {
                Envelope.Frame frame = Envelope.read(in);
                if (frame == null) {
                    return;
                }
                if (frame.isEndStream()) {
                    return;
                }
                String json = new String(frame.payload(), StandardCharsets.UTF_8);
                PlatformMessage.Builder builder = PlatformMessage.newBuilder();
                try {
                    jsonParser.merge(json, builder);
                    handler.accept(builder.build());
                } catch (InvalidProtocolBufferException e) {
                    throw new IOException("Malformed PlatformMessage frame: " + json, e);
                }
            }
        }
    }

    public static class TunnelRpcException extends IOException {
        private final int httpStatus;

        public TunnelRpcException(int httpStatus, String body) {
            super("tunnel RPC failed with status " + httpStatus + ": " + body);
            this.httpStatus = httpStatus;
        }

        public int httpStatus() {
            return httpStatus;
        }
    }
}
