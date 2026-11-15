package com.aivle.project.aiintegration.internal;

import com.aivle.project.aiintegration.api.*;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

/** 기존 로컬 mock 전용 제약과 타임아웃을 유지한다. */
@Component
public class LocalAiPdfClient implements AiReportTransport {
	private final String url;
	private final int responseTimeout, connectTimeout;
	public LocalAiPdfClient(@Value("${custom-report.ai.base-url:${ai.server.url:http://127.0.0.1:18081}}") String url,
		@Value("${custom-report.ai.response-timeout-ms:10000}") int responseTimeout,
		@Value("${custom-report.ai.connect-timeout-ms:2000}") int connectTimeout) {
		this.url = url; this.responseTimeout = responseTimeout; this.connectTimeout = connectTimeout;
	}
	@Override public byte[] generatePdf(byte[] json) {
		URI target = URI.create(url);
		if (target.getUserInfo() != null || !Set.of("http", "https").contains(target.getScheme()) ||
			!Set.of("127.0.0.1", "localhost", "::1", "[::1]").contains(String.valueOf(target.getHost()).toLowerCase(Locale.ROOT)))
			throw new AiTransportException("AI_NOT_LOCAL", false);
		var client = WebClient.builder().baseUrl(url).clientConnector(new ReactorClientHttpConnector(HttpClient.create()
			.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeout).responseTimeout(Duration.ofMillis(responseTimeout))))
			.codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(MAX_BYTES)).build();
		try {
			return client.post().uri("/test-ai/custom-reports").contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_PDF)
				.bodyValue(json).exchangeToMono(response -> {
					int status = response.statusCode().value();
					if (status != 200) return response.releaseBody().then(Mono.<byte[]>error(new AiTransportException("AI_HTTP_"+status, status >= 500 || status == 429)));
					if (response.headers().contentLength().orElse(0) > MAX_BYTES) return response.releaseBody().then(Mono.<byte[]>error(new AiTransportException("PDF_SIZE", false)));
					if (!response.headers().contentType().orElse(MediaType.APPLICATION_OCTET_STREAM).isCompatibleWith(MediaType.APPLICATION_PDF))
						return response.releaseBody().then(Mono.<byte[]>error(new AiTransportException("PDF_TYPE", false)));
					return response.bodyToMono(byte[].class).defaultIfEmpty(new byte[0]);
				}).block(Duration.ofMillis(Math.min(20000L, (long) connectTimeout + responseTimeout + 1000)));
		} catch (AiTransportException known) { throw known; }
		catch (Exception failure) {
			for (Throwable cause = failure; cause != null; cause = cause.getCause())
				if (cause instanceof org.springframework.core.io.buffer.DataBufferLimitException) throw new AiTransportException("PDF_SIZE", false);
			throw new AiTransportException("AI_TIMEOUT_OR_CONNECTION", true);
		}
	}
}
