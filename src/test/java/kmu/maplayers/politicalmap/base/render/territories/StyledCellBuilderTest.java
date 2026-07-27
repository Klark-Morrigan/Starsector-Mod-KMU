package kmu.maplayers.politicalmap.base.render.territories;

import kmlib.starsector.factions.FactionPalette;

import kmu.maplayers.politicalmap.base.BlocStyleAdjustment;
import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.dominance.OwnershipGrouping;
import kmu.maplayers.politicalmap.base.geometry.ShapedCell;
import kmu.maplayers.politicalmap.base.politics.DominantOwner;
import kmu.maplayers.politicalmap.base.render.style.theme.BorderSmoothingStyle;
import kmu.maplayers.politicalmap.base.render.style.theme.CategoryStyle;
import kmu.maplayers.politicalmap.base.render.style.theme.ElementStyle;
import kmu.maplayers.politicalmap.base.render.style.theme.GlobalStyle;
import kmu.maplayers.politicalmap.base.render.style.theme.HatchStyle;
import kmu.maplayers.politicalmap.base.render.style.theme.HoverGlowStyle;
import kmu.maplayers.politicalmap.base.render.style.theme.HoverHighlightStyle;
import kmu.maplayers.politicalmap.base.render.style.theme.HoverWashStyle;
import kmu.maplayers.politicalmap.base.render.style.theme.MapCategory;
import kmu.maplayers.politicalmap.base.render.style.theme.RenderStyle;
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
 * Pins how one shaped cell is baked into its draw record: an owned cell threading its bloc's
 * palette and adjustment into the interior seam it contributes, and a factionless cell resolving
 * both palette slots to the neutral colour for the fill and outline it keeps for itself - with
 * decivilised ground alone taking the pass's recede over that neutral, and uninhabited ground
 * never doing so.
 *
 * <p>The style cascade these read through is pinned by
 * {@link kmu.maplayers.politicalmap.base.render.style.BlocStylingTest}, the palette rules by
 * {@link kmu.maplayers.politicalmap.base.render.style.MapPalettes}'s own suite, and the footprint
 * fill's partition by {@link FillSplitTest}.
 */
final class StyledCellBuilderTest {
    // An inert hover highlight: the builder bakes draw lists, and nothing it produces is
    // hovered here, so the style is carried untouched and its values never read.
    private static final HoverHighlightStyle NO_HOVER_HIGHLIGHT = new HoverHighlightStyle(
            FactionPaletteChoice.NONE, new HoverGlowStyle(0, 0, 0, 0, 0),
            new HoverWashStyle(0, 0, 0));

    // A view stub answering both per-bloc style seams with fixed values, so a test can prove
    // whether the style resolver consulted the view (off filter) or bypassed it (under filter).
    private static PoliticalMapView viewMockDeciding(boolean usesIndependentStyle,
            BlocStyleAdjustment adjustment) {
        var viewMock = mock(PoliticalMapView.class);
        when(viewMock.shouldUseIndependentStyle(any(), any(), any()))
                .thenReturn(usesIndependentStyle);
        when(viewMock.resolveBlocStyleAdjustment(any(), any())).thenReturn(adjustment);
        return viewMock;
    }

    @Nested
    class BuildStyledCellForSystem {

        private static final String SYSTEM_ID = "hegemony-system";
        // The one system in the factionless fixture's decivilised set, so a test can address
        // either factionless category by which id it builds the cell as.
        private static final String DECIVILISED_SYSTEM_ID = "some-decivilised-system";
        private static final Color OWNER_PRIMARY = Color.RED;
        private static final Color OWNER_SECONDARY = Color.BLUE;
        private static final Color DESATURATED_PRIMARY = Color.GREEN;
        private static final Color DESATURATED_SECONDARY = Color.YELLOW;
        // The neutral shade a factionless cell resolves both its palette slots to, distinct from
        // every owner/desaturation colour so an observed outline names the factionless path.
        private static final Color FACTIONLESS_NEUTRAL = Color.PINK;
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
            var styled = StyledCellBuilder.buildStyledCellForSystem(
                    drawablesWith(viewMockAdjusting(new BlocStyleAdjustment(0.5, false))),
                    SYSTEM_ID, ownedCell());

            assertThat(styled.inner().color()).isEqualTo(OWNER_SECONDARY);
            assertThat(styled.inner().alpha()).isEqualTo(0.5f);
        }

        @Test
        void buildStyledCellForSystemDesaturatesToThePassPaletteAtFullOpacityWhenOnlyDesaturateIsSet() {
            var styled = StyledCellBuilder.buildStyledCellForSystem(
                    drawablesWith(viewMockAdjusting(new BlocStyleAdjustment(1.0, true))),
                    SYSTEM_ID, ownedCell());

            assertThat(styled.inner().color()).isEqualTo(DESATURATED_SECONDARY);
            assertThat(styled.inner().alpha()).isEqualTo(1.0f);
        }

        @Test
        void buildStyledCellForSystemMutesAndDesaturatesTogetherWhenBothAreSet() {
            var styled = StyledCellBuilder.buildStyledCellForSystem(
                    drawablesWith(viewMockAdjusting(new BlocStyleAdjustment(0.5, true))),
                    SYSTEM_ID, ownedCell());

            assertThat(styled.inner().color()).isEqualTo(DESATURATED_SECONDARY);
            assertThat(styled.inner().alpha()).isEqualTo(0.5f);
        }

        @Test
        void buildStyledCellForSystemLeavesTheOwnerPaletteAndOpacityUntouchedForTheNoneAdjustment() {
            var styled = StyledCellBuilder.buildStyledCellForSystem(
                    drawablesWith(viewMockAdjusting(BlocStyleAdjustment.NONE)),
                    SYSTEM_ID, ownedCell());

            assertThat(styled.inner().color()).isEqualTo(OWNER_SECONDARY);
            assertThat(styled.inner().alpha()).isEqualTo(1.0f);
        }

        @Test
        void buildStyledCellForSystemDefersAnOwnedCellsFillAndOutlineToItsCluster() {
            // An owned cell's fill and national border are the cluster's, so the cell bakes no
            // geometry for them and resolves no colour - nothing of them is emitted per cell.
            var styled = StyledCellBuilder.buildStyledCellForSystem(
                    drawablesWith(viewMockAdjusting(BlocStyleAdjustment.NONE)),
                    SYSTEM_ID, ownedCell());

            assertThat(styled.fillTriangles()).isEmpty();
            assertThat(styled.boundaryEdges()).isEmpty();
            assertThat(styled.fillPaint().isHidden()).isTrue();
            assertThat(styled.outer().isHidden()).isTrue();
        }

        @Test
        void buildStyledCellForSystemRecedesANonSpotlightedBlocUnderFilterAndIgnoresTheView() {
            // Under an active filter the view's per-bloc seams are bypassed: a real (non-spotlit)
            // owner takes the pass's shared recede, not whatever the view would have said. The view
            // stub returns the identity adjustment, so seeing the recede applied proves the filter,
            // not the view, styled the cell.
            var styled = StyledCellBuilder.buildStyledCellForSystem(
                    filteringDrawablesWith(new BlocStyleAdjustment(0.5, true)),
                    SYSTEM_ID, ownedCell());

            assertThat(styled.inner().color()).isEqualTo(DESATURATED_SECONDARY);
            assertThat(styled.inner().alpha()).isEqualTo(0.5f);
        }

        @Test
        void buildStyledCellForSystemDrawsACellWithNoStarAsUninhabitedGround() {
            // A cell that draws as no system - a shard of a dead star's leftover space the
            // redistribution pass leaves behind - has no owner and no market to have died, so it
            // paints as plain uninhabited ground. The null star must resolve through the draws-as
            // map without being taken for decivilised: the decivilised category is "No color"
            // here, so had the null id been routed there the cell would have dropped to null.
            var styled = StyledCellBuilder.buildStyledCellForSystem(
                    factionlessDrawablesWith(noColorStyle(), drawnOutlineStyle()),
                    null, ownedCell());

            assertThat(styled).isNotNull();
            assertThat(styled.outer().color()).isEqualTo(FACTIONLESS_NEUTRAL);
        }

        @Test
        void buildStyledCellForSystemFillsADecivilisedCellInTheNeutralColor() {
            // Dead colonies carry a fill of their own - factionless ground fills per cell, since
            // it never fuses into a cluster with a tessellated region to fill from - so both the
            // paint and the baked triangles have to come back off the cell itself.
            var styled = StyledCellBuilder.buildStyledCellForSystem(
                    factionlessDrawablesWith(filledOutlineStyle(), drawnOutlineStyle()),
                    DECIVILISED_SYSTEM_ID, ownedCell());

            assertThat(styled.fillPaint().color()).isEqualTo(FACTIONLESS_NEUTRAL);
            assertThat(styled.fillTriangles()).isNotEmpty();
        }

        @Test
        void buildStyledCellForSystemRecedesADecivilisedCellUnderTheFiltersRecede() {
            // A dead colony is part of the "rest of the sector" a spotlight recedes, so its own
            // fill dims and recolours to the pass's desaturation palette exactly as a non-spotlit
            // bloc's does - otherwise it out-reads the bloc the spotlight is meant to isolate.
            var styled = StyledCellBuilder.buildStyledCellForSystem(
                    filteringFactionlessDrawablesWith(
                            filledOutlineStyle(), new BlocStyleAdjustment(0.5, true)),
                    DECIVILISED_SYSTEM_ID, ownedCell());

            assertThat(styled.fillPaint().color()).isEqualTo(DESATURATED_PRIMARY);
            assertThat(styled.fillPaint().alpha()).isEqualTo(0.5f);
        }

        @Test
        void buildStyledCellForSystemLeavesUninhabitedGroundUntouchedByTheFiltersRecede() {
            // Uninhabited ground is the backdrop the map is drawn over rather than something the
            // spotlight competes with, so the same receding pass leaves its outline at full
            // neutral strength - the sector keeps its shape.
            var styled = StyledCellBuilder.buildStyledCellForSystem(
                    filteringFactionlessDrawablesWith(
                            filledOutlineStyle(), new BlocStyleAdjustment(0.5, true)),
                    "never-settled-system", ownedCell());

            assertThat(styled.outer().color()).isEqualTo(FACTIONLESS_NEUTRAL);
            assertThat(styled.outer().alpha()).isEqualTo(1.0f);
        }

        @Test
        void buildStyledCellForSystemKeepsADecivilisedCellAtFullStrengthWithNoRecedeInThePass() {
            // Off filter the pass's recede is the identity, so a dead colony draws in the neutral
            // colour at its style opacity - an unfiltered map is unchanged by the recede path.
            var styled = StyledCellBuilder.buildStyledCellForSystem(
                    factionlessDrawablesWith(filledOutlineStyle(), drawnOutlineStyle()),
                    DECIVILISED_SYSTEM_ID, ownedCell());

            assertThat(styled.fillPaint().color()).isEqualTo(FACTIONLESS_NEUTRAL);
            assertThat(styled.fillPaint().alpha()).isEqualTo(1.0f);
        }

        @Test
        void buildStyledCellForSystemKeepsAFactionlessCellDrawnByItsFillAloneWhenTheOutlineIsHidden() {
            // The outline's opacity is its only on/off, so zeroing it must not take the fill down
            // with it: the cell is kept for whichever of the two still puts ink on the map.
            var styled = StyledCellBuilder.buildStyledCellForSystem(
                    factionlessDrawablesWith(fillOnlyStyle(), drawnOutlineStyle()),
                    DECIVILISED_SYSTEM_ID, ownedCell());

            assertThat(styled).isNotNull();
            assertThat(styled.fillTriangles()).isNotEmpty();
            assertThat(styled.outer().isHidden()).isTrue();
        }

        @Test
        void buildStyledCellForSystemBakesNoFillTrianglesForAnOutlineOnlyFactionlessCell() {
            // Uninhabited ground covers everything nothing else holds, so triangulating a fill it
            // never paints would be the rebuild's largest wasted cost - the geometry stays unbuilt.
            var styled = StyledCellBuilder.buildStyledCellForSystem(
                    factionlessDrawablesWith(filledOutlineStyle(), drawnOutlineStyle()),
                    "never-settled-system", ownedCell());

            assertThat(styled).isNotNull();
            assertThat(styled.fillTriangles()).isEmpty();
        }

        @Test
        void buildStyledCellForSystemDropsAFactionlessCellThatDrawsNothing() {
            var styled = StyledCellBuilder.buildStyledCellForSystem(
                    factionlessDrawablesWith(noColorStyle(), drawnOutlineStyle()),
                    DECIVILISED_SYSTEM_ID, ownedCell());

            assertThat(styled).isNull();
        }

        // A view stub that paints in the full faction style (never independent) and
        // returns the given adjustment for any bloc, so each test names only the
        // adjustment it exercises.
        private static PoliticalMapView viewMockAdjusting(BlocStyleAdjustment adjustment) {
            return viewMockDeciding(false, adjustment);
        }

        // The unfiltered factionless backdrop under a chosen pair of factionless category styles,
        // so a test names the two styles whose interplay it is about and shares everything else.
        private static PoliticalMapTerritories factionlessDrawablesWith(
                CategoryStyle decivilisedStyle, CategoryStyle uninhabitedStyle) {
            return factionlessDrawablesWith(
                    decivilisedStyle, uninhabitedStyle, null, BlocStyleAdjustment.NONE);
        }

        // A filtered pass over the factionless backdrop, receding by the given adjustment, with one
        // style shared by both factionless categories - so which of the two a test builds decides
        // the outcome and the styles themselves cannot account for it.
        private static PoliticalMapTerritories filteringFactionlessDrawablesWith(
                CategoryStyle factionlessStyle, BlocStyleAdjustment recede) {
            return factionlessDrawablesWith(
                    factionlessStyle, factionlessStyle, "selected-bloc", recede);
        }

        // The shared factionless backdrop: the two factionless styles plus the pass's spotlight
        // state, since a factionless cell's recede is the filter's. Carries an immutable owner map
        // AND an immutable decivilised set, both null-hostile: a clean result for a null system
        // proves that path reads neither - it resolves no owner and is not taken for decivilised
        // without ever probing a map with the null key.
        private static PoliticalMapTerritories factionlessDrawablesWith(
                CategoryStyle decivilisedStyle,
                CategoryStyle uninhabitedStyle,
                String selectedBlocId,
                BlocStyleAdjustment recede) {
            Map<MapCategory, CategoryStyle> categories = new EnumMap<>(MapCategory.class);
            categories.put(MapCategory.FACTION, STYLE);
            categories.put(MapCategory.INDEPENDENT, STYLE);
            categories.put(MapCategory.DECIVILISED, decivilisedStyle);
            categories.put(MapCategory.UNINHABITED, uninhabitedStyle);
            return new PoliticalMapTerritories(
                    Map.of(), Set.of(DECIVILISED_SYSTEM_ID), Set.of(),
                    new MapStyling(
                            new RenderStyle(new GlobalStyle(new HatchStyle(0, 0, 0),
                                    new BorderSmoothingStyle(false, false, 0, 0, 0),
                                    NO_HOVER_HIGHLIGHT, 0.3), categories),
                            FACTIONLESS_NEUTRAL,
                            new FactionPalette(DESATURATED_PRIMARY, DESATURATED_SECONDARY)),
                    new ViewGrouping(viewMockAdjusting(BlocStyleAdjustment.NONE),
                            OwnershipGrouping.identity()),
                    new FilterSnapshot(selectedBlocId, recede, Set.of()));
        }

        // A category whose outer outline is drawn (a real palette slot, resolved to the neutral
        // colour for a factionless cell), so a factionless cell built under it comes back drawable.
        private static CategoryStyle drawnOutlineStyle() {
            return new CategoryStyle(
                    new ElementStyle(FactionPaletteChoice.NONE, 1.0),
                    new ElementStyle(FactionPaletteChoice.PRIMARY, 1.0), 3.0,
                    new ElementStyle(FactionPaletteChoice.NONE, 1.0), 1.0);
        }

        // The decivilised shape: both a fill and an outline drawn, each in the neutral colour a
        // factionless cell resolves a PRIMARY slot to.
        private static CategoryStyle filledOutlineStyle() {
            return new CategoryStyle(
                    new ElementStyle(FactionPaletteChoice.PRIMARY, 1.0),
                    new ElementStyle(FactionPaletteChoice.PRIMARY, 1.0), 3.0,
                    new ElementStyle(FactionPaletteChoice.NONE, 1.0), 1.0);
        }

        // A fill with its outline zeroed out - the one setting combination that can hide a
        // factionless outline now that neither element carries a colour choice.
        private static CategoryStyle fillOnlyStyle() {
            return new CategoryStyle(
                    new ElementStyle(FactionPaletteChoice.PRIMARY, 1.0),
                    new ElementStyle(FactionPaletteChoice.PRIMARY, 0.0), 3.0,
                    new ElementStyle(FactionPaletteChoice.NONE, 1.0), 1.0);
        }

        // A category that draws nothing - every slot "No color" - so a cell built under it drops
        // to null rather than a drawable record.
        private static CategoryStyle noColorStyle() {
            return new CategoryStyle(
                    new ElementStyle(FactionPaletteChoice.NONE, 1.0),
                    new ElementStyle(FactionPaletteChoice.NONE, 1.0), 3.0,
                    new ElementStyle(FactionPaletteChoice.NONE, 1.0), 1.0);
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
                    Map.of(SYSTEM_ID, OWNER), Set.of(), Set.of(),
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
                    new BorderSmoothingStyle(false, false, 0, 0, 0), NO_HOVER_HIGHLIGHT, 0.3), categories);
        }

        // A small, non-empty square cell so the fill-polygon-empty short-circuit never
        // fires; its edges are all interior seams, which the owned-cell path never reads.
        private static ShapedCell ownedCell() {
            return new ShapedCell(
                    List.of(new double[] {0, 0}, new double[] {10, 0},
                            new double[] {10, 10}, new double[] {0, 10}),
                    new boolean[] {false, false, false, false});
        }
    }
}
