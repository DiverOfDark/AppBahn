package eu.appbahn.shared.crd;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * Typed pointer into the cluster's secret store: identifies a single property
 * ({@code key}) inside a named secret entry ({@code secretName}). Modeled after
 * Kubernetes' core/v1 {@code SecretKeySelector} so platform, operator, and CRDs
 * agree on the shape regardless of the backing store (ESO ClusterSecretStore,
 * vanilla K8s Secret, etc.).
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class CredentialRef {

    private String secretName;
    private String key;
}
