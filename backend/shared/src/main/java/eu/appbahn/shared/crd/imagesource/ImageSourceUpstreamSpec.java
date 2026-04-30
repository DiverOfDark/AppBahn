package eu.appbahn.shared.crd.imagesource;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * Coordinates of the upstream {@link ImageSourceCrd} that a downstream
 * {@code type: imageSource} promotion follows. {@code cluster} may equal the operator's own
 * cluster — in that case the operator reads the upstream CR directly via the K8s client; in
 * the cross-cluster case, the platform brokers digest changes via {@code ApplyResourceBundle}.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ImageSourceUpstreamSpec {
    private String cluster;
    private String namespace;
    private String name;
}
