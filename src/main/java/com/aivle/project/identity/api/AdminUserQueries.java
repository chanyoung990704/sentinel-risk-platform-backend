package com.aivle.project.identity.api;
import com.aivle.project.identity.api.AdminUserListItemDto;
import java.util.List;
/** 도메인의 공개 업무 계약. 저장소 구현은 모듈 내부에 둔다. */
public interface AdminUserQueries {
	List<AdminUserListItemDto> getActiveUsers();
	void validateActiveUser(Long userId);
}
