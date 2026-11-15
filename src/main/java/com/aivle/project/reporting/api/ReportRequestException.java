package com.aivle.project.reporting.api;
import com.aivle.project.foundation.api.error.CommonException;
import com.aivle.project.foundation.api.error.ErrorCode;
import org.springframework.http.HttpStatus;
/** 외부 응답은 정해진 설명만 사용하고 내부 오류에도 입력 원문을 넣지 않는다. */
public class ReportRequestException extends CommonException {
	public enum Code implements ErrorCode {
		INVALID(HttpStatus.BAD_REQUEST,"요청 입력 또는 재무 데이터 범위를 확인해 주세요."),
		NOT_FOUND(HttpStatus.NOT_FOUND,"요청 또는 파일을 찾을 수 없습니다."),
		UNAUTHORIZED(HttpStatus.UNAUTHORIZED,"로그인이 필요합니다."),
		FORBIDDEN(HttpStatus.FORBIDDEN,"이 작업의 권한이 없습니다."),
		CONFLICT(HttpStatus.CONFLICT,"동일 키의 요청 내용 또는 처리 상태가 다릅니다."),
		USER_CAPACITY(HttpStatus.TOO_MANY_REQUESTS,"진행 중인 요청 한도를 초과했습니다."),
		GLOBAL_CAPACITY(HttpStatus.SERVICE_UNAVAILABLE,"현재 요청을 수용할 수 없습니다.");
		private final HttpStatus status;private final String message;
		Code(HttpStatus status,String message){this.status=status;this.message=message;}
		public String getCode(){return "CUSTOM_REPORT_"+name();} public String getMessage(){return message;} public HttpStatus getStatus(){return status;}
	}
	public ReportRequestException(Code code){super(code);}
	@Override public String getMessage(){return getErrorCode().getCode();}
}
