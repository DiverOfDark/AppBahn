package eu.appbahn.operator.dev;

import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.Serialization;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Installs the AppBahn CRDs into the dev cluster on operator startup. The CRD YAMLs
 * are emitted at compile time by the fabric8 APT in {@code :shared} and packaged into
 * the shared JAR under {@code META-INF/fabric8/*-v1.yml} — same source the helm chart
 * pulls from in production.
 *
 * <p>Each CRD is sent via {@code serverSideApply()} with the operator as field manager,
 * so re-runs against an existing CRD reconcile the schema in place without churning the
 * resourceVersion of unrelated fields. The platform's {@code CrMigrationStartupSweep}
 * runs after this and will surface schema drift if any is left behind by an older dev
 * cluster.
 */
@Component
@Profile("dev")
public class DevCrdInstaller {

    private static final Logger LOG = LoggerFactory.getLogger(DevCrdInstaller.class);

    private static final List<String> CRD_RESOURCES = List.of(
            "META-INF/fabric8/resources.appbahn.eu-v1.yml",
            "META-INF/fabric8/imagesources.appbahn.eu-v1.yml",
            "META-INF/fabric8/resourcetypedefinitions.appbahn.eu-v1.yml");

    private final KubernetesClient kubernetesClient;
    private final boolean enabled;

    public DevCrdInstaller(
            KubernetesClient kubernetesClient, @Value("${operator.install-crds:false}") boolean enabled) {
        this.kubernetesClient = kubernetesClient;
        this.enabled = enabled;
    }

    @PostConstruct
    public void installCrds() {
        if (!enabled) {
            return;
        }
        for (String resource : CRD_RESOURCES) {
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource)) {
                if (in == null) {
                    LOG.warn("CRD resource {} missing on classpath — skipping", resource);
                    continue;
                }
                CustomResourceDefinition crd =
                        Serialization.unmarshal(new String(in.readAllBytes()), CustomResourceDefinition.class);
                kubernetesClient.resource(crd).serverSideApply();
                LOG.info("Applied CRD {}", crd.getMetadata().getName());
            } catch (IOException e) {
                throw new IllegalStateException("Failed to install CRD " + resource, e);
            }
        }
    }
}
