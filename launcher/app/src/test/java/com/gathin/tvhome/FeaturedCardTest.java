package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FeaturedCardTest {
    private static final float DENSITY = 2f;   // a 1080p TV

    @Test public void theCardFillsTheSpaceAboveTheDockBand() {
        // 250 px above the band (5 columns, one row under the dock): minus the gap to the band and the top inset.
        assertEquals(250 - 36, FeaturedCard.heightFor(250, DENSITY));
    }

    @Test public void aTallSpaceCapsTheCard() {
        assertEquals(400, FeaturedCard.heightFor(1000, DENSITY));
    }

    @Test public void aShortSpaceStillGivesAReadableCard() {
        assertEquals(144, FeaturedCard.heightFor(60, DENSITY));
    }

    @Test public void theGridLeavesRoomForTheSmallestCard() {
        assertEquals(144 + 36, FeaturedCard.minSpacePx(DENSITY));
    }

    @Test public void theCardSitsAGapAboveTheBand() {
        assertEquals(484f - 28f - 214f, FeaturedCard.topFor(484f, 214, DENSITY), 0.001f);
    }

    @Test public void theBannerKeepsItsSixteenByNineShape() {
        // Card 214 px tall with 24 px of padding above and below: a 166 px tall banner.
        assertEquals(295, FeaturedCard.bannerWidthFor(214, DENSITY));
    }
}
