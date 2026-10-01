package kmu.maplayers.ownermap.render.labels;

import kmlib.starsector.systems.SystemKey;
import kmlib.starsector.ui.font.StarsectorFont;

import kmu.maplayers.ownermap.ContentInputs;
import kmu.maplayers.ownermap.ContentInputsFixtures;
import kmu.maplayers.ownermap.OwnerPaintedView;
import kmu.maplayers.ownermap.ViewReading;
import kmu.maplayers.ownermap.owners.OwnerPalette;
import kmu.maplayers.ownermap.owners.OwnerReadingFake;
import kmu.maplayers.ownermap.owners.OwnerSourceFake;
import kmu.maplayers.ownermap.owners.SystemOwner;
import kmu.maplayers.ownermap.render.clusters.MapStyling;
import kmu.maplayers.ownermap.render.clusters.OwnerMapBuildInputs;
import kmu.maplayers.ownermap.render.clusters.OwnerMapClusterFixtures;
import kmu.maplayers.ownermap.render.clusters.OwnerMapClusters;
import kmu.maplayers.ownermap.render.clusters.SystemOccupancy;
import kmu.maplayers.ownermap.render.style.HolderCategories;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.Map;
import java.util.Set;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Pins the read that turns a finished build into the values a label rebuild styles from.
 * The point of the type is that they describe one pass, so every assertion here is an
 * identity check against the clusters' own retained state rather than a value comparison:
 * a snapshot that copied or re-derived any of them could drift from the fills it must match.
 */
final class ClusterLabelStylingSnapshotTests {

    private static final String SPOTLIT_BLOC_ID = "hegemony";

    @Nested
    class ResolveFrom {

        @Test
        void carriesTheClustersOwnHoldersAndDesaturationPalette() {

            var holderBySystemKey = Map.of(
                buildCellKey("corvus"),
                new SystemOwner(
                    SPOTLIT_BLOC_ID,
                    new OwnerPalette(Color.BLUE, Color.DARK_GRAY)));

            var desaturationPalette = new OwnerPalette(Color.LIGHT_GRAY, Color.GRAY);

            var clusters = buildClusters(
                holderBySystemKey,
                desaturationPalette,
                buildViewReading(),
                buildSpotlightPicks());

            var styling = ClusterLabelStylingSnapshot.resolveFrom(clusters, StarsectorFont.VANILLA_INSIGNIA_42);

            // Against what the built map itself hands out rather than against what was handed to
            // it: the holders are the occupancy's own, so identity here is what says the snapshot
            // reads them live rather than taking a copy that a later refresh would leave behind.
            assertThat(styling.holderBySystemKey())
                .isSameAs(clusters.getOccupancy().getHolderBySystemKey());

            assertThat(styling.desaturationPalette())
                .isSameAs(desaturationPalette);
        }

        @Test
        void carriesTheBuildsReadingCategoriesAndSampledPicksWhole() {
            // Taken as the retained values rather than re-resolved, so the reading a label is named
            // under, the categories its name style comes from, the filter it recedes by and the
            // format it is spelled in cannot come from two passes.
            var viewReading = buildViewReading();
            var contentInputs = buildSpotlightPicks();

            var clusters = buildClusters(
                Map.of(),
                new OwnerPalette(Color.GRAY, Color.GRAY),
                viewReading,
                contentInputs);

            var styling = ClusterLabelStylingSnapshot.resolveFrom(clusters, StarsectorFont.VANILLA_INSIGNIA_42);

            assertThat(styling.reading())
                .isSameAs(viewReading.reading());
            assertThat(styling.categories())
                .isSameAs(clusters.getBuildInputs().styling().categories());
            assertThat(styling.contentInputs())
                .isSameAs(contentInputs);
        }

        @Test
        void carriesAnUnfilteredPassAsUnfiltered() {
            // A pass with no spotlight still answers the filter question, so the label rebuild
            // reads "nothing recedes" off the snapshot rather than off a null it must interpret.
            var styling = ClusterLabelStylingSnapshot.resolveFrom(
                buildClusters(
                    Map.of(),
                    new OwnerPalette(Color.GRAY, Color.GRAY),
                    buildViewReading(),
                    ContentInputs.createEmpty()),
                StarsectorFont.VANILLA_INSIGNIA_42);

            assertThat(styling.contentInputs().isFiltering())
                .isFalse();
        }

        @Test
        void carriesTheFaceTheSectorSettledItsLabelsOn() {
            // Settled by the cache that asked for the rebuild, so the names are fitted in the face
            // they are minted in rather than in one the snapshot chose for itself.
            var styling = ClusterLabelStylingSnapshot.resolveFrom(
                buildClusters(
                    Map.of(),
                    new OwnerPalette(Color.GRAY, Color.GRAY),
                    buildViewReading(),
                    ContentInputs.createEmpty()),
                StarsectorFont.VANILLA_INSIGNIA_25);

            assertThat(styling.labelFace())
                .isEqualTo(StarsectorFont.VANILLA_INSIGNIA_25);
        }
    }

    private static OwnerMapClusters buildClusters(
            Map<SystemKey, SystemOwner> holderBySystemKey,
            OwnerPalette desaturationPalette,
            ViewReading viewReading,
            ContentInputs contentInputs) {

        return new OwnerMapClusters(
            SystemOccupancy.createCopyOf(holderBySystemKey, Set.of(), Set.of()),
            new OwnerMapBuildInputs(
                new MapStyling(
                    null,
                    HolderCategories.INSTANCE,
                    OwnerMapClusterFixtures.NEUTRAL_PALETTE,
                    desaturationPalette,
                    OwnerMapClusterFixtures.NEUTRAL_PALETTE),
                viewReading,
                contentInputs,
                Set.of(),
                Set.of()));
    }

    // The view is a seam the snapshot never reads, so a mock stands in for whichever concrete view
    // was being painted; the reading is the one it carries on.
    private static ViewReading buildViewReading() {
        return new ViewReading(
            mock(OwnerPaintedView.class),
            OwnerReadingFake.createAnsweringNothing(),
            new OwnerSourceFake());
    }

    private static ContentInputs buildSpotlightPicks() {
        return ContentInputsFixtures.createInputsSpotlighting(SPOTLIT_BLOC_ID);
    }
}
