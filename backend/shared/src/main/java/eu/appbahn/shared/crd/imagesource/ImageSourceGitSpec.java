package eu.appbahn.shared.crd.imagesource;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * Git source coordinates. {@code credentialsSecretRef} names a Secret in the same namespace.
 * The Secret's K8s {@code type} field ({@code kubernetes.io/basic-auth} or
 * {@code kubernetes.io/ssh-auth}) is what the operator inspects to pick the right credential
 * provider — no separate auth-type field on the spec itself.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ImageSourceGitSpec {
    private String repo;
    private String branch;
    private String credentialsSecretRef;
}
