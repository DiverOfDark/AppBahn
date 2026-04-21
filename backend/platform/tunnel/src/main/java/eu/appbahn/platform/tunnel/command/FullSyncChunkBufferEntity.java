package eu.appbahn.platform.tunnel.command;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "full_sync_chunk_buffer")
public class FullSyncChunkBufferEntity {

    @EmbeddedId
    private Pk id;

    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] payload;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Embeddable
    @NoArgsConstructor
    @Getter
    @Setter
    public static class Pk implements Serializable {

        @Column(name = "cluster_name", nullable = false, length = 63)
        private String clusterName;

        @Column(name = "sync_session_id", nullable = false)
        private UUID syncSessionId;

        @Column(name = "chunk_index", nullable = false)
        private int chunkIndex;

        public Pk(String clusterName, UUID syncSessionId, int chunkIndex) {
            this.clusterName = clusterName;
            this.syncSessionId = syncSessionId;
            this.chunkIndex = chunkIndex;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Pk pk)) return false;
            return chunkIndex == pk.chunkIndex
                    && Objects.equals(clusterName, pk.clusterName)
                    && Objects.equals(syncSessionId, pk.syncSessionId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(clusterName, syncSessionId, chunkIndex);
        }
    }
}
