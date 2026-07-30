package kmu.maplayers.politicalmap.base.render.territories;

import kmlib.starsector.factions.FactionPalette;

import kmu.maplayers.base.geometry.CellEdge;
import kmu.maplayers.base.geometry.CellGeometryCache;
import kmu.maplayers.base.geometry.EdgeTarget;
import kmu.maplayers.base.theme.BorderSmoothingStyle;
import kmu.maplayers.base.theme.CategoryStyle;
import kmu.maplayers.base.theme.ElementStyle;
import kmu.maplayers.base.theme.GlobalStyle;
import kmu.maplayers.base.theme.HatchStyle;
import kmu.maplayers.base.theme.HoverGlowStyle;
import kmu.maplayers.base.theme.HoverHighlightStyle;
import kmu.maplayers.base.theme.HoverWashStyle;
import kmu.maplayers.base.theme.MapCategory;
import kmu.maplayers.base.theme.RenderStyle;
import kmu.maplayers.politicalmap.base.BlocStyleAdjustment;
import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.dominance.OwnershipGrouping;
import kmu.maplayers.politicalmap.base.politics.DominantOwner;
import kmu.settings.FactionPaletteChoice;
import kmu.settings.KmuLunaSettings;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.awt.Color;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Pins what a bloc's baked territory is: one frontier around everything it holds contiguously,
 * a separate ring per disjoint cluster, and the two paints its fill and border draw under - plus
 * the two cases that bake nothing at all rather than a record the draw pass would skip.
 *
 * <p>The ring count is the claim worth guarding. A bloc is traced from its member cells in one
 * pass, so two systems that touch must come back as a single continuous border rather than two
 * squares drawn over each other - and two that do not touch must stay two, since that is what
 * makes an enclave read as an enclave.
 *
 * <p>The geometry underneath is pinned elsewhere and only wired here: the ring trace by
 * {@link kmu.maplayers.base.render.regions.ClusterBorderTraceIntegrationTest}, the fill's carve by
 * {@link kmu.maplayers.base.render.regions.SplitFillBuilderTest}, the palette rules by
 * {@link kmu.maplayers.politicalmap.base.render.style.MapPalettes}'s own suite. Cells are
 * hand-built 2000-unit squares, sized well clear of the fixed border channel, so which cells
 * touch is plain to read.
 */
final class FactionTerritoryBuilderTest {
    private static final String HEGEMONY = "hegemony";
    private static final String TRITACHYON = "tritachyon";
    // Two cells of one bloc meeting along x = 2000, so the pair can be asked for a fused
    // frontier. The three that follow share no edge with anything: each is enclosed by the reach
    // bound alone, so a case naming one of them traces a closed ring whoever else is on the map.
    private static final String HELD_SYSTEM = "held";
    private static final String NEIGHBOUR_SYSTEM = "neighbour";
    private static final String ISLAND_SYSTEM = "island";
    private static final String EXCLAVE_SYSTEM = "exclave";
    private static final String RIVAL_SYSTEM = "rival";
    // The owner's two shades, kept distinct so an observed paint names which slot it came from.
    private static final Color OWNER_PRIMARY = Color.RED;
    private static final Color OWNER_SECONDARY = Color.BLUE;
    private static final DominantOwner HEGEMONY_OWNER =
            new DominantOwner(HEGEMONY, OWNER_PRIMARY, OWNER_SECONDARY);
    private static final DominantOwner TRITACHYON_OWNER =
            new DominantOwner(TRITACHYON, OWNER_PRIMARY, OWNER_SECONDARY);
    private static final double FILL_OPACITY = 0.5;
    private static final double BORDER_OPACITY = 0.25;
    private static final double BORDER_WIDTH = 3.0;
    // The live Dev-tab trace parameters, stubbed at their read: a weld tolerance loose enough to
    // chain the hand-built corners, and the miter limit the shipped border uses.
    private static final double WELD_TOLERANCE = 1e-3;
    private static final double MITER_SPIKE_LIMIT = 4.0;
    // An inert hover highlight: nothing built here is hovered, so it is carried and never read.
    private static final HoverHighlightStyle NO_HOVER_HIGHLIGHT = new HoverHighlightStyle(
            FactionPaletteChoice.NONE, new HoverGlowStyle(0, 0, 0, 0, 0),
            new HoverWashStyle(0, 0, 0));
    private static final Map<String, List<CellEdge>> EDGES = Map.of(
            HELD_SYSTEM, List.of(
                    edgeFacing(0, 0, 2000, 0, null),
                    edgeFacing(2000, 0, 2000, 2000, NEIGHBOUR_SYSTEM),
                    edgeFacing(2000, 2000, 0, 2000, null),
                    edgeFacing(0, 2000, 0, 0, null)),
            NEIGHBOUR_SYSTEM, List.of(
                    edgeFacing(2000, 0, 4000, 0, null),
                    edgeFacing(4000, 0, 4000, 2000, null),
                    edgeFacing(4000, 2000, 2000, 2000, null),
                    edgeFacing(2000, 2000, 2000, 0, HELD_SYSTEM)),
            ISLAND_SYSTEM, List.of(
                    edgeFacing(10000, 0, 12000, 0, null),
                    edgeFacing(12000, 0, 12000, 2000, null),
                    edgeFacing(12000, 2000, 10000, 2000, null),
                    edgeFacing(10000, 2000, 10000, 0, null)),
            EXCLAVE_SYSTEM, List.of(
                    edgeFacing(30000, 0, 32000, 0, null),
                    edgeFacing(32000, 0, 32000, 2000, null),
                    edgeFacing(32000, 2000, 30000, 2000, null),
                    edgeFacing(30000, 2000, 30000, 0, null)),
            RIVAL_SYSTEM, List.of(
                    edgeFacing(20000, 0, 22000, 0, null),
                    edgeFacing(22000, 0, 22000, 2000, null),
                    edgeFacing(22000, 2000, 20000, 2000, null),
                    edgeFacing(20000, 2000, 20000, 0, null)));

    // The trace reads its parameters off the live settings, so the seam stands for every case
    // here; without it the two Dev-tab reads would fault outside the game.
    private MockedStatic<KmuLunaSettings> settingsMock;

    @BeforeEach
    void openTheBorderTraceSeam() {
        settingsMock = mockStatic(KmuLunaSettings.class);
        settingsMock.when(KmuLunaSettings::getPoliticalMapBorderWeldTolerance)
                .thenReturn(WELD_TOLERANCE);
        settingsMock.when(KmuLunaSettings::getPoliticalMapBorderMiterLimit)
                .thenReturn(MITER_SPIKE_LIMIT);
    }

    @AfterEach
    void closeTheBorderTraceSeam() {
        settingsMock.close();
    }

    @Nested
    class BuildFactionTerritory {

        @Test
        void buildFactionTerritoryTracesTwoTouchingSystemsAsOneFrontier() {
            var territory = FactionTerritoryBuilder.buildFactionTerritory(
                    territoriesStyledBy(drawnStyle()),
                    cellsFor(HELD_SYSTEM, NEIGHBOUR_SYSTEM),
                    HEGEMONY,
                    List.of(HELD_SYSTEM, NEIGHBOUR_SYSTEM));

            // The shared edge is a same-bloc seam, so it is never a border: the pair reads as one
            // territory rather than two squares stroked along the line between them.
            assertThat(territory.borderLoops()).hasSize(1);
            assertThat(territory.fillTriangles()).isNotEmpty();
        }

        @Test
        void buildFactionTerritoryTracesDisjointHoldingsAsARingApiece() {
            var territory = FactionTerritoryBuilder.buildFactionTerritory(
                    territoriesStyledBy(drawnStyle()),
                    cellsFor(ISLAND_SYSTEM, EXCLAVE_SYSTEM),
                    HEGEMONY,
                    List.of(ISLAND_SYSTEM, EXCLAVE_SYSTEM));

            // Rebuilding a bloc from its current members re-splits it: an exclave keeps its own
            // frontier instead of being welded to the homeland by the trace.
            assertThat(territory.borderLoops()).hasSize(2);
        }

        @Test
        void buildFactionTerritoryPaintsEachSlotFromItsOwnPaletteChoiceAndOpacity() {
            var territory = FactionTerritoryBuilder.buildFactionTerritory(
                    territoriesStyledBy(drawnStyle()),
                    cellsFor(ISLAND_SYSTEM),
                    HEGEMONY,
                    List.of(ISLAND_SYSTEM));

            assertThat(territory.fill().color()).isEqualTo(OWNER_PRIMARY);
            assertThat(territory.fill().alpha()).isEqualTo((float) FILL_OPACITY);
            assertThat(territory.border().color()).isEqualTo(OWNER_SECONDARY);
            assertThat(territory.border().alpha()).isEqualTo((float) BORDER_OPACITY);
            assertThat(territory.borderWidth()).isEqualTo((float) BORDER_WIDTH);
        }

        @Test
        void buildFactionTerritoryBakesNoBorderRunsForANoColourBorder() {
            var territory = FactionTerritoryBuilder.buildFactionTerritory(
                    territoriesStyledBy(fillOnlyStyle()),
                    cellsFor(ISLAND_SYSTEM),
                    HEGEMONY,
                    List.of(ISLAND_SYSTEM));

            // A border switched off bakes no runs at all rather than runs the draw pass skips -
            // while the fill, which is still on, comes back as the frontier's own tessellation.
            assertThat(territory.borderLoops()).isEmpty();
            assertThat(territory.fillTriangles()).isNotEmpty();
        }

        @Test
        void buildFactionTerritoryBakesNothingWhenNeitherFillNorBorderDrawsAColour() {
            var territory = FactionTerritoryBuilder.buildFactionTerritory(
                    territoriesStyledBy(noColourStyle()),
                    cellsFor(ISLAND_SYSTEM),
                    HEGEMONY,
                    List.of(ISLAND_SYSTEM));

            // Short-circuited before the trace: a bloc that paints nothing must not pay for the
            // ring walk that only its paints would have used.
            assertThat(territory).isNull();
        }

        @Test
        void buildFactionTerritoryBakesNothingForMembersThatYieldNoBorderableGeometry() {
            var geometryCacheMock = mock(CellGeometryCache.class);
            when(geometryCacheMock.getCellEdgesByCellId()).thenReturn(Map.of());
            when(geometryCacheMock.getSystemIdByCellId()).thenReturn(Map.of(
                    HELD_SYSTEM, HELD_SYSTEM));

            var territory = FactionTerritoryBuilder.buildFactionTerritory(
                    territoriesStyledBy(drawnStyle()),
                    geometryCacheMock,
                    HEGEMONY,
                    List.of(HELD_SYSTEM));

            // A member whose cell carries no edges traces no ring, and a territory with no
            // frontier has nothing to clip its fill against, so the whole record is dropped.
            assertThat(territory).isNull();
        }
    }

    @Nested
    class BuildAllFactionTerritories {

        @Test
        void buildAllFactionTerritoriesKeysEachBlocsTerritoryByItsGroupingKey() {
            var territories = territoriesStyledBy(drawnStyle(), Map.of(
                    HELD_SYSTEM, HEGEMONY_OWNER,
                    NEIGHBOUR_SYSTEM, HEGEMONY_OWNER,
                    RIVAL_SYSTEM, TRITACHYON_OWNER));

            FactionTerritoryBuilder.buildAllFactionTerritories(
                    territories, cellsFor(HELD_SYSTEM, NEIGHBOUR_SYSTEM, RIVAL_SYSTEM));

            // One entry per bloc rather than per system: the two Hegemony systems fuse into the
            // single territory their shared key groups them into.
            assertThat(territories.getFactionTerritoryByFactionId())
                    .containsOnlyKeys(HEGEMONY, TRITACHYON);
            assertThat(territories.getFactionTerritoryByFactionId().get(HEGEMONY).borderLoops())
                    .hasSize(1);
        }

        @Test
        void buildAllFactionTerritoriesSkipsABlocThatBakesNothing() {
            var territories = territoriesStyledBy(drawnStyle(), Map.of(
                    HELD_SYSTEM, HEGEMONY_OWNER,
                    RIVAL_SYSTEM, TRITACHYON_OWNER));
            // The rival's cell is grouped but carries no edges, so its bloc bakes nothing.
            var geometryCacheMock = mock(CellGeometryCache.class);
            when(geometryCacheMock.getCellEdgesByCellId())
                    .thenReturn(Map.of(HELD_SYSTEM, EDGES.get(HELD_SYSTEM)));
            when(geometryCacheMock.getSystemIdByCellId()).thenReturn(Map.of(
                    HELD_SYSTEM, HELD_SYSTEM,
                    RIVAL_SYSTEM, RIVAL_SYSTEM));

            FactionTerritoryBuilder.buildAllFactionTerritories(territories, geometryCacheMock);

            // Absent rather than mapped to null: every reader of this map paints what it finds.
            assertThat(territories.getFactionTerritoryByFactionId()).containsOnlyKeys(HEGEMONY);
        }
    }

    // One cell edge facing the given neighbour system, or the reach bound when it is null.
    private static CellEdge edgeFacing(double x1, double y1, double x2, double y2,
            String neighbourSystemId) {
        return new CellEdge(x1, y1, x2, y2,
                neighbourSystemId == null
                        ? EdgeTarget.REACH_BOUND
                        : new EdgeTarget.AcrossSystem(neighbourSystemId));
    }

    // A geometry cache holding just the named cells, each drawing as its own star - the raw
    // partition a bloc's border is traced from.
    private static CellGeometryCache cellsFor(String... systemIds) {
        var geometryCacheMock = mock(CellGeometryCache.class);
        var edgesByCellId = new LinkedHashMap<String, List<CellEdge>>();
        var systemIdByCellId = new LinkedHashMap<String, String>();
        for (var systemId : systemIds) {
            edgesByCellId.put(systemId, EDGES.get(systemId));
            systemIdByCellId.put(systemId, systemId);
        }
        when(geometryCacheMock.getCellEdgesByCellId()).thenReturn(edgesByCellId);
        when(geometryCacheMock.getSystemIdByCellId()).thenReturn(systemIdByCellId);
        return geometryCacheMock;
    }

    // An unfiltered pass over the four fixture systems, every category drawing under the one
    // style a test names - so which category the resolver picks cannot account for an outcome.
    private static PoliticalMapTerritories territoriesStyledBy(CategoryStyle style) {
        return territoriesStyledBy(style, Map.of(
                HELD_SYSTEM, HEGEMONY_OWNER,
                NEIGHBOUR_SYSTEM, HEGEMONY_OWNER,
                ISLAND_SYSTEM, HEGEMONY_OWNER,
                EXCLAVE_SYSTEM, HEGEMONY_OWNER,
                RIVAL_SYSTEM, TRITACHYON_OWNER));
    }

    private static PoliticalMapTerritories territoriesStyledBy(
            CategoryStyle style,
            Map<String, DominantOwner> ownerBySystemId) {
        return new PoliticalMapTerritories(
                ownerBySystemId, Set.of(), Set.of(),
                new MapStyling(
                        renderStyleWithEveryCategory(style),
                        Color.GRAY,
                        new FactionPalette(Color.GREEN, Color.YELLOW)),
                new ViewGrouping(viewMockAdjustingNothing(), OwnershipGrouping.identity()),
                new FilterSnapshot(null, BlocStyleAdjustment.NONE, Set.of()));
    }

    // A view stub that styles every bloc as its own faction and recedes none of them, so the
    // paints below come from the category style and the owner's palette alone.
    private static PoliticalMapView viewMockAdjustingNothing() {
        var viewMock = mock(PoliticalMapView.class);
        when(viewMock.shouldUseIndependentStyle(any(), any(), any())).thenReturn(false);
        when(viewMock.resolveBlocStyleAdjustment(any(), any()))
                .thenReturn(BlocStyleAdjustment.NONE);
        return viewMock;
    }

    // The theme wrapper: one style for all four categories over an inert global tier. Smoothing
    // is off and the hatch is zeroed, so the rings a test counts are the raw traced ones.
    private static RenderStyle renderStyleWithEveryCategory(CategoryStyle style) {
        Map<MapCategory, CategoryStyle> categories = new EnumMap<>(MapCategory.class);
        for (var category : MapCategory.values()) {
            categories.put(category, style);
        }
        return new RenderStyle(
                new GlobalStyle(
                        new HatchStyle(0, 0, 0),
                        new BorderSmoothingStyle(false, false, 0, 0, 0, 0, 0),
                        NO_HOVER_HIGHLIGHT,
                        0.3),
                categories);
    }

    // Fill and national border both drawn, each from a different palette slot so the two paints
    // are told apart by colour; the interior seam is a per-cell record and never read here.
    private static CategoryStyle drawnStyle() {
        return new CategoryStyle(
                new ElementStyle(FactionPaletteChoice.PRIMARY, FILL_OPACITY),
                new ElementStyle(FactionPaletteChoice.SECONDARY, BORDER_OPACITY), BORDER_WIDTH,
                new ElementStyle(FactionPaletteChoice.NONE, 1.0), 1.0);
    }

    // The fill on and the border switched off - the one slot combination that still draws.
    private static CategoryStyle fillOnlyStyle() {
        return new CategoryStyle(
                new ElementStyle(FactionPaletteChoice.PRIMARY, FILL_OPACITY),
                new ElementStyle(FactionPaletteChoice.NONE, BORDER_OPACITY), BORDER_WIDTH,
                new ElementStyle(FactionPaletteChoice.NONE, 1.0), 1.0);
    }

    // Every slot "No color" - a bloc the player has switched off entirely.
    private static CategoryStyle noColourStyle() {
        return new CategoryStyle(
                new ElementStyle(FactionPaletteChoice.NONE, FILL_OPACITY),
                new ElementStyle(FactionPaletteChoice.NONE, BORDER_OPACITY), BORDER_WIDTH,
                new ElementStyle(FactionPaletteChoice.NONE, 1.0), 1.0);
    }
}
