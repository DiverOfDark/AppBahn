package eu.appbahn.platform.resource.entity;

import java.io.Serializable;
import lombok.Data;

@Data
public class ResourceTypeAvailabilityId implements Serializable {

    private String type;
    private String clusterName;

    public ResourceTypeAvailabilityId() {}

    public ResourceTypeAvailabilityId(String type, String clusterName) {
        this.type = type;
        this.clusterName = clusterName;
    }
}
