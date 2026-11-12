package com.assistent.api.user;

import com.assistent.api.common.mapper.CentralMapperConfig;
import com.assistent.api.user.dto.UserResponse;
import org.mapstruct.Mapper;

@Mapper(config = CentralMapperConfig.class)
public interface UserMapper {

    UserResponse toResponse(AppUser user);
}
