package com.aivle.project.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.aivle.project.support.CdcRecoveryGuard;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CdcRecoveryGuardTest {
    @ParameterizedTest
    @ValueSource(strings = {"connect", "broker"})
    void partialStopFailureAlwaysAttemptsRecovery(String service) {
        AtomicBoolean running = new AtomicBoolean(true);
        // given/when: 정지는 완료됐지만 제어 명령이 실패한 상황이다.
        assertThatThrownBy(() -> {
            try (var recovery = new CdcRecoveryGuard(() -> running.set(true))) {
                running.set(false);
                throw new IllegalStateException("partial stop: " + service);
            }
        }).isInstanceOf(IllegalStateException.class);
        // then: 제어 예외와 별개로 이미 등록한 복구가 실행된다.
        assertThat(running).isTrue();
    }
}
