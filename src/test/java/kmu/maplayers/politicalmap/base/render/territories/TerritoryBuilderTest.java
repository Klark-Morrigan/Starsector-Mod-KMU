package kmu.maplayers.politicalmap.base.render.territories;

import kmlib.starsector.factions.FactionPalette;

import kmu.maplayers.politicalmap.base.BlocStyleAdjustment;
import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.geometry.ShapedCell;
import kmu.maplayers.politicalmap.base.politics.DominantOwner;
import kmu.maplayers.politicalmap.base.politics.OwnershipGrouping;
import kmu.maplayers.politicalmap.base.render.style.BorderSmoothingStyle;
import kmu.maplayers.politicalmap.base.render.style.CategoryStyle;
import kmu.maplayers.politicalmap.base.render.style.ElementStyle;
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
 * Pins the builder's one off-engine, deterministic piece: an owned cell's style-adjustment
 * application (palette swap and opacity scale). The
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
    class BuildStyledCellForSystem {

        // The two owned categories' fill opacities, kept distinct (and neither 1.0, which the
        // shared STYLE already uses) so an observed fill alpha names the category it came from.
        private static final double FACTION_FILL_OPACITY = 0.4;
        private static final double INDEPENDENT_FILL_OPACITY = 0.2;
        // A seam width only the independent bundle carries, so it witnesses that the rest of that
        // bundle survives a desaturated pass rather than being replaced wholesale.
        private static final double INDEPENDENT_INNER_WIDTH = 2.0;

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
                new ElementStyle(FactionPaletteChoice.NONE, 1.0),
                new ElementStyle(FactionPaletteChoice.NONE, 1.0), 3.0,
                new ElementStyle(FactionPaletteChoice.SECONDARY, 1.0), 1.0);

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
        void buildStyledCellForSystemFillsADesaturatedIndependentStyledBlocAtTheFactionOpacity() {
            // Desaturated ground holds the one faction fill opacity, so the whole desaturated
            // surface reads uniform rather than splitting into two weights of grey.
            var styled = TerritoryBuilder.buildStyledCellForSystem(
                    independentStyledDrawablesWith(new BlocStyleAdjustment(1.0, true)),
                    SYSTEM_ID, ownedCell());

            assertThat(styled.fillPaint().alpha()).isEqualTo((float) FACTION_FILL_OPACITY);
        }

        @Test
        void buildStyledCellForSystemFillsAnUndesaturatedIndependentStyledBlocAtItsOwnOpacity() {
            // In full colour the independent bundle keeps its own lighter fill, so independent
            // space still recedes behind faction ground.
            var styled = TerritoryBuilder.buildStyledCellForSystem(
                    independentStyledDrawablesWith(BlocStyleAdjustment.NONE),
                    SYSTEM_ID, ownedCell());

            assertThat(styled.fillPaint().alpha()).isEqualTo((float) INDEPENDENT_FILL_OPACITY);
        }

        @Test
        void buildStyledCellForSystemKeepsTheIndependentSeamWidthWhenDesaturated() {
            // Only the fill opacity crosses over: the rest of the independent bundle still
            // applies, so independent ground keeps its own borders and seams.
            var styled = TerritoryBuilder.buildStyledCellForSystem(
                    independentStyledDrawablesWith(new BlocStyleAdjustment(1.0, true)),
                    SYSTEM_ID, ownedCell());

            assertThat(styled.innerWidth()).isEqualTo((float) INDEPENDENT_INNER_WIDTH);
        }

        // An un-filtered pass whose view recedes every bloc to the independent style, over a theme
        // whose two owned categories fill at different opacities - the backdrop that can tell which
        // category the fill opacity was sourced from.
        private static PoliticalMapTerritories independentStyledDrawablesWith(
                BlocStyleAdjustment adjustment) {
            Map<MapCategory, CategoryStyle> categories = new EnumMap<>(MapCategory.class);
            categories.put(MapCategory.FACTION, styleFilling(FACTION_FILL_OPACITY, 1.0));
            categories.put(MapCategory.INDEPENDENT,
                    styleFilling(INDEPENDENT_FILL_OPACITY, INDEPENDENT_INNER_WIDTH));
            categories.put(MapCategory.DECIVILISED, STYLE);
            categories.put(MapCategory.UNINHABITED, STYLE);
            return new PoliticalMapTerritories(
                    Map.of(SYSTEM_ID, OWNER), Set.of(),
                    new MapStyling(
                            new RenderStyle(new GlobalStyle(new HatchStyle(0, 0, 0),
                                    new BorderSmoothingStyle(false, false, 0, 0, 0), 0.3),
                                    categories),
                            Color.GRAY,
                            new FactionPalette(DESATURATED_PRIMARY, DESATURATED_SECONDARY)),
                    new ViewGrouping(viewMockDeciding(true, adjustment),
                            OwnershipGrouping.identity()),
                    new FilterSnapshot(null, BlocStyleAdjustment.NONE, Set.of()));
        }

        // A category style identified solely by the two values these tests read back, so an
        // assertion on either one names which category the builder sourced it from.
        private static CategoryStyle styleFilling(double fillOpacity, double innerWidth) {
            return new CategoryStyle(
                    new ElementStyle(FactionPaletteChoice.NONE, fillOpacity),
                    new ElementStyle(FactionPaletteChoice.NONE, 1.0), 3.0,
                    new ElementStyle(FactionPaletteChoice.SECONDARY, 1.0), innerWidth);
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
