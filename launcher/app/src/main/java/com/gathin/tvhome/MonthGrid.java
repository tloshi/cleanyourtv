package com.gathin.tvhome;

import java.time.DayOfWeek;
import java.time.YearMonth;

/**
 * The cells of the calendar card ({@code calendar_widget}): a month laid out in weeks that start on the
 * TV's first day of the week, the days before the first and after the last filled with the neighbouring
 * months' numbers. Pure java.time, so it is tested on the JVM; {@link MonthView} draws it.
 */
final class MonthGrid {
    static final int COLUMNS = 7;

    final YearMonth month;
    final DayOfWeek firstDayOfWeek;
    /** Cells before the 1st: the tail of the previous month. */
    final int leading;
    /** Days in the month. */
    final int days;
    /** Weeks the month spans, 4 to 6. */
    final int rows;

    private MonthGrid(YearMonth month, DayOfWeek firstDayOfWeek) {
        this.month = month;
        this.firstDayOfWeek = firstDayOfWeek;
        this.leading = (month.atDay(1).getDayOfWeek().getValue() - firstDayOfWeek.getValue() + 7) % 7;
        this.days = month.lengthOfMonth();
        this.rows = (leading + days + COLUMNS - 1) / COLUMNS;
    }

    static MonthGrid of(YearMonth month, DayOfWeek firstDayOfWeek) {
        return new MonthGrid(month, firstDayOfWeek);
    }

    /** The cells, row by row: {@code rows * 7}. */
    int cells() {
        return rows * COLUMNS;
    }

    /** Whether a cell is a day of this month rather than of the one before or after. */
    boolean inMonth(int cell) {
        return cell >= leading && cell < leading + days;
    }

    /** The number written in a cell: this month's day, or the neighbouring month's for the cells around it. */
    int dayNumber(int cell) {
        if (cell < leading) return month.minusMonths(1).lengthOfMonth() - leading + cell + 1;
        if (cell < leading + days) return cell - leading + 1;
        return cell - leading - days + 1;
    }

    /** The cell of a day of this month, 1 to {@link #days}. */
    int cellOf(int dayOfMonth) {
        return leading + dayOfMonth - 1;
    }

    /** The weekdays in header order, starting from the first day of the week. */
    static DayOfWeek[] weekdays(DayOfWeek first) {
        DayOfWeek[] out = new DayOfWeek[COLUMNS];
        for (int i = 0; i < COLUMNS; i++) out[i] = first.plus(i);
        return out;
    }
}
