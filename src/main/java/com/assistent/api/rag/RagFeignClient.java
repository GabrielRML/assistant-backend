package com.assistent.api.rag;

import com.assistent.api.rag.dto.RagQueryRequest;
import com.assistent.api.rag.dto.RagQueryResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/** Cliente declarativo do serviço Python (chamadas sem streaming). O SSE continua no {@link RagClient}. */
@FeignClient(name = "rag", url = "${app.rag.base-url}")
public interface RagFeignClient {

    @PostMapping(value = "/query", consumes = MediaType.APPLICATION_JSON_VALUE)
    RagQueryResponse query(@RequestBody RagQueryRequest request);
}
