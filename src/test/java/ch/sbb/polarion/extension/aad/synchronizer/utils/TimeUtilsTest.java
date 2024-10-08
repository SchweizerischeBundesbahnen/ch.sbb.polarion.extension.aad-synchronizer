package ch.sbb.polarion.extension.aad.synchronizer.utils;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Date;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TimeUtilsTest {

    private static Stream<Arguments> testValues() {
        return Stream.of(
                Arguments.of(new Date(), false),
                Arguments.of(new Date(new Date().getTime() - 60 * 60 * 1000), true),
                Arguments.of(new Date(new Date().getTime() - 30 * 60 * 1000), false),
                Arguments.of(new Date(new Date().getTime() - 3 * 60 * 60 * 1000), true)
        );
    }

    @ParameterizedTest
    @MethodSource("testValues")
    void removePolarionUsers(Date lastSyncDate, boolean expected) {
        assertThat(TimeUtils.isExpiredAADSync(lastSyncDate)).isEqualTo(expected);
    }
}
