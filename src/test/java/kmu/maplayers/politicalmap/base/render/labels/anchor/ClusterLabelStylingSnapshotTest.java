package kmu.maplayers.politicalmap.base.render.labels.anchor;

import kmlib.starsector.factions.FactionPalette;
import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.ViewGrouping;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.politics.DominantHolder;
import kmu.maplayers.politicalmap.base.render.ContentInputs;
import kmu.maplayers.politicalmap.base.render.ContentInputsFixtures;
import kmu.maplayers.politicalmap.base.render.territories.MapStyling;
import kmu.maplayers.politicalmap.base.render.territories.PoliticalMapTerritories;
import kmu.maplayers.politicalmap.base.render.territories.PoliticalMapTerritoryFixtures;
import kmu.maplayers.politicalmap.base.render.territories.SystemOccupancy;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Pins the read that turns a finished build into the four values a label rebuild styles from.
 * The point of the type is that those four describe one pass, so every assertion here is an
 * identity check against the territories' own retained state rather than a value comparison:
 * a snapshot that copied or re-derived any of them could drift from the fills it must match.
 */
final class ClusterLabelStylingSnapshotTest {

    private static final String SPOTLIT_BLOC_ID = "hegemony";

    @Nested
    class ResolveFrom {

        @Test
        void resolveFromCarriesTheTerritoriesOwnHoldersAndDesaturationPalette() {

            var holderBySystemKey = Map.of(
                buildCellKey("corvus"),
                new DominantHolder(
                    SPOTLIT_BLOC_ID,
                    Color.BLUE,
                    Color.DARK_GRAY));

            var desaturationPalette = new FactionPalette(Color.LIGHT_GRAY, Color.GRAY);

            var territories = buildTerritories(
                holderBySystemKey,
                desaturationPalette,
                buildViewGrouping(),
                buildSpotlightPicks());

            var styling = ClusterLabelStylingSnapshot.resolveFrom(territories);

            // Against what the built map itself hands out rather than against what was handed to
            // it: the holders are the occupancy's own, so identity here is what says the snapshot
            // reads them live rather than taking a copy that a later refresh would leave behind.
            assertThat(styling.holderBySystemKey())
                .isSameAs(territories.getHolderBySystemKey());

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
                buildTerritories(
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
                buildTerritories(
                    Map.of(),
                    new FactionPalette(Color.GRAY, Color.GRAY),
                    buildViewGrouping(),
                    ContentInputs.createEmpty()));

            assertThat(styling.contentInputs().isFiltering())
                .isFalse();
        }
    }

    private static PoliticalMapTerritories buildTerritories(
            Map<SystemKey, DominantHolder> holderBySystemKey,
            FactionPalette desaturationPalette,
            ViewGrouping viewGrouping,
            ContentInputs contentInputs) {

        return new PoliticalMapTerritories(
            SystemOccupancy.createCopyOf(holderBySystemKey, Set.of(), Set.of()),
            new LinkedHashSet<>(),
            new MapStyling(
                null,
                PoliticalMapTerritoryFixtures.NEUTRAL_PALETTE,
                desaturationPalette,
                PoliticalMapTerritoryFixtures.NEUTRAL_PALETTE),
            viewGrouping,
            contentInputs,
            Set.of());
    }

    // The view is a seam the snapshot only carries, so a mock stands in for whichever concrete
    // view was being painted.
    private static ViewGrouping buildViewGrouping() {
        return new ViewGrouping(mock(PoliticalMapView.class), HolderGrouping.identity());
    }

    private static ContentInputs buildSpotlightPicks() {
        return ContentInputsFixtures.createInputsSpotlighting(SPOTLIT_BLOC_ID);
    }
}
