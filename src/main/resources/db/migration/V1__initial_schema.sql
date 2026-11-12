CREATE EXTENSION IF NOT EXISTS vector SCHEMA public;
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
    embedding     public.vector(384) NOT NULL,
    token_count   integer,
    metadata      jsonb,
    created_at    timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX ix_chunk_workspace ON rag.chunk (workspace_id);
CREATE INDEX ix_chunk_document  ON rag.chunk (document_id);
CREATE INDEX ix_chunk_embedding ON rag.chunk USING hnsw (embedding public.vector_cosine_ops);

-- ===== app (depende de rag.chunk) =====

CREATE TABLE app.message_source (
    message_id uuid NOT NULL REFERENCES app.message (id) ON DELETE CASCADE,
    chunk_id   uuid NOT NULL REFERENCES rag.chunk (id) ON DELETE CASCADE,
    score      real NOT NULL,
    PRIMARY KEY (message_id, chunk_id)
);
