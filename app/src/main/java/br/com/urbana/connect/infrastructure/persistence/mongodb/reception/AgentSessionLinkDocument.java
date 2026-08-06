package br.com.urbana.connect.infrastructure.persistence.mongodb.reception;

import br.com.urbana.connect.domain.reception.model.SessionLinkStatus;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** One document per contact; prior Hermes sessions remain embedded lineage. */
@Data
@Document(collection = "reception_agent_session_links")
public class AgentSessionLinkDocument {
    @Id
    private String contactId;
    @Indexed
    private String hermesSessionId;
    private SessionLinkStatus status;
    private Instant createdAt;
    private Instant lastUsedAt;
    private String replacedBySessionId;
    private long version;
    private List<SessionLineageDocument> lineage = new ArrayList<>();

    @Data
    public static class SessionLineageDocument {
        private String hermesSessionId;
        private SessionLinkStatus status;
        private Instant createdAt;
        private Instant lastUsedAt;
        private String replacedBySessionId;
        private long version;

        public SessionLineageDocument() {
        }

        public SessionLineageDocument(String hermesSessionId, SessionLinkStatus status, Instant createdAt,
                                      Instant lastUsedAt, String replacedBySessionId, long version) {
            this.hermesSessionId = hermesSessionId;
            this.status = status;
            this.createdAt = createdAt;
            this.lastUsedAt = lastUsedAt;
            this.replacedBySessionId = replacedBySessionId;
            this.version = version;
        }
    }
}
