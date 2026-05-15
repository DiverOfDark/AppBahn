package eu.appbahn.operator.dev;

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
 * Builds the {@link KubernetesClient} from the dev-stack kubeconfig instead of
 * fabric8's auto-config (which would otherwise prefer in-cluster service account
 * credentials, then {@code ~/.kube/config}). JOSDK's auto-configuration declares its
 * client bean as {@code @ConditionalOnMissingBean(KubernetesClient.class)} so this
 * bean wins automatically when the {@code dev} profile is active.
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
