package com.aivle.project.identity.internal.auth.mapper;

import com.aivle.project.identity.internal.auth.dto.SignupResponse;
import com.aivle.project.identity.api.RoleName;
import com.aivle.project.identity.internal.user.entity.UserEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface AuthMapper {

	// 회원가입 응답은 사용자 정보와 역할을 합성해 생성한다.
	@Mapping(target = "role", source = "role")
	SignupResponse toSignupResponse(UserEntity user, RoleName role);
}
