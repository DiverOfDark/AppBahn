package eu.appbahn.platform.dev;

import eu.appbahn.shared.dev.DevKubeconfigResolver;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Builds the platform's {@link KubernetesClient} from the dev-stack kubeconfig. Wins over
 * the production {@link eu.appbahn.platform.common.kubernetes.KubernetesClientConfig#kubernetesClient()}
 * via {@code @ConditionalOnMissingBean}.
 */
@Configuration
@Profile("dev")
public class DevKubernetesClientConfig {

    private static final Logger LOG = LoggerFactory.getLogger(DevKubernetesClientConfig.class);

    @Bean
    public KubernetesClient kubernetesClient() throws IOException {
        LOG.info("Building dev KubernetesClient from {}", DevKubeconfigResolver.resolvePath());
        Config config = DevKubeconfigResolver.resolveConfig();
        return new KubernetesClientBuilder().withConfig(config).build();
    }
}
