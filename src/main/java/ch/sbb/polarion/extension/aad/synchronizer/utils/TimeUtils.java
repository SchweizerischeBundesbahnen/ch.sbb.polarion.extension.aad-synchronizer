package ch.sbb.polarion.extension.aad.synchronizer.utils;

import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;

public final class TimeUtils {

    private static final ZoneId UTC_TIME_ZONE = ZoneId.of("UTC");

    private TimeUtils() {
    }

    public static boolean isExpiredAADSync(@Nullable Date lastSyncDate) {
        if (lastSyncDate == null) {
            return true;
        }
        ZonedDateTime zonedLastSyncDate = ZonedDateTime.ofInstant(lastSyncDate.toInstant(), UTC_TIME_ZONE);
        ZonedDateTime now = ZonedDateTime.ofInstant(Instant.now(), UTC_TIME_ZONE);

        Duration duration = Duration.between(zonedLastSyncDate, now);
        return !duration.minusHours(1).isNegative();
    }
}
