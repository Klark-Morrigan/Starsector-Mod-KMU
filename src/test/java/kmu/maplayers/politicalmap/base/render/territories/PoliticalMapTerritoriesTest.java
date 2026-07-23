package kmu.maplayers.politicalmap.base.render.territories;

import kmlib.starsector.factions.FactionPalette;
import kmlib.starsector.ui.render.gl.UiElementPaint;

import kmu.maplayers.politicalmap.base.BlocStyleAdjustment;
import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.dominance.OwnershipGrouping;
import kmu.maplayers.politicalmap.base.geometry.CellEdge;
import kmu.maplayers.politicalmap.base.geometry.EdgeTarget;
import kmu.maplayers.politicalmap.base.politics.DominantOwner;
import kmu.maplayers.politicalmap.base.render.style.BorderSmoothingStyle;
import kmu.maplayers.politicalmap.base.render.style.CategoryStyle;
import kmu.maplayers.politicalmap.base.render.style.ElementStyle;
import kmu.maplayers.politicalmap.base.render.style.GlobalStyle;
import kmu.maplayers.politicalmap.base.render.style.HatchStyle;
import kmu.maplayers.politicalmap.base.render.style.HoverGlowStyle;
import kmu.maplayers.politicalmap.base.render.style.HoverHighlightStyle;
import kmu.maplayers.politicalmap.base.render.style.HoverWashStyle;
import kmu.maplayers.politicalmap.base.render.style.MapCategory;
import kmu.maplayers.politicalmap.base.render.style.RenderStyle;
import kmu.settings.FactionPaletteChoice;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Pins the built map state the renderer paints and the incremental refresh edits:
 * that the empty fallback is a harmless no-op the render path can lean on after a failed
 * first build, that {@link PoliticalMapTerritories#isEmpty} tracks either draw list, and
 * that the constructor threads its inputs into the matching accessors (the {@link RenderStyle}
 * theme, whose global tier and four same-typed category bundles a swap would not otherwise catch,
 * the view and grouping the incremental re-shape classifies against, and the filter snapshot it
 * recedes by).
 *
 * <p>Also pins the two invariants the cursor read leans on, since a break in either is invisible
 * until a hover lands on the wrong cell: that a cell's draw record and the shape it is hit-tested
 * against are written and dropped together, and that the cluster index tracks ownership through
 * the splits and merges a single flip can cause.
 */
final class PoliticalMapTerritoriesTest {
    // An inert hover highlight: this model carries the style through untouched, and no case here
    // hovers anything, so its values are never read.
    private static final HoverHighlightStyle NO_HOVER_HIGHLIGHT = new HoverHighlightStyle(
            FactionPaletteChoice.NONE, new HoverGlowStyle(0, 0, 0, 0, 0),
            new HoverWashStyle(0, 0, 0));

    @Nested
    class CreateEmpty {

        @Test
        void createEmptyYieldsAnEmptyNoOpFallback() {
            // A stand-in view so this model test names no concrete view: the territories only carry
            // the view for the incremental re-shape to read back, so any PoliticalMapView serves.
            PoliticalMapView viewMock = mock(PoliticalMapView.class);
            var territories = PoliticalMapTerritories.createEmpty(viewMock);

            // Both draw lists empty, so the render is a no-op and isEmpty short-circuits
            // the GL state push; the retained inputs are neutral placeholders the next
            // frame's real build replaces before any incremental pass reads them.
            assertThat(territories.isEmpty()).isTrue();
            assertThat(territories.getStyledCellByCellId()).isEmpty();
            assertThat(territories.getFactionTerritoryByFactionId()).isEmpty();
            assertThat(territories.getOwnerBySystemId()).isEmpty();
            assertThat(territories.getDecivilisedSystemIds()).isEmpty();
            assertThat(territories.getUnfilledSystemIds()).isEmpty();
            assertThat(territories.getNeutralColor()).isEqualTo(Color.GRAY);
            assertThat(territories.getDesaturationPalette())
                    .isEqualTo(new FactionPalette(Color.GRAY, Color.GRAY));
            // The empty fallback is never a filtered build, so it selects no bloc and recedes
            // nothing.
            assertThat(territories.getSelectedBlocId()).isNull();
            assertThat(territories.isFiltering()).isFalse();
            assertThat(territories.getRecedeAdjustment()).isEqualTo(BlocStyleAdjustment.NONE);
            assertThat(territories.getContestedSystemIds()).isEmpty();
        }
    }

    @Nested
    class IsEmpty {

        @Test
        void isEmptyIsTrueWhenBothDrawListsAreEmpty() {
            var territories = drawablesWith(Map.of(), Map.of());

            assertThat(territories.isEmpty()).isTrue();
        }

        @Test
        void isEmptyIsFalseWhenAStyledCellIsPresent() {
            var territories = drawablesWith(Map.of("system", anyStyledCell()), Map.of());

            assertThat(territories.isEmpty()).isFalse();
        }

        @Test
        void isEmptyIsFalseWhenAFactionTerritoryIsPresent() {
            var territories = drawablesWith(Map.of(), Map.of("faction", anyFactionTerritory()));

            assertThat(territories.isEmpty()).isFalse();
        }
    }

    @Nested
    class Getters {

        @Test
        void gettersReturnEachConstructorInputInItsMatchingSlot() {
            Map<String, DominantOwner> owners = new LinkedHashMap<>();
            Set<String> decivilised = new LinkedHashSet<>();
            // A distinct unfilled set so a swapped slot is caught by identity, held apart from the
            // decivilised set it sits beside.
            Set<String> unfilled = new LinkedHashSet<>(Set.of("unfilled-system"));
            var neutral = Color.CYAN;
            var desaturationPalette = new FactionPalette(Color.MAGENTA, Color.ORANGE);
            // Four distinct category instances plus a distinct global tier, all wrapped in one
            // theme, so a swapped style slot is caught by identity, not just by the shared
            // CategoryStyle type the compiler would accept either way.
            var factionStyle = styleMarked(1);
            var independentStyle = styleMarked(2);
            var decivilisedStyle = styleMarked(3);
            var uninhabitedStyle = styleMarked(4);
            Map<MapCategory, CategoryStyle> categories = new EnumMap<>(MapCategory.class);
            categories.put(MapCategory.FACTION, factionStyle);
            categories.put(MapCategory.INDEPENDENT, independentStyle);
            categories.put(MapCategory.DECIVILISED, decivilisedStyle);
            categories.put(MapCategory.UNINHABITED, uninhabitedStyle);
            var globalStyle = new GlobalStyle(new HatchStyle(5, 5, 5),
                    new BorderSmoothingStyle(true, true, 5, 5, 5), NO_HOVER_HIGHLIGHT, 0.3);
            var renderStyle = new RenderStyle(globalStyle, categories);
            PoliticalMapView viewMock = mock(PoliticalMapView.class);
            var grouping = OwnershipGrouping.identity();
            // A distinct, non-identity adjustment so a swapped recede field is caught by value.
            var recedeAdjustment = new BlocStyleAdjustment(0.25, true);
            // A non-null selected bloc so the filter snapshot is caught by value and isFiltering()
            // reads true off it.
            var selectedBlocId = "selected-bloc";
            // A distinct contested set so a swapped filter-snapshot field is caught by identity.
            Set<String> contested = new LinkedHashSet<>(Set.of("contested-system"));

            var territories = new PoliticalMapTerritories(owners, decivilised, unfilled,
                    new MapStyling(renderStyle, neutral, desaturationPalette),
                    new ViewGrouping(viewMock, grouping),
                    new FilterSnapshot(selectedBlocId, recedeAdjustment, contested));

            // The two draw lists are created internally, not passed, so the build can fill them;
            // they start empty and stay mutable for the incremental refresh to edit in place.
            assertThat(territories.getStyledCellByCellId()).isEmpty();
            assertThat(territories.getFactionTerritoryByFactionId()).isEmpty();
            assertThat(territories.getOwnerBySystemId()).isSameAs(owners);
            assertThat(territories.getDecivilisedSystemIds()).isSameAs(decivilised);
            assertThat(territories.getUnfilledSystemIds()).isSameAs(unfilled);
            assertThat(territories.getNeutralColor()).isSameAs(neutral);
            assertThat(territories.getDesaturationPalette()).isSameAs(desaturationPalette);
            assertThat(territories.getRenderStyle()).isSameAs(renderStyle);
            assertThat(territories.getGlobalStyle()).isSameAs(globalStyle);
            assertThat(territories.getCategoryStyle(MapCategory.FACTION)).isSameAs(factionStyle);
            assertThat(territories.getCategoryStyle(MapCategory.INDEPENDENT))
                    .isSameAs(independentStyle);
            assertThat(territories.getCategoryStyle(MapCategory.DECIVILISED))
                    .isSameAs(decivilisedStyle);
            assertThat(territories.getCategoryStyle(MapCategory.UNINHABITED))
                    .isSameAs(uninhabitedStyle);
            assertThat(territories.getView()).isSameAs(viewMock);
            assertThat(territories.getGrouping()).isSameAs(grouping);
            assertThat(territories.getSelectedBlocId()).isEqualTo(selectedBlocId);
            assertThat(territories.isFiltering()).isTrue();
            assertThat(territories.getRecedeAdjustment()).isSameAs(recedeAdjustment);
            assertThat(territories.getContestedSystemIds()).isSameAs(contested);
        }
    }

    @Nested
    class PutStyledCell {

        @Test
        void putStyledCellRecordsTheDrawRecordAndItsShapeUnderTheSameSystem() {
            var territories = drawablesWith(Map.of(), Map.of());
            var styledCell = anyStyledCell();
            var fillPolygon = squarePolygon();

            territories.putStyledCell("system", styledCell, fillPolygon);

            assertThat(territories.getStyledCellByCellId()).containsOnlyKeys("system");
            assertThat(territories.getStyledCellByCellId().get("system")).isSameAs(styledCell);
            assertThat(territories.getFillPolygonByCellId()).containsOnlyKeys("system");
            assertThat(territories.getFillPolygonByCellId().get("system")).isSameAs(fillPolygon);
        }

        @Test
        void putStyledCellReplacesBothHalvesWhenACellIsReshaped() {
            // The drift the paired write exists to prevent: a re-shaped cell must not keep
            // answering the cursor with the extent it had before it was re-shaped.
            var territories = drawablesWith(Map.of(), Map.of());
            territories.putStyledCell("system", anyStyledCell(), squarePolygon());
            var reshapedCell = anyStyledCell();
            var reshapedPolygon = trianglePolygon();

            territories.putStyledCell("system", reshapedCell, reshapedPolygon);

            assertThat(territories.getStyledCellByCellId().get("system")).isSameAs(reshapedCell);
            assertThat(territories.getFillPolygonByCellId().get("system"))
                    .isSameAs(reshapedPolygon);
        }
    }

    @Nested
    class RemoveStyledCell {

        @Test
        void removeStyledCellDropsTheDrawRecordAndItsShapeTogether() {
            // A cell that draws nothing can be hovered no more than it can be seen, so the
            // shape must go with the draw record rather than linger as a phantom hit region.
            var territories = drawablesWith(Map.of(), Map.of());
            territories.putStyledCell("system", anyStyledCell(), squarePolygon());

            territories.removeStyledCell("system");

            assertThat(territories.getStyledCellByCellId()).isEmpty();
            assertThat(territories.getFillPolygonByCellId()).isEmpty();
        }

        @Test
        void removeStyledCellLeavesEveryOtherCellStanding() {
            var territories = drawablesWith(Map.of(), Map.of());
            territories.putStyledCell("dropped", anyStyledCell(), squarePolygon());
            territories.putStyledCell("kept", anyStyledCell(), trianglePolygon());

            territories.removeStyledCell("dropped");

            assertThat(territories.getStyledCellByCellId()).containsOnlyKeys("kept");
            assertThat(territories.getFillPolygonByCellId()).containsOnlyKeys("kept");
        }
    }

    @Nested
    class GetStarIconGeometry {

        @Test
        void getStarIconGeometryReturnsTheGeometryCapturedForTheSystem() {
            var territories = drawablesWith(Map.of(), Map.of());
            var starIcon = new StarIconGeometry(12d, -34d, 56f);
            territories.putStarIconGeometry("system", starIcon);

            assertThat(territories.getStarIconGeometry("system")).isSameAs(starIcon);
        }

        @Test
        void getStarIconGeometryIsNullForASystemThatWasNeverCaptured() {
            // A system with no anchor is skipped at build, so its cursor read finds no icon to
            // gate on rather than a stale one - null is the "not over an icon" answer.
            var territories = drawablesWith(Map.of(), Map.of());

            assertThat(territories.getStarIconGeometry("uncaptured")).isNull();
        }
    }

    @Nested
    class ReindexClusters {

        @Test
        void reindexClustersResolvesASystemToItsWholeContiguousTerritory() {
            var territories = ownedBy(Map.of("A", "F", "B", "F"));

            reindex(territories, Map.of(
                    "A", List.of(edgeTo("B")),
                    "B", List.of(edgeTo("A"))));

            assertThat(territories.getClusterIndex().findClusterMembersOf("A"))
                    .containsExactlyInAnyOrder("A", "B");
        }

        @Test
        void reindexClustersExcludesADifferentlyOwnedNeighbour() {
            var territories = ownedBy(Map.of("A", "F", "B", "RIVAL"));

            reindex(territories, Map.of(
                    "A", List.of(edgeTo("B")),
                    "B", List.of(edgeTo("A"))));

            assertThat(territories.getClusterIndex().findClusterMembersOf("A"))
                    .containsExactly("A");
        }

        @Test
        void reindexClustersSeversOneTerritoryInTwoWhenTheBridgeSystemFlips() {
            // Why the index is re-derived rather than patched: B is the only thing joining A to
            // C, so B changing hands splits one territory into two pockets - a change no edit of
            // the old index would find, since neither A nor C was itself touched.
            var edges = Map.of(
                    "A", List.of(edgeTo("B")),
                    "B", List.of(edgeTo("A"), edgeTo("C")),
                    "C", List.of(edgeTo("B")));
            var territories = ownedBy(Map.of("A", "F", "B", "F", "C", "F"));
            reindex(territories, edges);
            assertThat(territories.getClusterIndex().findClusterMembersOf("A"))
                    .containsExactlyInAnyOrder("A", "B", "C");

            territories.getOwnerBySystemId().put("B", ownerOf("RIVAL"));
            reindex(territories, edges);

            assertThat(territories.getClusterIndex().findClusterMembersOf("A"))
                    .containsExactly("A");
            assertThat(territories.getClusterIndex().findClusterMembersOf("C"))
                    .containsExactly("C");
        }

        @Test
        void reindexClustersBridgesTwoTerritoriesIntoOneWhenTheGapSystemIsGained() {
            // The mirror of the sever: B joining F merges what were two lone pockets.
            var edges = Map.of(
                    "A", List.of(edgeTo("B")),
                    "B", List.of(edgeTo("A"), edgeTo("C")),
                    "C", List.of(edgeTo("B")));
            var territories = ownedBy(Map.of("A", "F", "B", "RIVAL", "C", "F"));
            reindex(territories, edges);

            territories.getOwnerBySystemId().put("B", ownerOf("F"));
            reindex(territories, edges);

            assertThat(territories.getClusterIndex().findClusterMembersOf("A"))
                    .containsExactlyInAnyOrder("A", "B", "C");
        }

        @Test
        void reindexClustersCarriesNoClusterForAnUnownedSystem() {
            var territories = ownedBy(Map.of("A", "F"));

            reindex(territories, Map.of(
                    "A", List.of(edgeTo("UNOWNED")),
                    "UNOWNED", List.of(edgeTo("A"))));

            assertThat(territories.getClusterIndex().findClusterMembersOf("UNOWNED")).isEmpty();
        }
    }

    // A territories holding the given owners, the one input the cluster index is derived from;
    // every other slot is an inert placeholder.
    private static PoliticalMapTerritories ownedBy(Map<String, String> factionIdBySystemId) {
        var territories = drawablesWith(Map.of(), Map.of());
        for (var entry : factionIdBySystemId.entrySet()) {
            territories.getOwnerBySystemId().put(entry.getKey(), ownerOf(entry.getValue()));
        }
        return territories;
    }

    // Clustering keys off the faction id alone, so the palette shades are inert here.
    private static DominantOwner ownerOf(String factionId) {
        return new DominantOwner(factionId, Color.GRAY, Color.GRAY);
    }

    // One cell edge facing the given neighbour system. Clustering reads only the adjacency tag,
    // so the segment is left at the origin.
    private static CellEdge edgeTo(String neighbourSystemId) {
        return new CellEdge(0, 0, 0, 0, new EdgeTarget.AcrossSystem(neighbourSystemId));
    }

    // Reindexes the clusters over the given adjacency, each cell drawing as its own star
    // (identity draws-as), so a test names only the edges the clusters are walked over.
    private static void reindex(
            PoliticalMapTerritories territories, Map<String, List<CellEdge>> edges) {
        var systemIdByCellId = new LinkedHashMap<String, String>();
        for (var cellId : edges.keySet()) {
            systemIdByCellId.put(cellId, cellId);
        }
        territories.reindexClusters(edges, systemIdByCellId);
    }

    // Two distinct fill shapes, so a test that swaps one for the other is caught by identity.
    // Nothing here reads the geometry, only which instance is held against a system.
    private static List<double[]> squarePolygon() {
        return List.of(new double[] {0, 0}, new double[] {1, 0}, new double[] {1, 1},
                new double[] {0, 1});
    }

    private static List<double[]> trianglePolygon() {
        return List.of(new double[] {0, 0}, new double[] {2, 0}, new double[] {0, 2});
    }

    // A territories whose only varying inputs are the two draw lists; the retained inputs
    // are inert placeholders, since isEmpty reads only the draw lists.
    private static PoliticalMapTerritories drawablesWith(Map<String, StyledCell> styledCells,
            Map<String, FactionTerritory> territories) {
        PoliticalMapView viewMock = mock(PoliticalMapView.class);
        var drawables = new PoliticalMapTerritories(new LinkedHashMap<>(), new LinkedHashSet<>(),
                new LinkedHashSet<>(),
                new MapStyling(null, Color.GRAY, null),
                new ViewGrouping(viewMock, OwnershipGrouping.identity()),
                new FilterSnapshot(null, BlocStyleAdjustment.NONE, new LinkedHashSet<>()));
        // The draw lists are no longer constructor inputs; fill the internally-created maps so
        // this fixture's only varying state is what isEmpty reads.
        drawables.getStyledCellByCellId().putAll(styledCells);
        drawables.getFactionTerritoryByFactionId().putAll(territories);
        return drawables;
    }

    // A hidden element paint (null color) is enough to stand in wherever a StyledCell or
    // FactionTerritory only needs to exist, not draw.
    private static UiElementPaint hiddenPaint() {
        return new UiElementPaint(null, 0f);
    }

    private static StyledCell anyStyledCell() {
        return new StyledCell(new float[0], new float[0], new float[0],
                hiddenPaint(), hiddenPaint(), hiddenPaint(), 0f, 0f);
    }

    private static FactionTerritory anyFactionTerritory() {
        return new FactionTerritory(new float[0], new float[0], hiddenPaint(),
                List.of(), hiddenPaint(), 0f);
    }

    // A CategoryStyle whose opacities and widths carry one marker value, so four otherwise
    // interchangeable style bundles are distinct instances.
    private static CategoryStyle styleMarked(double marker) {
        var element = new ElementStyle(FactionPaletteChoice.PRIMARY, marker);
        return new CategoryStyle(element, element, marker, element, marker);
    }
}
