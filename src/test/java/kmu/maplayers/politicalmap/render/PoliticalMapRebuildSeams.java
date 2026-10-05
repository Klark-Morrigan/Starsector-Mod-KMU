package kmu.maplayers.politicalmap.render;

import com.fs.starfarer.api.Global;

import kmlib.testfixtures.statics.StaticSeams;

import kmu.maplayers.base.sidebar.FilterSelection;
import kmu.maplayers.base.visibility.systems.MapVisibilityRules;
import kmu.maplayers.ownermap.OwnerMapRebuildSeams;
import kmu.maplayers.ownermap.render.clusters.OwnerMapClusterFixtures;
import kmu.maplayers.ownermap.render.style.RenderStyleReader;
import kmu.maplayers.politicalmap.dominance.DominancePassFixtures;
import kmu.maplayers.politicalmap.dominance.weighting.DominanceRules;
import kmu.settings.KmuMapLabelSettings;
import kmu.settings.KmuOwnerMapDiagnosticsSettings;
import kmu.settings.KmuOwnerMapRibbonSettings;

import org.mockito.MockedStatic;

import static org.mockito.ArgumentMatchers.any;

/**
 * Everything a real political-map rebuild reaches that no test JVM answers: what any layer's rebuild
 * reaches, from {@link OwnerMapRebuildSeams}, beside the weighting rule only this layer reads and the
 * label, diagnostics and theme reads a rebuild drawing no names answers one way.
 *
 * <p>The suites that stage a rebuild's inputs rather than run one - the incremental fold's, which
 * hand-builds its cells and paints each category apart - deliberately do not take this. They would
 * have to override half of what it opened, and a fixture whose callers undo it is worse than the
 * lines it saves; they hold a {@link StaticSeams} directly instead.
 */
public final class PoliticalMapRebuildSeams {

    private final OwnerMapRebuildSeams ownerMapSeams = OwnerMapRebuildSeams.openEverySeamARebuildNeeds();

    private PoliticalMapRebuildSeams() {

        // The anchor tuning and the dev overlays, both LunaLib-backed: no rebuild claim turns on
        // either, so the seam's own answers stand for them.
        ownerMapSeams.openFurtherSeam(KmuMapLabelSettings.class);
        ownerMapSeams.openFurtherSeam(KmuOwnerMapDiagnosticsSettings.class);

        // The weighting rule the fills and the bands are both resolved under, read live off LunaLib
        // in production - left to the settings seam it would weigh every colony at nothing and leave
        // the sector unheld, which is the one state that makes a holder assertion vacuous.
        ownerMapSeams.openFurtherSeam(DominanceRules.class)
            .when(DominanceRules::readFromLunaSettings)
            .thenReturn(DominancePassFixtures.buildStabilityWeightedRules());

        // One style bundle for every category, so a difference in what was drawn can only have come
        // from the geometry or the holding.
        ownerMapSeams.openFurtherSeam(RenderStyleReader.class)
            .when(() -> RenderStyleReader.readRenderStyle(any(), any()))
            .thenReturn(OwnerMapClusterFixtures.createRenderStyleForEveryCategory(
                OwnerMapClusterFixtures.createInertCategoryStyle()));
    }

    /**
     * Stands in for every class a political rebuild reaches, answering each the one way a rebuild
     * wants unless it is one of the four handed back below.
     *
     * @return the open arrangement, which its holder closes on the way out
     */
    public static PoliticalMapRebuildSeams openEverySeamARebuildNeeds() {
        return new PoliticalMapRebuildSeams();
    }

    /** Closes every seam this opened. */
    public void closeEverySeam() {
        ownerMapSeams.closeEverySeam();
    }

    /**
     * @return the seam standing in for the running game, as {@link OwnerMapRebuildSeams} hands it
     */
    public MockedStatic<Global> resolveGlobalSeam() {
        return ownerMapSeams.resolveGlobalSeam();
    }

    /**
     * @return the seam standing in for the band settings, as {@link OwnerMapRebuildSeams} hands it
     */
    public MockedStatic<KmuOwnerMapRibbonSettings> resolveRibbonSettingsSeam() {
        return ownerMapSeams.resolveRibbonSettingsSeam();
    }

    /**
     * @return the seam standing in for the player's visibility settings, as
     *         {@link OwnerMapRebuildSeams} hands it
     */
    public MockedStatic<MapVisibilityRules> resolveVisibilityRulesSeam() {
        return ownerMapSeams.resolveVisibilityRulesSeam();
    }

    /**
     * @return the seam standing in for the spotlight pick, as {@link OwnerMapRebuildSeams} hands it
     */
    public MockedStatic<FilterSelection> resolveFilterSelectionSeam() {
        return ownerMapSeams.resolveFilterSelectionSeam();
    }
}
