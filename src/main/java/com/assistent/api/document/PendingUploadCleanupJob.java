package com.assistent.api.document;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PendingUploadCleanupJob {

    private final DocumentService documentService;

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT5M")
    public void run() {
        int removed = documentService.cleanupExpiredUploads();
        if (removed > 0) {
            log.info("Removed {} documents with expired pending upload", removed);
        }
    }
}
