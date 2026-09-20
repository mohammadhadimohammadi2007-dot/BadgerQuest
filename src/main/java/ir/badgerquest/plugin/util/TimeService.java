package ir.badgerquest.plugin.util;

import ir.badgerquest.plugin.config.ConfigManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Handles "day-key" arithmetic. A day rolls over at reset_hour in configured zone.
 * DayKey = yyyy*10000 + mm*100 + dd of the day the "current period" started.
 */
public final class TimeService {

    private final ConfigManager cfg;

    public TimeService(ConfigManager cfg) {
        this.cfg = cfg;
    }

    public int currentDayKey() {
        return dayKeyAt(ZonedDateTime.now(cfg.zoneId()));
    }

    public int dayKeyAt(ZonedDateTime instant) {
        LocalDateTime local = instant.toLocalDateTime();
        LocalDate date = local.toLocalDate();
        if (local.getHour() < cfg.resetHour()) {
            date = date.minusDays(1);
        }
        return date.getYear() * 10000 + date.getMonthValue() * 100 + date.getDayOfMonth();
    }

    public LocalDate dayFromKey(int dayKey) {
        int year = dayKey / 10000;
        int month = (dayKey / 100) % 100;
        int day = dayKey % 100;
        return LocalDate.of(year, month, day);
    }

    /** Distance in days between two day-keys (b - a). */
    public long daysBetween(int a, int b) {
        return ChronoUnit.DAYS.between(dayFromKey(a), dayFromKey(b));
    }

    /** Seconds until the next reset boundary from now. */
    public long secondsUntilNextReset() {
        ZoneId zone = cfg.zoneId();
        ZonedDateTime now = ZonedDateTime.now(zone);
        ZonedDateTime next = now.withHour(cfg.resetHour()).withMinute(0).withSecond(0).withNano(0);
        if (!next.isAfter(now)) next = next.plusDays(1);
        return ChronoUnit.SECONDS.between(now, next);
    }

    public LocalTime resetTime() {
        return LocalTime.of(cfg.resetHour(), 0);
    }
}
