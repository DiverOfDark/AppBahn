package eu.appbahn.operator.auth;

import eu.appbahn.operator.client.ApiClient;
import eu.appbahn.operator.client.api.ResourceSyncApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

@Configuration
public class OAuth2ClientConfig {

    @Bean
    public OAuth2AuthorizedClientManager authorizedClientManager(
            ClientRegistrationRepository clientRegistrationRepository,
            OAuth2AuthorizedClientService authorizedClientService
    ) {
        var clientProvider = OAuth2AuthorizedClientProviderBuilder.builder()
                .clientCredentials()
                .build();
        var manager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(
                clientRegistrationRepository, authorizedClientService);
        manager.setAuthorizedClientProvider(clientProvider);
        return manager;
    }

    @Bean
    public ResourceSyncApi resourceSyncApi(
            OAuth2AuthorizedClientManager clientManager,
            @Value("${platform.api.base-url:http://localhost:8080}") String baseUrl
    ) {
        var apiClient = new ApiClient();
        apiClient.updateBaseUri(baseUrl + "/api/v1/internal");
        apiClient.setRequestInterceptor(builder -> {
            var request = OAuth2AuthorizeRequest
                    .withClientRegistrationId("appbahn")
                    .principal("operator")
                    .build();
            var authorized = clientManager.authorize(request);
            if (authorized != null && authorized.getAccessToken() != null) {
                builder.header("Authorization", "Bearer " + authorized.getAccessToken().getTokenValue());
            }
        });
        return new ResourceSyncApi(apiClient);
    }
}
