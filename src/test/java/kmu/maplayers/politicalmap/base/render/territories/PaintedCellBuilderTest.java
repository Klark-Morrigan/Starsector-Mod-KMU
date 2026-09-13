package kmu.maplayers.politicalmap.base.render.territories;

import kmlib.math.geometry.CornerRounding;
import kmlib.starsector.factions.FactionPalette;
import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.base.geometry.ShapedCell;
import kmu.maplayers.base.render.clusters.PaintedCell;
import kmu.maplayers.base.render.clusters.StyledCell;
import kmu.maplayers.base.theme.CategoryStyle;
import kmu.maplayers.base.theme.CornerRoundingStyle;
import kmu.maplayers.base.theme.ElementStyle;
import kmu.maplayers.base.theme.ElementStyleAdjustment;
import kmu.maplayers.base.theme.GlobalStyle;
import kmu.maplayers.base.theme.MapStyleCategory;
import kmu.maplayers.base.theme.RenderStyle;
import kmu.maplayers.base.theme.ThemeFixtures;
import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.ViewGrouping;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.politics.DominantHolder;
import kmu.maplayers.politicalmap.base.render.ContentInputsFixtures;
import kmu.maplayers.politicalmap.base.render.style.FactionPaletteSlot;
import kmu.maplayers.politicalmap.base.render.style.PoliticalMapCategory;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins how one shaped cell is baked into its draw record: an owned cell threading its bloc's
 * palette and adjustment into the interior seam it contributes, and a factionless cell resolving
 * both palette slots to the neutral colour for the fill and outline it keeps for itself - with
 * a decivilised cell alone taking the pass's recede over that neutral, and an uninhabited cell
 * never doing so.
 *
 * <p>And the ring each form reports beside that record, which is what everything downstream takes
 * for where the cell put ink: the rounded one for a lone cell, since that is the ring it strokes,
 * and the raw extent for a fused cell under the very same rounding gate, since a cluster's border
 * is what bounds it.
 *
 * <p>The style cascade these read through is pinned by
 * {@link kmu.maplayers.politicalmap.base.render.style.BlocStylingTest}, the palette rules by
 * {@link kmu.maplayers.politicalmap.base.render.style.MapPalettes}'s own suite, and the footprint
 * fill's partition by {@link kmu.maplayers.base.render.clusters.FillSplitTest}.
 */
final class PaintedCellBuilderTest {

    // A view stub answering both per-bloc style seams with fixed values, so a test can prove
    // whether the style resolver consulted the view (off filter) or bypassed it (under filter).
    private static PoliticalMapView buildViewMockDeciding(
            boolean usesIndependentStyle,
            ElementStyleAdjustment adjustment) {

        var viewMock = mock(PoliticalMapView.class);

        when(viewMock.shouldUseIndependentStyle(any(), any(), any()))
            .thenReturn(usesIndependentStyle);
        when(viewMock.resolveBlocStyleAdjustment(any(), any(), any()))
            .thenReturn(adjustment);

        return viewMock;
    }

    @Nested
    class BuildPaintedCellForSystem {

        private static final String SYSTEM_ID = "hegemony-system";

        // Two of the systems in the factionless fixture's inhabited set - one settled by a dead
        // colony, one by a live colony no holder was resolved for - so a test can address either
        // factionless category by which ID it builds the cell as, and can tell the two reasons a
        // cell counts as settled apart.
        private static final String DECIVILISED_SYSTEM_ID = "some-decivilised-system";
        private static final String UNHELD_INHABITED_SYSTEM_ID = "some-pirate-haven";
        private static final Color OWNER_PRIMARY = Color.RED;
        private static final Color OWNER_SECONDARY = Color.BLUE;
        private static final Color DESATURATED_PRIMARY = Color.GREEN;
        private static final Color DESATURATED_SECONDARY = Color.YELLOW;

        // The lifted neutral a spared cell paints in. A colour of its own rather than a real wash
        // of FACTIONLESS_NEUTRAL, so an observed shade names which palette the cell read rather
        // than how far toward white the fixture happened to move it - the wash itself is
        // MapPalettes' to pin.
        private static final Color PRESENCE_LIFTED = Color.ORANGE;

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

        // Two systems the sector answers to one ID for - vanilla's own unnamed deep space, which
        // only their anchors tell apart - and the rival holding the second of them. Its seam shade
        // is distinct from every other colour here, so an observed seam names which of the pair's
        // holders the cell read.
        private static final SystemKey FIRST_TWIN = new SystemKey("deep space", "", "8b3");
        private static final SystemKey SECOND_TWIN = new SystemKey("deep space", "", "38d53");
        private static final Color RIVAL_PRIMARY = Color.CYAN;
        private static final Color RIVAL_SECONDARY = Color.MAGENTA;

        private static final DominantHolder RIVAL_OWNER =
            new DominantHolder("tritachyon", RIVAL_PRIMARY, RIVAL_SECONDARY);

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
        void buildPaintedCellForSystemAppliesTheOpacityMultiplierAndKeepsTheHolderPaletteWhenNotDesaturated() {

            var painted = PaintedCellBuilder.buildPaintedCellForSystem(
                buildDrawablesWith(buildViewMockAdjusting(new ElementStyleAdjustment(0.5, false))),
                buildCellKey(SYSTEM_ID),
                buildOwnedCell());

            assertThat(requireFusedCell(painted).seamPaint().colour())
                .isEqualTo(OWNER_SECONDARY);
            assertThat(requireFusedCell(painted).seamPaint().alpha())
                .isEqualTo(0.5f);
        }

        @Test
        void buildPaintedCellForSystemDrawsTwoSystemsSharingAnIdInTheirOwnHoldersColours() {
            // The collision the whole address exists for, at the cell that shows it: both systems
            // answer to one vanilla ID and are held by different blocs, so the two cells paint in
            // two shades - where a holding keyed by ID drew the second in the first's colours.
            var territories = buildDrawablesHeldByTwins();

            assertThat(requireFusedCell(PaintedCellBuilder.buildPaintedCellForSystem(
                    territories,
                    FIRST_TWIN,
                    buildOwnedCell()))
                .seamPaint().colour())
                .isEqualTo(OWNER_SECONDARY);

            assertThat(requireFusedCell(PaintedCellBuilder.buildPaintedCellForSystem(
                    territories,
                    SECOND_TWIN,
                    buildOwnedCell()))
                .seamPaint().colour())
                .isEqualTo(RIVAL_SECONDARY);
        }

        @Test
        void buildPaintedCellForSystemDesaturatesToThePassPaletteAtFullOpacityWhenOnlyDesaturateIsSet() {

            var painted = PaintedCellBuilder.buildPaintedCellForSystem(
                buildDrawablesWith(buildViewMockAdjusting(new ElementStyleAdjustment(1.0, true))),
                buildCellKey(SYSTEM_ID),
                buildOwnedCell());

            assertThat(requireFusedCell(painted).seamPaint().colour())
                .isEqualTo(DESATURATED_SECONDARY);
            assertThat(requireFusedCell(painted).seamPaint().alpha())
                .isEqualTo(1.0f);
        }

        @Test
        void buildPaintedCellForSystemMutesAndDesaturatesTogetherWhenBothAreSet() {

            var painted = PaintedCellBuilder.buildPaintedCellForSystem(
                buildDrawablesWith(buildViewMockAdjusting(new ElementStyleAdjustment(0.5, true))),
                buildCellKey(SYSTEM_ID),
                buildOwnedCell());

            assertThat(requireFusedCell(painted).seamPaint().colour())
                .isEqualTo(DESATURATED_SECONDARY);
            assertThat(requireFusedCell(painted).seamPaint().alpha())
                .isEqualTo(0.5f);
        }

        @Test
        void buildPaintedCellForSystemLeavesTheHolderPaletteAndOpacityUntouchedForTheNoneAdjustment() {

            var painted = PaintedCellBuilder.buildPaintedCellForSystem(
                buildDrawablesWith(buildViewMockAdjusting(ElementStyleAdjustment.NONE)),
                buildCellKey(SYSTEM_ID),
                buildOwnedCell());

            assertThat(requireFusedCell(painted).seamPaint().colour())
                .isEqualTo(OWNER_SECONDARY);
            assertThat(requireFusedCell(painted).seamPaint().alpha())
                .isEqualTo(1.0f);
        }

        @Test
        void buildPaintedCellForSystemBuildsAnOwnedCellInTheFusedFormWithItsSeamsAlone() {
            // An owned cell's fill and national border are the cluster's, drawn from the cluster's
            // own shape. It comes back in the fused form, which has no slot for either, so what it
            // does not draw is a fact about its type rather than a hidden paint a reader has to
            // spot - and the seams it does draw are all it carries.
            var painted = PaintedCellBuilder.buildPaintedCellForSystem(
                buildDrawablesWith(buildViewMockAdjusting(ElementStyleAdjustment.NONE)),
                buildCellKey(SYSTEM_ID),
                buildOwnedCell());

            assertThat(painted.styledCell())
                .isInstanceOf(StyledCell.FusedCell.class);
            assertThat(requireFusedCell(painted).seamEdges())
                .isNotNull();
        }

        @Test
        void buildPaintedCellForSystemRecedesANonSpotlightedBlocUnderFilterAndIgnoresTheView() {
            // Under an active filter the view's per-bloc seams are bypassed: a real (non-spotlit)
            // holder takes the pass's shared recede, not whatever the view would have said. The view
            // stub returns the identity adjustment, so seeing the recede applied proves the filter,
            // not the view, styled the cell.
            var painted = PaintedCellBuilder.buildPaintedCellForSystem(
                buildFilteringDrawablesWith(new ElementStyleAdjustment(0.5, true)),
                buildCellKey(SYSTEM_ID),
                buildOwnedCell());

            assertThat(requireFusedCell(painted).seamPaint().colour())
                .isEqualTo(DESATURATED_SECONDARY);
            assertThat(requireFusedCell(painted).seamPaint().alpha())
                .isEqualTo(0.5f);
        }

        @Test
        void buildPaintedCellForSystemDrawsACellWithNoStarAsUninhabited() {
            // A cell that draws as no system - a shard of a dead star's leftover space the
            // redistribution pass leaves behind - has no holder and no market to have died, so it
            // paints as plain uninhabited. The null star must resolve through the draws-as
            // map without being taken for decivilised: the decivilised category is "No color"
            // here, so had the null ID been routed there the cell would have dropped to null.
            var painted = PaintedCellBuilder.buildPaintedCellForSystem(
                buildFactionlessDrawablesWith(buildNoColourStyle(), buildDrawnOutlineStyle()),
                null,
                buildOwnedCell());

            assertThat(painted)
                .isNotNull();
            assertThat(requireLoneCell(painted).outlinePaint().colour())
                .isEqualTo(FACTIONLESS_NEUTRAL);
        }

        @Test
        void buildPaintedCellForSystemFillsADecivilisedCellInTheNeutralColour() {
            // Dead colonies carry a fill of their own - a factionless cell fills on its own, since
            // it never fuses into a cluster with a tessellated cluster to fill from - so both the
            // paint and the baked triangles have to come back off the cell itself.
            var painted = PaintedCellBuilder.buildPaintedCellForSystem(
                buildFactionlessDrawablesWith(buildFilledOutlineStyle(), buildDrawnOutlineStyle()),
                buildCellKey(DECIVILISED_SYSTEM_ID),
                buildOwnedCell());

            assertThat(requireLoneCell(painted).fillPaint().colour())
                .isEqualTo(FACTIONLESS_NEUTRAL);
            assertThat(requireLoneCell(painted).fillTriangles())
                .isNotEmpty();
        }

        @Test
        void buildPaintedCellForSystemRecedesADecivilisedCellUnderTheFiltersRecede() {
            // A dead colony is part of the "rest of the sector" a spotlight recedes, so its own
            // fill dims and recolours to the pass's desaturation palette exactly as a non-spotlit
            // bloc's does - otherwise it out-reads the bloc the spotlight is meant to isolate. Its
            // outline recedes with it: the two are the whole of what the cell puts on the map, so
            // one receding without the other would leave a bright ring around a sunken fill.
            var painted = PaintedCellBuilder.buildPaintedCellForSystem(
                buildFilteringFactionlessDrawablesWith(
                    buildFilledOutlineStyle(),
                    new ElementStyleAdjustment(0.5, true)),
                buildCellKey(DECIVILISED_SYSTEM_ID),
                buildOwnedCell());

            assertThat(requireLoneCell(painted).fillPaint().colour())
                .isEqualTo(DESATURATED_PRIMARY);
            assertThat(requireLoneCell(painted).fillPaint().alpha())
                .isEqualTo(0.5f);
            assertThat(requireLoneCell(painted).outlinePaint().colour())
                .isEqualTo(DESATURATED_PRIMARY);
            assertThat(requireLoneCell(painted).outlinePaint().alpha())
                .isEqualTo(0.5f);
        }

        @Test
        void buildPaintedCellForSystemSparesASettledCellTheSpotlitBlocLivesInTheFiltersRecede() {
            // The pick's own colony in a system this layer's holding could not attribute to it - an
            // unclaimed pirate haven under a pirate spotlight. Under the same receding pass that
            // sinks the case above, this cell keeps full opacity and paints the lifted neutral:
            // presence spares it the recede and lifts it clear of the greys that sank, without
            // ever handing it the bloc's own shades and the claim those would assert.
            var painted = PaintedCellBuilder.buildPaintedCellForSystem(
                buildFilteringFactionlessDrawablesWith(
                    buildFilledOutlineStyle(),
                    new ElementStyleAdjustment(0.5, true),
                    Set.of(buildCellKey(UNHELD_INHABITED_SYSTEM_ID))),
                buildCellKey(UNHELD_INHABITED_SYSTEM_ID),
                buildOwnedCell());

            assertThat(requireLoneCell(painted).fillPaint().colour())
                .isEqualTo(PRESENCE_LIFTED);
            assertThat(requireLoneCell(painted).fillPaint().alpha())
                .isEqualTo(1.0f);
            assertThat(requireLoneCell(painted).outlinePaint().colour())
                .isEqualTo(PRESENCE_LIFTED);
            assertThat(requireLoneCell(painted).outlinePaint().alpha())
                .isEqualTo(1.0f);
        }

        @Test
        void buildPaintedCellForSystemStillRecedesASettledCellTheSpotlitBlocIsAbsentFrom() {
            // The other half of the case above, under the identical pass: a settled system the pick
            // does not live in stays part of the receded background, so the exception turns on the
            // presence set rather than on the cell being settled at all.
            var painted = PaintedCellBuilder.buildPaintedCellForSystem(
                buildFilteringFactionlessDrawablesWith(
                    buildFilledOutlineStyle(),
                    new ElementStyleAdjustment(0.5, true),
                    Set.of(buildCellKey(UNHELD_INHABITED_SYSTEM_ID))),
                buildCellKey(DECIVILISED_SYSTEM_ID),
                buildOwnedCell());

            assertThat(requireLoneCell(painted).fillPaint().colour())
                .isEqualTo(DESATURATED_PRIMARY);
            assertThat(requireLoneCell(painted).fillPaint().alpha())
                .isEqualTo(0.5f);
        }

        @Test
        void buildPaintedCellForSystemDimsADecivilisedCellWithoutRecolouringItWhenOnlyMuteIsSet() {
            // Mute and Desaturate are independent toggles, and Mute alone is the commoner setting:
            // the dead colony sinks in weight while staying the neutral colour it reads as when
            // nothing is spotlighted.
            var painted = PaintedCellBuilder.buildPaintedCellForSystem(
                buildFilteringFactionlessDrawablesWith(
                    buildFilledOutlineStyle(),
                    new ElementStyleAdjustment(0.5, false)),
                buildCellKey(DECIVILISED_SYSTEM_ID),
                buildOwnedCell());

            assertThat(requireLoneCell(painted).fillPaint().colour())
                .isEqualTo(FACTIONLESS_NEUTRAL);
            assertThat(requireLoneCell(painted).fillPaint().alpha())
                .isEqualTo(0.5f);
        }

        @Test
        void buildPaintedCellForSystemLeavesAnUninhabitedCellUntouchedByTheFiltersRecede() {
            // An uninhabited cell is the backdrop the map is drawn over rather than something the
            // spotlight competes with, so the same receding pass leaves its outline at full
            // neutral strength - the sector keeps its shape.
            var painted = PaintedCellBuilder.buildPaintedCellForSystem(
                buildFilteringFactionlessDrawablesWith(
                    buildFilledOutlineStyle(),
                    new ElementStyleAdjustment(0.5, true)),
                buildCellKey("never-settled-system"),
                buildOwnedCell());

            assertThat(requireLoneCell(painted).outlinePaint().colour())
                .isEqualTo(FACTIONLESS_NEUTRAL);
            assertThat(requireLoneCell(painted).outlinePaint().alpha())
                .isEqualTo(1.0f);
        }

        @Test
        void buildPaintedCellForSystemDrawsAnInhabitedCellWithNoHolderAsSettledRatherThanBackdrop() {
            // The claims layer's case: vanilla's claim walk skips a hidden market, and
            // a pirate base is created hidden, so a system settled by pirates alone
            // resolves no claimant and arrives here holderless - exactly as an empty
            // system does. It must still take the settled bundle, since the uninhabited
            // bundle is what the uninhabited-systems checkbox switches off, and a
            // populated system erased by that checkbox is the map lying about what is there.
            //
            // The two bundles are told apart by the fill: only the settled one carries one here.
            var painted = PaintedCellBuilder.buildPaintedCellForSystem(
                buildFactionlessDrawablesWith(buildFilledOutlineStyle(), buildDrawnOutlineStyle()),
                buildCellKey(UNHELD_INHABITED_SYSTEM_ID),
                buildOwnedCell());

            assertThat(requireLoneCell(painted).fillTriangles())
                .isNotEmpty();
            assertThat(requireLoneCell(painted).fillPaint().colour())
                .isEqualTo(FACTIONLESS_NEUTRAL);
        }

        @Test
        void buildPaintedCellForSystemDrawsAnUninhabitedCellWithNoFill() {
            // The other half of the case above: a system nothing stands in takes the outline-only
            // bundle, so the assertion there is about the classification rather than about every
            // factionless cell happening to carry a fill.
            var painted = PaintedCellBuilder.buildPaintedCellForSystem(
                buildFactionlessDrawablesWith(buildFilledOutlineStyle(), buildDrawnOutlineStyle()),
                buildCellKey("never-settled-system"),
                buildOwnedCell());

            assertThat(requireLoneCell(painted).fillTriangles())
                .isEmpty();
        }

        @Test
        void buildPaintedCellForSystemKeepsADecivilisedCellAtFullStrengthWithNoRecedeInThePass() {
            // Off filter the pass's recede is the identity, so a dead colony draws in the neutral
            // colour at its style opacity - an unfiltered map is unchanged by the recede path.
            var painted = PaintedCellBuilder.buildPaintedCellForSystem(
                buildFactionlessDrawablesWith(buildFilledOutlineStyle(), buildDrawnOutlineStyle()),
                buildCellKey(DECIVILISED_SYSTEM_ID),
                buildOwnedCell());

            assertThat(requireLoneCell(painted).fillPaint().colour())
                .isEqualTo(FACTIONLESS_NEUTRAL);
            assertThat(requireLoneCell(painted).fillPaint().alpha())
                .isEqualTo(1.0f);
        }

        @Test
        void buildPaintedCellForSystemKeepsAFactionlessCellDrawnByItsFillAloneWhenTheOutlineIsHidden() {
            // The outline's opacity is its only on/off, so zeroing it must not take the fill down
            // with it: the cell is kept for whichever of the two still puts ink on the map.
            var painted = PaintedCellBuilder.buildPaintedCellForSystem(
                buildFactionlessDrawablesWith(buildFillOnlyStyle(), buildDrawnOutlineStyle()),
                buildCellKey(DECIVILISED_SYSTEM_ID),
                buildOwnedCell());

            assertThat(painted)
                .isNotNull();
            assertThat(requireLoneCell(painted).fillTriangles())
                .isNotEmpty();
            assertThat(requireLoneCell(painted).outlinePaint().isHidden())
                .isTrue();
        }

        @Test
        void buildPaintedCellForSystemBakesNoFillTrianglesForAnOutlineOnlyFactionlessCell() {
            // Uninhabited cells cover everything nothing else holds, so triangulating a fill they
            // never paints would be the rebuild's largest wasted cost - the geometry stays unbuilt.
            var painted = PaintedCellBuilder.buildPaintedCellForSystem(
                buildFactionlessDrawablesWith(buildFilledOutlineStyle(), buildDrawnOutlineStyle()),
                buildCellKey("never-settled-system"),
                buildOwnedCell());

            assertThat(painted)
                .isNotNull();
            assertThat(requireLoneCell(painted).fillTriangles())
                .isEmpty();
        }

        @Test
        void buildPaintedCellForSystemRoundsALoneCellsOutlineWhenTheSectorWideGateIsOn() {

            var painted = PaintedCellBuilder.buildPaintedCellForSystem(
                buildRoundingFactionlessDrawablesWith(buildFilledOutlineStyle()),
                buildCellKey(DECIVILISED_SYSTEM_ID),
                buildOwnedCell());

            // The cell's own sharp corner is gone: rounding steps back from it along both edges,
            // so the vertex the raw Voronoi cell had at the origin is not in the stroked outline.
            assertThat(hasPoint(requireLoneCell(painted).outlineEdges(), 0, 0))
                .isFalse();

            // And the fill is triangulated from that same rounded ring rather than the raw one -
            // built at all is the observable half, since a fill cut from the sharp cell would
            // spill past the line the outline strokes.
            assertThat(requireLoneCell(painted).fillTriangles())
                .isNotEmpty();
        }

        @Test
        void buildPaintedCellForSystemLeavesALoneCellsOutlineSharpWhenTheGateIsOff() {
            // The other half of the gate, and the reason the case above is not just "the outline
            // has vertices": with rounding off the raw Voronoi cell is what the map draws, corner
            // at the origin included.
            var painted = PaintedCellBuilder.buildPaintedCellForSystem(
                buildFactionlessDrawablesWith(buildFilledOutlineStyle(), buildDrawnOutlineStyle()),
                buildCellKey(DECIVILISED_SYSTEM_ID),
                buildOwnedCell());

            assertThat(hasPoint(requireLoneCell(painted).outlineEdges(), 0, 0))
                .isTrue();
        }

        @Test
        void buildPaintedCellForSystemReportsALoneCellsRoundedRingAsWhatItPainted() {
            // The ring the cell reports is the one it strokes, not the cell it was shaped from.
            // Everything that has to know where a cell put ink - the cursor read, the wash lit over
            // it - reads this and nothing else, so a raw ring reported here would have them both
            // working from corners the cell does not draw.
            var painted = PaintedCellBuilder.buildPaintedCellForSystem(
                buildRoundingFactionlessDrawablesWith(buildFilledOutlineStyle()),
                buildCellKey(DECIVILISED_SYSTEM_ID),
                buildOwnedCell());

            assertThat(hasVertex(painted.paintedExtent(), 0, 0))
                .isFalse();

            // And it is the stroked ring itself rather than some other rounding of the cell: every
            // vertex it reports is one the outline passes through.
            for (var vertex : painted.paintedExtent()) {
                assertThat(hasPoint(
                        requireLoneCell(painted).outlineEdges(),
                        (float) vertex[0],
                        (float) vertex[1]))
                    .isTrue();
            }
        }

        @Test
        void buildPaintedCellForSystemReportsALoneCellsSharpRingWhenTheGateIsOff() {
            // With rounding off the cell strokes the shape it was handed, so that is what it
            // reports - the four corners of the fixture's square, the origin included.
            var painted = PaintedCellBuilder.buildPaintedCellForSystem(
                buildFactionlessDrawablesWith(buildFilledOutlineStyle(), buildDrawnOutlineStyle()),
                buildCellKey(DECIVILISED_SYSTEM_ID),
                buildOwnedCell());

            assertThat(painted.paintedExtent())
                .hasSize(4);
            assertThat(hasVertex(painted.paintedExtent(), 0, 0))
                .isTrue();
        }

        @Test
        void buildPaintedCellForSystemReportsAnOwnedCellsRawRingEvenUnderTheRoundingGate() {
            // A fused cell is bounded by its cluster's border rather than by anything of its own,
            // and that border is rounded where the cluster is traced. So the cell reports its raw
            // extent under the same gate that rounds a lone cell's: rounding it here would pull the
            // cell in from a frontier the cluster draws for it.
            var painted = PaintedCellBuilder.buildPaintedCellForSystem(
                buildRoundingDrawablesWith(buildViewMockAdjusting(ElementStyleAdjustment.NONE)),
                buildCellKey(SYSTEM_ID),
                buildOwnedCell());

            assertThat(painted.paintedExtent())
                .hasSize(4);
            assertThat(hasVertex(painted.paintedExtent(), 0, 0))
                .isTrue();
        }

        @Test
        void buildPaintedCellForSystemDropsAFactionlessCellThatDrawsNothing() {

            var painted = PaintedCellBuilder.buildPaintedCellForSystem(
                buildFactionlessDrawablesWith(buildNoColourStyle(), buildDrawnOutlineStyle()),
                buildCellKey(DECIVILISED_SYSTEM_ID),
                buildOwnedCell());

            assertThat(painted)
                .isNull();
        }

        // A view stub that paints in the full faction style (never independent) and
        // returns the given adjustment for any bloc, so each test names only the
        // adjustment it exercises.
        private static PoliticalMapView buildViewMockAdjusting(ElementStyleAdjustment adjustment) {
            return buildViewMockDeciding(false, adjustment);
        }

        // The unfiltered factionless backdrop under a chosen pair of factionless category styles,
        // so a test names the two styles whose interplay it is about and shares everything else.
        private static PoliticalMapTerritories buildFactionlessDrawablesWith(
                CategoryStyle decivilisedStyle,
                CategoryStyle uninhabitedStyle) {

            return buildFactionlessDrawablesWith(
                buildFactionlessTheme(
                    decivilisedStyle,
                    uninhabitedStyle,
                    ThemeFixtures.createInertGlobalStyle()),
                null,
                ElementStyleAdjustment.NONE);
        }

        // A filtered pass over the factionless backdrop, receding by the given adjustment, with one
        // style shared by both factionless categories - so which of the two a test builds decides
        // the outcome and the styles themselves cannot account for it.
        private static PoliticalMapTerritories buildFilteringFactionlessDrawablesWith(
                CategoryStyle factionlessStyle,
                ElementStyleAdjustment recede) {

            return buildFilteringFactionlessDrawablesWith(factionlessStyle, recede, Set.of());
        }

        // The same filtered pass with the spotlit bloc recorded as living in the given systems, so
        // a case can put a cell inside and outside the pick's presence under one identical recede.
        private static PoliticalMapTerritories buildFilteringFactionlessDrawablesWith(
                CategoryStyle factionlessStyle,
                ElementStyleAdjustment recede,
                Set<SystemKey> spotlitPresenceSystemKeys) {

            return buildFactionlessDrawablesWith(
                buildFactionlessTheme(
                    factionlessStyle,
                    factionlessStyle,
                    ThemeFixtures.createInertGlobalStyle()),
                "selected-bloc",
                recede,
                spotlitPresenceSystemKeys);
        }

        // The unfiltered factionless backdrop with the sector-wide corner rounding switched on,
        // for the cases about the shape of a lone cell's outline rather than its colour. One
        // style for both factionless categories, since the rounding is global-tier and cannot
        // differ between them.
        private static PoliticalMapTerritories buildRoundingFactionlessDrawablesWith(
                CategoryStyle factionlessStyle) {

            return buildFactionlessDrawablesWith(
                buildFactionlessTheme(
                    factionlessStyle,
                    factionlessStyle,
                    buildRoundingGlobalStyle()),
                null,
                ElementStyleAdjustment.NONE);
        }

        // The owned backdrop under that same rounding tier, for the case about what a fused cell
        // reports under the gate that reshapes a lone cell's ring - the one arrangement where the
        // two forms have to answer differently about the shape they were built from.
        private static PoliticalMapTerritories buildRoundingDrawablesWith(PoliticalMapView viewMock) {

            return new PoliticalMapTerritories(
                SystemOccupancy.createCopyOf(Map.of(buildCellKey(SYSTEM_ID), OWNER), Set.of(), Set.of()),
                Set.of(),
                new MapStyling(
                    buildFactionlessTheme(STYLE, STYLE, buildRoundingGlobalStyle()),
                    new FactionPalette(FACTIONLESS_NEUTRAL, FACTIONLESS_NEUTRAL),
                    new FactionPalette(DESATURATED_PRIMARY, DESATURATED_SECONDARY),
                    new FactionPalette(PRESENCE_LIFTED, PRESENCE_LIFTED)),
                new ViewGrouping(viewMock, HolderGrouping.identity()),
                ContentInputsFixtures.createInputsRecedingBehind(
                    null, // No bloc spotlighted.
                    ElementStyleAdjustment.NONE),
                Set.of());
        }

        // The sector-wide tier the rounding cases share: every corner arced, at a radius the shared
        // 10-unit cell does not clamp. Held in one place because a case reading a lone cell's ring
        // and one reading a fused cell's have to be under the identical gate for the difference
        // between their answers to be about the cells.
        private static GlobalStyle buildRoundingGlobalStyle() {
            return ThemeFixtures.createGlobalStyleRoundingBy(new CornerRoundingStyle(
                true,
                CORNER_RADIUS,
                CORNER_SEGMENTS,
                NO_CHAMFER,
                CornerRounding.ROUND_EVERY_CORNER));
        }

        // The theme a factionless case reads: the two factionless bundles it is about over the
        // given sector-wide tier, with the owned pair held at the shared style. Taken apart from
        // the backdrop below because the two vary independently - a case is about a category
        // bundle or about a global-tier knob, never both.
        private static RenderStyle buildFactionlessTheme(
                CategoryStyle decivilisedStyle,
                CategoryStyle uninhabitedStyle,
                GlobalStyle globalStyle) {

            var categories = new LinkedHashMap<MapStyleCategory, CategoryStyle>();

            categories.put(PoliticalMapCategory.FACTION, STYLE);
            categories.put(PoliticalMapCategory.INDEPENDENT, STYLE);
            categories.put(PoliticalMapCategory.DECIVILISED, decivilisedStyle);
            categories.put(PoliticalMapCategory.UNINHABITED, uninhabitedStyle);

            return new RenderStyle(globalStyle, categories);
        }

        // The shared factionless backdrop: the theme plus the pass's spotlight state, since a
        // factionless cell's recede is the filter's. What the null-system cases read off it is
        // that a cell drawing as no system resolves no holder and is not taken for settled; that
        // neither read reaches its collection with a null key at all is the resolver's own claim,
        // pinned over null-hostile collections in FactionlessStyleResolverTest.
        private static PoliticalMapTerritories buildFactionlessDrawablesWith(
                RenderStyle theme,
                String selectedBlocId,
                ElementStyleAdjustment recede) {

            return buildFactionlessDrawablesWith(theme, selectedBlocId, recede, Set.of());
        }

        // The same backdrop with the spotlit bloc recorded as living in the given systems, for the
        // cases about a pick's presence sparing a settled cell the recede.
        private static PoliticalMapTerritories buildFactionlessDrawablesWith(
                RenderStyle theme,
                String selectedBlocId,
                ElementStyleAdjustment recede,
                Set<SystemKey> spotlitPresenceSystemKeys) {

            return new PoliticalMapTerritories(
                SystemOccupancy.createCopyOf(
                    Map.of(),
                    Set.of(buildCellKey(DECIVILISED_SYSTEM_ID), buildCellKey(UNHELD_INHABITED_SYSTEM_ID)),
                    spotlitPresenceSystemKeys),
                Set.of(),
                new MapStyling(
                    theme,
                    new FactionPalette(FACTIONLESS_NEUTRAL, FACTIONLESS_NEUTRAL),
                    new FactionPalette(DESATURATED_PRIMARY, DESATURATED_SECONDARY),
                    new FactionPalette(PRESENCE_LIFTED, PRESENCE_LIFTED)),
                new ViewGrouping(
                    buildViewMockAdjusting(ElementStyleAdjustment.NONE),
                    HolderGrouping.identity()),
                ContentInputsFixtures.createInputsRecedingBehind(selectedBlocId, recede),
                Set.of());
        }

        // A category whose outer outline is drawn (a real palette slot, resolved to the neutral
        // colour for a factionless cell), so a factionless cell built under it comes back drawable.
        private static CategoryStyle buildDrawnOutlineStyle() {
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
        private static CategoryStyle buildFilledOutlineStyle() {
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
        private static CategoryStyle buildFillOnlyStyle() {
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
        private static CategoryStyle buildNoColourStyle() {
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
        private static PoliticalMapTerritories buildDrawablesWith(PoliticalMapView viewMock) {
            return buildDrawablesWith(viewMock, false, ElementStyleAdjustment.NONE);
        }

        // A filtered pass whose recede is the given adjustment, backed by a view stub that would
        // return the identity adjustment if consulted - so a recede in the output can only have
        // come from the filter mode bypassing the view.
        private static PoliticalMapTerritories buildFilteringDrawablesWith(ElementStyleAdjustment recede) {
            return buildDrawablesWith(buildViewMockAdjusting(ElementStyleAdjustment.NONE), true, recede);
        }

        // The same backdrop holding the two systems that answer to one ID, each under its own
        // bloc - the only arrangement that tells a cell reading its holder by key from one
        // narrowing to the ID first.
        private static PoliticalMapTerritories buildDrawablesHeldByTwins() {

            var holders = new LinkedHashMap<SystemKey, DominantHolder>();

            holders.put(FIRST_TWIN, OWNER);
            holders.put(SECOND_TWIN, RIVAL_OWNER);

            return new PoliticalMapTerritories(
                SystemOccupancy.createCopyOf(holders, Set.of(), Set.of()),
                Set.of(),
                new MapStyling(
                    PoliticalMapTerritoryFixtures.createRenderStyleForEveryCategory(STYLE),
                    PoliticalMapTerritoryFixtures.NEUTRAL_PALETTE,
                    new FactionPalette(DESATURATED_PRIMARY, DESATURATED_SECONDARY),
                    new FactionPalette(PRESENCE_LIFTED, PRESENCE_LIFTED)),
                new ViewGrouping(
                    buildViewMockAdjusting(ElementStyleAdjustment.NONE),
                    HolderGrouping.identity()),
                ContentInputsFixtures.createInputsRecedingBehind(
                    null, // No bloc spotlighted.
                    ElementStyleAdjustment.NONE),
                Set.of());
        }

        // The one owned system every adjustment test shares over a fixed style/palette backdrop;
        // only the view stub, whether the pass filters, and the recede it applies vary.
        private static PoliticalMapTerritories buildDrawablesWith(
                PoliticalMapView viewMock,
                boolean isFiltering,
                ElementStyleAdjustment recede) {

            // A filtered pass carries the selected bloc's ID; the fixture's holder is never that
            // bloc, so it reads as non-spotlit and the recede applies. Off filter the ID is null.
            return new PoliticalMapTerritories(
                SystemOccupancy.createCopyOf(Map.of(buildCellKey(SYSTEM_ID), OWNER), Set.of(), Set.of()),
                Set.of(),
                new MapStyling(
                    PoliticalMapTerritoryFixtures.createRenderStyleForEveryCategory(STYLE),
                    PoliticalMapTerritoryFixtures.NEUTRAL_PALETTE,
                    new FactionPalette(DESATURATED_PRIMARY, DESATURATED_SECONDARY),
                    new FactionPalette(PRESENCE_LIFTED, PRESENCE_LIFTED)),
                new ViewGrouping(viewMock, HolderGrouping.identity()),
                ContentInputsFixtures.createInputsRecedingBehind(
                    isFiltering ? "selected-bloc" : null,
                    recede),
                Set.of());
        }

        // A small, non-empty square cell so the fill-polygon-empty short-circuit never
        // fires; its edges are all interior seams, which the owned-cell path never reads.
        private static ShapedCell buildOwnedCell() {
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
    private static StyledCell.FusedCell requireFusedCell(PaintedCell painted) {
        assertThat(painted.styledCell()).isInstanceOf(StyledCell.FusedCell.class);
        return (StyledCell.FusedCell) painted.styledCell();
    }

    // Whether a ring of {x, y} pairs passes through the given point - the same question as below,
    // asked of the unflattened form a painted extent is reported in.
    private static boolean hasVertex(List<double[]> ring, double x, double y) {
        for (var vertex : ring) {
            if (vertex[0] == x && vertex[1] == y) {
                return true;
            }
        }
        return false;
    }

    // Whether a flattened GL_LINES run has an endpoint at the given point. The run is a flat
    // [x, y, x, y, ...] of segment endpoints, so a vertex the ring passes through appears in it
    // twice - once ending one segment and once starting the next - and either occurrence answers.
    private static boolean hasPoint(float[] segments, float x, float y) {
        for (var i = 0; i < segments.length; i += 2) {
            if (segments[i] == x && segments[i + 1] == y) {
                return true;
            }
        }
        return false;
    }

    // Narrows a built cell to the lone form - a cell that is its own cluster, carrying its fill and
    // outline. As above, the narrowing doubles as the assertion that the builder read the cell as
    // a factionless cell rather than as part of a cluster.
    private static StyledCell.LoneCell requireLoneCell(PaintedCell painted) {
        assertThat(painted.styledCell()).isInstanceOf(StyledCell.LoneCell.class);
        return (StyledCell.LoneCell) painted.styledCell();
    }
}
