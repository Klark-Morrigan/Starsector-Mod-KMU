package kmu.maplayers.politicalmap.base.render;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

import kmu.maplayers.politicalmap.base.BlocStyleAdjustment;
import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.geometry.ShapedCell;
import kmu.maplayers.politicalmap.base.politics.DominantOwner;
import kmu.maplayers.politicalmap.base.politics.OwnershipGrouping;
import kmu.maplayers.politicalmap.base.render.model.DesaturationPalette;
import kmu.maplayers.politicalmap.base.render.model.MapStyle;
import kmu.maplayers.politicalmap.base.render.model.PoliticalMapDrawables;
import kmu.settings.DesaturationProfileChoice;
import kmu.settings.FactionPaletteChoice;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the builder's off-engine, deterministic pieces: the palette-color pick that maps
 * a player's color choice to a palette shade, the desaturation-palette resolver, and
 * an owned cell's Step 3 style-adjustment application (palette swap and opacity scale).
 * The rest shapes cells and reads settings that only resolve in-engine; the cluster-anchor
 * fit lives in {@link ClusterAnchorsBuilder} and is pinned by its own suite.
 */
final class DrawablesBuilderTest {

    // Two distinct shades so a pick can be told apart from its counterpart.
    private static final Color PRIMARY = Color.RED;
    private static final Color SECONDARY = Color.BLUE;

    @Nested
    class PickPaletteColor {

        @Test
        void pickPaletteColorReturnsThePrimaryShadeForAPrimaryChoice() {
            assertThat(DrawablesBuilder.pickPaletteColor(
                    FactionPaletteChoice.PRIMARY, PRIMARY, SECONDARY)).isEqualTo(PRIMARY);
        }

        @Test
        void pickPaletteColorReturnsTheSecondaryShadeForASecondaryChoice() {
            assertThat(DrawablesBuilder.pickPaletteColor(
                    FactionPaletteChoice.SECONDARY, PRIMARY, SECONDARY)).isEqualTo(SECONDARY);
        }

        @Test
        void pickPaletteColorReturnsNullForNoColor() {
            // NONE is the player's "No color" choice; a null color signals the render
            // layer to skip that element.
            assertThat(DrawablesBuilder.pickPaletteColor(
                    FactionPaletteChoice.NONE, PRIMARY, SECONDARY)).isNull();
        }
    }

    @Nested
    class ResolveDesaturationPalette {

        @Test
        void resolveDesaturationPaletteForgesTheIndependentFactionsSharesUnderTheIndependentProfile() {
            var independentMock = mock(FactionAPI.class);
            when(independentMock.getBrightUIColor()).thenReturn(Color.GREEN);
            when(independentMock.getDarkUIColor()).thenReturn(Color.YELLOW);
            var sectorMock = mock(SectorAPI.class);
            when(sectorMock.getFaction(Factions.INDEPENDENT)).thenReturn(independentMock);

            var palette = DrawablesBuilder.resolveDesaturationPalette(
                    DesaturationProfileChoice.INDEPENDENT, sectorMock, Color.GRAY);

            assertThat(palette).isEqualTo(new DesaturationPalette(Color.GREEN, Color.YELLOW));
        }

        @Test
        void resolveDesaturationPaletteYieldsTheNeutralColorInBothSlotsUnderTheNeutralProfile() {
            // The Neutral profile never touches the sector, so a bare mock stands in.
            var palette = DrawablesBuilder.resolveDesaturationPalette(
                    DesaturationProfileChoice.NEUTRAL, mock(SectorAPI.class), Color.GRAY);

            assertThat(palette).isEqualTo(new DesaturationPalette(Color.GRAY, Color.GRAY));
        }
    }

    @Nested
    class BuildStyledCellForSystem {

        private static final String SYSTEM_ID = "hegemony-system";
        private static final Color OWNER_PRIMARY = Color.RED;
        private static final Color OWNER_SECONDARY = Color.BLUE;
        private static final Color DESATURATED_PRIMARY = Color.GREEN;
        private static final Color DESATURATED_SECONDARY = Color.YELLOW;
        private static final DominantOwner OWNER =
                new DominantOwner("hegemony", OWNER_PRIMARY, OWNER_SECONDARY);
        // An owned cell's fill and national border are per cluster (in FactionTerritory),
        // so fill and outer are "No color" here and only inner - the interior seam -
        // resolves a real color, the one slot these tests can observe.
        private static final MapStyle STYLE = new MapStyle(
                FactionPaletteChoice.NONE, 1.0,
                FactionPaletteChoice.NONE, 1.0, 3.0,
                FactionPaletteChoice.SECONDARY, 1.0, 1.0);

        @Test
        void buildStyledCellForSystemAppliesTheOpacityMultiplierAndKeepsTheOwnerPaletteWhenNotDesaturated() {
            var styled = DrawablesBuilder.buildStyledCellForSystem(
                    drawablesWith(viewMockAdjusting(new BlocStyleAdjustment(0.5, false))),
                    SYSTEM_ID, ownedCell());

            assertThat(styled.inner().color()).isEqualTo(OWNER_SECONDARY);
            assertThat(styled.inner().alpha()).isEqualTo(0.5f);
        }

        @Test
        void buildStyledCellForSystemDesaturatesToThePassPaletteAtFullOpacityWhenOnlyDesaturateIsSet() {
            var styled = DrawablesBuilder.buildStyledCellForSystem(
                    drawablesWith(viewMockAdjusting(new BlocStyleAdjustment(1.0, true))),
                    SYSTEM_ID, ownedCell());

            assertThat(styled.inner().color()).isEqualTo(DESATURATED_SECONDARY);
            assertThat(styled.inner().alpha()).isEqualTo(1.0f);
        }

        @Test
        void buildStyledCellForSystemMutesAndDesaturatesTogetherWhenBothAreSet() {
            var styled = DrawablesBuilder.buildStyledCellForSystem(
                    drawablesWith(viewMockAdjusting(new BlocStyleAdjustment(0.5, true))),
                    SYSTEM_ID, ownedCell());

            assertThat(styled.inner().color()).isEqualTo(DESATURATED_SECONDARY);
            assertThat(styled.inner().alpha()).isEqualTo(0.5f);
        }

        @Test
        void buildStyledCellForSystemReproducesCurrentOutputForTheNoneAdjustment() {
            // Regression pin: the identity adjustment leaves the owner's own palette and
            // the style's own opacity untouched, exactly as before Step 3.
            var styled = DrawablesBuilder.buildStyledCellForSystem(
                    drawablesWith(viewMockAdjusting(BlocStyleAdjustment.NONE)),
                    SYSTEM_ID, ownedCell());

            assertThat(styled.inner().color()).isEqualTo(OWNER_SECONDARY);
            assertThat(styled.inner().alpha()).isEqualTo(1.0f);
        }

        // A view stub that paints in the full faction style (never independent) and
        // returns the given adjustment for any bloc, so each test names only the
        // adjustment it exercises.
        private static PoliticalMapView viewMockAdjusting(BlocStyleAdjustment adjustment) {
            var viewMock = mock(PoliticalMapView.class);
            when(viewMock.shouldUseIndependentStyle(any(), any())).thenReturn(false);
            when(viewMock.resolveBlocStyleAdjustment(any(), any())).thenReturn(adjustment);
            return viewMock;
        }

        private static PoliticalMapDrawables drawablesWith(PoliticalMapView viewMock) {
            return new PoliticalMapDrawables(new LinkedHashMap<>(), new LinkedHashMap<>(),
                    Map.of(SYSTEM_ID, OWNER), Set.of(), Color.GRAY,
                    new DesaturationPalette(DESATURATED_PRIMARY, DESATURATED_SECONDARY),
                    STYLE, STYLE, STYLE, STYLE, viewMock, OwnershipGrouping.identity());
        }

        // A small, non-empty square cell so the fill-polygon-empty short-circuit never
        // fires; its edges are all interior seams, which this owned-cell path never reads.
        private static ShapedCell ownedCell() {
            return new ShapedCell(
                    List.of(new double[] {0, 0}, new double[] {10, 0},
                            new double[] {10, 10}, new double[] {0, 10}),
                    new boolean[] {false, false, false, false});
        }
    }
}
