package com.aivle.project.support;
import java.nio.file.Path;
/** 자식 JVM도 명시된 시험 namespace의 자격 경로를 그대로 사용한다. */
public final class PortfolioSecretPaths {
    private PortfolioSecretPaths() { }
    public static Path directory() {
        return Path.of(System.getenv().getOrDefault("PORTFOLIO_SECRET_DIR",Path.of(System.getProperty("user.home"),".local/share/sentinel/portfolio/secrets").toString()));
    }
    public static Path file(String name) {
        if(!name.matches("[a-z0-9-]+"))throw new IllegalArgumentException("비밀 파일 이름 확인 필요");
        return directory().resolve(name);
    }
}
