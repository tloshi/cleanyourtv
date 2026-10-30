package com.gathin.tvhome;

import org.junit.Test;

import java.time.DayOfWeek;
import java.time.YearMonth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Plain JVM tests for the calendar card's grid: where a month starts, how many weeks it spans, the cells around it. */
public class MonthGridTest {

    @Test
    public void octoberTwentyTwentySixOpensOnAThursday() {
        MonthGrid monday = MonthGrid.of(YearMonth.of(2026, 10), DayOfWeek.MONDAY);
        assertEquals(3, monday.leading);
        assertEquals(31, monday.days);
        assertEquals(5, monday.rows);
        MonthGrid sunday = MonthGrid.of(YearMonth.of(2026, 10), DayOfWeek.SUNDAY);
        assertEquals(4, sunday.leading);
        assertEquals(5, sunday.rows);
    }

    @Test
    public void theCellsAroundTheMonthCarryTheNeighboursNumbers() {
        MonthGrid grid = MonthGrid.of(YearMonth.of(2026, 10), DayOfWeek.MONDAY);
        assertEquals(28, grid.dayNumber(0));     // Monday 28 September
        assertEquals(30, grid.dayNumber(2));
        assertEquals(1, grid.dayNumber(3));      // Thursday 1 October
        assertEquals(31, grid.dayNumber(33));
        assertEquals(1, grid.dayNumber(34));     // Sunday 1 November
        assertFalse(grid.inMonth(2));
        assertTrue(grid.inMonth(3));
        assertTrue(grid.inMonth(33));
        assertFalse(grid.inMonth(34));
        assertEquals(3, grid.cellOf(1));
        assertEquals(24, grid.cellOf(22));
        assertEquals(35, grid.cells());
    }

    @Test
    public void aMonthCanSpanFourWeeksOrSix() {
        assertEquals(6, MonthGrid.of(YearMonth.of(2026, 11), DayOfWeek.MONDAY).rows);   // starts on a Sunday
        assertEquals(4, MonthGrid.of(YearMonth.of(2027, 2), DayOfWeek.MONDAY).rows);    // 28 days from a Monday
        assertEquals(5, MonthGrid.of(YearMonth.of(2028, 2), DayOfWeek.MONDAY).rows);    // leap year, from a Tuesday
    }

    @Test
    public void theHeaderStartsOnTheFirstDayOfTheWeek() {
        DayOfWeek[] fromSunday = MonthGrid.weekdays(DayOfWeek.SUNDAY);
        assertEquals(DayOfWeek.SUNDAY, fromSunday[0]);
        assertEquals(DayOfWeek.MONDAY, fromSunday[1]);
        assertEquals(DayOfWeek.SATURDAY, fromSunday[6]);
        assertEquals(DayOfWeek.SUNDAY, MonthGrid.weekdays(DayOfWeek.MONDAY)[6]);
    }
}
