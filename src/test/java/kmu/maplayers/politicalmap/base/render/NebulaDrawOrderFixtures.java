package kmu.maplayers.politicalmap.base.render;

import kmu.settings.KmuPoliticalMapSettings;
import kmu.settings.NebulaDrawOrderChoice;

import org.mockito.MockedStatic;

/**
 * Stands in for the player's four nebula draw-order choices wherever a suite has to drive the
 * overlay's band routing.
 *
 * <p>Needed at all because reading a choice for real reaches LunaLib, which no test JVM has a game
 * to load - and a suite that stubbed only some of the four would drive a layout resolved from
 * nulls. Shared because the four reads have to be answered in the order the layout states them,
 * and four settings of one type are interchangeable by the compiler: written out per suite, a
 * transposed pair would drive a layout other than the one the case names, and both the cases
 * asserting presence and the cases asserting absence can pass anyway.
 */
final class NebulaDrawOrderFixtures {

    // Stubs only; never instantiated.
    private NebulaDrawOrderFixtures() {
    }

    /**
     * Answers the four choices with the cell geometry below the map's nebulae and the two readouts
     * laid over cells above them - the picture the overlay painted before the group existed, and
     * the background a case about anything else wants.
     *
     * <p>Stated as a layout rather than read back as "the defaults": what the shipped defaults are
     * is settled between the CSV row and its fallback constant, which are pinned against each
     * other, and a third copy claiming to be those numbers would be the one nothing checks.
     *
     * @param settingsMock an open seam over the political map's settings, which the caller owns
     *                     and closes; a case wanting another layout stubs it on top
     */
    static void stubGeometryBelowAndReadoutsAbove(
            MockedStatic<KmuPoliticalMapSettings> settingsMock) {

        stubChosenDrawOrders(
            settingsMock,
            NebulaDrawOrderChoice.BELOW,
            NebulaDrawOrderChoice.BELOW,
            NebulaDrawOrderChoice.ABOVE,
            NebulaDrawOrderChoice.ABOVE);
    }

    /**
     * Answers the four choices one by one, in the order the layout states them.
     *
     * @param settingsMock     an open seam over the political map's settings, which the caller owns
     *                         and closes
     * @param fillDrawOrder    which side the cluster and lone-cell fills are asked for
     * @param borderDrawOrder  which side the cluster boundaries and seams are asked for; a layout
     *                         resolves this upward where the fills went above it
     * @param ribbonDrawOrder  which side the per-cell presence bands are asked for
     * @param labelDrawOrder   which side the cluster names are asked for
     */
    static void stubChosenDrawOrders(
            MockedStatic<KmuPoliticalMapSettings> settingsMock,
            NebulaDrawOrderChoice fillDrawOrder,
            NebulaDrawOrderChoice borderDrawOrder,
            NebulaDrawOrderChoice ribbonDrawOrder,
            NebulaDrawOrderChoice labelDrawOrder) {

        settingsMock
            .when(KmuPoliticalMapSettings::getPoliticalMapFillNebulaDrawOrder)
            .thenReturn(fillDrawOrder);
        settingsMock
            .when(KmuPoliticalMapSettings::getPoliticalMapBorderNebulaDrawOrder)
            .thenReturn(borderDrawOrder);
        settingsMock
            .when(KmuPoliticalMapSettings::getPoliticalMapRibbonNebulaDrawOrder)
            .thenReturn(ribbonDrawOrder);
        settingsMock
            .when(KmuPoliticalMapSettings::getPoliticalMapLabelNebulaDrawOrder)
            .thenReturn(labelDrawOrder);
    }
}
