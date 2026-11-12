# CLAUDE.md

Contexto geral do projeto para assistentes de código. Leia este arquivo inteiro antes de qualquer tarefa.
A especificação detalhada da API está em `docs/API_CONTEXT.md`. Consulte-a antes de criar entidades, DTOs, endpoints ou migrations.

## O que é o sistema

Assistente que responde perguntas usando RAG sobre documentos cadastrados pela empresa.

- **Painel web (este backend):** a equipe da empresa sobe documentos para a base de conhecimento e testa respostas no playground.
- **Serviço de RAG (outro repositório, Python/FastAPI):** faz ingestão (parse, chunking, embeddings) e gera respostas. Este backend NÃO implementa lógica de IA.

## Arquitetura (visão rápida)

```
Painel web ──HTTPS──> [Spring Boot API] ──HTTP──> [Python RAG] ──> LLM
                          │
                     MinIO/S3
                          │
                 PostgreSQL + pgvector (schemas app e rag)
```

- Spring Boot é dono do schema `app`. Python é dono dos dados do schema `rag` (tabela `chunk`).
- As migrations Flyway deste projeto criam AMBOS os schemas (incluindo `rag.chunk`), mas o Spring nunca escreve em `rag.chunk`.
- Arquivos da base de conhecimento **não passam pelo servidor**: o navegador envia e baixa direto do MinIO por URLs pré-assinadas. O Spring só registra metadados, gera as URLs e confirma o upload.
- Playground usa streaming (SSE) do Python até o navegador.

## Stack

- Java + Spring Boot 4 (web MVC, Data JPA, Security, Validation, Flyway) — veja a versão exata no `build.gradle`
- PostgreSQL com extensão pgvector
- Lombok + MapStruct
- springdoc-openapi (Swagger UI em `/swagger-ui.html`)
- MinIO (cliente `io.minio`), JWT via `spring-boot-starter-oauth2-resource-server`
- Gradle

Atenção ao Spring Boot 4:
- Jackson 3 é o padrão: classes de databind ficam em `tools.jackson.*`. As anotações continuam em `com.fasterxml.jackson.annotation`.
- Spring Security 7: use apenas a DSL com lambdas.
- Hibernate 7 / Jakarta Persistence 3.2.

## Comandos

```bash
./gradlew bootRun          # sobe a API (exige a infra local rodando)
./gradlew test             # todos os testes
./gradlew build            # compila, testa e gera o jar
docker compose up -d       # Postgres (pgvector), MinIO
```

## Estrutura de pacotes

Organização por funcionalidade (package-by-feature). Cada feature contém suas próprias camadas:

```
com.example.assistant
├── config/        # Security, OpenAPI, MinIO, JPA, @ConfigurationProperties
├── common/        # exceptions, handler global, MapStruct config, segurança (CurrentUser)
├── auth/
├── workspace/
├── user/
├── document/
├── conversation/  # Conversation, Message, MessageSource, playground e fontes das respostas
├── rag/           # cliente HTTP do serviço Python
├── storage/       # StorageService e implementação MinIO
```

Dentro de cada feature: `XxxController`, `XxxService`, `XxxRepository`, entidade `Xxx`, `XxxMapper`, e DTOs em `dto/`.

## Convenções obrigatórias

### Camadas
- Controller → Service → Repository. Controller nunca acessa repository.
- Controller só recebe e devolve DTOs. **Entidades nunca saem nem entram pela API.**
- Regras de negócio e `@Transactional` ficam no service. Consultas usam `@Transactional(readOnly = true)`.
- `spring.jpa.open-in-view=false`.

### DTOs
- Sempre `record`. Nomes: `XxxCreateRequest`, `XxxUpdateRequest`, `XxxResponse`, `XxxSummaryResponse` (listagens).
- Requests com Bean Validation (`@NotBlank`, `@Size`, `@Email`…) e mensagens em português.
- Controllers usam `@Valid` nos requests.

### Mapeamento
- MapStruct para entidade ↔ DTO, com `@Mapper(config = CentralMapperConfig.class)`.
- `CentralMapperConfig` define `componentModel = SPRING` e `unmappedTargetPolicy = ERROR`.
- Campos controlados pelo sistema (id, workspace, datas, status) são ignorados explicitamente no mapeamento de requests.
- Atualizações usam `@MappingTarget` com `nullValuePropertyMappingStrategy = IGNORE` quando for PATCH.

### Entidades JPA
- Lombok: `@Getter`, `@Setter`, `@NoArgsConstructor(access = AccessLevel.PROTECTED)`. Nunca `@Data` nem `@EqualsAndHashCode` padrão em entidades.
- IDs `UUID` com `@GeneratedValue(strategy = GenerationType.UUID)`.
- Enums com `@Enumerated(EnumType.STRING)`.
- Datas como `Instant`, colunas `timestamptz`, preenchidas via JPA Auditing (`@CreatedDate`).
- Relacionamentos `@ManyToOne(fetch = FetchType.LAZY)`. Evite `@OneToMany` bidirecional, exceto quando realmente necessário.
- `hibernate.default_schema=app`. Tabelas e colunas em inglês, `snake_case`.
- `ddl-auto=validate`. **Toda mudança de schema é uma nova migration Flyway** (`V{n}__descricao.sql`). Nunca edite uma migration já aplicada.

### Multi-tenancy (crítico)
- Todo dado pertence a um `workspace`. O workspace vem SEMPRE do usuário autenticado (`CurrentUser`), nunca do request.
- Toda consulta de repository filtra por `workspaceId`, por exemplo `findByIdAndWorkspaceId`.
- Recurso de outro workspace responde **404**, nunca 403, para não revelar que ele existe.

### Erros
- `@RestControllerAdvice` global retornando `ProblemDetail` (RFC 9457).
- Exceções de domínio: `ResourceNotFoundException` (404), `ConflictException` (409), `BusinessException` (422).
- Erros de validação retornam 400 com a lista de campos inválidos.
- Mensagens de erro para o cliente em português; código, logs e nomes em inglês.

### API REST
- Prefixo `/api/v1`. Recursos no plural e em kebab-case: `/api/v1/playground/conversations`.
- Status corretos: `201` com `Location` ao criar, `202` para processamento assíncrono, `204` ao excluir.
- Listagens paginadas com `Pageable` e `@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)`.
- Todo controller documentado com `@Tag` e `@Operation` do springdoc.

### Segurança
- JWT stateless (HMAC), sem sessão, CSRF desabilitado para `/api/**`.
- Senhas com `BCryptPasswordEncoder` (via `PasswordEncoderFactories`).
- Rotas públicas: `/api/v1/auth/**`, Swagger.
- Segredos (JWT, MinIO) somente por variáveis de ambiente.

### Testes
- Services: JUnit 5 + Mockito, padrão Arrange-Act-Assert, nome `metodo_cenario_resultadoEsperado`.
- Controllers: `@WebMvcTest` + MockMvc, com `@WithMockUser` ou JWT mockado.
- Repositories: `@DataJpaTest` com Testcontainers (imagem `pgvector/pgvector`). Não use H2: ele não suporta pgvector nem os tipos usados.
- Cubra: sucesso, não encontrado, validação, conflito e isolamento entre workspaces.

## Regras para o assistente

- Siga `docs/API_CONTEXT.md` como fonte da verdade para nomes de tabelas, colunas, enums, endpoints e contratos.
- Não adicione dependências sem necessidade. Se precisar, explique o motivo.
- Não implemente lógica de RAG, embeddings ou chamadas a LLM neste projeto: isso é do serviço Python.
- Não escreva na tabela `rag.chunk`. Para exibir fontes, leia via consulta nativa.
- Não receba nem devolva arquivos de documentos pelo servidor (`MultipartFile`, stream de download): use URLs pré-assinadas.
- Ao criar uma feature, entregue junto: migration (se necessário), entidade, repository, DTOs, mapper, service, controller e testes.
- Mantenha o padrão de código existente antes de introduzir um novo.
