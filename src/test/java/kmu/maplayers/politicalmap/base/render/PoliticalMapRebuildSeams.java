package kmu.maplayers.politicalmap.base.render;

import com.fs.starfarer.api.Global;

import kmu.maplayers.base.sidebar.FilterSelection;
import kmu.maplayers.base.visibility.systems.MapVisibilityRules;
import kmu.maplayers.politicalmap.base.FactionNameFormatChoice;
import kmu.maplayers.politicalmap.base.NameFormatPreference;
import kmu.maplayers.politicalmap.base.dominance.weighting.DominanceRules;
import kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures;
import kmu.maplayers.politicalmap.base.render.style.RenderStyleReader;
import kmu.maplayers.politicalmap.base.render.territories.PoliticalMapTerritoryFixtures;
import kmu.settings.KmuLunaSettings;
import kmu.settings.KmuMapLayerSettings;
import kmu.settings.KmuPoliticalMapDiagnosticsSettings;
import kmu.settings.KmuPoliticalMapGeometrySettings;
import kmu.settings.KmuPoliticalMapRibbonSettings;

import org.apache.log4j.Logger;
import org.mockito.MockedStatic;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;

/**
 * Everything a real political-map rebuild reaches that no test JVM answers, stood in for in one
 * place: the logger, and the live LunaLib reads each stage of the rebuild is configured by.
 *
 * <p>Which classes those are is a fact about what a rebuild touches rather than about any one
 * suite's cases, which is why it is answered here. A stage that starts reading a new knob otherwise
 * breaks every suite driving a whole rebuild at once, each in its own copy of the same arrangement.
 *
 * <p>Four seams are handed back rather than answered here, because they are what those suites
 * legitimately disagree on: which sector the game is running, whether the bands are on, what the
 * player is allowed to see, and which bloc is spotlighted. The last is handed back for the suites
 * whose subject is what a rebuild is owed, since a pick moved between two frames is how such a case
 * is posed at all. Everything else answers the same way for any rebuild, so a caller states only
 * what it varies.
 *
 * <p>The suites that stage a rebuild's inputs rather than run one - the incremental fold's, which
 * hand-builds its cells and paints each category apart - deliberately do not take this. They would
 * have to override half of what it opened, and a fixture whose callers undo it is worse than the
 * lines it saves; they hold a {@link StaticSeams} directly instead.
 */
final class PoliticalMapRebuildSeams {

    // The cells' seed knobs, wide enough that a cell holds clear of its own inset border.
    private static final int CELL_BOUND_SEGMENTS = 16;
    private static final double CELL_RADIUS = 4000.0;

    private final StaticSeams seams = new StaticSeams();

    private final MockedStatic<Global> globalSeam;
    private final MockedStatic<KmuPoliticalMapRibbonSettings> ribbonSettingsSeam;
    private final MockedStatic<MapVisibilityRules> visibilityRulesSeam;
    private final MockedStatic<FilterSelection> filterSelectionSeam;

    private PoliticalMapRebuildSeams() {

        globalSeam = seams.openSeam(Global.class);

        // A real logger rather than a mock, and this is not optional: a class whose static LOG field
        // is first resolved inside this seam keeps whatever it was handed for the rest of the JVM, so
        // a null there faults every later suite that logs a line. Named for this fixture, the name
        // reaching nothing any suite asserts.
        globalSeam
            .when(() -> Global.getLogger(any(Class.class)))
            .thenReturn(Logger.getLogger(PoliticalMapRebuildSeams.class));

        // The dev reveal, the anchor tuning and the dev overlays, all LunaLib-backed: no rebuild
        // claim turns on any of the three, so the seam's own answers stand for them.
        seams.openSeam(KmuMapLayerSettings.class);
        seams.openSeam(KmuLunaSettings.class);
        seams.openSeam(KmuPoliticalMapDiagnosticsSettings.class);

        // No bloc spotlighted, which the seam's own null answers - the pick is sector-memory state
        // no test JVM has.
        filterSelectionSeam = seams.openSeam(FilterSelection.class);

        var geometrySettingsSeam = seams.openSeam(KmuPoliticalMapGeometrySettings.class);
        geometrySettingsSeam
            .when(KmuPoliticalMapGeometrySettings::getPoliticalMapCellBoundSegments)
            .thenReturn(CELL_BOUND_SEGMENTS);
        geometrySettingsSeam
            .when(KmuPoliticalMapGeometrySettings::getPoliticalMapCellRadius)
            .thenReturn(CELL_RADIUS);

        // Opened unanswered, so the bands are off unless a caller says otherwise: a rebuild whose
        // claim is not about the bands should not be paying to bake them.
        ribbonSettingsSeam = seams.openSeam(KmuPoliticalMapRibbonSettings.class);

        visibilityRulesSeam = seams.openSeam(MapVisibilityRules.class);
        visibilityRulesSeam
            .when(MapVisibilityRules::readFromLunaSettings)
            .thenReturn(MapVisibilityRules.BASE);

        // The weighting rule the fills and the bands are both resolved under, read live off LunaLib
        // in production - left to the settings seam it would weigh every colony at nothing and leave
        // the sector unheld, which is the one state that makes a holder assertion vacuous.
        var dominanceRulesSeam = seams.openSeam(DominanceRules.class);
        dominanceRulesSeam
            .when(DominanceRules::readFromLunaSettings)
            .thenReturn(SectorPoliticsFixtures.buildStabilityWeightedRules());

        // One style bundle for every category, so a difference in what was drawn can only have come
        // from the geometry or the holding.
        var renderStyleSeam = seams.openSeam(RenderStyleReader.class);
        renderStyleSeam
            .when(() -> RenderStyleReader.readRenderStyle(anyBoolean()))
            .thenReturn(PoliticalMapTerritoryFixtures.createRenderStyleForEveryCategory(
                PoliticalMapTerritoryFixtures.createInertCategoryStyle()));

        // The names off, which keeps the label mint and the anchor fit off a rebuild that has no
        // font to measure with; the choice is sector-memory state as well.
        var nameFormatSeam = seams.openSeam(NameFormatPreference.class);
        nameFormatSeam
            .when(() -> NameFormatPreference.getSelectedNameFormat(any()))
            .thenReturn(FactionNameFormatChoice.NONE);
    }

    /**
     * Stands in for every class a rebuild reaches, answering each the one way a rebuild wants unless
     * it is one of the four handed back below.
     *
     * @return the open arrangement, which its holder closes on the way out
     */
    static PoliticalMapRebuildSeams openEverySeamARebuildNeeds() {
        return new PoliticalMapRebuildSeams();
    }

    /** Closes every seam this opened. */
    void closeEverySeam() {
        seams.closeEverySeam();
    }

    /**
     * @return the seam standing in for the running game, for a caller to say which sector that is -
     *         commonly the one it installed on, and pointedly not it, for a case posing a stage that
     *         must read its own sector rather than whichever is loaded
     */
    MockedStatic<Global> resolveGlobalSeam() {
        return globalSeam;
    }

    /**
     * @return the seam standing in for the band settings, for a caller whose claim is about the
     *         bands and so needs them switched on at sizes that draw
     */
    MockedStatic<KmuPoliticalMapRibbonSettings> resolveRibbonSettingsSeam() {
        return ribbonSettingsSeam;
    }

    /**
     * @return the seam standing in for the player's visibility settings, for a caller posing a gate
     *         flipped between two rebuilds
     */
    MockedStatic<MapVisibilityRules> resolveVisibilityRulesSeam() {
        return visibilityRulesSeam;
    }

    /**
     * @return the seam standing in for the spotlight pick, for a caller posing a bloc picked or
     *         cleared between two frames
     */
    MockedStatic<FilterSelection> resolveFilterSelectionSeam() {
        return filterSelectionSeam;
    }

}
