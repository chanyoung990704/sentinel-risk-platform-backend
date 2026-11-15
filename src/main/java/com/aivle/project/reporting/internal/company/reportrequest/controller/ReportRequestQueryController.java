package com.aivle.project.reporting.internal.company.reportrequest.controller;
import com.aivle.project.foundation.api.dto.ApiResponse;
import com.aivle.project.identity.api.CurrentUser;
import com.aivle.project.reporting.internal.company.reportrequest.service.ReportRequestQueryService;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
@RestController @RequestMapping("/api/report-requests") public class ReportRequestQueryController {
	private final ReportRequestQueryService queries;public ReportRequestQueryController(ReportRequestQueryService queries){this.queries=queries;}
	@GetMapping("/{requestId}") public ResponseEntity<ApiResponse<com.aivle.project.reporting.api.ReportRequestView>> status(@CurrentUser Long user,@PathVariable UUID requestId){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(queries.getOwned(user,requestId)));}
	@GetMapping("/{requestId}/file") public ResponseEntity<byte[]> file(@CurrentUser Long user,@PathVariable UUID requestId){byte[] bytes=queries.openOwnedFile(user,requestId);return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL,"private, no-store").header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\"custom-report.pdf\"").contentType(MediaType.APPLICATION_PDF).contentLength(bytes.length).body(bytes);}
}
