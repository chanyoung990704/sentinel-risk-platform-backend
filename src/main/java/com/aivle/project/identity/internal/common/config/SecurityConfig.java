package com.aivle.project.identity.internal.common.config;

import com.aivle.project.identity.internal.auth.service.AccessTokenBlacklistService;
import com.aivle.project.identity.internal.auth.token.AccessTokenValidator;
import com.aivle.project.identity.internal.auth.token.JwtKeyProvider;
import com.aivle.project.identity.internal.auth.token.JwtProperties;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;

/**
 * 계정 인증·JWT 발급/검증 설정. 앱의 경로 정책은 app 모듈에서 조립한다.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(JwtProperties.class)
@RequiredArgsConstructor
public class SecurityConfig {

	private final JwtKeyProvider jwtKeyProvider;
	private final JwtProperties jwtProperties;
	private final AccessTokenBlacklistService accessTokenBlacklistService;


	@Bean
	public AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
		DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
		provider.setUserDetailsService(userDetailsService);
		provider.setPasswordEncoder(passwordEncoder);
		return new ProviderManager(provider);
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public JwtDecoder jwtDecoder() {
		RSAPublicKey publicKey = jwtKeyProvider.loadPublicKey();
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey).build();
		OAuth2TokenValidator<Jwt> validator = JwtValidators.createDefaultWithIssuer(jwtProperties.getIssuer());
		OAuth2TokenValidator<Jwt> accessTokenValidator = new AccessTokenValidator(accessTokenBlacklistService);
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validator, accessTokenValidator));
		return decoder;
	}

	@Bean
	public JwtEncoder jwtEncoder() {
		RSAPublicKey publicKey = jwtKeyProvider.loadPublicKey();
		RSAPrivateKey privateKey = jwtKeyProvider.loadPrivateKey();
		RSAKey rsaKey = new RSAKey.Builder(publicKey)
			.privateKey(privateKey)
			.keyID(jwtProperties.getKeys().getCurrentKid())
			.build();
		return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsaKey)));
	}

	@Bean
	public JwtAuthenticationConverter jwtAuthenticationConverter() {
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(jwt -> {
			List<String> roles = jwt.getClaimAsStringList("roles");
			if (roles == null || roles.isEmpty()) {
				return List.of();
			}

			boolean allowLegacyPrefix = isLegacyRolePrefixAllowed();
			if (!allowLegacyPrefix && containsLegacyPrefix(roles)) {
				throw new IllegalArgumentException("ROLE_ 접두사가 없는 레거시 토큰은 더 이상 허용되지 않습니다.");
			}

			List<GrantedAuthority> authorities = new ArrayList<>();
			for (String role : roles) {
				String normalized = normalizeRole(role, allowLegacyPrefix);
				if (normalized != null) {
					authorities.add(new SimpleGrantedAuthority(normalized));
				}
			}
			return authorities;
		});
		return converter;
	}

	private boolean isLegacyRolePrefixAllowed() {
		if (!jwtProperties.getLegacy().isRolePrefixSupportEnabled()) {
			return false;
		}
		long cutoffEpoch = jwtProperties.getLegacy().getRolePrefixAcceptUntilEpoch();
		if (cutoffEpoch <= 0) {
			return true;
		}
		return Instant.now().getEpochSecond() <= cutoffEpoch;
	}

	private String normalizeRole(String role, boolean allowLegacyPrefix) {
		if (role == null || role.isBlank()) {
			return null;
		}
		String trimmed = role.trim();
		if (trimmed.startsWith("ROLE_")) {
			return trimmed;
		}
		return allowLegacyPrefix ? "ROLE_" + trimmed : null;
	}





	private boolean containsLegacyPrefix(List<String> roles) {
		for (String role : roles) {
			if (role != null && !role.trim().startsWith("ROLE_")) {
				return true;
			}
		}
		return false;
	}
}
