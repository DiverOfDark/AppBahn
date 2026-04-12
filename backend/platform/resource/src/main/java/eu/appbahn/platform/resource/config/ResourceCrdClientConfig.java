package eu.appbahn.platform.resource.config;

import eu.appbahn.platform.resource.service.KubernetesResourceCrdClient;
import eu.appbahn.platform.resource.service.NoOpResourceCrdClient;
import eu.appbahn.platform.resource.service.ResourceCrdClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ResourceCrdClientConfig {

    @Bean
    @ConditionalOnProperty(name = "platform.kubernetes.enabled", matchIfMissing = true)
    public ResourceCrdClient kubernetesResourceCrdClient(KubernetesClient kubernetesClient) {
        return new KubernetesResourceCrdClient(kubernetesClient);
    }

    @Bean
    @ConditionalOnMissingBean(ResourceCrdClient.class)
    public ResourceCrdClient noOpResourceCrdClient() {
        return new NoOpResourceCrdClient();
    }
}
