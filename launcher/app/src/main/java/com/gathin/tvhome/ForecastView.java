package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The week's weather card ({@code forecast_widget}): seven columns, each a day's name, its sky as a glyph, the
 * high, a bar that shows where the day's range sits in the week's (cool blue at the bottom of the week's range,
 * warm orange at the top) and the low. Drawn once per answer from {@link WeatherClient}; it takes no focus.
 */
public class ForecastView extends View {

    private static final int DAYS = 7;
    private static final float PAD_DP = 16f, COLUMN_DP = 52f, ICON_DP = 24f;
    private static final float LABEL_SP = 10.5f, HIGH_SP = 17f, LOW_SP = 12f;
    private static final float LABEL_Y_DP = 11f, ICON_Y_DP = 22f, HIGH_Y_DP = 70f, TRACK_TOP_DP = 82f;
    private static final float LOW_FROM_BOTTOM_DP = 6f, TRACK_FROM_BOTTOM_DP = 26f, TRACK_W_DP = 4f, MIN_RANGE_DP = 6f;
    private static final float HEIGHT_DP = 2 * PAD_DP + 22f + 18f + 6 * 21f;   // the calendar card's, so the two match
    private static final int COOL = 0xFF5B9DFF, WARM = 0xFFFFB15C;

    private final float density;
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelToday = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint high = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint low = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint range = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bar = new RectF();
    private final Drawable[] icons = new Drawable[Weather.Icon.values().length];
    private final List<Weather.Day> days = new ArrayList<>();
    private float trackTop, trackBottom;

    public ForecastView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        float scaled = getResources().getDisplayMetrics().scaledDensity;
        label.setTypeface(getResources().getFont(R.font.outfit_medium));
        label.setTextSize(LABEL_SP * scaled);
        label.setColor(0x8CFFFFFF);
        label.setLetterSpacing(0.12f);
        label.setTextAlign(Paint.Align.CENTER);
        labelToday.set(label);
        labelToday.setColor(0xFFFFFFFF);
        high.setTypeface(getResources().getFont(R.font.outfit_light));
        high.setTextSize(HIGH_SP * scaled);
        high.setColor(0xFFFFFFFF);
        high.setTextAlign(Paint.Align.CENTER);
        low.setTypeface(getResources().getFont(R.font.outfit_regular));
        low.setTextSize(LOW_SP * scaled);
        low.setColor(0x8CFFFFFF);
        low.setTextAlign(Paint.Align.CENTER);
        track.setColor(0x26FFFFFF);
        setFocusable(false);
    }

    /** The days from today on, at most seven; an answer from before midnight drops the days already gone. */
    public void setReport(Weather.Report report, LocalDate today) {
        days.clear();
        if (report != null) {
            for (Weather.Day d : report.days) {
                if (d.date.isBefore(today)) continue;
                days.add(d);
                if (days.size() == DAYS) break;
            }
        }
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = Math.round((2 * PAD_DP + DAYS * COLUMN_DP) * density);
        int h = Math.round(HEIGHT_DP * density);
        setMeasuredDimension(resolveSize(w, widthMeasureSpec), resolveSize(h, heightMeasureSpec));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        trackTop = TRACK_TOP_DP * density;
        trackBottom = h - TRACK_FROM_BOTTOM_DP * density;
        range.setShader(new LinearGradient(0, trackBottom, 0, trackTop, COOL, WARM, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (days.isEmpty()) return;
        double weekLow = Double.MAX_VALUE, weekHigh = -Double.MAX_VALUE;
        for (Weather.Day d : days) {
            weekLow = Math.min(weekLow, d.low);
            weekHigh = Math.max(weekHigh, d.high);
        }
        double span = Math.max(1.0, weekHigh - weekLow);
        float pad = PAD_DP * density;
        float columnW = (getWidth() - 2 * pad) / DAYS;
        float icon = ICON_DP * density;
        float trackW = TRACK_W_DP * density;
        float minRange = MIN_RANGE_DP * density;
        Locale locale = Locale.getDefault();
        for (int i = 0; i < days.size(); i++) {
            Weather.Day d = days.get(i);
            float cx = pad + columnW * (i + 0.5f);
            String name = i == 0 ? getContext().getString(R.string.forecast_today)
                    : d.date.getDayOfWeek().getDisplayName(TextStyle.SHORT, locale);
            canvas.drawText(name.toUpperCase(locale), cx, LABEL_Y_DP * density + label.getTextSize() * 0.35f,
                    i == 0 ? labelToday : label);
            Drawable glyph = icon(Weather.icon(d.condition(), true));
            if (glyph != null) {
                int left = Math.round(cx - icon / 2), top = Math.round(ICON_Y_DP * density);
                glyph.setBounds(left, top, left + Math.round(icon), top + Math.round(icon));
                glyph.draw(canvas);
            }
            canvas.drawText(Weather.degrees(d.high), cx, HIGH_Y_DP * density, high);
            // The track is the week's range; the bar is this day's share of it.
            bar.set(cx - trackW / 2, trackTop, cx + trackW / 2, trackBottom);
            canvas.drawRoundRect(bar, trackW / 2, trackW / 2, track);
            float yHigh = (float) (trackBottom - (d.high - weekLow) / span * (trackBottom - trackTop));
            float yLow = (float) (trackBottom - (d.low - weekLow) / span * (trackBottom - trackTop));
            if (yLow - yHigh < minRange) {
                float mid = (yLow + yHigh) / 2;
                yHigh = Math.max(trackTop, mid - minRange / 2);
                yLow = Math.min(trackBottom, mid + minRange / 2);
            }
            bar.set(cx - trackW / 2, yHigh, cx + trackW / 2, yLow);
            canvas.drawRoundRect(bar, trackW / 2, trackW / 2, range);
            canvas.drawText(Weather.degrees(d.low), cx, getHeight() - LOW_FROM_BOTTOM_DP * density - low.descent(), low);
        }
    }

    private Drawable icon(Weather.Icon which) {
        Drawable d = icons[which.ordinal()];
        if (d == null) {
            d = getContext().getDrawable(iconFor(which));
            if (d != null) {
                d = d.mutate();
                d.setAlpha(230);
            }
            icons[which.ordinal()] = d;
        }
        return d;
    }

    /** The drawable for a weather glyph; the line under the clock uses it too. */
    static int iconFor(Weather.Icon icon) {
        switch (icon) {
            case SUN: return R.drawable.ic_wx_sun;
            case MOON: return R.drawable.ic_wx_moon;
            case CLOUD_SUN: return R.drawable.ic_wx_cloud_sun;
            case CLOUD_MOON: return R.drawable.ic_wx_cloud_moon;
            case FOG: return R.drawable.ic_wx_fog;
            case DRIZZLE: return R.drawable.ic_wx_drizzle;
            case RAIN: return R.drawable.ic_wx_rain;
            case SNOW: return R.drawable.ic_wx_snow;
            case STORM: return R.drawable.ic_wx_storm;
            default: return R.drawable.ic_wx_cloud;
        }
    }

    /** The word for a condition, in the TV's language. */
    static int wordFor(Weather.Condition condition) {
        switch (condition) {
            case CLEAR: return R.string.wx_clear;
            case MOSTLY_CLEAR: return R.string.wx_mostly_clear;
            case PARTLY_CLOUDY: return R.string.wx_partly_cloudy;
            case FOG: return R.string.wx_fog;
            case DRIZZLE: return R.string.wx_drizzle;
            case RAIN: return R.string.wx_rain;
            case SHOWERS: return R.string.wx_showers;
            case SNOW: return R.string.wx_snow;
            case THUNDERSTORM: return R.string.wx_thunderstorm;
            default: return R.string.wx_overcast;
        }
    }
}
