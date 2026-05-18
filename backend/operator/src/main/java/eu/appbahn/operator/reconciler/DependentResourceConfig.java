package eu.appbahn.operator.reconciler;

import eu.appbahn.operator.reconciler.imagesource.buildjob.BuildJobBuilder;
import eu.appbahn.operator.reconciler.imagesource.buildjob.BuildJobDependentResource;
import eu.appbahn.operator.tunnel.AdminConfigCache;
import io.javaoperatorsdk.operator.api.config.ConfigurationServiceOverrider;
import java.util.function.Consumer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

/** Wires JOSDK to resolve dependent resources via Spring so they can constructor-inject beans. */
@Configuration
public class DependentResourceConfig {

    @Bean
    public SpringDependentResourceFactory springDependentResourceFactory(ApplicationContext applicationContext) {
        return new SpringDependentResourceFactory(applicationContext);
    }

    @Bean
    public Consumer<ConfigurationServiceOverrider> dependentResourceFactoryOverrider(
            SpringDependentResourceFactory factory) {
        return overrider -> overrider.withDependentResourceFactory(factory);
    }

    // Prototype scope: JOSDK expects a fresh instance per workflow binding.

    @Bean
    @Scope("prototype")
    public DeploymentDependentResource deploymentDependentResource(
            OperatorConfig operatorConfig, AdminConfigCache adminConfigCache) {
        return new DeploymentDependentResource(operatorConfig, adminConfigCache);
    }

    @Bean
    @Scope("prototype")
    public IngressDependentResource ingressDependentResource(OperatorConfig operatorConfig) {
        return new IngressDependentResource(operatorConfig);
    }

    @Bean
    @Scope("prototype")
    public BuildJobDependentResource buildJobDependentResource(BuildJobBuilder jobBuilder) {
        return new BuildJobDependentResource(jobBuilder);
    }

    @Bean
    @Scope("prototype")
    public PdbDependentResource pdbDependentResource() {
        return new PdbDependentResource();
    }
}
