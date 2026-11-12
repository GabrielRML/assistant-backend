package com.assistent.api.document;

/** Evento interno: remover o objeto do storage após o commit da exclusão. */
public record DocumentDeletedEvent(String storageKey) {
}
