package com.aivle.project.company.reportjob.adapter;
import com.aivle.project.company.reportjob.model.*;
import com.aivle.project.company.reportjob.port.ReportGenerationPort;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.aivle.project.dataimport.service.ImportPreflightService;
import java.net.URI;
import java.time.Duration;
import java.util.*;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import io.netty.channel.ChannelOption;
import reactor.core.publisher.Mono;
@Component public class HttpMockReportGenerationAdapter implements ReportGenerationPort {
	private final String url;private final int responseTimeout,connectTimeout;
	private final ReportPdfValidator pdf=new ReportPdfValidator();
	public HttpMockReportGenerationAdapter(@Value("${custom-report.ai.base-url:${ai.server.url:http://127.0.0.1:18081}}") String url,@Value("${custom-report.ai.response-timeout-ms:10000}") int responseTimeout,@Value("${custom-report.ai.connect-timeout-ms:2000}") int connectTimeout) {this.url=url;this.responseTimeout=responseTimeout;this.connectTimeout=connectTimeout;}
	public GeneratedReport generate(ReportSourceData source,CustomReportInput input,UUID requestId){
		URI target=URI.create(url);if(target.getUserInfo()!=null||!Set.of("http","https").contains(target.getScheme())||!Set.of("127.0.0.1","localhost","::1","[::1]").contains(String.valueOf(target.getHost()).toLowerCase(Locale.ROOT)))throw new ReportGenerationFailure("AI_NOT_LOCAL",false);
		var client=WebClient.builder().baseUrl(url).clientConnector(new ReactorClientHttpConnector(HttpClient.create().option(ChannelOption.CONNECT_TIMEOUT_MILLIS,connectTimeout).responseTimeout(Duration.ofMillis(responseTimeout))))
			.codecs(c->c.defaultCodecs().maxInMemorySize(ReportPdfValidator.MAX_BYTES)).build();
		try {
			byte[] bytes=client.post().uri("/test-ai/custom-reports").contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_PDF)
				.bodyValue(Map.of("schemaVersion",1,"source",source,"input",input,"requestId",requestId))
				.exchangeToMono(response->{
					int status=response.statusCode().value();
					if(status!=200)return response.releaseBody().then(Mono.<byte[]>error(new ReportGenerationFailure("AI_HTTP_"+status,status>=500||status==429)));
					if(response.headers().contentLength().orElse(0)>ReportPdfValidator.MAX_BYTES)return response.releaseBody().then(Mono.<byte[]>error(new ReportGenerationFailure("PDF_SIZE",false)));
					if(!response.headers().contentType().orElse(MediaType.APPLICATION_OCTET_STREAM).isCompatibleWith(MediaType.APPLICATION_PDF))return response.releaseBody().then(Mono.<byte[]>error(new ReportGenerationFailure("PDF_TYPE",false)));
					return response.bodyToMono(byte[].class).defaultIfEmpty(new byte[0]);
				}).block(Duration.ofMillis(Math.min(20000L,(long)connectTimeout+responseTimeout+1000)));
			pdf.validate(bytes);return new GeneratedReport(bytes,ImportPreflightService.sha256(bytes));
		} catch(ReportGenerationFailure known){throw known;}
		catch(Exception failure){
			for(Throwable t=failure;t!=null;t=t.getCause())if(t instanceof org.springframework.core.io.buffer.DataBufferLimitException)throw new ReportGenerationFailure("PDF_SIZE",false);
			throw new ReportGenerationFailure("AI_TIMEOUT_OR_CONNECTION",true);
		}
	}
}
