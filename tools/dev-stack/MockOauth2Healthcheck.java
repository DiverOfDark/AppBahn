// Tiny HTTP probe used as mock-oauth2's docker healthcheck.
//
// The mock-oauth2-server image is distroless (jib-based, only the JRE
// binary is present), so wget/curl/nc aren't available. Java 11+ single-
// file source-mode lets us run this file directly as a script.
//
// Exit 0 on HTTP 200, exit 1 otherwise.
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class MockOauth2Healthcheck {
    public static void main(String[] args) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8081/default/.well-known/openid-configuration"))
                .timeout(Duration.ofSeconds(2))
                .GET()
                .build();
        HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
        System.exit(response.statusCode() == 200 ? 0 : 1);
    }
}
