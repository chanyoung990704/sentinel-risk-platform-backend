package com.aivle.project.company.reportrequest.controller;
import com.aivle.project.common.dto.ApiResponse;
import com.aivle.project.common.security.CurrentUser;
import com.aivle.project.company.reportrequest.service.ReportRequestService;
import com.aivle.project.company.reportrequest.model.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
@RestController public class CustomReportController {
	private final ReportRequestService requests;public CustomReportController(ReportRequestService requests){this.requests=requests;}
	@PostMapping("/api/companies/{companyId}/custom-report-requests")
	public ResponseEntity<ApiResponse<AcceptedReportRequest>> accept(@CurrentUser Long user,@PathVariable Long companyId,@RequestHeader("Idempotency-Key") String key,@RequestBody CustomReportCommand command){var accepted=requests.accept(user,companyId,key,command);return ResponseEntity.accepted().header("Location",accepted.statusUrl()).body(ApiResponse.ok(accepted));}
}
