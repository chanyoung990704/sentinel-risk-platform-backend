package com.aivle.project.support;

/** 정지 명령의 부분 실패에도 자원 복구 책임을 유지하는 시험 지원 객체. */
public final class CdcRecoveryGuard implements AutoCloseable {
    @FunctionalInterface
    public interface Recovery { void run() throws Exception; }
    private final Recovery recovery;

    public CdcRecoveryGuard(Recovery recovery) {
        this.recovery = recovery;
    }

    @Override
    public void close() throws Exception {
        recovery.run();
    }
}
