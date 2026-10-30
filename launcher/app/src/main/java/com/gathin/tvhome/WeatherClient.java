package com.gathin.tvhome;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Fetches the weather for the place in config and keeps the last answer in a file, the way
 * {@link WallpaperPlayer} keeps its picture: one request on a worker thread, only while Home is on screen
 * ({@link #start}, {@link #stop}), every {@link Weather#REFRESH_MS}, {@link Weather#RETRY_MS} after a
 * failure. Home's first frame shows the kept answer; a fresh one replaces it when it arrives. Nothing but
 * the coordinates leaves the TV (see {@link Weather.Place#url}).
 */
final class WeatherClient {

    /** A new answer, on the main thread. */
    interface Listener {
        void onWeather(Weather.Report report);
    }

    private static final String TAG = "TvHome.Weather";
    private static final String CACHE_NAME = "weather.json";
    private static final int CONNECT_MS = 6_000, READ_MS = 8_000;
    /** An answer is about 2 KB; anything much larger is not the API. */
    private static final int MAX_BYTES = 128 * 1024;

    private final Weather.Place place;
    private final Listener listener;
    private final File cache;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private Weather.Report report;
    private long lastFailure;
    private boolean active, loading, closed;

    WeatherClient(Context ctx, Weather.Place place, Listener listener) {
        this.place = place;
        this.listener = listener;
        cache = new File(ctx.getFilesDir(), CACHE_NAME);
        report = readCache();   // a 2 KB file, read here so Home's first frame already has the last answer
    }

    /** The last answer, if it is still worth showing; null otherwise. */
    Weather.Report current() {
        return report != null && report.fresh(System.currentTimeMillis()) ? report : null;
    }

    void start() {
        active = true;
        update();
    }

    void stop() {
        active = false;
    }

    void close() {
        active = false;
        closed = true;
        worker.shutdownNow();
    }

    /** Once a minute from Home's tick: asks again when the last answer is old enough, never twice at once. */
    void update() {
        if (!active || closed || loading) return;
        long now = System.currentTimeMillis();
        if (!Weather.due(report == null ? 0 : report.fetchedAt, lastFailure, now)) return;
        loading = true;
        worker.execute(() -> {
            Weather.Report fresh = null;
            try {
                String body = fetch(place.url());
                fresh = parse(body, System.currentTimeMillis());
                writeCache(body);
            } catch (Exception e) {
                Log.w(TAG, "no weather: " + e);
            }
            final Weather.Report result = fresh;
            main.post(() -> {
                loading = false;
                if (closed) return;
                if (result == null) {
                    lastFailure = System.currentTimeMillis();
                    return;
                }
                report = result;
                listener.onWeather(result);
            });
        });
    }

    private static String fetch(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(CONNECT_MS);
        c.setReadTimeout(READ_MS);
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("User-Agent", "CleanYourTV launcher");
        try {
            int status = c.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) throw new IOException("HTTP " + status);
            try (InputStream in = c.getInputStream()) {
                return readAll(in);
            }
        } finally {
            c.disconnect();
        }
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
            if (out.size() > MAX_BYTES) throw new IOException("answer over " + MAX_BYTES + " bytes");
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    /**
     * Open-Meteo's answer: {@code current} (temperature_2m, weather_code, is_day) and {@code daily} as parallel
     * arrays (time, weather_code, temperature_2m_max, temperature_2m_min). A day with a null in it is skipped.
     */
    static Weather.Report parse(String body, long fetchedAt) throws JSONException {
        JSONObject root = new JSONObject(body);
        JSONObject current = root.getJSONObject("current");
        double temperature = current.getDouble("temperature_2m");
        int code = current.getInt("weather_code");
        boolean day = current.optInt("is_day", 1) != 0;
        JSONObject daily = root.getJSONObject("daily");
        JSONArray time = daily.getJSONArray("time");
        JSONArray codes = daily.getJSONArray("weather_code");
        JSONArray highs = daily.getJSONArray("temperature_2m_max");
        JSONArray lows = daily.getJSONArray("temperature_2m_min");
        List<Weather.Day> days = new ArrayList<>();
        int n = Math.min(Math.min(time.length(), codes.length()), Math.min(highs.length(), lows.length()));
        for (int i = 0; i < n; i++) {
            if (time.isNull(i) || codes.isNull(i) || highs.isNull(i) || lows.isNull(i)) continue;
            try {
                days.add(new Weather.Day(LocalDate.parse(time.getString(i)), codes.getInt(i),
                        highs.getDouble(i), lows.getDouble(i)));
            } catch (java.time.format.DateTimeParseException ignored) {
            }
        }
        if (days.isEmpty()) throw new JSONException("no forecast days");
        return new Weather.Report(fetchedAt, temperature, code, day, days);
    }

    /** The kept answer, dated by the file; null when there is none or it does not parse. */
    private Weather.Report readCache() {
        if (!cache.isFile() || cache.length() == 0 || cache.length() > MAX_BYTES) return null;
        try (InputStream in = new FileInputStream(cache)) {
            return parse(readAll(in), cache.lastModified());
        } catch (Exception e) {
            Log.w(TAG, "ignoring " + cache.getName() + ": " + e.getMessage());
            return null;
        }
    }

    private void writeCache(String body) {
        try (FileOutputStream out = new FileOutputStream(cache)) {
            out.write(body.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            Log.w(TAG, "could not keep the answer: " + e.getMessage());
        }
    }
}
