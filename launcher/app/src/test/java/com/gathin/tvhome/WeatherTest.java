package com.gathin.tvhome;

import org.junit.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** Plain JVM tests for the weather line and card: codes, glyphs, degrees, the place from config and timing. */
public class WeatherTest {

    @Test
    public void codesFollowTheWmoGroupsOpenMeteoDocuments() {
        assertEquals(Weather.Condition.CLEAR, Weather.condition(0));
        assertEquals(Weather.Condition.MOSTLY_CLEAR, Weather.condition(1));
        assertEquals(Weather.Condition.PARTLY_CLOUDY, Weather.condition(2));
        assertEquals(Weather.Condition.OVERCAST, Weather.condition(3));
        assertEquals(Weather.Condition.FOG, Weather.condition(45));
        assertEquals(Weather.Condition.FOG, Weather.condition(48));
        for (int code : new int[]{51, 53, 55, 56, 57}) assertEquals(Weather.Condition.DRIZZLE, Weather.condition(code));
        for (int code : new int[]{61, 63, 65, 66, 67}) assertEquals(Weather.Condition.RAIN, Weather.condition(code));
        for (int code : new int[]{71, 73, 75, 77, 85, 86}) assertEquals(Weather.Condition.SNOW, Weather.condition(code));
        for (int code : new int[]{80, 81, 82}) assertEquals(Weather.Condition.SHOWERS, Weather.condition(code));
        for (int code : new int[]{95, 96, 99}) assertEquals(Weather.Condition.THUNDERSTORM, Weather.condition(code));
        assertEquals("a code added later reads as plain cloud", Weather.Condition.OVERCAST, Weather.condition(42));
    }

    @Test
    public void clearSkiesShowTheMoonAtNight() {
        assertEquals(Weather.Icon.SUN, Weather.icon(Weather.Condition.CLEAR, true));
        assertEquals(Weather.Icon.MOON, Weather.icon(Weather.Condition.CLEAR, false));
        assertEquals(Weather.Icon.MOON, Weather.icon(Weather.Condition.MOSTLY_CLEAR, false));
        assertEquals(Weather.Icon.CLOUD_SUN, Weather.icon(Weather.Condition.PARTLY_CLOUDY, true));
        assertEquals(Weather.Icon.CLOUD_MOON, Weather.icon(Weather.Condition.PARTLY_CLOUDY, false));
        assertEquals(Weather.Icon.CLOUD, Weather.icon(Weather.Condition.OVERCAST, false));
        assertEquals(Weather.Icon.RAIN, Weather.icon(Weather.Condition.SHOWERS, true));
        assertEquals(Weather.Icon.STORM, Weather.icon(Weather.Condition.THUNDERSTORM, false));
    }

    @Test
    public void degreesRoundToTheDegreeAndNeverReadMinusZero() {
        assertEquals("27°", Weather.degrees(27.4));
        assertEquals("28°", Weather.degrees(27.5));
        assertEquals("0°", Weather.degrees(-0.4));
        assertEquals("-4°", Weather.degrees(-3.6));
    }

    @Test
    public void theLineLeavesEmptyPartsOut() {
        assertEquals("Kanpur · 27° Clear · H 31° L 21°", Weather.join("Kanpur", "27° Clear", "H 31° L 21°"));
        assertEquals("27° Clear", Weather.join("", "27° Clear", null));
        assertEquals("", Weather.join("", null));
    }

    @Test
    public void thePlaceComesFromConfigAndRejectsNonsense() {
        Weather.Place berlin = Weather.Place.parse("52.52", "13.405", " Berlin ", "celsius");
        assertNotNull(berlin);
        assertEquals(52.52, berlin.latitude, 1e-9);
        assertEquals(13.405, berlin.longitude, 1e-9);
        assertEquals("Berlin", berlin.name);
        assertFalse(berlin.fahrenheit);
        assertTrue(berlin.url().startsWith("https://api.open-meteo.com/v1/forecast?latitude=52.5200&longitude=13.4050&"));
        assertTrue(berlin.url().contains("forecast_days=7"));
        assertFalse(berlin.url().contains("temperature_unit"));

        Weather.Place austin = Weather.Place.parse("30.27", "-97.74", null, "Fahrenheit");
        assertNotNull(austin);
        assertTrue(austin.fahrenheit);
        assertEquals("", austin.name);
        assertTrue(austin.url().endsWith("&temperature_unit=fahrenheit"));

        assertNull(Weather.Place.parse("", "", "", "celsius"));
        assertNull(Weather.Place.parse("fifty", "13.4", "", "celsius"));
        assertNull(Weather.Place.parse("91", "0", "", "celsius"));
        assertNull(Weather.Place.parse("0", "181", "", "celsius"));
        assertNull(Weather.Place.parse(null, "0", "", "celsius"));
    }

    @Test
    public void asksAgainAfterHalfAnHourButNotRightAfterAFailure() {
        long now = 1_000_000_000L;
        assertTrue(Weather.due(0, 0, now));
        assertTrue(Weather.due(now - Weather.REFRESH_MS, 0, now));
        assertFalse(Weather.due(now - Weather.REFRESH_MS + 1, 0, now));
        assertFalse("a failure a minute ago: wait", Weather.due(0, now - 60_000L, now));
        assertTrue(Weather.due(0, now - Weather.RETRY_MS, now));
    }

    @Test
    public void aReportStaysGoodForADayAndKnowsToday() {
        LocalDate today = LocalDate.of(2026, 10, 22);
        List<Weather.Day> days = Arrays.asList(
                new Weather.Day(today, 0, 31.2, 20.8),
                new Weather.Day(today.plusDays(1), 61, 28.0, 19.0));
        long now = 1_000_000_000L;
        Weather.Report report = new Weather.Report(now - 60 * 60_000L, 27.3, 0, false, days);
        assertTrue(report.fresh(now));
        assertFalse(new Weather.Report(now - Weather.STALE_MS, 27.3, 0, false, days).fresh(now));
        assertSame(days.get(0), report.today(today));
        assertNull(report.today(today.minusDays(1)));
        assertEquals(Weather.Condition.CLEAR, report.condition());
        assertEquals(Weather.Icon.MOON, report.icon());
        assertEquals(Weather.Condition.RAIN, days.get(1).condition());
    }
}
