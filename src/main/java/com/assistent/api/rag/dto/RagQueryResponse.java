package com.assistent.api.rag.dto;

import java.util.List;

public record RagQueryResponse(
        String answer, boolean answered, List<RagSource> sources,
        Integer inputTokens, Integer outputTokens, Integer latencyMs) {
}
