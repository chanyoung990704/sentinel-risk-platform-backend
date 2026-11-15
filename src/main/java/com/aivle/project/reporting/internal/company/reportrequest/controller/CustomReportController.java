package com.aivle.project.reporting.internal.company.reportrequest.controller;
import com.aivle.project.reporting.api.CustomReportCommand;
import com.aivle.project.reporting.api.AcceptedReportRequest;
import com.aivle.project.foundation.api.dto.ApiResponse;
import com.aivle.project.identity.api.CurrentUser;
import com.aivle.project.reporting.internal.company.reportrequest.service.ReportRequestService;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
@RestController public class CustomReportController {
	private final ReportRequestService requests;public CustomReportController(ReportRequestService requests){this.requests=requests;}
	@PostMapping("/api/companies/{companyId}/custom-report-requests")
	public ResponseEntity<ApiResponse<AcceptedReportRequest>> accept(@CurrentUser Long user,@PathVariable Long companyId,@RequestHeader("Idempotency-Key") String key,@RequestBody CustomReportCommand command){var accepted=requests.accept(user,companyId,key,command);return ResponseEntity.accepted().header("Location",accepted.statusUrl()).body(ApiResponse.ok(accepted));}
}
