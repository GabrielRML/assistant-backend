package com.assistent.api.conversation;

import static org.assertj.core.api.Assertions.assertThat;

import com.assistent.api.PostgresTestSupport;
import com.assistent.api.config.JpaConfig;
import com.assistent.api.document.Document;
import com.assistent.api.document.DocumentStatus;
import com.assistent.api.user.AppUser;
import com.assistent.api.workspace.Workspace;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
@ActiveProfiles("test")
class MessageSourceRepositoryTest {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PostgresTestSupport.POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", PostgresTestSupport.POSTGRES::getUsername);
        registry.add("spring.datasource.password", PostgresTestSupport.POSTGRES::getPassword);
    }

    @Autowired MessageSourceRepository messageSourceRepository;
    @Autowired MessageRepository messageRepository;
    @Autowired ConversationRepository conversationRepository;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;

    private Workspace workspace(String name) {
        Workspace workspace = Workspace.create(name);
        em.persist(workspace);
        return workspace;
    }

    private UUID chunk(Workspace workspace, Document document, String content) {
        UUID id = UUID.randomUUID();
        String vector = "[" + String.join(",", java.util.Collections.nCopies(384, "0.1")) + "]";
        jdbc.update("insert into rag.chunk (id, document_id, workspace_id, chunk_index, content, embedding) "
                + "values (?, ?, ?, 0, ?, ?::vector)", id, document.getId(), workspace.getId(), content, vector);
        return id;
    }

    @Test
    void findChunkPreviews_onlyReturnsChunksOfTheGivenWorkspace() {
        Workspace a = workspace("A");
        Workspace b = workspace("B");
        AppUser ua = AppUser.create(a, "Ana", "a@a.com", "{bcrypt}h");
        em.persist(ua);
        Document doc = Document.create(a, ua, "tabela.pdf", "application/pdf", 10L, "1".repeat(64));
        doc.setStorageKey("k");
        doc.setStatus(DocumentStatus.READY);
        em.persist(doc);
        em.flush();
        UUID chunkId = chunk(a, doc, "Horário: 9h às 18h");

        List<ChunkPreview> visible = messageSourceRepository.findChunkPreviews(List.of(chunkId), a.getId());
        List<ChunkPreview> hidden = messageSourceRepository.findChunkPreviews(List.of(chunkId), b.getId());

        assertThat(visible).hasSize(1);
        assertThat(visible.get(0).getContent()).isEqualTo("Horário: 9h às 18h");
        assertThat(visible.get(0).getFileName()).isEqualTo("tabela.pdf");
        assertThat(visible.get(0).getDocumentId()).isEqualTo(doc.getId());
        assertThat(hidden).isEmpty();
    }

    @Test
    void save_messageSource_isReadBackByMessageId() {
        Workspace a = workspace("A");
        AppUser ua = AppUser.create(a, "Ana", "a@a.com", "{bcrypt}h");
        em.persist(ua);
        Document doc = Document.create(a, ua, "tabela.pdf", "application/pdf", 10L, "1".repeat(64));
        doc.setStorageKey("k");
        doc.setStatus(DocumentStatus.READY);
        em.persist(doc);
        Conversation conversation = conversationRepository.save(Conversation.forPlayground(a, ua));
        Message message = messageRepository.save(Message.of(conversation, MessageRole.ASSISTANT, "ok"));
        em.flush();
        UUID chunkId = chunk(a, doc, "conteúdo");

        messageSourceRepository.saveAndFlush(MessageSource.of(message, chunkId, 0.82f));
        em.clear();

        List<MessageSource> sources = messageSourceRepository.findByIdMessageId(message.getId());
        assertThat(sources).hasSize(1);
        assertThat(sources.get(0).getChunkId()).isEqualTo(chunkId);
        assertThat(sources.get(0).getScore()).isEqualTo(0.82f);
    }

}
