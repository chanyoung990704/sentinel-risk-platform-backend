package com.aivle.project.company.reportrequest.controller;
import com.aivle.project.common.dto.ApiResponse;
import com.aivle.project.common.error.ErrorResponse;
import com.aivle.project.company.reportrequest.model.ReportRequestException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
/** 새 요청 API의 연결 장애는 원문/SQL을 노출하지 않고 재접수 가능한 응답으로 제한한다. */
@Order(-10) @RestControllerAdvice(basePackageClasses=CustomReportController.class)
public class ReportDatabaseExceptionHandler {
	@ExceptionHandler({org.springframework.dao.TransientDataAccessException.class,org.springframework.dao.DataAccessResourceFailureException.class})
	public ResponseEntity<ApiResponse<Void>> unavailable(Exception failure,HttpServletRequest request){var code=ReportRequestException.Code.GLOBAL_CAPACITY;return ResponseEntity.status(code.getStatus()).body(ApiResponse.fail(ErrorResponse.of(code,request.getRequestURI(),java.time.OffsetDateTime.now().toString())));}
}
