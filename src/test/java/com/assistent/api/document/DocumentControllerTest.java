package com.assistent.api.document;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.assistent.api.common.exception.BusinessException;
import com.assistent.api.common.exception.ConflictException;
import com.assistent.api.common.exception.ResourceNotFoundException;
import com.assistent.api.config.WebMvcTestConfig;
import com.assistent.api.document.dto.DocumentCreateRequest;
import com.assistent.api.document.dto.DocumentResponse;
import com.assistent.api.document.dto.DocumentUploadResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DocumentController.class)
@Import(WebMvcTestConfig.class)
@ActiveProfiles("test")
class DocumentControllerTest {

    static final UUID ID = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    static final String VALID_BODY = "{\"fileName\":\"manual.pdf\",\"mimeType\":\"application/pdf\",\"sizeBytes\":1000,\"sha256Hash\":\"%s\"}"
            .formatted("a".repeat(64));

    @Autowired MockMvc mockMvc;
    @MockitoBean DocumentService documentService;
    @MockitoBean JwtDecoder jwtDecoder;

    private DocumentResponse response(DocumentStatus status) {
        return new DocumentResponse(ID, "manual.pdf", "application/pdf", 1000L, status, null, "Ana", Instant.now(), null);
    }

    @Test
    void create_validRequest_returns201WithLocationAndPresignedPost() throws Exception {
        when(documentService.create(any(DocumentCreateRequest.class))).thenReturn(new DocumentUploadResponse(
                response(DocumentStatus.AWAITING_UPLOAD), "http://minio/bucket", Map.of("key", "k"), Instant.now()));

        mockMvc.perform(post("/api/v1/documents").with(jwt()).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/documents/" + ID))
                .andExpect(jsonPath("$.uploadUrl").value("http://minio/bucket"))
                .andExpect(jsonPath("$.formFields.key").value("k"));
    }

    @Test
    void create_invalidHash_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/documents").with(jwt()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"fileName":"a.pdf","mimeType":"application/pdf","sizeBytes":10,"sha256Hash":"XYZ"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("sha256Hash"));
    }

    @Test
    void create_duplicateHash_returns409WithExistingId() throws Exception {
        when(documentService.create(any(DocumentCreateRequest.class)))
                .thenThrow(new ConflictException("Este arquivo já foi enviado.", ID));

        mockMvc.perform(post("/api/v1/documents").with(jwt()).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.existingId").value(ID.toString()));
    }

    @Test
    void create_businessRuleViolated_returns422() throws Exception {
        when(documentService.create(any(DocumentCreateRequest.class))).thenThrow(new BusinessException("Tipo não permitido"));

        mockMvc.perform(post("/api/v1/documents").with(jwt()).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void complete_existingDocument_returns202() throws Exception {
        when(documentService.complete(ID)).thenReturn(response(DocumentStatus.PENDING));

        mockMvc.perform(post("/api/v1/documents/{id}/complete", ID).with(jwt()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void get_documentOfAnotherWorkspace_returns404() throws Exception {
        when(documentService.get(ID)).thenThrow(new ResourceNotFoundException("Documento não encontrado."));

        mockMvc.perform(get("/api/v1/documents/{id}", ID).with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Documento não encontrado."));
    }

    @Test
    void list_withStatusFilter_returnsPagedModel() throws Exception {
        when(documentService.list(org.mockito.ArgumentMatchers.eq(DocumentStatus.READY), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(response(DocumentStatus.READY))));

        mockMvc.perform(get("/api/v1/documents").param("status", "READY").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("READY"))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void delete_existingDocument_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/documents/{id}", ID).with(jwt())).andExpect(status().isNoContent());
    }

    @Test
    void delete_missingDocument_returns404() throws Exception {
        doThrow(new ResourceNotFoundException("Documento não encontrado.")).when(documentService).delete(ID);

        mockMvc.perform(delete("/api/v1/documents/{id}", ID).with(jwt())).andExpect(status().isNotFound());
    }

    @Test
    void list_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/documents")).andExpect(status().isUnauthorized());
    }
}
