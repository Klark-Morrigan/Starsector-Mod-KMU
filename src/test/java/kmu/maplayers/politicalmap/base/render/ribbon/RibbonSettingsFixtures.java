package kmu.maplayers.politicalmap.base.render.ribbon;

import kmu.settings.KmuPoliticalMapSettings;

import org.mockito.MockedStatic;

/**
 * Stands in for the player's band knobs wherever a suite has to bake a band but is not about the
 * sizes it is baked at.
 *
 * <p>Needed at all because reading a knob for real reaches LunaLib, which no test JVM has a game
 * to load; and shared because the alternative is the same six stubbings written out in each suite
 * that happens to run the bake, where one of them drifting would show as a band mysteriously
 * absent from that suite alone.
 */
public final class RibbonSettingsFixtures {

    // The shipped sizes, so a band baked under these is the band a player sees, and a cell built
    // to hold "the default band" in one suite holds the same one in another.
    private static final double BAND_WIDTH_WORLD = 120.0;
    private static final double BAND_INSET_PAD_WORLD = 60.0;
    private static final int SEGMENT_LENGTH_UNITS = 3;
    private static final int INTERJECTION_LENGTH_UNITS = 1;
    private static final double MIN_DRAWN_WIDTH_PIXELS = 1.5;

    // Stubs only; never instantiated.
    private RibbonSettingsFixtures() {
    }

    /**
     * Answers the band knobs with the shipped sizes and the bands switched on.
     *
     * @param settingsMock an open seam over the political map's settings, which the caller owns
     *                     and closes; a suite wanting the bands off re-stubs the switch on top
     */
    public static void stubBandsOnAtShippedSizes(
            MockedStatic<KmuPoliticalMapSettings> settingsMock) {

        settingsMock
            .when(KmuPoliticalMapSettings::shouldDrawPoliticalMapRibbons)
            .thenReturn(true);
        settingsMock
            .when(KmuPoliticalMapSettings::getPoliticalMapRibbonWidth)
            .thenReturn(BAND_WIDTH_WORLD);
        settingsMock
            .when(KmuPoliticalMapSettings::getPoliticalMapRibbonInsetPad)
            .thenReturn(BAND_INSET_PAD_WORLD);
        settingsMock
            .when(KmuPoliticalMapSettings::getPoliticalMapRibbonSegmentLength)
            .thenReturn(SEGMENT_LENGTH_UNITS);
        settingsMock
            .when(KmuPoliticalMapSettings::getPoliticalMapRibbonInterjectionLength)
            .thenReturn(INTERJECTION_LENGTH_UNITS);
        settingsMock
            .when(KmuPoliticalMapSettings::getPoliticalMapRibbonMinDrawnWidth)
            .thenReturn(MIN_DRAWN_WIDTH_PIXELS);
    }
}
