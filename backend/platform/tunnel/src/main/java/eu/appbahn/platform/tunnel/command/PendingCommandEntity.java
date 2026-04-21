package eu.appbahn.platform.tunnel.command;

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
@Table(name = "pending_command")
public class PendingCommandEntity {

    @Id
    private UUID id;

    @Column(name = "cluster_name", nullable = false, length = 63)
    private String clusterName;

    @Column(name = "correlation_id", nullable = false, unique = true)
    private UUID correlationId;

    @Column(name = "command_type", nullable = false, length = 32)
    private String commandType;

    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] payload;

    @Column(name = "enqueued_at", nullable = false)
    private Instant enqueuedAt;

    @Column(name = "claimed_by_replica", length = 64)
    private String claimedByReplica;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "acked_at")
    private Instant ackedAt;

    @Column(name = "response_status", length = 32)
    private String responseStatus;

    @Column(name = "response_message")
    private String responseMessage;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}
