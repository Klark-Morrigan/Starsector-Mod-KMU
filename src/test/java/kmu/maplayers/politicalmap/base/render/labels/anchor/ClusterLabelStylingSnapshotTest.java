package kmu.maplayers.politicalmap.base.render.labels.anchor;

import kmlib.starsector.factions.FactionPalette;

import kmu.maplayers.base.theme.ElementStyleAdjustment;
import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.politics.DominantHolder;
import kmu.maplayers.politicalmap.base.render.territories.FilterSnapshot;
import kmu.maplayers.politicalmap.base.render.territories.MapStyling;
import kmu.maplayers.politicalmap.base.render.territories.PoliticalMapTerritories;
import kmu.maplayers.politicalmap.base.render.territories.PoliticalMapTerritoryFixtures;
import kmu.maplayers.politicalmap.base.render.territories.ViewGrouping;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

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
            var holderBySystemId = Map.of("corvus", new DominantHolder(
                SPOTLIT_BLOC_ID, Color.BLUE, Color.DARK_GRAY));
            var desaturationPalette = new FactionPalette(Color.LIGHT_GRAY, Color.GRAY);

            var styling = ClusterLabelStylingSnapshot.resolveFrom(
                buildTerritories(holderBySystemId, desaturationPalette,
                    buildViewGrouping(), buildSpotlightFilter()));

            assertThat(styling.holderBySystemId()).isSameAs(holderBySystemId);
            assertThat(styling.desaturationPalette()).isSameAs(desaturationPalette);
        }

        @Test
        void resolveFromCarriesTheViewGroupingAndFilterWhole() {
            // Taken as the two retained records rather than unpacked and recombined, so the view
            // a label is named under and the filter it recedes by cannot come from two passes.
            var viewGrouping = buildViewGrouping();
            var filterSnapshot = buildSpotlightFilter();

            var styling = ClusterLabelStylingSnapshot.resolveFrom(
                buildTerritories(Map.of(), new FactionPalette(Color.GRAY, Color.GRAY),
                    viewGrouping, filterSnapshot));

            assertThat(styling.viewGrouping()).isSameAs(viewGrouping);
            assertThat(styling.filterSnapshot()).isSameAs(filterSnapshot);
        }

        @Test
        void resolveFromCarriesAnUnfilteredPassAsUnfiltered() {
            // A pass with no spotlight still answers the filter question, so the label rebuild
            // reads "nothing recedes" off the snapshot rather than off a null it must interpret.
            var styling = ClusterLabelStylingSnapshot.resolveFrom(
                buildTerritories(Map.of(), new FactionPalette(Color.GRAY, Color.GRAY),
                    buildViewGrouping(), FilterSnapshot.unfiltered()));

            assertThat(styling.filterSnapshot().isFiltering()).isFalse();
        }
    }

    private static PoliticalMapTerritories buildTerritories(
            Map<String, DominantHolder> holderBySystemId,
            FactionPalette desaturationPalette,
            ViewGrouping viewGrouping,
            FilterSnapshot filterSnapshot) {

        return new PoliticalMapTerritories(
            holderBySystemId,
            new LinkedHashSet<>(),
            new LinkedHashSet<>(),
            new MapStyling(
                null,
                PoliticalMapTerritoryFixtures.NEUTRAL_PALETTE,
                desaturationPalette,
                PoliticalMapTerritoryFixtures.NEUTRAL_PALETTE),
            viewGrouping,
            filterSnapshot);
    }

    // The view is a seam the snapshot only carries, so a mock stands in for whichever concrete
    // view was being painted.
    private static ViewGrouping buildViewGrouping() {
        return new ViewGrouping(mock(PoliticalMapView.class), HolderGrouping.identity());
    }

    private static FilterSnapshot buildSpotlightFilter() {
        return new FilterSnapshot(
            SPOTLIT_BLOC_ID,
            ElementStyleAdjustment.NONE,
            Set.of("corvus"),
            Set.of());
    }
}
