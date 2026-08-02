package kmu.maplayers.politicalmap.base.render.territories;

import kmlib.starsector.factions.FactionPalette;

import kmu.maplayers.base.geometry.ShapedCell;
import kmu.maplayers.base.render.clusters.StyledCell;
import kmu.maplayers.base.theme.CategoryStyle;
import kmu.maplayers.base.theme.CornerRoundingStyle;
import kmu.maplayers.base.theme.ElementStyle;
import kmu.maplayers.base.theme.GlobalStyle;
import kmu.maplayers.base.theme.MapStyleCategory;
import kmu.maplayers.base.theme.RenderStyle;
import kmu.maplayers.base.theme.ThemeFixtures;
import kmu.maplayers.politicalmap.base.BlocStyleAdjustment;
import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.politics.DominantHolder;
import kmu.maplayers.politicalmap.base.render.style.FactionPaletteSlot;
import kmu.maplayers.politicalmap.base.render.style.PoliticalMapCategory;

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
 * Pins how one shaped cell is baked into its draw record: an owned cell threading its bloc's
 * palette and adjustment into the interior seam it contributes, and a factionless cell resolving
 * both palette slots to the neutral colour for the fill and outline it keeps for itself - with
 * decivilised ground alone taking the pass's recede over that neutral, and uninhabited ground
 * never doing so.
 *
 * <p>The style cascade these read through is pinned by
 * {@link kmu.maplayers.politicalmap.base.render.style.BlocStylingTest}, the palette rules by
 * {@link kmu.maplayers.politicalmap.base.render.style.MapPalettes}'s own suite, and the footprint
 * fill's partition by {@link kmu.maplayers.base.render.clusters.FillSplitTest}.
 */
final class StyledCellBuilderTest {

    // A view stub answering both per-bloc style seams with fixed values, so a test can prove
    // whether the style resolver consulted the view (off filter) or bypassed it (under filter).
    private static PoliticalMapView viewMockDeciding(
            boolean usesIndependentStyle,
            BlocStyleAdjustment adjustment) {

        var viewMock = mock(PoliticalMapView.class);
        
        when(viewMock.shouldUseIndependentStyle(any(), any(), any()))
            .thenReturn(usesIndependentStyle);
        when(viewMock.resolveBlocStyleAdjustment(any(), any()))
            .thenReturn(adjustment);

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
        // every holder/desaturation colour so an observed outline names the factionless path.
        private static final Color FACTIONLESS_NEUTRAL = Color.PINK;

        // A corner shape that rounds the shared 10-unit cell without being clamped: the step-back
        // is capped at half the shorter adjacent edge, so a radius under 5 arcs at its full size.
        // The chamfer threshold is off (non-positive), so every corner arcs rather than being cut.
        private static final double CORNER_RADIUS = 3.0;
        private static final int CORNER_SEGMENTS = 4;
        private static final double NO_CHAMFER = 0.0;

        private static final DominantHolder OWNER =
            new DominantHolder("hegemony", OWNER_PRIMARY, OWNER_SECONDARY);

        // An owned cell's fill and national border are per cluster (in StyledCluster),
        // so fill and outer are "No color" here and only inner - the interior seam -
        // resolves a real colour, the one slot these tests can observe.
        private static final CategoryStyle STYLE = new CategoryStyle(
            new ElementStyle(
                null,
                1.0),
            new ElementStyle(
                null,
                1.0),
                3.0,
            new ElementStyle(
                FactionPaletteSlot.SECONDARY,
                1.0),
                1.0);

        @Test
        void buildStyledCellForSystemAppliesTheOpacityMultiplierAndKeepsTheHolderPaletteWhenNotDesaturated() {
            var styled = StyledCellBuilder.buildStyledCellForSystem(
                drawablesWith(viewMockAdjusting(new BlocStyleAdjustment(0.5, false))),
                SYSTEM_ID,
                ownedCell());

            assertThat(requireFusedCell(styled).seamPaint().colour())
                .isEqualTo(OWNER_SECONDARY);
            assertThat(requireFusedCell(styled).seamPaint().alpha())
                .isEqualTo(0.5f);
        }

        @Test
        void buildStyledCellForSystemDesaturatesToThePassPaletteAtFullOpacityWhenOnlyDesaturateIsSet() {
            var styled = StyledCellBuilder.buildStyledCellForSystem(
                drawablesWith(viewMockAdjusting(new BlocStyleAdjustment(1.0, true))),
                SYSTEM_ID,
                ownedCell());

            assertThat(requireFusedCell(styled).seamPaint().colour())
                .isEqualTo(DESATURATED_SECONDARY);
            assertThat(requireFusedCell(styled).seamPaint().alpha())
                .isEqualTo(1.0f);
        }

        @Test
        void buildStyledCellForSystemMutesAndDesaturatesTogetherWhenBothAreSet() {
            var styled = StyledCellBuilder.buildStyledCellForSystem(
                drawablesWith(viewMockAdjusting(new BlocStyleAdjustment(0.5, true))),
                SYSTEM_ID,
                ownedCell());

            assertThat(requireFusedCell(styled).seamPaint().colour())
                .isEqualTo(DESATURATED_SECONDARY);
            assertThat(requireFusedCell(styled).seamPaint().alpha())
                .isEqualTo(0.5f);
        }

        @Test
        void buildStyledCellForSystemLeavesTheHolderPaletteAndOpacityUntouchedForTheNoneAdjustment() {
            var styled = StyledCellBuilder.buildStyledCellForSystem(
                drawablesWith(viewMockAdjusting(BlocStyleAdjustment.NONE)),
                SYSTEM_ID,
                ownedCell());

            assertThat(requireFusedCell(styled).seamPaint().colour())
                .isEqualTo(OWNER_SECONDARY);
            assertThat(requireFusedCell(styled).seamPaint().alpha())
                .isEqualTo(1.0f);
        }

        @Test
        void buildStyledCellForSystemBuildsAnOwnedCellInTheFusedFormWithItsSeamsAlone() {
            // An owned cell's fill and national border are the cluster's, drawn from the cluster's
            // own shape. It comes back in the fused form, which has no slot for either, so what it
            // does not draw is a fact about its type rather than a hidden paint a reader has to
            // spot - and the seams it does draw are all it carries.
            var styled = StyledCellBuilder.buildStyledCellForSystem(
                drawablesWith(viewMockAdjusting(BlocStyleAdjustment.NONE)),
                SYSTEM_ID,
                ownedCell());

            assertThat(styled)
                .isInstanceOf(StyledCell.FusedCell.class);
            assertThat(requireFusedCell(styled).seamEdges())
                .isNotNull();
        }

        @Test
        void buildStyledCellForSystemRecedesANonSpotlightedBlocUnderFilterAndIgnoresTheView() {
            // Under an active filter the view's per-bloc seams are bypassed: a real (non-spotlit)
            // holder takes the pass's shared recede, not whatever the view would have said. The view
            // stub returns the identity adjustment, so seeing the recede applied proves the filter,
            // not the view, styled the cell.
            var styled = StyledCellBuilder.buildStyledCellForSystem(
                filteringDrawablesWith(new BlocStyleAdjustment(0.5, true)),
                SYSTEM_ID,
                ownedCell());

            assertThat(requireFusedCell(styled).seamPaint().colour())
                .isEqualTo(DESATURATED_SECONDARY);
            assertThat(requireFusedCell(styled).seamPaint().alpha())
                .isEqualTo(0.5f);
        }

        @Test
        void buildStyledCellForSystemDrawsACellWithNoStarAsUninhabitedGround() {
            // A cell that draws as no system - a shard of a dead star's leftover space the
            // redistribution pass leaves behind - has no holder and no market to have died, so it
            // paints as plain uninhabited ground. The null star must resolve through the draws-as
            // map without being taken for decivilised: the decivilised category is "No color"
            // here, so had the null id been routed there the cell would have dropped to null.
            var styled = StyledCellBuilder.buildStyledCellForSystem(
                factionlessDrawablesWith(noColourStyle(), drawnOutlineStyle()),
                null,
                ownedCell());

            assertThat(styled)
                .isNotNull();
            assertThat(requireLoneCell(styled).outlinePaint().colour())
                .isEqualTo(FACTIONLESS_NEUTRAL);
        }

        @Test
        void buildStyledCellForSystemFillsADecivilisedCellInTheNeutralColour() {
            // Dead colonies carry a fill of their own - factionless ground fills per cell, since
            // it never fuses into a cluster with a tessellated cluster to fill from - so both the
            // paint and the baked triangles have to come back off the cell itself.
            var styled = StyledCellBuilder.buildStyledCellForSystem(
                factionlessDrawablesWith(filledOutlineStyle(), drawnOutlineStyle()),
                DECIVILISED_SYSTEM_ID,
                ownedCell());

            assertThat(requireLoneCell(styled).fillPaint().colour())
                .isEqualTo(FACTIONLESS_NEUTRAL);
            assertThat(requireLoneCell(styled).fillTriangles())
                .isNotEmpty();
        }

        @Test
        void buildStyledCellForSystemRecedesADecivilisedCellUnderTheFiltersRecede() {
            // A dead colony is part of the "rest of the sector" a spotlight recedes, so its own
            // fill dims and recolours to the pass's desaturation palette exactly as a non-spotlit
            // bloc's does - otherwise it out-reads the bloc the spotlight is meant to isolate. Its
            // outline recedes with it: the two are the whole of what the cell puts on the map, so
            // one receding without the other would leave a bright ring around a sunken fill.
            var styled = StyledCellBuilder.buildStyledCellForSystem(
                filteringFactionlessDrawablesWith(
                    filledOutlineStyle(),
                    new BlocStyleAdjustment(0.5, true)),
                DECIVILISED_SYSTEM_ID,
                ownedCell());

            assertThat(requireLoneCell(styled).fillPaint().colour())
                .isEqualTo(DESATURATED_PRIMARY);
            assertThat(requireLoneCell(styled).fillPaint().alpha())
                .isEqualTo(0.5f);
            assertThat(requireLoneCell(styled).outlinePaint().colour())
                .isEqualTo(DESATURATED_PRIMARY);
            assertThat(requireLoneCell(styled).outlinePaint().alpha())
                .isEqualTo(0.5f);
        }

        @Test
        void buildStyledCellForSystemDimsADecivilisedCellWithoutRecolouringItWhenOnlyMuteIsSet() {
            // Mute and Desaturate are independent toggles, and Mute alone is the commoner setting:
            // the dead colony sinks in weight while staying the neutral colour it reads as when
            // nothing is spotlighted.
            var styled = StyledCellBuilder.buildStyledCellForSystem(
                filteringFactionlessDrawablesWith(
                    filledOutlineStyle(),
                    new BlocStyleAdjustment(0.5, false)),
                DECIVILISED_SYSTEM_ID,
                ownedCell());

            assertThat(requireLoneCell(styled).fillPaint().colour())
                .isEqualTo(FACTIONLESS_NEUTRAL);
            assertThat(requireLoneCell(styled).fillPaint().alpha())
                .isEqualTo(0.5f);
        }

        @Test
        void buildStyledCellForSystemLeavesUninhabitedGroundUntouchedByTheFiltersRecede() {
            // Uninhabited ground is the backdrop the map is drawn over rather than something the
            // spotlight competes with, so the same receding pass leaves its outline at full
            // neutral strength - the sector keeps its shape.
            var styled = StyledCellBuilder.buildStyledCellForSystem(
                filteringFactionlessDrawablesWith(
                    filledOutlineStyle(),
                    new BlocStyleAdjustment(0.5, true)),
                "never-settled-system",
                ownedCell());

            assertThat(requireLoneCell(styled).outlinePaint().colour())
                .isEqualTo(FACTIONLESS_NEUTRAL);
            assertThat(requireLoneCell(styled).outlinePaint().alpha())
                .isEqualTo(1.0f);
        }

        @Test
        void buildStyledCellForSystemKeepsADecivilisedCellAtFullStrengthWithNoRecedeInThePass() {
            // Off filter the pass's recede is the identity, so a dead colony draws in the neutral
            // colour at its style opacity - an unfiltered map is unchanged by the recede path.
            var styled = StyledCellBuilder.buildStyledCellForSystem(
                factionlessDrawablesWith(filledOutlineStyle(), drawnOutlineStyle()),
                DECIVILISED_SYSTEM_ID,
                ownedCell());

            assertThat(requireLoneCell(styled).fillPaint().colour())
                .isEqualTo(FACTIONLESS_NEUTRAL);
            assertThat(requireLoneCell(styled).fillPaint().alpha())
                .isEqualTo(1.0f);
        }

        @Test
        void buildStyledCellForSystemKeepsAFactionlessCellDrawnByItsFillAloneWhenTheOutlineIsHidden() {
            // The outline's opacity is its only on/off, so zeroing it must not take the fill down
            // with it: the cell is kept for whichever of the two still puts ink on the map.
            var styled = StyledCellBuilder.buildStyledCellForSystem(
                factionlessDrawablesWith(fillOnlyStyle(), drawnOutlineStyle()),
                DECIVILISED_SYSTEM_ID,
                ownedCell());

            assertThat(styled)
                .isNotNull();
            assertThat(requireLoneCell(styled).fillTriangles())
                .isNotEmpty();
            assertThat(requireLoneCell(styled).outlinePaint().isHidden())
                .isTrue();
        }

        @Test
        void buildStyledCellForSystemBakesNoFillTrianglesForAnOutlineOnlyFactionlessCell() {
            // Uninhabited ground covers everything nothing else holds, so triangulating a fill it
            // never paints would be the rebuild's largest wasted cost - the geometry stays unbuilt.
            var styled = StyledCellBuilder.buildStyledCellForSystem(
                factionlessDrawablesWith(filledOutlineStyle(), drawnOutlineStyle()),
                "never-settled-system",
                ownedCell());

            assertThat(styled)
                .isNotNull();
            assertThat(requireLoneCell(styled).fillTriangles())
                .isEmpty();
        }

        @Test
        void buildStyledCellForSystemRoundsALoneCellsOutlineWhenTheSectorWideGateIsOn() {
            var styled = StyledCellBuilder.buildStyledCellForSystem(
                roundingFactionlessDrawablesWith(filledOutlineStyle()),
                DECIVILISED_SYSTEM_ID,
                ownedCell());

            // The cell's own sharp corner is gone: rounding steps back from it along both edges,
            // so the vertex the raw Voronoi cell had at the origin is not in the stroked outline.
            assertThat(containsPoint(requireLoneCell(styled).outlineEdges(), 0, 0))
                .isFalse();

            // And the fill is triangulated from that same rounded ring rather than the raw one -
            // built at all is the observable half, since a fill cut from the sharp cell would
            // spill past the line the outline strokes.
            assertThat(requireLoneCell(styled).fillTriangles())
                .isNotEmpty();
        }

        @Test
        void buildStyledCellForSystemLeavesALoneCellsOutlineSharpWhenTheGateIsOff() {
            // The other half of the gate, and the reason the case above is not just "the outline
            // has vertices": with rounding off the raw Voronoi cell is what the map draws, corner
            // at the origin included.
            var styled = StyledCellBuilder.buildStyledCellForSystem(
                factionlessDrawablesWith(filledOutlineStyle(), drawnOutlineStyle()),
                DECIVILISED_SYSTEM_ID,
                ownedCell());

            assertThat(containsPoint(requireLoneCell(styled).outlineEdges(), 0, 0))
                .isTrue();
        }

        @Test
        void buildStyledCellForSystemDropsAFactionlessCellThatDrawsNothing() {
            var styled = StyledCellBuilder.buildStyledCellForSystem(
                factionlessDrawablesWith(noColourStyle(), drawnOutlineStyle()),
                DECIVILISED_SYSTEM_ID,
                ownedCell());

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
                CategoryStyle decivilisedStyle,
                CategoryStyle uninhabitedStyle) {
            return factionlessDrawablesWith(
                factionlessTheme(
                    decivilisedStyle,
                    uninhabitedStyle,
                    ThemeFixtures.createInertGlobalStyle()),
                null,
                BlocStyleAdjustment.NONE);
        }

        // A filtered pass over the factionless backdrop, receding by the given adjustment, with one
        // style shared by both factionless categories - so which of the two a test builds decides
        // the outcome and the styles themselves cannot account for it.
        private static PoliticalMapTerritories filteringFactionlessDrawablesWith(
                CategoryStyle factionlessStyle,
                BlocStyleAdjustment recede) {
            return factionlessDrawablesWith(
                factionlessTheme(
                    factionlessStyle,
                    factionlessStyle,
                    ThemeFixtures.createInertGlobalStyle()),
                "selected-bloc",
                recede);
        }

        // The unfiltered factionless backdrop with the sector-wide corner rounding switched on,
        // for the cases about the shape of a lone cell's outline rather than its colour. One
        // style for both factionless categories, since the rounding is global-tier and cannot
        // differ between them.
        private static PoliticalMapTerritories roundingFactionlessDrawablesWith(
                CategoryStyle factionlessStyle) {

            return factionlessDrawablesWith(
                factionlessTheme(
                    factionlessStyle,
                    factionlessStyle,
                    ThemeFixtures.createGlobalStyleRoundingBy(new CornerRoundingStyle(
                        true,
                        CORNER_RADIUS,
                        CORNER_SEGMENTS,
                        NO_CHAMFER))),
                null,
                BlocStyleAdjustment.NONE);
        }

        // The theme a factionless case reads: the two factionless bundles it is about over the
        // given sector-wide tier, with the owned pair held at the shared style. Taken apart from
        // the backdrop below because the two vary independently - a case is about a category
        // bundle or about a global-tier knob, never both.
        private static RenderStyle factionlessTheme(
                CategoryStyle decivilisedStyle,
                CategoryStyle uninhabitedStyle,
                GlobalStyle globalStyle) {

            Map<MapStyleCategory, CategoryStyle> categories = new LinkedHashMap<>();

            categories.put(PoliticalMapCategory.FACTION, STYLE);
            categories.put(PoliticalMapCategory.INDEPENDENT, STYLE);
            categories.put(PoliticalMapCategory.DECIVILISED, decivilisedStyle);
            categories.put(PoliticalMapCategory.UNINHABITED, uninhabitedStyle);

            return new RenderStyle(globalStyle, categories);
        }

        // The shared factionless backdrop: the theme plus the pass's spotlight state, since a
        // factionless cell's recede is the filter's. Carries an immutable holder map AND an
        // immutable decivilised set, both null-hostile: a clean result for a null system proves
        // that path reads neither - it resolves no holder and is not taken for decivilised
        // without ever probing a map with the null key.
        private static PoliticalMapTerritories factionlessDrawablesWith(
                RenderStyle theme,
                String selectedBlocId,
                BlocStyleAdjustment recede) {

            return new PoliticalMapTerritories(
                Map.of(),
                Set.of(DECIVILISED_SYSTEM_ID),
                Set.of(),
                new MapStyling(
                    theme,
                    FACTIONLESS_NEUTRAL,
                    new FactionPalette(DESATURATED_PRIMARY, DESATURATED_SECONDARY)),
                new ViewGrouping(
                    viewMockAdjusting(BlocStyleAdjustment.NONE),
                    HolderGrouping.identity()),
                new FilterSnapshot(
                    selectedBlocId,
                    recede,
                    Set.of()));
        }

        // A category whose outer outline is drawn (a real palette slot, resolved to the neutral
        // colour for a factionless cell), so a factionless cell built under it comes back drawable.
        private static CategoryStyle drawnOutlineStyle() {
            return new CategoryStyle(
                new ElementStyle(
                    null,
                    1.0),
                new ElementStyle(
                    FactionPaletteSlot.PRIMARY,
                    1.0), 3.0,
                new ElementStyle(
                    null,
                    1.0), 1.0);
        }

        // The decivilised shape: both a fill and an outline drawn, each in the neutral colour a
        // factionless cell resolves a PRIMARY slot to.
        private static CategoryStyle filledOutlineStyle() {
            return new CategoryStyle(
                new ElementStyle(
                    FactionPaletteSlot.PRIMARY, 1.0),
                new ElementStyle(
                    FactionPaletteSlot.PRIMARY, 1.0), 3.0,
                new ElementStyle(
                    null, 1.0), 1.0);
        }

        // A fill with its outline zeroed out - the one setting combination that can hide a
        // factionless outline now that neither element carries a colour choice.
        private static CategoryStyle fillOnlyStyle() {
            return new CategoryStyle(
                new ElementStyle(
                    FactionPaletteSlot.PRIMARY, 1.0),
                new ElementStyle(
                    FactionPaletteSlot.PRIMARY, 0.0), 3.0,
                new ElementStyle(
                    null, 1.0), 1.0);
        }

        // A category that draws nothing - every slot "No color" - so a cell built under it drops
        // to null rather than a drawable record.
        private static CategoryStyle noColourStyle() {
            return new CategoryStyle(
                new ElementStyle(
                    null, 1.0),
                new ElementStyle(
                    null, 1.0), 3.0,
                new ElementStyle(
                    null, 1.0), 1.0);
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
        private static PoliticalMapTerritories drawablesWith(
                PoliticalMapView viewMock,
                boolean isFiltering,
                BlocStyleAdjustment recede) {

            // A filtered pass carries the selected bloc's id; the fixture's holder is never that
            // bloc, so it reads as non-spotlit and the recede applies. Off filter the id is null.
            return new PoliticalMapTerritories(
                Map.of(SYSTEM_ID, OWNER), Set.of(), Set.of(),
                new MapStyling(
                    PoliticalMapTerritoryFixtures.createRenderStyleForEveryCategory(STYLE),
                    Color.GRAY,
                    new FactionPalette(DESATURATED_PRIMARY, DESATURATED_SECONDARY)),
                new ViewGrouping(viewMock, HolderGrouping.identity()),
                new FilterSnapshot(isFiltering ? "selected-bloc" : null, recede, Set.of()));
        }

        // A small, non-empty square cell so the fill-polygon-empty short-circuit never
        // fires; its edges are all interior seams, which the owned-cell path never reads.
        private static ShapedCell ownedCell() {
            return new ShapedCell(
                List.of(
                    new double[] {0, 0},
                    new double[] {10, 0},
                    new double[] {10, 10},
                    new double[] {0, 10}),
                new boolean[] {false, false, false, false});
        }
    }

    // Narrows a built cell to the fused form - a cell inside a cluster, carrying only its seams.
    // Failing here is itself the assertion for a case about an owned cell: the builder choosing the
    // other form would mean the cell claimed a fill and an outline of its own.
    private static StyledCell.FusedCell requireFusedCell(StyledCell styled) {
        assertThat(styled).isInstanceOf(StyledCell.FusedCell.class);
        return (StyledCell.FusedCell) styled;
    }

    // Whether a flattened GL_LINES run has an endpoint at the given point. The run is a flat
    // [x, y, x, y, ...] of segment endpoints, so a vertex the ring passes through appears in it
    // twice - once ending one segment and once starting the next - and either occurrence answers.
    private static boolean containsPoint(float[] segments, float x, float y) {
        for (var i = 0; i < segments.length; i += 2) {
            if (segments[i] == x && segments[i + 1] == y) {
                return true;
            }
        }
        return false;
    }

    // Narrows a built cell to the lone form - a cell that is its own cluster, carrying its fill and
    // outline. As above, the narrowing doubles as the assertion that the builder read the cell as
    // factionless ground rather than as part of a cluster.
    private static StyledCell.LoneCell requireLoneCell(StyledCell styled) {
        assertThat(styled).isInstanceOf(StyledCell.LoneCell.class);
        return (StyledCell.LoneCell) styled;
    }
}
