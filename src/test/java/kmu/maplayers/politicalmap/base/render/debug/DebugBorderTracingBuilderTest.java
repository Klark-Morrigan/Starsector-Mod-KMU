package kmu.maplayers.politicalmap.base.render.debug;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.markets.DecivilisedMarkets;

import kmu.maplayers.base.geometry.CellEdge;
import kmu.maplayers.base.geometry.CellGeometryCache;
import kmu.maplayers.base.geometry.EdgeTarget;
import kmu.maplayers.base.theme.BorderSmoothingStyle;
import kmu.maplayers.base.theme.CategoryStyle;
import kmu.maplayers.base.theme.ElementStyle;
import kmu.maplayers.base.theme.GlobalStyle;
import kmu.maplayers.base.theme.HatchStyle;
import kmu.maplayers.base.theme.MapStyleCategory;
import kmu.maplayers.base.theme.RenderStyle;
import kmu.maplayers.base.theme.ThemeFixtures;
import kmu.maplayers.politicalmap.base.politics.DominantOwner;
import kmu.maplayers.politicalmap.base.politics.SectorPolitics;
import kmu.maplayers.politicalmap.base.render.style.PoliticalMapCategory;
import kmu.maplayers.politicalmap.base.render.style.RenderStyleReader;
import kmu.settings.FactionPaletteChoice;
import kmu.settings.KmuLunaSettings;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Pins what the debug overlay captures, which is the one claim the diagnostic rests on: a stage
 * list holds the geometry the normal render would have drawn at that point in the pipeline, and a
 * stage the player has switched off comes back empty rather than holding the unsmoothed geometry
 * under a smoothed stage's name. An overlay that quietly filled all three would read as "sanding
 * ran" whatever the gates say, which is exactly the question it exists to answer.
 *
 * <p>The two visibility rules are pinned beside it: an owned cell is traced once, as part of its
 * cluster rather than again as a lone outline, and a factionless cell draws only where its
 * category's outline is switched on - without which the overlay floods with every uninhabited
 * system in the sector and shows nothing.
 *
 * <p>The geometry itself is pinned elsewhere - the ring trace by
 * {@link kmu.maplayers.base.render.regions.ClusterBorderTraceIntegrationTest}, the smoothing
 * passes by their own suites - so it runs for real here and is only ever counted, never measured.
 * Cells are hand-built 2000-unit squares, comfortably clear of the fixed border channel, so which
 * cells touch is plain to read. The sector reads (ownership, decivilisation) and the two settings
 * reads are stubbed: the builder resolves them itself by design, and none of them answers outside
 * the game.
 */
final class DebugBorderTracingBuilderTest {
    private static final String HEGEMONY = "hegemony";
    // Two cells of one bloc meeting along x = 2000, so the pair proves the trace fuses them; the
    // other two share no edge with anything, each enclosed by the reach bound alone.
    private static final String HELD_SYSTEM = "held";
    private static final String NEIGHBOUR_SYSTEM = "neighbour";
    private static final String DEAD_SYSTEM = "dead";
    private static final String EMPTY_SYSTEM = "empty";
    // A 100-unit cell, well under twice the 150-unit border channel, so insetting it leaves
    // nothing to outline.
    private static final String TINY_SYSTEM = "tiny";
    private static final DominantOwner HEGEMONY_OWNER =
            new DominantOwner(HEGEMONY, Color.RED, Color.BLUE);
    // The live Dev-tab trace parameters, stubbed at their read: a weld tolerance loose enough to
    // chain the hand-built corners, and the miter limit the shipped border uses.
    private static final double WELD_TOLERANCE = 1e-3;
    private static final double MITER_SPIKE_LIMIT = 4.0;
    // A smoothing profile whose shape is well inside a 2000-unit square, so whether a pass ran is
    // never confused with a pass that ran and degenerated the loop away.
    private static final double SPIKE_HEIGHT = 300.0;
    private static final double SPIKE_ANGLE_RADIANS = 0.5;
    private static final double CORNER_RADIUS = 100.0;
    private static final int CORNER_SEGMENTS = 4;
    private static final double CHAMFER_ANGLE_RADIANS = 0.5;
    private static final ElementStyle DRAWN_OUTLINE =
            new ElementStyle(FactionPaletteChoice.PRIMARY.resolveElementPaint(), 1.0);
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
            DEAD_SYSTEM, List.of(
                    edgeFacing(10000, 0, 12000, 0, null),
                    edgeFacing(12000, 0, 12000, 2000, null),
                    edgeFacing(12000, 2000, 10000, 2000, null),
                    edgeFacing(10000, 2000, 10000, 0, null)),
            EMPTY_SYSTEM, List.of(
                    edgeFacing(20000, 0, 22000, 0, null),
                    edgeFacing(22000, 0, 22000, 2000, null),
                    edgeFacing(22000, 2000, 20000, 2000, null),
                    edgeFacing(20000, 2000, 20000, 0, null)),
            TINY_SYSTEM, List.of(
                    edgeFacing(30000, 0, 30100, 0, null),
                    edgeFacing(30100, 0, 30100, 100, null),
                    edgeFacing(30100, 100, 30000, 100, null),
                    edgeFacing(30000, 100, 30000, 0, null)));

    // The sector is never read: every question the builder asks of it is stubbed at the resolver
    // that would have walked it, so this stands only for the argument those stubs match on.
    private final SectorAPI sectorMock = mock(SectorAPI.class);
    private MockedStatic<KmuLunaSettings> settingsMock;
    private MockedStatic<SectorPolitics> politicsMock;
    private MockedStatic<DecivilisedMarkets> decivilisedMarketsMock;
    private MockedStatic<RenderStyleReader> styleReaderMock;

    @BeforeEach
    void openTheSectorAndSettingsSeams() {
        settingsMock = mockStatic(KmuLunaSettings.class);
        settingsMock.when(KmuLunaSettings::getPoliticalMapBorderWeldTolerance)
                .thenReturn(WELD_TOLERANCE);
        settingsMock.when(KmuLunaSettings::getPoliticalMapBorderMiterLimit)
                .thenReturn(MITER_SPIKE_LIMIT);
        politicsMock = mockStatic(SectorPolitics.class);
        decivilisedMarketsMock = mockStatic(DecivilisedMarkets.class);
        styleReaderMock = mockStatic(RenderStyleReader.class);
        // An empty sector by default, so a case names only the ground it is about.
        stubOwners(Map.of());
        stubDecivilisedSystems();
        stubTheme(noSmoothing(), ElementStyle.NOT_DRAWN, ElementStyle.NOT_DRAWN);
    }

    @AfterEach
    void closeTheSectorAndSettingsSeams() {
        styleReaderMock.close();
        decivilisedMarketsMock.close();
        politicsMock.close();
        settingsMock.close();
    }

    @Nested
    class BuildDebugDrawables {

        @Test
        void buildDebugDrawablesCapturesOneFusedBaseLoopWithBothSmoothingGatesOff() {
            stubOwners(Map.of(
                    HELD_SYSTEM, HEGEMONY_OWNER,
                    NEIGHBOUR_SYSTEM, HEGEMONY_OWNER));

            var drawables = DebugBorderTracingBuilder.buildDebugDrawables(
                    cellsFor(HELD_SYSTEM, NEIGHBOUR_SYSTEM), sectorMock);

            // One loop rather than two: the shared edge is a same-bloc seam, exactly as in the
            // production trace this stage is supposed to be showing.
            assertThat(drawables.baseLoops()).hasSize(1);
            // Both gates off, so the two smoothed stages are empty rather than echoing the base -
            // an echo would read as "the passes ran and changed nothing".
            assertThat(drawables.despikedLoops()).isEmpty();
            assertThat(drawables.roundedLoops()).isEmpty();
        }

        @Test
        void buildDebugDrawablesCapturesTheDespikedStageOnlyWhenSandingIsGatedOn() {
            stubOwners(Map.of(HELD_SYSTEM, HEGEMONY_OWNER));
            stubTheme(sandingOnly(), ElementStyle.NOT_DRAWN, ElementStyle.NOT_DRAWN);

            var drawables = DebugBorderTracingBuilder.buildDebugDrawables(
                    cellsFor(HELD_SYSTEM), sectorMock);

            assertThat(drawables.baseLoops()).hasSize(1);
            assertThat(drawables.despikedLoops()).hasSize(1);
            assertThat(drawables.roundedLoops()).isEmpty();
        }

        @Test
        void buildDebugDrawablesRoundsTheBaseDirectlyWhenSandingIsGatedOff() {
            stubOwners(Map.of(HELD_SYSTEM, HEGEMONY_OWNER));
            stubTheme(roundingOnly(), ElementStyle.NOT_DRAWN, ElementStyle.NOT_DRAWN);

            var drawables = DebugBorderTracingBuilder.buildDebugDrawables(
                    cellsFor(HELD_SYSTEM), sectorMock);

            // Rounding feeds off whatever the previous stage left, so with sanding off it rounds
            // the base - and the skipped stage stays empty rather than standing in for it.
            assertThat(drawables.roundedLoops()).hasSize(1);
            assertThat(drawables.despikedLoops()).isEmpty();
        }

        @Test
        void buildDebugDrawablesOutlinesOnlyTheFactionlessCategoryWhoseOutlineIsSwitchedOn() {
            stubDecivilisedSystems(DEAD_SYSTEM);
            stubTheme(noSmoothing(), DRAWN_OUTLINE, ElementStyle.NOT_DRAWN);

            var drawables = DebugBorderTracingBuilder.buildDebugDrawables(
                    cellsFor(DEAD_SYSTEM, EMPTY_SYSTEM), sectorMock);

            // The dead world's cell resolves to the decivilised bundle and draws; the uninhabited
            // one resolves to the bundle the player switched off and is skipped, which is what
            // keeps the overlay off every empty corner of the sector.
            assertThat(drawables.baseLoops()).hasSize(1);
        }

        @Test
        void buildDebugDrawablesOutlinesNoFactionlessCellWhenNeitherCategoryDraws() {
            stubDecivilisedSystems(DEAD_SYSTEM);

            var drawables = DebugBorderTracingBuilder.buildDebugDrawables(
                    cellsFor(DEAD_SYSTEM, EMPTY_SYSTEM), sectorMock);

            assertThat(drawables.isEmpty()).isTrue();
        }

        @Test
        void buildDebugDrawablesLeavesAnOwnedCellToTheClusterPassRatherThanOutliningItTwice() {
            stubOwners(Map.of(HELD_SYSTEM, HEGEMONY_OWNER));
            stubTheme(noSmoothing(), DRAWN_OUTLINE, DRAWN_OUTLINE);

            var drawables = DebugBorderTracingBuilder.buildDebugDrawables(
                    cellsFor(HELD_SYSTEM), sectorMock);

            // One loop with both factionless outlines on: the cell is grouped, so the factionless
            // pass steps over it instead of stroking a second ring inside its cluster border.
            assertThat(drawables.baseLoops()).hasSize(1);
        }

        @Test
        void buildDebugDrawablesCapturesNothingForAClusterThatTracesNoRing() {
            stubOwners(Map.of(HELD_SYSTEM, HEGEMONY_OWNER));
            var geometryCacheMock = mock(CellGeometryCache.class);
            when(geometryCacheMock.getCellEdgesByCellId())
                    .thenReturn(Map.of(HELD_SYSTEM, List.of()));
            when(geometryCacheMock.getSystemIdByCellId())
                    .thenReturn(Map.of(HELD_SYSTEM, HELD_SYSTEM));

            var drawables = DebugBorderTracingBuilder.buildDebugDrawables(
                    geometryCacheMock, sectorMock);

            // A bloc whose cells carry no edges traces nothing, and the overlay drops it rather
            // than capturing an empty stage entry the renderer would walk.
            assertThat(drawables.isEmpty()).isTrue();
        }

        @Test
        void buildDebugDrawablesOutlinesNoFactionlessCellThatTheBorderChannelSwallows() {
            stubDecivilisedSystems(TINY_SYSTEM);
            stubTheme(noSmoothing(), DRAWN_OUTLINE, DRAWN_OUTLINE);

            var drawables = DebugBorderTracingBuilder.buildDebugDrawables(
                    cellsFor(TINY_SYSTEM), sectorMock);

            // The cell is narrower than twice the border inset, so insetting leaves no polygon
            // at all - dropped rather than flattened into a degenerate run.
            assertThat(drawables.isEmpty()).isTrue();
        }

        @Test
        void buildDebugDrawablesGivesAFactionlessOutlineNoDespikedStageEvenWithSandingOn() {
            stubDecivilisedSystems(DEAD_SYSTEM);
            stubTheme(bothGatesOn(), DRAWN_OUTLINE, ElementStyle.NOT_DRAWN);

            var drawables = DebugBorderTracingBuilder.buildDebugDrawables(
                    cellsFor(DEAD_SYSTEM), sectorMock);

            // A lone convex cell has no needle protrusions to sand, so its two stages are the raw
            // inset and its rounded corners - the sanding gate being on does not invent a third.
            assertThat(drawables.baseLoops()).hasSize(1);
            assertThat(drawables.roundedLoops()).hasSize(1);
            assertThat(drawables.despikedLoops()).isEmpty();
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
    // partition both the cluster trace and the factionless outline pass walk.
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

    // Both gates off, so a captured smoothed stage can only have come from a gate being honoured
    // wrongly rather than from the profile's shape.
    private static BorderSmoothingStyle noSmoothing() {
        return smoothing(false, false);
    }

    private static BorderSmoothingStyle sandingOnly() {
        return smoothing(true, false);
    }

    private static BorderSmoothingStyle roundingOnly() {
        return smoothing(false, true);
    }

    private static BorderSmoothingStyle bothGatesOn() {
        return smoothing(true, true);
    }

    private static BorderSmoothingStyle smoothing(
            boolean shouldSandSpikes,
            boolean shouldRoundCorners) {
        return new BorderSmoothingStyle(shouldSandSpikes, shouldRoundCorners, SPIKE_HEIGHT,
                SPIKE_ANGLE_RADIANS, CORNER_RADIUS, CORNER_SEGMENTS, CHAMFER_ANGLE_RADIANS);
    }

    // The theme as the builder reads it: the smoothing profile it stages the passes by, and the
    // two factionless bundles whose outlines decide which unowned cells are drawn at all. The
    // owned bundles never gate anything here, so they take the drawn outline throughout.
    private void stubTheme(
            BorderSmoothingStyle smoothing,
            ElementStyle decivilisedOutline,
            ElementStyle uninhabitedOutline) {

        Map<MapStyleCategory, CategoryStyle> categories = new LinkedHashMap<>();
        categories.put(PoliticalMapCategory.FACTION, outlinedBy(DRAWN_OUTLINE));
        categories.put(PoliticalMapCategory.INDEPENDENT, outlinedBy(DRAWN_OUTLINE));
        categories.put(PoliticalMapCategory.DECIVILISED, outlinedBy(decivilisedOutline));
        categories.put(PoliticalMapCategory.UNINHABITED, outlinedBy(uninhabitedOutline));
        // The smoothing profile is the one sector-wide value these cases vary, so the rest of the
        // tier is the shared inert one.
        var renderStyle = new RenderStyle(
                new GlobalStyle(
                        new HatchStyle(0, 0, 0),
                        smoothing,
                        ThemeFixtures.NO_HOVER_HIGHLIGHT,
                        0.3),
                categories);
        styleReaderMock.when(RenderStyleReader::readBorderSmoothingStyle).thenReturn(smoothing);
        styleReaderMock.when(RenderStyleReader::readRenderStyle).thenReturn(renderStyle);
    }

    private void stubOwners(Map<String, DominantOwner> ownerBySystemId) {
        politicsMock.when(() -> SectorPolitics.resolveDominantOwnerBySystemId(sectorMock))
                .thenReturn(ownerBySystemId);
    }

    private void stubDecivilisedSystems(String... systemIds) {
        decivilisedMarketsMock
                .when(() -> DecivilisedMarkets.findRevealedDecivilisedSystemIds(sectorMock))
                .thenReturn(Set.of(systemIds));
    }

    // A bundle the overlay reads only for its outer element: fill and seam never reach the
    // border tracing, so they are inert.
    private static CategoryStyle outlinedBy(ElementStyle outer) {
        return new CategoryStyle(ElementStyle.NOT_DRAWN, outer, 1.0, ElementStyle.NOT_DRAWN, 1.0);
    }
}
