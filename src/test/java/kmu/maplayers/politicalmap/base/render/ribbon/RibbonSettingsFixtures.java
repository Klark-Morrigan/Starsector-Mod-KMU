package kmu.maplayers.politicalmap.base.render.ribbon;

import kmu.settings.KmuPoliticalMapSettings;
import kmu.settings.RibbonNameClearanceChoice;

import org.mockito.MockedStatic;

/**
 * Stands in for the player's band knobs wherever a suite has to bake a band but is not about the
 * sizes it is baked at.
 *
 * <p>Needed at all because reading a knob for real reaches LunaLib, which no test JVM has a game
 * to load; and shared because the alternative is the same handful of stubbings written out in each
 * suite that happens to run the bake, where one of them drifting would show as a band mysteriously
 * absent from that suite alone.
 */
public final class RibbonSettingsFixtures {

    // Sizes a band draws at and a four-thousand-unit cell holds, which is all any suite reaching
    // for these needs. Deliberately not the shipped sizes: the CSV row and its fallback constant
    // are pinned against each other, and a third copy claiming to be the same number would be the
    // only one nothing checks - so a default moved on the settings screen would leave this quietly
    // disagreeing while still reading as authoritative.
    private static final double BAND_WIDTH_WORLD = 120.0;
    private static final double BAND_INSET_PAD_WORLD = 60.0;
    private static final int SEGMENT_LENGTH_UNITS = 3;
    private static final int INTERJECTION_LENGTH_UNITS = 1;

    // The uncontested cells left bare, so a suite reaching for these bakes only the bands that
    // report a contest. A suite about what a lone holder's cell draws states that arm itself,
    // where the answer it is posing is visible beside the case.
    private static final boolean UNCONTESTED_CELLS_BANDED = false;
    private static final boolean UNCONTESTED_RUNS_SHORTENED = false;

    // The bands kept clear of the names, which is the shipped answer and the one every case about
    // the carve is written against - a suite reaching for these and getting the other answer would
    // see its name boxes silently dropped and its band run the whole ring. A suite about the knob
    // itself states it on the case, for the reason the pair above are stated there.
    private static final boolean BANDS_KEPT_CLEAR_OF_NAMES = true;

    // The placements' own boxes rather than the drawn words, which is not the shipped answer and is
    // the only stubbing here that is not. The words are measured with the map-label face, and no
    // test JVM can load one, so under the shipped answer every name would measure as nothing and a
    // suite about the carve would silently be posing a map with no names on it. The fitted box is
    // read off the placement alone, so it is the reading a hand-built placement can actually state.
    private static final RibbonNameClearanceChoice NAME_CLEARANCE =
        RibbonNameClearanceChoice.FITTED_BOX;

    // Stubs only; never instantiated.
    private RibbonSettingsFixtures() {
    }

    /**
     * Answers the band knobs with the bands switched on, at sizes a band draws at.
     *
     * @param settingsMock an open seam over the political map's settings, which the caller owns
     *                     and closes; a suite wanting the bands off re-stubs the switch on top
     */
    public static void stubBandsOnAtSizesThatDraw(
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
            .when(KmuPoliticalMapSettings::shouldDrawPoliticalMapUncontestedRibbons)
            .thenReturn(UNCONTESTED_CELLS_BANDED);
        settingsMock
            .when(KmuPoliticalMapSettings::shouldShortenPoliticalMapUncontestedRibbonRuns)
            .thenReturn(UNCONTESTED_RUNS_SHORTENED);
        settingsMock
            .when(KmuPoliticalMapSettings::shouldKeepPoliticalMapRibbonsClearOfNames)
            .thenReturn(BANDS_KEPT_CLEAR_OF_NAMES);
        settingsMock
            .when(KmuPoliticalMapSettings::getPoliticalMapRibbonNameClearance)
            .thenReturn(NAME_CLEARANCE);
    }
}
