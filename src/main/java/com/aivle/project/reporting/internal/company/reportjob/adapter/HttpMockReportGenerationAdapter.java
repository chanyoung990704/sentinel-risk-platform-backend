package com.aivle.project.reporting.internal.company.reportjob.adapter;
import com.aivle.project.reporting.internal.company.reportjob.model.*;
import com.aivle.project.reporting.internal.company.reportjob.port.ReportGenerationPort;
import java.util.UUID;
import org.springframework.stereotype.Component;
import com.aivle.project.aiintegration.api.AiReportTransport;
import com.aivle.project.aiintegration.api.AiTransportException;
import com.aivle.project.foundation.api.Hashing;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
@Component public class HttpMockReportGenerationAdapter implements ReportGenerationPort {
	private final AiReportTransport transport;
	private final ObjectMapper mapper;
	private final ReportPdfValidator pdf=new ReportPdfValidator();
	public HttpMockReportGenerationAdapter(AiReportTransport transport, ObjectMapper mapper) { this.transport=transport; this.mapper=mapper; }
	public GeneratedReport generate(ReportSourceData source,CustomReportInput input,UUID requestId){
		try {
			byte[] json=mapper.writeValueAsBytes(Map.of("schemaVersion",1,"source",source,"input",input,"requestId",requestId));
			byte[] bytes=transport.generatePdf(json);
			pdf.validate(bytes);return new GeneratedReport(bytes,Hashing.sha256(bytes));
		} catch(AiTransportException failure){
			throw new ReportGenerationFailure(failure.code(),failure.retryable());
		} catch(com.fasterxml.jackson.core.JsonProcessingException failure){
			throw new ReportGenerationFailure("AI_INPUT_SERIALIZATION",false);
		}
	}
}
