package ch.sbb.polarion.extension.aad.synchronizer.utils;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

public final class TimeUtils {

    private TimeUtils() {
    }

    public static boolean isExpiredAADSync(@Nullable Instant lastSyncDate) {
        return isExpiredAADSync(lastSyncDate, Clock.systemUTC());
    }

    @VisibleForTesting
    static boolean isExpiredAADSync(@Nullable Instant lastSyncDate, @NotNull Clock clock) {
        if (lastSyncDate == null) {
            return true;
        }
        Duration duration = Duration.between(lastSyncDate, clock.instant());
        return !duration.minusHours(1).isNegative();
    }
}
