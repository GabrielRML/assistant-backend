package com.assistent.api.document;

import com.assistent.api.document.dto.DocumentCreateRequest;
import com.assistent.api.document.dto.DocumentResponse;
import com.assistent.api.document.dto.DocumentUploadResponse;
import com.assistent.api.document.dto.DownloadUrlResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
@Tag(name = "Documentos")
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping
    @Operation(summary = "Registra um documento e devolve a política de upload pré-assinada")
    public ResponseEntity<DocumentUploadResponse> create(@Valid @RequestBody DocumentCreateRequest request) {
        DocumentUploadResponse response = documentService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/documents/" + response.document().id())).body(response);
    }

    @PostMapping("/{id}/complete")
    @Operation(summary = "Confirma o upload e dispara a ingestão")
    public ResponseEntity<DocumentResponse> complete(@PathVariable UUID id) {
        return ResponseEntity.accepted().body(documentService.complete(id));
    }

    @GetMapping
    @Operation(summary = "Lista os documentos do workspace")
    public PagedModel<DocumentResponse> list(@RequestParam(required = false) DocumentStatus status,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return new PagedModel<>(documentService.list(status, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalha um documento")
    public DocumentResponse get(@PathVariable UUID id) {
        return documentService.get(id);
    }

    @GetMapping("/{id}/download-url")
    @Operation(summary = "Gera uma URL pré-assinada de download")
    public DownloadUrlResponse downloadUrl(@PathVariable UUID id) {
        return documentService.downloadUrl(id);
    }

    @PostMapping("/{id}/reprocess")
    @Operation(summary = "Reprocessa um documento READY ou FAILED")
    public ResponseEntity<DocumentResponse> reprocess(@PathVariable UUID id) {
        return ResponseEntity.accepted().body(documentService.reprocess(id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Exclui um documento e seus chunks")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        documentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
