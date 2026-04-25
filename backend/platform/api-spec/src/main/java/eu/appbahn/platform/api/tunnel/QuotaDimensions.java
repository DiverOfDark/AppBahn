package eu.appbahn.platform.api.tunnel;

import lombok.Data;

/**
 * Five-dimension quota snapshot, used for both caps (limits side) and aggregated usage
 * (current side). CPU encoded in millicores (1 core = 1000 millicores) so the wire type
 * stays integral.
 */
@Data
public class QuotaDimensions {

    private int resources;
    private int cpuMillicores;
    private int memoryMb;
    private int storageGb;
    private int replicas;
}
