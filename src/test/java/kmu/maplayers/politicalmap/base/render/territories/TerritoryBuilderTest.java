package kmu.maplayers.politicalmap.base.render.territories;

import kmlib.starsector.factions.FactionPalette;

import kmu.maplayers.politicalmap.base.BlocStyleAdjustment;
import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.geometry.CellEdge;
import kmu.maplayers.politicalmap.base.geometry.ShapedCell;
import kmu.maplayers.politicalmap.base.politics.DominantOwner;
import kmu.maplayers.politicalmap.base.politics.OwnershipGrouping;
import kmu.maplayers.politicalmap.base.render.style.BorderSmoothingStyle;
import kmu.maplayers.politicalmap.base.render.style.CategoryStyle;
import kmu.maplayers.politicalmap.base.render.style.GlobalStyle;
import kmu.maplayers.politicalmap.base.render.style.HatchStyle;
import kmu.maplayers.politicalmap.base.render.style.MapCategory;
import kmu.maplayers.politicalmap.base.render.style.RenderStyle;
import kmu.settings.FactionPaletteChoice;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the builder's off-engine, deterministic pieces: the contested-border trace (the interior
 * edges touching a hatched cell) and an owned cell's style-adjustment application (palette swap
 * and opacity scale). The
 * shared styling resolvers it used to hold now live in
 * {@link kmu.maplayers.politicalmap.base.render.style.MapPalettes} and
 * {@link kmu.maplayers.politicalmap.base.render.style.BlocStyleResolver} with their own suites;
 * the rest shapes cells and reads settings that
 * only resolve in-engine, and the cluster-anchor fit is pinned by
 * {@link kmu.maplayers.politicalmap.base.render.labels.anchor.ClusterAnchorsBuilder}.
 */
final class TerritoryBuilderTest {

    // A view stub answering both per-bloc style seams with fixed values, so a test can prove
    // whether the style resolver consulted the view (off filter) or bypassed it (under filter).
    private static PoliticalMapView viewMockDeciding(boolean usesIndependentStyle,
            BlocStyleAdjustment adjustment) {
        var viewMock = mock(PoliticalMapView.class);
        when(viewMock.shouldUseIndependentStyle(any(), any())).thenReturn(usesIndependentStyle);
        when(viewMock.resolveBlocStyleAdjustment(any(), any())).thenReturn(adjustment);
        return viewMock;
    }

    @Nested
    class ComputeContestedBorders {

        @Test
        void emitsTheEdgeWhereADominantCellMeetsAContestedCell() {
            // The solid<->hatched transition is a contested border. Each shared edge is walked from
            // both cells (Voronoi adjacency is symmetric) but emitted once, from the smaller id -
            // here "con" < "dom" - so its coordinates come from the contested cell's edge record.
            var dominantEdges = List.of(new CellEdge(0, 0, 10, 0, "con"));
            var contestedEdges = List.of(new CellEdge(10, 0, 0, 0, "dom"));

            var borders = TerritoryBuilder.computeContestedBorders(List.of("dom", "con"),
                    Set.of("con"), Map.of("dom", dominantEdges, "con", contestedEdges));

            assertThat(borders).containsExactly(10f, 0f, 0f, 0f);
        }

        @Test
        void emitsTheDivisionWhereTwoContestedCellsMeet() {
            // Two joined contested cells' hatch fills fuse into one continuous soup, so their shared
            // edge is stroked to keep them reading as distinct bounded territories.
            var firstEdges = List.of(new CellEdge(0, 0, 5, 5, "con2"));
            var secondEdges = List.of(new CellEdge(5, 5, 0, 0, "con1"));

            var borders = TerritoryBuilder.computeContestedBorders(List.of("con1", "con2"),
                    Set.of("con1", "con2"), Map.of("con1", firstEdges, "con2", secondEdges));

            // Emitted once, from the smaller id ("con1"), so it is not stroked twice.
            assertThat(borders).containsExactly(0f, 0f, 5f, 5f);
        }

        @Test
        void omitsTheEdgeBetweenTwoDominantCells() {
            // Two dominated cells fuse into the solid region with only their faint per-cell interior
            // seam, so their shared edge carries no contested border.
            var firstEdges = List.of(new CellEdge(0, 0, 5, 5, "dom2"));
            var secondEdges = List.of(new CellEdge(5, 5, 0, 0, "dom1"));

            var borders = TerritoryBuilder.computeContestedBorders(List.of("dom1", "dom2"),
                    Set.of(), Map.of("dom1", firstEdges, "dom2", secondEdges));

            assertThat(borders).isEmpty();
        }

        @Test
        void ignoresAnEdgeToASystemOutsideTheFootprint() {
            // A non-spotlit receded rival and a frontier edge (null neighbour) both lie on the
            // footprint's national border, traced elsewhere, so neither is an interior division.
            var contestedEdges = List.of(
                    new CellEdge(0, 0, 10, 0, "receded-rival"),
                    new CellEdge(10, 0, 10, 10, null));

            var borders = TerritoryBuilder.computeContestedBorders(List.of("con"),
                    Set.of("con"), Map.of("con", contestedEdges));

            assertThat(borders).isEmpty();
        }

        @Test
        void skipsAMemberWithNoCellEdges() {
            // A member absent from the edge map (no cell) contributes no border rather than throwing.
            var borders = TerritoryBuilder.computeContestedBorders(List.of("dom"),
                    Set.of("con"), Map.of());

            assertThat(borders).isEmpty();
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
        private static final CategoryStyle STYLE = new CategoryStyle(
                FactionPaletteChoice.NONE, 1.0,
                FactionPaletteChoice.NONE, 1.0, 3.0,
                FactionPaletteChoice.SECONDARY, 1.0, 1.0);

        @Test
        void buildStyledCellForSystemAppliesTheOpacityMultiplierAndKeepsTheOwnerPaletteWhenNotDesaturated() {
            var styled = TerritoryBuilder.buildStyledCellForSystem(
                    drawablesWith(viewMockAdjusting(new BlocStyleAdjustment(0.5, false))),
                    SYSTEM_ID, ownedCell());

            assertThat(styled.inner().color()).isEqualTo(OWNER_SECONDARY);
            assertThat(styled.inner().alpha()).isEqualTo(0.5f);
        }

        @Test
        void buildStyledCellForSystemDesaturatesToThePassPaletteAtFullOpacityWhenOnlyDesaturateIsSet() {
            var styled = TerritoryBuilder.buildStyledCellForSystem(
                    drawablesWith(viewMockAdjusting(new BlocStyleAdjustment(1.0, true))),
                    SYSTEM_ID, ownedCell());

            assertThat(styled.inner().color()).isEqualTo(DESATURATED_SECONDARY);
            assertThat(styled.inner().alpha()).isEqualTo(1.0f);
        }

        @Test
        void buildStyledCellForSystemMutesAndDesaturatesTogetherWhenBothAreSet() {
            var styled = TerritoryBuilder.buildStyledCellForSystem(
                    drawablesWith(viewMockAdjusting(new BlocStyleAdjustment(0.5, true))),
                    SYSTEM_ID, ownedCell());

            assertThat(styled.inner().color()).isEqualTo(DESATURATED_SECONDARY);
            assertThat(styled.inner().alpha()).isEqualTo(0.5f);
        }

        @Test
        void buildStyledCellForSystemReproducesCurrentOutputForTheNoneAdjustment() {
            // Regression pin: the identity adjustment leaves the owner's own palette and
            // the style's own opacity untouched, exactly as before Step 3.
            var styled = TerritoryBuilder.buildStyledCellForSystem(
                    drawablesWith(viewMockAdjusting(BlocStyleAdjustment.NONE)),
                    SYSTEM_ID, ownedCell());

            assertThat(styled.inner().color()).isEqualTo(OWNER_SECONDARY);
            assertThat(styled.inner().alpha()).isEqualTo(1.0f);
        }

        // A view stub that paints in the full faction style (never independent) and
        // returns the given adjustment for any bloc, so each test names only the
        // adjustment it exercises.
        private static PoliticalMapView viewMockAdjusting(BlocStyleAdjustment adjustment) {
            return viewMockDeciding(false, adjustment);
        }

        @Test
        void buildStyledCellForSystemRecedesANonSpotlightedBlocUnderFilterAndIgnoresTheView() {
            // Under an active filter the view's per-bloc seams are bypassed: a real (non-spotlit)
            // owner takes the pass's shared recede, not whatever the view would have said. The view
            // stub returns the identity adjustment, so seeing the recede applied proves the filter,
            // not the view, styled the cell.
            var styled = TerritoryBuilder.buildStyledCellForSystem(
                    filteringDrawablesWith(new BlocStyleAdjustment(0.5, true)),
                    SYSTEM_ID, ownedCell());

            assertThat(styled.inner().color()).isEqualTo(DESATURATED_SECONDARY);
            assertThat(styled.inner().alpha()).isEqualTo(0.5f);
        }

        // An un-filtered pass over one owned system, styled by the given view stub - the backdrop
        // the view-driven adjustment tests read.
        private static PoliticalMapTerritories drawablesWith(PoliticalMapView viewMock) {
            return drawablesWith(viewMock, false, BlocStyleAdjustment.NONE);
        }

        // A filtered pass whose recede is the given adjustment, backed by a view stub that would
        // return the identity adjustment if consulted - so a recede in the output can only have
        // come from the filter mode bypassing the view.
        private static PoliticalMapTerritories filteringDrawablesWith(BlocStyleAdjustment recede) {
            return drawablesWith(viewMockAdjusting(BlocStyleAdjustment.NONE), true, recede);
        }

        // The one owned system every adjustment test shares over a fixed style/palette backdrop;
        // only the view stub, whether the pass filters, and the recede it applies vary.
        private static PoliticalMapTerritories drawablesWith(PoliticalMapView viewMock,
                boolean isFiltering, BlocStyleAdjustment recede) {
            // A filtered pass carries the selected bloc's id; the fixture's owner is never that
            // bloc, so it reads as non-spotlit and the recede applies. Off filter the id is null.
            return new PoliticalMapTerritories(
                    Map.of(SYSTEM_ID, OWNER), Set.of(),
                    new MapStyling(renderStyleWithEveryCategory(STYLE), Color.GRAY,
                            new FactionPalette(DESATURATED_PRIMARY, DESATURATED_SECONDARY)),
                    new ViewGrouping(viewMock, OwnershipGrouping.identity()),
                    new FilterSnapshot(isFiltering ? "selected-bloc" : null, recede, Set.of()));
        }

        // Wraps one category style into a full theme with all four categories set to it and an
        // inert global tier, so this owned-cell fixture reads its style off the territories the way
        // production does. The owned-cell path never rounds a per-cell outline or hatches, so the
        // global tier's smoothing and hatch values are never read here.
        private static RenderStyle renderStyleWithEveryCategory(CategoryStyle style) {
            Map<MapCategory, CategoryStyle> categories = new EnumMap<>(MapCategory.class);
            for (var category : MapCategory.values()) {
                categories.put(category, style);
            }
            return new RenderStyle(new GlobalStyle(new HatchStyle(0, 0, 0),
                    new BorderSmoothingStyle(false, false, 0, 0, 0), 0.3), categories);
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
