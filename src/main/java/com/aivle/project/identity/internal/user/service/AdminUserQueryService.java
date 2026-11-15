package com.aivle.project.identity.internal.user.service;

import com.aivle.project.foundation.api.error.CommonErrorCode;
import com.aivle.project.foundation.api.error.CommonException;
import com.aivle.project.foundation.api.util.NameMaskingUtil;
import com.aivle.project.identity.api.AdminUserListItemDto;
import com.aivle.project.identity.api.RoleName;
import com.aivle.project.identity.api.UserStatus;
import com.aivle.project.identity.internal.user.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 사용자 조회/검증 서비스.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUserQueryService implements com.aivle.project.identity.api.AdminUserQueries {

	private final UserRepository userRepository;

	/**
	 * 활성 + 미삭제 사용자 목록을 반환한다.
	 */
	public List<AdminUserListItemDto> getActiveUsers() {
		return userRepository.findListByStatusAndDeletedAtIsNullOrderByIdAscExcludingRole(
			UserStatus.ACTIVE,
			RoleName.ROLE_ADMIN
		).stream()
			.map(user -> new AdminUserListItemDto(
				user.id(),
				NameMaskingUtil.mask(user.name()),
				user.email()
			))
			.toList();
	}

	/**
	 * 조회 대상 userId가 활성 사용자인지 검증한다.
	 */
	public void validateActiveUser(Long userId) {
		if (userId == null) {
			throw new CommonException(CommonErrorCode.COMMON_400);
		}
		boolean exists = userRepository.existsByIdAndStatusAndDeletedAtIsNull(userId, UserStatus.ACTIVE);
		if (!exists) {
			throw new CommonException(CommonErrorCode.COMMON_404);
		}
	}
}
