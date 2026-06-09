package ch.sbb.polarion.extension.aad.synchronizer.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TimeUtilsTest {

    private static final Instant NOW = Instant.parse("2024-05-12T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static Stream<Arguments> testValues() {
        return Stream.of(
                Arguments.of(NOW, false),
                Arguments.of(NOW.minus(Duration.ofHours(1)), true),
                Arguments.of(NOW.minus(Duration.ofMinutes(30)), false),
                Arguments.of(NOW.minus(Duration.ofHours(3)), true),
                Arguments.of(null, true)
        );
    }

    @ParameterizedTest
    @MethodSource("testValues")
    void isExpiredAADSync(Instant lastSyncDate, boolean expected) {
        assertThat(TimeUtils.isExpiredAADSync(lastSyncDate, FIXED_CLOCK)).isEqualTo(expected);
    }

    @Test
    void publicOverloadUsesSystemClock() {
        // The system-clock overload must report a timestamp far in the past as expired. Using a
        // fixed past instant keeps the assertion deterministic without reading the system clock here.
        assertThat(TimeUtils.isExpiredAADSync(Instant.parse("2000-01-01T00:00:00Z"))).isTrue();
    }
}
