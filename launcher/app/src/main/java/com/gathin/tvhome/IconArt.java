package com.gathin.tvhome;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

/**
 * A square tile from an app's icon, for the icon row. An adaptive icon shows its whole background with the
 * foreground over it, the way Android's launchers draw them, and the tile's rounded corners are the mask. A
 * flat icon that fills its square (opaque at every edge) fills the tile too; one with a shape of its own sits
 * a little smaller on dark glass, so no app comes out as an odd cut-out. Rendered once per app, at the tile's
 * size, the first time its tile is bound; about 16 KB each at 1080p.
 */
final class IconArt {
    private static final int GLASS = 0xFF1C1C22;
    /** The adaptive layers are 108 dp for 72 dp of icon: a quarter of the tile hangs over each edge. */
    private static final float ADAPTIVE_OVERHANG = 0.25f;
    private static final float INSET = 0.17f;
    private static final int PROBE = 16;

    private IconArt() {
    }

    static Drawable square(Resources res, Drawable icon, int sizePx) {
        if (icon == null || sizePx <= 0) return null;
        Drawable d = icon.getConstantState() != null ? icon.getConstantState().newDrawable(res).mutate() : icon;
        Bitmap bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        if (d instanceof AdaptiveIconDrawable) {
            AdaptiveIconDrawable adaptive = (AdaptiveIconDrawable) d;
            int over = Math.round(sizePx * ADAPTIVE_OVERHANG);
            for (Drawable layer : new Drawable[]{adaptive.getBackground(), adaptive.getForeground()}) {
                if (layer == null) continue;
                layer.setBounds(-over, -over, sizePx + over, sizePx + over);
                layer.draw(c);
            }
        } else if (fillsSquare(d)) {
            d.setBounds(0, 0, sizePx, sizePx);
            d.draw(c);
        } else {
            c.drawColor(GLASS);
            int inset = Math.round(sizePx * INSET);
            d.setBounds(inset, inset, sizePx - inset, sizePx - inset);
            d.draw(c);
        }
        return new BitmapDrawable(res, bmp);
    }

    /** Opaque at the middle of every edge: a square icon meant to reach the tile's edges. */
    static boolean fillsSquare(Drawable d) {
        Bitmap probe = Bitmap.createBitmap(PROBE, PROBE, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(probe);
        d.setBounds(0, 0, PROBE, PROBE);
        d.draw(c);
        int mid = PROBE / 2, edge = 1, far = PROBE - 1 - edge;
        boolean opaque = opaque(probe, mid, edge) && opaque(probe, mid, far)
                && opaque(probe, edge, mid) && opaque(probe, far, mid);
        probe.recycle();
        return opaque;
    }

    private static boolean opaque(Bitmap b, int x, int y) {
        return Color.alpha(b.getPixel(x, y)) >= 240;
    }
}
