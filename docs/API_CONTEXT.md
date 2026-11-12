# API_CONTEXT.md — Especificação da API Spring Boot

Fonte da verdade para construir o backend. Complementa o `CLAUDE.md` (convenções gerais).
Todos os nomes de tabelas, colunas, enums, classes e endpoints estão em inglês. Mensagens exibidas ao usuário ficam em português.

---

## 1. Dependências

Já instaladas: `data-jpa`, `flyway` (+ `flyway-database-postgresql`), `security`, `validation`, `webmvc`, `springdoc-openapi-starter-webmvc-ui`, `lombok`, `postgresql` e os starters de teste correspondentes.

Adicionar ao `build.gradle` (confira as versões mais recentes no Maven Central antes de fixar):

```gradle
ext {
    mapstructVersion = '1.6.3'
}

dependencies {
    // Mapeamento entidade <-> DTO
    implementation "org.mapstruct:mapstruct:${mapstructVersion}"
    annotationProcessor "org.mapstruct:mapstruct-processor:${mapstructVersion}"
    annotationProcessor 'org.projectlombok:lombok-mapstruct-binding:0.2.0'

    // JWT (emissão e validação com HMAC)
    implementation 'org.springframework.boot:spring-boot-starter-oauth2-resource-server'

    // Armazenamento de arquivos
    implementation 'io.minio:minio:8.5.17'

    // Testes de integração com Postgres real (pgvector)
    testImplementation 'org.springframework.boot:spring-boot-testcontainers'
    testImplementation 'org.testcontainers:junit-jupiter'
    testImplementation 'org.testcontainers:postgresql'
}
```

Ordem dos annotation processors: `lombok` deve vir antes de `lombok-mapstruct-binding` e `mapstruct-processor` (o binding garante que o MapStruct enxergue os getters/setters gerados pelo Lombok).

Adicione também ao `compileJava`, para mapeamentos sem surpresas:

```gradle
tasks.withType(JavaCompile).configureEach {
    options.compilerArgs += [
        '-Amapstruct.defaultComponentModel=spring',
        '-Amapstruct.unmappedTargetPolicy=ERROR'
    ]
}
```

---

## 2. Configuração (`application.yml`)

```yaml
spring:
  application:
    name: assistant-api
  threads:
    virtual:
      enabled: true
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/assistant}
    username: ${DB_USER:assistant}
    password: ${DB_PASSWORD:assistant}
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        default_schema: app
        jdbc:
          time_zone: UTC
  flyway:
    schemas: app,rag
    default-schema: app
    locations: classpath:db/migration
app:
  security:
    jwt-secret: ${JWT_SECRET}            # mínimo 256 bits, base64
    jwt-expiration: PT8H
  storage:
    endpoint: ${MINIO_ENDPOINT:http://localhost:9000}              # usado pelo Spring (rede interna)
    public-endpoint: ${MINIO_PUBLIC_ENDPOINT:http://localhost:9000} # usado para assinar as URLs entregues ao navegador
    access-key: ${MINIO_ACCESS_KEY}
    secret-key: ${MINIO_SECRET_KEY}
    bucket: ${MINIO_BUCKET:assistant}
    upload-url-expiration: PT15M
    download-url-expiration: PT5M
  rag:
    base-url: ${RAG_BASE_URL:http://localhost:8000}
    timeout: PT60S
  documents:
    max-size: 50MB
    pending-upload-ttl: PT24H   # documentos sem upload confirmado são removidos após esse prazo
    allowed-mime-types:
      - application/pdf
      - application/vnd.openxmlformats-officedocument.wordprocessingml.document
      - text/plain
      - text/markdown
```

Cada bloco `app.*` é lido por um `@ConfigurationProperties` em `config/` como `record` validado (`@Validated`), por exemplo `SecurityProperties`, `StorageProperties`, `RagProperties`, `DocumentProperties`.

---

## 3. Banco de dados — migration inicial

Arquivo `src/main/resources/db/migration/V1__initial_schema.sql`.

A dimensão do vetor depende do modelo de embeddings escolhido no serviço Python (atual: 384). Confirme antes de aplicar; mudar depois exige nova migration e reprocessamento dos documentos.

```sql
CREATE EXTENSION IF NOT EXISTS vector;
CREATE SCHEMA IF NOT EXISTS app;
CREATE SCHEMA IF NOT EXISTS rag;

-- ===== app =====

CREATE TABLE app.workspace (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name          varchar(120) NOT NULL,
    system_prompt text,
    created_at    timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE app.app_user (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id  uuid         NOT NULL REFERENCES app.workspace (id),
    name          varchar(120) NOT NULL,
    email         varchar(254) NOT NULL,
    password_hash varchar(100) NOT NULL,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ux_app_user_email UNIQUE (email)
);

CREATE TABLE app.document (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id  uuid         NOT NULL REFERENCES app.workspace (id),
    uploaded_by   uuid         NOT NULL REFERENCES app.app_user (id),
    file_name     varchar(255) NOT NULL,
    mime_type     varchar(150) NOT NULL,
    size_bytes    bigint       NOT NULL,
    storage_key   varchar(512) NOT NULL,
    sha256_hash   char(64)     NOT NULL,
    status        varchar(20)  NOT NULL
        CHECK (status IN ('AWAITING_UPLOAD', 'PENDING', 'PROCESSING', 'READY', 'FAILED')),
    error         text,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    processed_at  timestamptz,
    CONSTRAINT ux_document_hash UNIQUE (workspace_id, sha256_hash)
);
CREATE INDEX ix_document_workspace_created ON app.document (workspace_id, created_at DESC);
CREATE INDEX ix_document_awaiting_upload ON app.document (created_at) WHERE status = 'AWAITING_UPLOAD';

CREATE TABLE app.conversation (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id    uuid        NOT NULL REFERENCES app.workspace (id),
    user_id         uuid        NOT NULL REFERENCES app.app_user (id),
    last_message_at timestamptz,
    created_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_conversation_workspace_last ON app.conversation (workspace_id, last_message_at DESC);

CREATE TABLE app.message (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id uuid        NOT NULL REFERENCES app.conversation (id) ON DELETE CASCADE,
    role            varchar(20) NOT NULL
        CHECK (role IN ('USER', 'ASSISTANT')),
    content         text,
    input_tokens    integer,
    output_tokens   integer,
    latency_ms      integer,
    created_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_message_conversation_created ON app.message (conversation_id, created_at);

-- ===== rag (dados escritos apenas pelo serviço Python) =====

CREATE TABLE rag.chunk (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id   uuid         NOT NULL REFERENCES app.document (id) ON DELETE CASCADE,
    workspace_id  uuid         NOT NULL REFERENCES app.workspace (id),
    chunk_index   integer      NOT NULL,
    content       text         NOT NULL,
    embedding     vector(384) NOT NULL,
    token_count   integer,
    metadata      jsonb,
    created_at    timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX ix_chunk_workspace ON rag.chunk (workspace_id);
CREATE INDEX ix_chunk_document  ON rag.chunk (document_id);
CREATE INDEX ix_chunk_embedding ON rag.chunk USING hnsw (embedding vector_cosine_ops);

-- ===== app (depende de rag.chunk) =====

CREATE TABLE app.message_source (
    message_id uuid NOT NULL REFERENCES app.message (id) ON DELETE CASCADE,
    chunk_id   uuid NOT NULL REFERENCES rag.chunk (id) ON DELETE CASCADE,
    score      real NOT NULL,
    PRIMARY KEY (message_id, chunk_id)
);
```

Observações:
- Excluir um `document` remove seus chunks (cascade) e, com eles, os registros de `message_source` que os citavam. As mensagens continuam.
- O serviço Python precisa de um usuário de banco com escrita apenas em `rag` e leitura em `app.document`.
- Row Level Security pode ser adicionada numa migration futura; até lá, o isolamento é garantido pelos filtros de `workspace_id` no código.

---

## 4. Entidades e enums

Enums (pacote da feature correspondente):

| Enum | Valores |
| --- | --- |
| `DocumentStatus` | `AWAITING_UPLOAD`, `PENDING`, `PROCESSING`, `READY`, `FAILED` |
| `MessageRole` | `USER`, `ASSISTANT` |

Entidades JPA (schema `app`):

| Entidade | Tabela | Relacionamentos (`LAZY`) |
| --- | --- | --- |
| `Workspace` | `workspace` | — |
| `AppUser` | `app_user` | `workspace` |
| `Document` | `document` | `workspace`, `uploadedBy` (`AppUser`) |
| `Conversation` | `conversation` | `workspace`, `user` (`AppUser`) |
| `Message` | `message` | `conversation` |
| `MessageSource` | `message_source` | `message`; `chunkId` como `UUID` simples (sem entidade `Chunk`) |

Regras:
- `MessageSource` usa chave composta com `@EmbeddedId` (`MessageSourceId(messageId, chunkId)`).
- Não existe entidade `Chunk`. Para exibir o trecho de uma fonte, use consulta nativa em `rag.chunk` retornando uma projeção (`interface ChunkPreview { UUID getId(); String getContent(); UUID getDocumentId(); String getFileName(); }`).
- Comportamento de domínio fica na entidade quando é simples e local, por exemplo `document.markProcessing()`, `document.markReady(Instant)`, `document.markFailed(String)`, `conversation.registerMessage(Instant)`.

Exemplo de padrão de entidade:

```java
@Entity
@Table(name = "document")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id")
    private Workspace workspace;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_by")
    private AppUser uploadedBy;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false)
    private String mimeType;

    @Column(nullable = false)
    private Long sizeBytes;

    @Column(nullable = false)
    private String storageKey;

    @Column(name = "sha256_hash", nullable = false, length = 64)
    private String sha256Hash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentStatus status;

    private String error;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant processedAt;

    public void markUploaded() {
        this.status = DocumentStatus.PENDING;
    }

    public void markProcessing() {
        this.status = DocumentStatus.PROCESSING;
    }

    public void markReady(Instant processedAt) {
        this.status = DocumentStatus.READY;
        this.error = null;
        this.processedAt = processedAt;
    }

    public void markFailed(String error) {
        this.status = DocumentStatus.FAILED;
        this.error = error;
    }
}
```

---

## 5. Padrões de DTO e mapper

```java
// common/mapper/CentralMapperConfig.java
@MapperConfig(
    componentModel = MappingConstants.ComponentModel.SPRING,
    unmappedTargetPolicy = ReportingPolicy.ERROR,
    injectionStrategy = InjectionStrategy.CONSTRUCTOR
)
public interface CentralMapperConfig {}
```

```java
// document/dto/DocumentResponse.java
public record DocumentResponse(
    UUID id,
    String fileName,
    String mimeType,
    Long sizeBytes,
    DocumentStatus status,
    String error,
    String uploadedByName,
    Instant createdAt,
    Instant processedAt
) {}
```

```java
// document/DocumentMapper.java
@Mapper(config = CentralMapperConfig.class)
public interface DocumentMapper {

    @Mapping(target = "uploadedByName", source = "uploadedBy.name")
    DocumentResponse toResponse(Document document);
}
```

Entidades são criadas no service (ou por um método de fábrica estático na entidade) a partir do request. Use o mapper `toEntity` apenas quando o request mapear diretamente para campos da entidade, sempre ignorando explicitamente `id`, `workspace`, datas e status.

---

## 6. Endpoints

Todos os endpoints, exceto os marcados como públicos, exigem `Authorization: Bearer <jwt>`. O workspace é sempre o do usuário autenticado.

### 6.1 Auth (`auth/`) — público

| Método | Rota | Request | Response | Status |
| --- | --- | --- | --- | --- |
| POST | `/api/v1/auth/register` | `RegisterRequest(workspaceName, name, email, password)` | `AuthResponse` | 201 |
| POST | `/api/v1/auth/login` | `LoginRequest(email, password)` | `AuthResponse` | 200 |

`AuthResponse(String accessToken, String tokenType, long expiresIn, UserResponse user)`.

`register` cria o workspace e o primeiro usuário na mesma transação. Serve para os testes iniciais; em produção pode ser desabilitado por propriedade.

### 6.2 Usuários (`user/`)

| Método | Rota | Request | Response | Status |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/users/me` | — | `UserResponse` | 200 |
| GET | `/api/v1/users` | `Pageable` | `PagedModel<UserResponse>` | 200 |
| POST | `/api/v1/users` | `UserCreateRequest(name, email, password)` | `UserResponse` | 201 |
| DELETE | `/api/v1/users/{id}` | — | — | 204 |

`UserResponse(UUID id, String name, String email, Instant createdAt)`. Sem papéis por enquanto: todo usuário tem acesso total ao seu workspace. Não permitir excluir o próprio usuário.

### 6.3 Workspace (`workspace/`)

| Método | Rota | Request | Response | Status |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/workspace` | — | `WorkspaceResponse` | 200 |
| PUT | `/api/v1/workspace` | `WorkspaceUpdateRequest(name, systemPrompt)` | `WorkspaceResponse` | 200 |

`systemPrompt` é enviado ao serviço Python em cada consulta e define o tom e os limites do assistente.

### 6.4 Documentos (`document/`)

Os arquivos **nunca passam pelo servidor**. O Spring registra os metadados e gera URLs pré-assinadas; o navegador envia e baixa o arquivo direto do MinIO. Não use `MultipartFile` para documentos.

| Método | Rota | Request | Response | Status |
| --- | --- | --- | --- | --- |
| POST | `/api/v1/documents` | `DocumentCreateRequest` | `DocumentUploadResponse` | 201 |
| POST | `/api/v1/documents/{id}/complete` | — | `DocumentResponse` | 202 |
| GET | `/api/v1/documents` | `status?`, `Pageable` | `PagedModel<DocumentResponse>` | 200 |
| GET | `/api/v1/documents/{id}` | — | `DocumentResponse` | 200 |
| GET | `/api/v1/documents/{id}/download-url` | — | `DownloadUrlResponse` | 200 |
| POST | `/api/v1/documents/{id}/reprocess` | — | `DocumentResponse` | 202 |
| DELETE | `/api/v1/documents/{id}` | — | — | 204 |

```java
public record DocumentCreateRequest(
    @NotBlank @Size(max = 255) String fileName,
    @NotBlank String mimeType,
    @NotNull @Positive Long sizeBytes,
    @NotBlank @Pattern(regexp = "^[a-f0-9]{64}$") String sha256Hash
) {}

public record DocumentUploadResponse(
    DocumentResponse document,
    String uploadUrl,               // URL do bucket para o POST do formulário
    Map<String, String> formFields, // campos da política, enviados antes do campo "file"
    Instant expiresAt
) {}

public record DownloadUrlResponse(String url, Instant expiresAt) {}
```

Fluxo de upload:
1. O navegador calcula o SHA-256 do arquivo (`crypto.subtle.digest`) e chama `POST /api/v1/documents` com nome, tipo, tamanho e hash.
2. O Spring valida `mimeType` em `app.documents.allowed-mime-types` e `sizeBytes` ≤ `app.documents.max-size` (senão 422). Se o hash já existir no workspace → 409 com o id do documento existente.
3. O Spring persiste o `Document` com status `AWAITING_UPLOAD`, `storage_key = workspaces/{workspaceId}/documents/{documentId}/{fileName sanitizado}`, e gera uma **política de POST pré-assinada** (`PostPolicy` do cliente MinIO) restrita a essa chave exata, ao `Content-Type` declarado e a um `content-length-range` com o tamanho máximo. Use POST policy em vez de PUT porque ela faz o MinIO recusar arquivos maiores que o limite.
4. O navegador envia o arquivo direto ao MinIO (`multipart/form-data` com os `formFields` e o campo `file` por último).
5. O navegador chama `POST /api/v1/documents/{id}/complete`. O Spring faz `statObject` no MinIO e confere se o objeto existe e se tamanho e tipo batem com o declarado. Se não bater → 422, remove o objeto e mantém `AWAITING_UPLOAD`. Se bater → status `PENDING`.
6. A integridade do conteúdo (hash declarado × hash real) é verificada pelo worker Python, que já lê o arquivo inteiro na ingestão. Divergência → documento `FAILED`.

Regras adicionais:
- `complete` só é aceito com status `AWAITING_UPLOAD` e é idempotente: chamar de novo um documento já `PENDING` ou além devolve o estado atual.
- Um job agendado (`@Scheduled`) remove documentos em `AWAITING_UPLOAD` mais antigos que `app.documents.pending-upload-ttl`, junto com o objeto no MinIO, se existir.
- `download-url` gera uma URL de `GET` pré-assinada com validade `app.storage.download-url-expiration` e `response-content-disposition` com o nome original do arquivo.
- `reprocess` só é permitido com status `FAILED` ou `READY`: volta para `PENDING`.
- `DELETE` remove o registro (os chunks caem por cascade) e o objeto do MinIO após o commit.
- As URLs são assinadas com `app.storage.public-endpoint` (o endereço que o navegador alcança), não com o endpoint interno.

### 6.5 Playground (`conversation/`)

| Método | Rota | Request | Response | Status |
| --- | --- | --- | --- | --- |
| POST | `/api/v1/playground/conversations` | — | `ConversationResponse` | 201 |
| GET | `/api/v1/playground/conversations` | `Pageable` | `PagedModel<ConversationSummaryResponse>` | 200 |
| GET | `/api/v1/playground/conversations/{id}/messages` | `Pageable` | `PagedModel<MessageResponse>` | 200 |
| POST | `/api/v1/playground/conversations/{id}/messages` | `ChatMessageRequest(content)` | `text/event-stream` | 200 |
| DELETE | `/api/v1/playground/conversations/{id}` | — | — | 204 |

O envio de mensagem no playground:
1. Persiste a mensagem `USER` e atualiza `last_message_at`.
2. Abre um `SseEmitter` e, numa thread virtual, chama `POST /query` no Python com `stream=true`.
3. Repassa ao navegador os eventos `token`, `done` e `error` (formato na seção 8).
4. No `done`, persiste a mensagem `ASSISTANT` com tokens, latência e `MessageSource`s, e envia ao navegador o `MessageResponse` final.

O playground lista apenas conversas do próprio workspace.

### 6.6 Fontes das respostas (`conversation/`)

| Método | Rota | Request | Response | Status |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/messages/{id}/sources` | — | `List<MessageSourceResponse>` | 200 |

- `ConversationResponse(id, createdAt, lastMessageAt)`.
- `ConversationSummaryResponse(id, lastMessagePreview, lastMessageAt)`.
- `MessageResponse(id, role, content, createdAt)`.
- `MessageSourceResponse(chunkId, documentId, fileName, contentPreview, score)`.

---

## 7. Segurança

- `SecurityFilterChain` stateless, `csrf` desabilitado, CORS liberado para a origem do painel (propriedade).
- JWT HMAC-SHA256 emitido no login com `NimbusJwtEncoder` e validado pelo resource server com `NimbusJwtDecoder.withSecretKey`.
- Claims: `sub` = id do usuário, `workspace_id`, `email`, `name`.
- `CurrentUser` (componente em `common/security`) expõe `userId()` e `workspaceId()` lidos do `Jwt` no `SecurityContext`.
- Rotas públicas: `/api/v1/auth/**`, `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`, `/actuator/health`.
- O serviço Python fica em rede privada; só este backend e o MinIO são expostos.
- O MinIO é acessado pelo navegador apenas por URLs pré-assinadas de curta duração. O bucket é privado (sem acesso anônimo) e tem CORS liberado só para a origem do painel, com os métodos `POST` e `GET`.

---

## 8. Contrato com o serviço Python (RAG)

Cliente em `rag/RagClient` usando `RestClient` (base URL e timeout de `app.rag`). Todas as chamadas são internas, sem autenticação de usuário; o `workspaceId` é sempre preenchido pelo Spring a partir do usuário.

### `POST /query`

```json
{
  "workspaceId": "uuid",
  "conversationId": "uuid",
  "systemPrompt": "texto do workspace",
  "question": "pergunta atual",
  "history": [
    { "role": "USER", "content": "..." },
    { "role": "ASSISTANT", "content": "..." }
  ],
  "stream": false
}
```

- `history`: últimas 10 mensagens da conversa (`USER` e `ASSISTANT`), em ordem cronológica, sem a pergunta atual.

Resposta (`stream=true`, playground): `text/event-stream` com os eventos

```
event: token
data: {"text": "parte da resposta"}

event: done
data: {"answer": "...", "answered": true, "sources": [...], "inputTokens": 0, "outputTokens": 0, "latencyMs": 0}

event: error
data: {"message": "descrição"}
```

Erros do Python (timeout, 5xx) viram `RagUnavailableException`. No playground, ela gera um evento SSE `error`.

---

## 9. Tratamento de erros

```java
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Recurso não encontrado");
        return problem;
    }

    // ConflictException -> 409, BusinessException -> 422,
    // validação (MethodArgumentNotValidException) -> 400 com propriedade "errors": [{field, message}],
    // MaxUploadSizeExceededException -> 413, demais -> 500 sem detalhes internos (logar o stack trace).
}
```

---

## 10. Ordem sugerida de implementação

1. Dependências, `application.yml`, properties records e `V1__initial_schema.sql`.
2. `common/`: `CentralMapperConfig`, exceções e `GlobalExceptionHandler`, JPA Auditing.
3. Segurança e `auth/` (register, login, JWT, `CurrentUser`) + `user/` e `workspace/`.
4. `storage/` (MinIO, URLs pré-assinadas) e `document/` (criação com URL de upload, confirmação, listagem, URL de download, exclusão, job de limpeza).
5. `rag/RagClient` e playground com SSE.
6. Fontes das respostas (`MessageSource`).
7. Testes de cada etapa conforme o `CLAUDE.md`.
