package kmu.maplayers.ownermap.render.labels;

import kmlib.starsector.factions.FactionPalette;
import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.ownermap.ContentInputs;
import kmu.maplayers.ownermap.ContentInputsFixtures;
import kmu.maplayers.ownermap.OwnerPaintedView;
import kmu.maplayers.ownermap.ViewGrouping;
import kmu.maplayers.ownermap.holding.HolderGrouping;
import kmu.maplayers.ownermap.owners.SystemOwner;
import kmu.maplayers.ownermap.render.clusters.MapStyling;
import kmu.maplayers.ownermap.render.clusters.OwnerMapBuildInputs;
import kmu.maplayers.ownermap.render.clusters.OwnerMapClusterFixtures;
import kmu.maplayers.ownermap.render.clusters.OwnerMapClusters;
import kmu.maplayers.ownermap.render.clusters.SystemOccupancy;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.Map;
import java.util.Set;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Pins the read that turns a finished build into the four values a label rebuild styles from.
 * The point of the type is that those four describe one pass, so every assertion here is an
 * identity check against the clusters' own retained state rather than a value comparison:
 * a snapshot that copied or re-derived any of them could drift from the fills it must match.
 */
final class ClusterLabelStylingSnapshotTest {

    private static final String SPOTLIT_BLOC_ID = "hegemony";

    @Nested
    class ResolveFrom {

        @Test
        void resolveFromCarriesTheClustersOwnHoldersAndDesaturationPalette() {

            var holderBySystemKey = Map.of(
                buildCellKey("corvus"),
                new SystemOwner(
                    SPOTLIT_BLOC_ID,
                    Color.BLUE,
                    Color.DARK_GRAY));

            var desaturationPalette = new FactionPalette(Color.LIGHT_GRAY, Color.GRAY);

            var clusters = buildClusters(
                holderBySystemKey,
                desaturationPalette,
                buildViewGrouping(),
                buildSpotlightPicks());

            var styling = ClusterLabelStylingSnapshot.resolveFrom(clusters);

            // Against what the built map itself hands out rather than against what was handed to
            // it: the holders are the occupancy's own, so identity here is what says the snapshot
            // reads them live rather than taking a copy that a later refresh would leave behind.
            assertThat(styling.holderBySystemKey())
                .isSameAs(clusters.getOccupancy().getHolderBySystemKey());

            assertThat(styling.desaturationPalette())
                .isSameAs(desaturationPalette);
        }

        @Test
        void resolveFromCarriesTheViewGroupingAndSampledPicksWhole() {
            // Taken as the two retained records rather than unpacked and recombined, so the view a
            // label is named under, the filter it recedes by and the format it is spelled in cannot
            // come from two passes.
            var viewGrouping = buildViewGrouping();
            var contentInputs = buildSpotlightPicks();

            var styling = ClusterLabelStylingSnapshot.resolveFrom(
                buildClusters(
                    Map.of(),
                    new FactionPalette(Color.GRAY, Color.GRAY),
                    viewGrouping,
                    contentInputs));

            assertThat(styling.viewGrouping())
                .isSameAs(viewGrouping);
            assertThat(styling.contentInputs())
                .isSameAs(contentInputs);
        }

        @Test
        void resolveFromCarriesAnUnfilteredPassAsUnfiltered() {
            // A pass with no spotlight still answers the filter question, so the label rebuild
            // reads "nothing recedes" off the snapshot rather than off a null it must interpret.
            var styling = ClusterLabelStylingSnapshot.resolveFrom(
                buildClusters(
                    Map.of(),
                    new FactionPalette(Color.GRAY, Color.GRAY),
                    buildViewGrouping(),
                    ContentInputs.createEmpty()));

            assertThat(styling.contentInputs().isFiltering())
                .isFalse();
        }
    }

    private static OwnerMapClusters buildClusters(
            Map<SystemKey, SystemOwner> holderBySystemKey,
            FactionPalette desaturationPalette,
            ViewGrouping viewGrouping,
            ContentInputs contentInputs) {

        return new OwnerMapClusters(
            SystemOccupancy.createCopyOf(holderBySystemKey, Set.of(), Set.of()),
            new OwnerMapBuildInputs(
                new MapStyling(
                    null,
                    OwnerMapClusterFixtures.NEUTRAL_PALETTE,
                    desaturationPalette,
                    OwnerMapClusterFixtures.NEUTRAL_PALETTE),
                viewGrouping,
                contentInputs,
                Set.of(),
                Set.of()));
    }

    // The view is a seam the snapshot only carries, so a mock stands in for whichever concrete
    // view was being painted.
    private static ViewGrouping buildViewGrouping() {
        return new ViewGrouping(mock(OwnerPaintedView.class), HolderGrouping.identity());
    }

    private static ContentInputs buildSpotlightPicks() {
        return ContentInputsFixtures.createInputsSpotlighting(SPOTLIT_BLOC_ID);
    }
}
