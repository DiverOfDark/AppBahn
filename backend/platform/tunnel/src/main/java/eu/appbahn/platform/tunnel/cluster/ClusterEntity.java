package eu.appbahn.platform.tunnel.cluster;

import eu.appbahn.shared.crd.ClusterConfig;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(name = "cluster")
public class ClusterEntity {

    @Id
    @Column(length = 63)
    private String name;

    private String description;

    @Column(name = "public_key", columnDefinition = "TEXT")
    private String publicKey;

    @Column(name = "public_key_fingerprint", length = 64)
    private String publicKeyFingerprint;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ClusterStatus status = ClusterStatus.PENDING;

    @Column(name = "operator_version", length = 64)
    private String operatorVersion;

    @Column(name = "operator_instance_id")
    private UUID operatorInstanceId;

    @Column(name = "last_heartbeat_at")
    private Instant lastHeartbeatAt;

    @Column(name = "connected_replica_id", length = 64)
    private String connectedReplicaId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "last_admission_miss_at")
    private Instant lastAdmissionMissAt;

    /**
     * Admin-managed per-cluster configuration (node-pool catalogue, …). Typed POJO persisted as
     * JSONB so the operator + SPA can evolve the shape without a schema migration.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config", columnDefinition = "jsonb")
    private ClusterConfig config;
}
