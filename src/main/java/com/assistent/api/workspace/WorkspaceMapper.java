package com.assistent.api.workspace;

import com.assistent.api.common.mapper.CentralMapperConfig;
import com.assistent.api.workspace.dto.WorkspaceResponse;
import com.assistent.api.workspace.dto.WorkspaceUpdateRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(config = CentralMapperConfig.class)
public interface WorkspaceMapper {

    WorkspaceResponse toResponse(Workspace workspace);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    void update(WorkspaceUpdateRequest request, @MappingTarget Workspace workspace);
}
