package com.assistent.api.document;

import com.assistent.api.common.mapper.CentralMapperConfig;
import com.assistent.api.document.dto.DocumentResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = CentralMapperConfig.class)
public interface DocumentMapper {

    @Mapping(target = "uploadedByName", source = "uploadedBy.name")
    DocumentResponse toResponse(Document document);
}
