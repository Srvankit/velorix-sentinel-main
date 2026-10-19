package com.velorix.sentinel.mapper;

import com.velorix.sentinel.dto.auth.UserResponse;
import com.velorix.sentinel.entity.User;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapping between the {@link User} aggregate and its DTOs.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserMapper {

    @Mapping(target = "fullName", expression = "java(user.fullName())")
    UserResponse toResponse(User user);

    List<UserResponse> toResponseList(List<User> users);
}
