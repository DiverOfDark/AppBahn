package eu.appbahn.shared.crd;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * Pod toleration declaration mirrored from {@code corev1.Toleration}. Kept as a typed POJO on the
 * shared wire so {@link NodePool} can persist tolerations to PostgreSQL JSONB without dragging in
 * the fabric8 K8s model classes. The operator translates these into
 * {@link io.fabric8.kubernetes.api.model.Toleration} when building the pod template.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Toleration {

    private String key;
    private String operator;
    private String value;
    private String effect;
    private Long tolerationSeconds;
}
