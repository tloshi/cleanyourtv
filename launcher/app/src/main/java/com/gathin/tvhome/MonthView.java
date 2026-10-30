package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.WeekFields;
import java.util.Locale;

/**
 * The calendar card ({@code calendar_widget}): this month as a grid of days with today in a white disc, the
 * weeks starting on the TV's first day of the week. Drawn with a handful of text calls, once, and again when
 * the day changes ({@link #update}, from Home's minute tick); it takes no focus. The layout comes from
 * {@link MonthGrid}.
 */
public class MonthView extends View {

    private static final float PAD_DP = 16f, COLUMN_DP = 30f, ROW_DP = 21f;
    private static final float TITLE_SP = 15f, HEADER_SP = 10.5f, DAY_SP = 12.5f;
    private static final float TITLE_H_DP = 22f, HEADER_H_DP = 18f, TODAY_RADIUS_DP = 11.5f;
    /** Always six rows, so the card keeps its height from one month to the next. */
    private static final int ROWS = 6;

    private final float density;
    private final Paint title = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint header = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint day = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outside = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint todayDisc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint todayText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private MonthGrid grid;
    private LocalDate today;
    private String titleText = "";
    private final String[] headers = new String[MonthGrid.COLUMNS];

    public MonthView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        float scaled = getResources().getDisplayMetrics().scaledDensity;
        title.setTypeface(getResources().getFont(R.font.outfit_medium));
        title.setTextSize(TITLE_SP * scaled);
        title.setColor(0xFFFFFFFF);
        header.setTypeface(getResources().getFont(R.font.outfit_medium));
        header.setTextSize(HEADER_SP * scaled);
        header.setColor(0x80FFFFFF);
        header.setLetterSpacing(0.08f);
        header.setTextAlign(Paint.Align.CENTER);
        day.setTypeface(getResources().getFont(R.font.outfit_regular));
        day.setTextSize(DAY_SP * scaled);
        day.setColor(0xE6FFFFFF);
        day.setTextAlign(Paint.Align.CENTER);
        outside.set(day);
        outside.setColor(0x3DFFFFFF);
        todayDisc.setColor(0xFFFFFFFF);
        todayText.set(day);
        todayText.setTypeface(getResources().getFont(R.font.outfit_medium));
        todayText.setColor(0xFF0B1020);
        setFocusable(false);
    }

    /** Lays today's month out; cheap to call every minute, it only works when the date has changed. */
    public void update() {
        LocalDate now = LocalDate.now();
        if (now.equals(today) && grid != null) return;
        today = now;
        Locale locale = Locale.getDefault();
        DayOfWeek first = WeekFields.of(locale).getFirstDayOfWeek();
        grid = MonthGrid.of(YearMonth.from(now), first);
        String month = now.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale));
        titleText = month.isEmpty() ? month : month.substring(0, 1).toUpperCase(locale) + month.substring(1);
        DayOfWeek[] days = MonthGrid.weekdays(first);
        for (int i = 0; i < headers.length; i++) {
            String name = days[i].getDisplayName(TextStyle.SHORT, locale);
            headers[i] = name.substring(0, Math.min(2, name.length())).toUpperCase(locale);
        }
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = Math.round((2 * PAD_DP + MonthGrid.COLUMNS * COLUMN_DP) * density);
        int h = Math.round((2 * PAD_DP + TITLE_H_DP + HEADER_H_DP + ROWS * ROW_DP) * density);
        setMeasuredDimension(resolveSize(w, widthMeasureSpec), resolveSize(h, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (grid == null) update();
        float pad = PAD_DP * density;
        float columnW = (getWidth() - 2 * pad) / MonthGrid.COLUMNS;
        float top = pad;
        canvas.drawText(titleText, pad + 2 * density, top + TITLE_H_DP * density * 0.72f, title);
        top += TITLE_H_DP * density;
        float headerBaseline = top + HEADER_H_DP * density * 0.75f;
        for (int i = 0; i < headers.length; i++) {
            canvas.drawText(headers[i], pad + columnW * (i + 0.5f), headerBaseline, header);
        }
        top += HEADER_H_DP * density;
        // The rows share whatever height the card was given, so a taller card spaces the weeks out.
        float rowH = (getHeight() - pad - top) / ROWS;
        int todayCell = grid.cellOf(today.getDayOfMonth());
        float textShift = (day.descent() + day.ascent()) / 2f;   // centres the number on the row, not its baseline
        for (int cell = 0; cell < ROWS * MonthGrid.COLUMNS; cell++) {
            int row = cell / MonthGrid.COLUMNS, column = cell % MonthGrid.COLUMNS;
            float cx = pad + columnW * (column + 0.5f);
            float cy = top + rowH * (row + 0.5f);
            if (cell >= grid.cells()) continue;   // a fifth-week month leaves the sixth row empty
            String number = String.valueOf(grid.dayNumber(cell));
            if (cell == todayCell) {
                canvas.drawCircle(cx, cy, TODAY_RADIUS_DP * density, todayDisc);
                canvas.drawText(number, cx, cy - textShift, todayText);
            } else {
                canvas.drawText(number, cx, cy - textShift, grid.inMonth(cell) ? day : outside);
            }
        }
    }
}
