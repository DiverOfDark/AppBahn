package eu.appbahn.operator.tunnel;

import eu.appbahn.operator.tunnel.client.model.SubscribeCommandsRequest;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Service;

/**
 * OkHttp-backed SSE reader for the tunnel's only server-streaming endpoint
 * ({@code /api/tunnel/v1/commands}). Unary endpoints are typed via {@link TunnelApiClient}.
 */
@Service
public class OperatorTunnelClient {

    private final OkHttpClient http;
    private final OperatorJwtMinter jwtMinter;
    private final OperatorTunnelConfig config;

    public OperatorTunnelClient(OperatorJwtMinter jwtMinter, OperatorTunnelConfig config) {
        this.jwtMinter = jwtMinter;
        this.config = config;
        // Long read timeout so the SSE stream can sit idle between keepalives without the
        // socket being torn down. Heartbeat frames keep proxies happy; we exit on any read
        // failure and let SubscribeCommandsBootstrap reconnect.
        this.http = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .build();
    }

    /**
     * Opens the SSE {@code /api/tunnel/v1/commands} stream. Blocks while reading frames and
     * dispatches each one to {@code handler}. Returns when the stream closes cleanly; throws
     * on transport error so the caller can decide whether to reconnect.
     */
    public void subscribe(SubscribeCommandsRequest request, SseHandler handler) throws IOException {
        StringBuilder qs = new StringBuilder("?clusterName=")
                .append(URLEncoder.encode(request.getClusterName(), StandardCharsets.UTF_8));
        if (request.getOperatorInstanceId() != null) {
            qs.append("&operatorInstanceId=")
                    .append(URLEncoder.encode(request.getOperatorInstanceId(), StandardCharsets.UTF_8));
        }
        if (request.getOperatorVersion() != null) {
            qs.append("&operatorVersion=")
                    .append(URLEncoder.encode(request.getOperatorVersion(), StandardCharsets.UTF_8));
        }
        qs.append("&lastAdminConfigRevision=").append(request.getLastAdminConfigRevision());
        qs.append("&lastQuotaRbacRevision=").append(request.getLastQuotaRbacRevision());

        Request httpReq = new Request.Builder()
                .url(config.platformBaseUrl() + "/api/tunnel/v1/commands" + qs)
                .header("Authorization", "Bearer " + jwtMinter.mint())
                .header("Accept", "text/event-stream")
                .get()
                .build();

        try (Response response = http.newCall(httpReq).execute()) {
            if (response.code() < 200 || response.code() >= 300) {
                String body = response.body() != null ? response.body().string() : "";
                throw new IOException("SSE subscribe failed with status " + response.code() + ": " + body);
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("SSE response has no body");
            }
            readSse(body, handler);
        }
    }

    /**
     * Minimal SSE parser: blank-line-delimited frames; only {@code event:} and {@code data:}
     * fields are interpreted. Multi-line data is concatenated with a newline per spec.
     */
    private void readSse(ResponseBody body, SseHandler handler) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(body.byteStream(), StandardCharsets.UTF_8));
        String eventName = null;
        StringBuilder data = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty()) {
                if (eventName != null || data.length() > 0) {
                    handler.onFrame(eventName != null ? eventName : "message", data.toString());
                }
                eventName = null;
                data.setLength(0);
                continue;
            }
            if (line.startsWith(":")) {
                continue; // SSE comment (e.g. keepalive prelude)
            }
            if (line.startsWith("event:")) {
                eventName = line.substring("event:".length()).trim();
            } else if (line.startsWith("data:")) {
                if (data.length() > 0) data.append('\n');
                data.append(line.substring("data:".length()).stripLeading());
            }
        }
    }

    /** Consumer of one SSE frame: {@code event:} name and {@code data:} JSON body. */
    @FunctionalInterface
    public interface SseHandler {
        void onFrame(String event, String data);
    }
}
