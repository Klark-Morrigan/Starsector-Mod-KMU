package kmu.maplayers.politicalmap.base.render.hover;

import kmu.maplayers.politicalmap.base.politics.DominantOwner;
import kmu.maplayers.politicalmap.base.render.territories.FactionTerritory;
import kmu.maplayers.politicalmap.base.render.territories.PoliticalMapTerritories;
import kmu.maplayers.politicalmap.base.render.territories.PoliticalMapTestTerritories;
import kmu.settings.FactionPaletteChoice;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins how the political map answers the framework's three questions about a hovered cell:
 * whose loops are the candidates, what shade the ground burns in, and what extent was painted
 * there - each read off the frame's own draw lists.
 *
 * <p>Also pins the identity contract the highlight's memoisation depends on: the same instances
 * come back for as long as the build behind them stands, so a cursor resting on one cell costs
 * one trace rather than one per frame.
 */
final class PoliticalMapHoverHighlightSourceTest {
    private static final String FACTION_ID = "hegemony";
    private static final Color PRIMARY = Color.RED;
    private static final Color SECONDARY = Color.BLUE;
    private static final DominantOwner OWNER =
            new DominantOwner(FACTION_ID, PRIMARY, SECONDARY);

    @Nested
    class ResolveCandidateFrontierLoopsOf {

        @Test
        void resolveCandidateFrontierLoopsOfReturnsTheOwnersOwnBorderLoops() {
            // Every loop the owner traced is a candidate; which of them encloses this cell is
            // the highlight's own geometric question, not one answered here.
            var loops = List.of(squareRun(0, 0, 100), squareRun(500, 0, 100));
            var source = sourceOf(territoriesWith(
                    Map.of("A", OWNER),
                    Map.of("A", square(10, 10, 80)),
                    territoryWithLoops(loops)));

            assertThat(source.resolveCandidateFrontierLoopsOf("A"))
                    .containsExactlyElementsOf(loops);
        }

        @Test
        void resolveCandidateFrontierLoopsOfReturnsNothingForFactionlessGround() {
            // A decivilised or uninhabited cell fuses into no territory, so it has no frontier
            // to offer at all.
            var source = sourceOf(territoriesWith(
                    Map.of(),
                    Map.of("A", square(10, 10, 80)),
                    null));

            assertThat(source.resolveCandidateFrontierLoopsOf("A")).isEmpty();
        }

        @Test
        void resolveCandidateFrontierLoopsOfReturnsNothingWhenTheOwnerBakedNoBorder() {
            // The owner's border is "No color", so its territory carries no loops - owned ground
            // that still has nothing to halo.
            var source = sourceOf(territoriesWith(
                    Map.of("A", OWNER),
                    Map.of("A", square(10, 10, 80)),
                    territoryWithLoops(List.of())));

            assertThat(source.resolveCandidateFrontierLoopsOf("A")).isEmpty();
        }

        @Test
        void resolveCandidateFrontierLoopsOfReturnsTheSameListWhileTheBuildStands() {
            // The highlight memoises on this instance, so handing back a fresh copy per call
            // would re-trace the loops every frame the cursor rests on one cell.
            var source = sourceOf(territoriesWith(
                    Map.of("A", OWNER),
                    Map.of("A", square(10, 10, 80)),
                    territoryWithLoops(List.of(squareRun(0, 0, 100)))));

            assertThat(source.resolveCandidateFrontierLoopsOf("A"))
                    .isSameAs(source.resolveCandidateFrontierLoopsOf("A"));
        }
    }

    @Nested
    class ResolveHighlightColourOf {

        @Test
        void resolveHighlightColourOfPaintsOwnedGroundInItsOwnersShade() {
            var source = sourceOf(territoriesWith(
                    Map.of("A", OWNER),
                    Map.of("A", square(10, 10, 80)),
                    territoryWithLoops(List.of())));

            assertThat(source.resolveHighlightColourOf("A", FactionPaletteChoice.SECONDARY))
                    .isEqualTo(SECONDARY);
        }

        @Test
        void resolveHighlightColourOfPaintsFactionlessGroundInTheNeutralShade() {
            // Unowned ground has no palette of its own, so the highlight says so in the same
            // neutral the cell's own outline draws in.
            var source = sourceOf(territoriesWith(
                    Map.of(),
                    Map.of("A", square(10, 10, 80)),
                    null));

            assertThat(source.resolveHighlightColourOf("A", FactionPaletteChoice.PRIMARY))
                    .isEqualTo(PoliticalMapTestTerritories.NEUTRAL_COLOUR);
        }

        @Test
        void resolveHighlightColourOfReturnsNothingForANoColorChoice() {
            // The theme points the highlight at no shade at all, which the render pass reads as
            // "skip the whole thing".
            var source = sourceOf(territoriesWith(
                    Map.of("A", OWNER),
                    Map.of("A", square(10, 10, 80)),
                    territoryWithLoops(List.of())));

            assertThat(source.resolveHighlightColourOf("A", FactionPaletteChoice.NONE)).isNull();
        }
    }

    @Nested
    class ResolvePaintedExtentOf {

        @Test
        void resolvePaintedExtentOfReturnsTheShapeTheBuildRecordedForTheCell() {
            var paintedExtent = square(10, 10, 80);
            var source = sourceOf(territoriesWith(
                    Map.of("A", OWNER),
                    Map.of("A", paintedExtent),
                    territoryWithLoops(List.of())));

            assertThat(source.resolvePaintedExtentOf("A")).isSameAs(paintedExtent);
        }

        @Test
        void resolvePaintedExtentOfReturnsNothingForACellTheBuildDropped() {
            // A cell that puts no ink on the map is absent from the extents rather than present
            // with an empty shape; the caller must read the two the same way.
            var source = sourceOf(territoriesWith(
                    Map.of("A", OWNER),
                    Map.of(),
                    territoryWithLoops(List.of())));

            assertThat(source.resolvePaintedExtentOf("A")).isEmpty();
        }
    }

    private static PoliticalMapHoverHighlightSource sourceOf(
            PoliticalMapTerritories territories) {
        return new PoliticalMapHoverHighlightSource(territories);
    }

    // Territories carrying just what the source reads: who owns each system, each cell's shape,
    // and the owner's traced loops. A null territory stands for a faction with none - the state
    // a factionless cell's owner lookup lands in.
    private static PoliticalMapTerritories territoriesWith(
            Map<String, DominantOwner> ownerBySystemId,
            Map<String, List<double[]>> fillPolygonBySystemId,
            FactionTerritory territory) {
        var territories = PoliticalMapTestTerritories
                .createTerritoriesOwnedBy(ownerBySystemId);
        for (var cell : fillPolygonBySystemId.entrySet()) {
            territories.putStyledCell(
                    cell.getKey(),
                    PoliticalMapTestTerritories.createPlaceholderStyledCell(),
                    cell.getValue());
        }
        if (territory != null) {
            territories.getFactionTerritoryByFactionId().put(FACTION_ID, territory);
        }
        return territories;
    }

    // A territory whose loops are all the source reads; its fills and paints never come up here.
    private static FactionTerritory territoryWithLoops(List<float[]> borderLoops) {
        return PoliticalMapTestTerritories.createTerritoryWithLoops(borderLoops);
    }

    // An axis-aligned square, counter-clockwise, standing in for a cell's painted extent; the
    // source hands it back untouched, so only its identity matters.
    private static List<double[]> square(double minX, double minY, double side) {
        return List.of(
                new double[] {minX, minY},
                new double[] {minX + side, minY},
                new double[] {minX + side, minY + side},
                new double[] {minX, minY + side});
    }

    // The same square as the baked [x, y, x, y, ...] run a border loop is kept in.
    private static float[] squareRun(float minX, float minY, float side) {
        return new float[] {
            minX, minY,
            minX + side, minY,
            minX + side, minY + side,
            minX, minY + side};
    }
}
