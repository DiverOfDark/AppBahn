package eu.appbahn.platform.common.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(name = "idempotency_record")
@IdClass(IdempotencyRecordEntity.IdempotencyRecordId.class)
public class IdempotencyRecordEntity {

    @Id
    @Column(name = "idempotency_key", nullable = false, length = 255)
    private String idempotencyKey;

    @Id
    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Column(name = "request_method", nullable = false, length = 10)
    private String requestMethod;

    @Column(name = "request_path", nullable = false, length = 2048)
    private String requestPath;

    @Column(name = "request_hash", nullable = false)
    private byte[] requestHash;

    @Column(name = "response_status", nullable = false)
    private short responseStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_headers", nullable = false, columnDefinition = "jsonb")
    private Map<String, String> responseHeaders;

    @Column(name = "response_body")
    private byte[] responseBody;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Data
    public static class IdempotencyRecordId implements Serializable {
        private String idempotencyKey;
        private UUID actorId;
    }
}
