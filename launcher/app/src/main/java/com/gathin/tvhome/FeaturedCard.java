package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

/**
 * One app as a wide card above the dock (featured_app in config.xml or a theme): its banner, its name in
 * large type and a tint of its colour, in the space the top shelf's name uses otherwise. It lives outside
 * the grid, so the dock and grid positions every navigation rule counts on stay as they are; MainActivity
 * keeps it lined up with the dock band while the grid scrolls.
 */
final class FeaturedCard {
    /** Gap between the card and the dock band, and the inset kept above the card. */
    static final float GAP_DP = 14f, TOP_DP = 4f;
    /** Padding around the banner inside the card. */
    static final float PADDING_DP = 12f;
    static final float MIN_HEIGHT_DP = 72f, MAX_HEIGHT_DP = 200f;
    // No focus zoom: the card is as wide as the clipping frame, so a zoom would be cut off at its sides;
    // the glass marks focus on its own.
    private static final float RADIUS_DP = 22f, BANNER_RADIUS_DP = 12f;

    final View view;
    final String label;
    final int color;
    private final ComponentName component;
    private final ImageView banner;
    private final float density;

    private FeaturedCard(View view, String label, int color, ComponentName component, float density) {
        this.view = view;
        this.label = label;
        this.color = color;
        this.component = component;
        this.banner = view.findViewById(R.id.featuredBanner);
        this.density = density;
    }

    /** The package's TV launcher activity, or null when it is not installed or has none. */
    static ResolveInfo resolve(Context ctx, String pkg) {
        if (pkg == null || pkg.isEmpty()) return null;
        Intent probe = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER).setPackage(pkg);
        List<ResolveInfo> found = ctx.getPackageManager().queryIntentActivities(probe, 0);
        return found.isEmpty() || found.get(0).activityInfo == null ? null : found.get(0);
    }

    /** The card for this package's TV launcher activity, added to parent; null when there is none to show. */
    static FeaturedCard create(Context ctx, ViewGroup parent, String pkg) {
        ResolveInfo ri = resolve(ctx, pkg);
        if (ri == null) return null;
        PackageManager pm = ctx.getPackageManager();
        float density = ctx.getResources().getDisplayMetrics().density;
        Drawable art = AppAdapter.loadBanner(ctx, pm, ri);
        boolean isBanner = art != null;
        if (!isBanner) art = ri.loadIcon(pm);
        String label = Cards.cleanLabel(String.valueOf(ri.loadLabel(pm)), pkg);
        int color = AppAdapter.vividColor(art);

        View view = LayoutInflater.from(ctx).inflate(R.layout.featured_card, parent, false);
        parent.addView(view);
        FeaturedCard card = new FeaturedCard(view, label, color,
                new ComponentName(pkg, ri.activityInfo.name), density);
        card.banner.setImageDrawable(art);
        card.banner.setScaleType(isBanner ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER);
        card.banner.setOutlineProvider(rounded(BANNER_RADIUS_DP * density));
        card.banner.setClipToOutline(true);
        ((TextView) view.findViewById(R.id.featuredName)).setText(label);
        view.setContentDescription(label);

        GradientDrawable background = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{blend(color, DockDecoration.BAND_COLOR, 0.42f), DockDecoration.BAND_COLOR});
        background.setCornerRadius(RADIUS_DP * density);
        background.setStroke(Math.max(1, Math.round(density)), DockDecoration.EDGE_COLOR);
        view.setBackground(background);
        view.setTranslationY(-view.getLayoutParams().height * 2f);   // out of sight until the dock is laid out
        return card;
    }

    /** Card height for the space between the grid's top and the dock band, kept readable and not oversized. */
    static int heightFor(int spacePx, float density) {
        int height = spacePx - Math.round((GAP_DP + TOP_DP) * density);
        int min = Math.round(MIN_HEIGHT_DP * density), max = Math.round(MAX_HEIGHT_DP * density);
        return Math.max(min, Math.min(max, height));
    }

    /** The space the grid's top padding must leave above the dock band for the smallest card. */
    static int minSpacePx(float density) {
        return Math.round((MIN_HEIGHT_DP + GAP_DP + TOP_DP) * density);
    }

    /** Top edge of a card of this height when the dock band starts at bandTopPx. */
    static float topFor(float bandTopPx, int heightPx, float density) {
        return bandTopPx - GAP_DP * density - heightPx;
    }

    /** Banner width at 16:9 for a card of this height. */
    static int bannerWidthFor(int heightPx, float density) {
        int inner = heightPx - 2 * Math.round(PADDING_DP * density);
        return Math.round(inner * 16f / 9f);
    }

    void setHeight(int heightPx) {
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        // Compared with the laid-out height, not only the params: a request made from inside a layout pass
        // (the grid's padding listener) is dropped by the view root, and would otherwise read as done.
        if (lp.height == heightPx && view.getHeight() == heightPx) return;
        lp.height = heightPx;
        view.setLayoutParams(lp);
        ViewGroup.LayoutParams bannerLp = banner.getLayoutParams();
        bannerLp.width = bannerWidthFor(heightPx, density);
        banner.setLayoutParams(bannerLp);
    }

    /** Lines the card up with the dock band: the same left and right edges, a gap above it. */
    void alignTo(float bandLeft, float bandRight, float bandTop) {
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        int width = Math.round(bandRight - bandLeft);
        if (lp.width != width || view.getWidth() != width) {
            lp.width = width;
            view.setLayoutParams(lp);
        }
        view.setTranslationX(bandLeft);
        view.setTranslationY(topFor(bandTop, lp.height, density));
    }

    /** The dock row scrolled out of the grid: park the card above it, still focusable for a later restore. */
    void park() {
        view.setTranslationY(-view.getHeight() * 2f);
    }

    void launch(Context ctx, boolean advancedEffects) {
        AppAdapter.open(ctx, component, label, advancedEffects);
    }

    float radiusPx() {
        return RADIUS_DP * density;
    }

    String packageName() {
        return component.getPackageName();
    }

    private static ViewOutlineProvider rounded(float radius) {
        return new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline outline) {
                outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
            }
        };
    }

    private static int blend(int color, int base, float amount) {
        return Color.rgb(
                Math.round(Color.red(base) + (Color.red(color) - Color.red(base)) * amount),
                Math.round(Color.green(base) + (Color.green(color) - Color.green(base)) * amount),
                Math.round(Color.blue(base) + (Color.blue(color) - Color.blue(base)) * amount));
    }
}
