package com.assistent.api.conversation;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "message_source")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MessageSource {

    @EmbeddedId
    private MessageSourceId id;

    @MapsId("messageId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id")
    private Message message;

    private float score;

    public static MessageSource of(Message message, java.util.UUID chunkId, float score) {
        MessageSource source = new MessageSource();
        source.id = new MessageSourceId(message.getId(), chunkId);
        source.message = message;
        source.score = score;
        return source;
    }

    public java.util.UUID getChunkId() {
        return id.chunkId();
    }
}
