package com.assistent.api.document;

import com.assistent.api.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentStorageCleaner {

    private final StorageService storageService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(DocumentDeletedEvent event) {
        try {
            storageService.delete(event.storageKey());
        } catch (RuntimeException e) {
            log.warn("Could not remove object {} after document deletion: {}", event.storageKey(), e.getMessage());
        }
    }
}
