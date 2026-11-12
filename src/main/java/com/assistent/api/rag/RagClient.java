package com.assistent.api.rag;

import com.assistent.api.common.exception.RagUnavailableException;
import com.assistent.api.rag.dto.RagQueryRequest;
import com.assistent.api.rag.dto.RagQueryResponse;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RagClient {

    private final RagFeignClient feignClient;

    public RagQueryResponse query(RagQueryRequest request) {
        try {
            RagQueryResponse response = feignClient.query(request);
            if (response == null) {
                throw new RagUnavailableException("RAG service returned an empty response");
            }
            return response;
        } catch (FeignException e) {
            throw new RagUnavailableException("RAG query call failed: " + e.getMessage(), e);
        }
    }
}
