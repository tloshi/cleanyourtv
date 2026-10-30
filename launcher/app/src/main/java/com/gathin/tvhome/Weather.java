package com.gathin.tvhome;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The weather under the clock and in the week's forecast card, for the place in config ({@code weather},
 * {@code latitude}, {@code longitude}), from Open-Meteo (https://open-meteo.com: free, no key, no account).
 * Everything here is data and maths with no Android types: the WMO weather codes the API answers with, the
 * conditions and icons they map to, the degrees as written, and how long an answer stays good. It is tested
 * on the JVM; {@link WeatherClient} does the one request and keeps the last answer on the TV.
 */
final class Weather {

    /** Ask again this long after the last good answer... */
    static final long REFRESH_MS = 30 * 60_000L;
    /** ...or this long after a failed one. */
    static final long RETRY_MS = 5 * 60_000L;
    /** An answer older than this is not shown at all: better no weather than yesterday's. */
    static final long STALE_MS = 24 * 60 * 60_000L;

    /** Where the TV is, and how the owner reads a thermometer. */
    static final class Place {
        final double latitude;
        final double longitude;
        /** The name shown first on the line ("Kanpur"), or empty for none. */
        final String name;
        final boolean fahrenheit;

        Place(double latitude, double longitude, String name, boolean fahrenheit) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.name = name;
            this.fahrenheit = fahrenheit;
        }

        /**
         * From the config's text: null unless both coordinates parse as decimal degrees on the globe, so a
         * theme with a typo shows no weather rather than the weather somewhere else.
         */
        static Place parse(String latitude, String longitude, String name, String units) {
            double lat, lon;
            try {
                lat = Double.parseDouble(latitude.trim());
                lon = Double.parseDouble(longitude.trim());
            } catch (NumberFormatException | NullPointerException e) {
                return null;
            }
            if (Double.isNaN(lat) || Double.isNaN(lon) || Math.abs(lat) > 90 || Math.abs(lon) > 180) return null;
            boolean f = units != null && units.trim().toLowerCase(Locale.ROOT).startsWith("f");
            return new Place(lat, lon, name == null ? "" : name.trim(), f);
        }

        /** The one request: now and seven days, in the TV's own time zone, in the owner's unit. */
        String url() {
            return String.format(Locale.US, "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                    + "&current=temperature_2m,weather_code,is_day"
                    + "&daily=weather_code,temperature_2m_max,temperature_2m_min"
                    + "&timezone=auto&forecast_days=7%s", latitude, longitude,
                    fahrenheit ? "&temperature_unit=fahrenheit" : "");
        }
    }

    /** What the sky is doing, in the words the launcher has strings for. */
    enum Condition { CLEAR, MOSTLY_CLEAR, PARTLY_CLOUDY, OVERCAST, FOG, DRIZZLE, RAIN, SHOWERS, SNOW, THUNDERSTORM }

    /** The glyph for a condition; clear and partly cloudy skies have a night version. */
    enum Icon { SUN, MOON, CLOUD_SUN, CLOUD_MOON, CLOUD, FOG, DRIZZLE, RAIN, SNOW, STORM }

    /** One day of the forecast. */
    static final class Day {
        final LocalDate date;
        final int code;
        final double high;
        final double low;

        Day(LocalDate date, int code, double high, double low) {
            this.date = date;
            this.code = code;
            this.high = high;
            this.low = low;
        }

        Condition condition() {
            return Weather.condition(code);
        }
    }

    /** One answer from the API: now, and the days from today on. */
    static final class Report {
        /** System.currentTimeMillis() when the answer arrived (or the cached file's time). */
        final long fetchedAt;
        final double temperature;
        final int code;
        final boolean day;
        final List<Day> days;

        Report(long fetchedAt, double temperature, int code, boolean day, List<Day> days) {
            this.fetchedAt = fetchedAt;
            this.temperature = temperature;
            this.code = code;
            this.day = day;
            this.days = Collections.unmodifiableList(new ArrayList<>(days));
        }

        Condition condition() {
            return Weather.condition(code);
        }

        Icon icon() {
            return Weather.icon(condition(), day);
        }

        /** Today's entry, or null when the forecast does not start today (a stale cache across midnight). */
        Day today(LocalDate today) {
            for (Day d : days) if (d.date.equals(today)) return d;
            return null;
        }

        /** Good enough to show: younger than {@link #STALE_MS}. */
        boolean fresh(long now) {
            return now - fetchedAt < STALE_MS && now >= fetchedAt - 60_000L;
        }
    }

    private Weather() {
    }

    /**
     * The WMO code groups Open-Meteo documents: 0 clear, 1 to 3 more and more cloud, 45 and 48 fog, 51 to 57
     * drizzle, 61 to 67 rain, 71 to 77 snow, 80 to 82 rain showers, 85 and 86 snow showers, 95 to 99 thunder.
     * Anything else (a code added later) counts as overcast, the plainest cloud.
     */
    static Condition condition(int code) {
        if (code == 0) return Condition.CLEAR;
        if (code == 1) return Condition.MOSTLY_CLEAR;
        if (code == 2) return Condition.PARTLY_CLOUDY;
        if (code == 3) return Condition.OVERCAST;
        if (code == 45 || code == 48) return Condition.FOG;
        if (code >= 51 && code <= 57) return Condition.DRIZZLE;
        if (code >= 61 && code <= 67) return Condition.RAIN;
        if (code >= 71 && code <= 77) return Condition.SNOW;
        if (code >= 80 && code <= 82) return Condition.SHOWERS;
        if (code == 85 || code == 86) return Condition.SNOW;
        if (code >= 95 && code <= 99) return Condition.THUNDERSTORM;
        return Condition.OVERCAST;
    }

    static Icon icon(Condition condition, boolean day) {
        switch (condition) {
            case CLEAR:
            case MOSTLY_CLEAR:
                return day ? Icon.SUN : Icon.MOON;
            case PARTLY_CLOUDY:
                return day ? Icon.CLOUD_SUN : Icon.CLOUD_MOON;
            case FOG:
                return Icon.FOG;
            case DRIZZLE:
                return Icon.DRIZZLE;
            case RAIN:
            case SHOWERS:
                return Icon.RAIN;
            case SNOW:
                return Icon.SNOW;
            case THUNDERSTORM:
                return Icon.STORM;
            default:
                return Icon.CLOUD;
        }
    }

    /** "27°": rounded to the degree, and a reading just under zero is "0°", never "-0°". */
    static String degrees(double t) {
        long rounded = Math.round(t);
        if (rounded == 0) rounded = 0;   // -0 rounds to 0, but keep the intent visible
        return rounded + "°";
    }

    /** The parts of the line under the clock, the empty ones left out: "Kanpur · 27° Clear · H 31° L 21°". */
    static String join(String... parts) {
        StringBuilder line = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isEmpty()) continue;
            if (line.length() > 0) line.append(" · ");
            line.append(part);
        }
        return line.toString();
    }

    /** Time to ask the API again: the last good answer is old, and the last failure is not too recent. */
    static boolean due(long fetchedAt, long lastFailure, long now) {
        if (now - lastFailure < RETRY_MS) return false;
        return now - fetchedAt >= REFRESH_MS;
    }
}
