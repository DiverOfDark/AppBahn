package eu.appbahn.platform.tunnel.cluster;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "cluster_session")
public class ClusterSessionEntity {

    @Id
    @Column(name = "cluster_name", length = 63)
    private String clusterName;

    @Column(name = "subscribing_replica_id", nullable = false, length = 64)
    private String subscribingReplicaId;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "operator_instance_id", nullable = false)
    private UUID operatorInstanceId;

    @Column(name = "connected_at", nullable = false)
    private Instant connectedAt;
}
