package eu.appbahn.operator.reconciler;

import eu.appbahn.shared.Labels;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "operator")
public class OperatorConfig {

    private final double resourceRequestFraction;
    private final String ingressClassName;
    private final String clusterName;
    private final String clusterIssuer;
    private final Security security;

    public OperatorConfig(
            @DefaultValue("0.25") double resourceRequestFraction,
            String ingressClassName,
            @DefaultValue(Labels.DEFAULT_CLUSTER_NAME) String clusterName,
            String clusterIssuer,
            @DefaultValue Security security) {
        this.resourceRequestFraction = resourceRequestFraction;
        this.ingressClassName = ingressClassName;
        this.clusterName = clusterName;
        this.clusterIssuer = (clusterIssuer != null && !clusterIssuer.isBlank()) ? clusterIssuer : null;
        this.security = security;
    }

    public double getRequestFraction() {
        return resourceRequestFraction;
    }

    public String getIngressClassName() {
        return ingressClassName;
    }

    public String getClusterName() {
        return clusterName;
    }

    public String getClusterIssuer() {
        return clusterIssuer;
    }

    public Security getSecurity() {
        return security;
    }

    public record Security(
            @DefaultValue("true") boolean runAsNonRoot,
            @DefaultValue("true") boolean readOnlyRootFilesystem,
            @DefaultValue("false") boolean allowPrivilegeEscalation,
            @DefaultValue("ALL") List<String> dropCapabilities) {}
}
