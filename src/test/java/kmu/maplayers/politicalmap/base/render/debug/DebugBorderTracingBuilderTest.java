package kmu.maplayers.politicalmap.base.render.debug;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.math.geometry.CornerRounding;

import kmu.maplayers.base.geometry.CellEdge;
import kmu.maplayers.base.geometry.CellGeometryCache;
import kmu.maplayers.base.geometry.EdgeTarget;
import kmu.maplayers.base.theme.BorderSmoothingStyle;
import kmu.maplayers.base.theme.CategoryStyle;
import kmu.maplayers.base.theme.CornerRoundingStyle;
import kmu.maplayers.base.theme.ElementStyle;
import kmu.maplayers.base.theme.GlobalStyle;
import kmu.maplayers.base.theme.MapStyleCategory;
import kmu.maplayers.base.theme.RenderStyle;
import kmu.maplayers.base.theme.SpikeSandingStyle;
import kmu.maplayers.base.theme.ThemeFixtures;
import kmu.maplayers.base.visibility.systems.MapVisibilityRules;
import kmu.maplayers.politicalmap.base.PoliticalMapInhabitation;
import kmu.maplayers.politicalmap.base.dominance.HolderPass;
import kmu.maplayers.politicalmap.base.politics.DominantHolder;
import kmu.maplayers.politicalmap.base.politics.SectorPolitics;
import kmu.maplayers.politicalmap.base.render.style.FactionPaletteSlot;
import kmu.maplayers.politicalmap.base.render.style.PoliticalMapCategory;
import kmu.maplayers.politicalmap.base.render.style.RenderStyleReader;
import kmu.settings.KmuMapLayerSettings;

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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
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
 * {@link kmu.maplayers.base.render.clusters.ClusterBorderTraceIntegrationTest}, the smoothing
 * passes by their own suites - so it runs for real here and is only ever counted, never measured.
 * Cells are hand-built 2000-unit squares, comfortably clear of the fixed border channel, so which
 * cells touch is plain to read. The sector reads (holding, decivilisation) and the two settings
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
    private static final DominantHolder HEGEMONY_OWNER =
        new DominantHolder(HEGEMONY, Color.RED, Color.BLUE);

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
        new ElementStyle(
            FactionPaletteSlot.PRIMARY,
            1.0);

    private static final Map<String, List<CellEdge>> EDGES = Map.of(
        HELD_SYSTEM, List.of(
            buildEdgeFacing(0, 0, 2000, 0, null),
            buildEdgeFacing(2000, 0, 2000, 2000, NEIGHBOUR_SYSTEM),
            buildEdgeFacing(2000, 2000, 0, 2000, null),
            buildEdgeFacing(0, 2000, 0, 0, null)),
        NEIGHBOUR_SYSTEM, List.of(
            buildEdgeFacing(2000, 0, 4000, 0, null),
            buildEdgeFacing(4000, 0, 4000, 2000, null),
            buildEdgeFacing(4000, 2000, 2000, 2000, null),
            buildEdgeFacing(2000, 2000, 2000, 0, HELD_SYSTEM)),
        DEAD_SYSTEM, List.of(
            buildEdgeFacing(10000, 0, 12000, 0, null),
            buildEdgeFacing(12000, 0, 12000, 2000, null),
            buildEdgeFacing(12000, 2000, 10000, 2000, null),
            buildEdgeFacing(10000, 2000, 10000, 0, null)),
        EMPTY_SYSTEM, List.of(
            buildEdgeFacing(20000, 0, 22000, 0, null),
            buildEdgeFacing(22000, 0, 22000, 2000, null),
            buildEdgeFacing(22000, 2000, 20000, 2000, null),
            buildEdgeFacing(20000, 2000, 20000, 0, null)),
        TINY_SYSTEM, List.of(
            buildEdgeFacing(30000, 0, 30100, 0, null),
            buildEdgeFacing(30100, 0, 30100, 100, null),
            buildEdgeFacing(30100, 100, 30000, 100, null),
            buildEdgeFacing(30000, 100, 30000, 0, null)));

    // The sector is never read: every question the builder asks of it is stubbed at the resolver
    // that would have walked it, so this stands only for the argument those stubs match on.
    private final SectorAPI sectorMock = mock(SectorAPI.class);
    private MockedStatic<KmuMapLayerSettings> settingsMock;
    private MockedStatic<SectorPolitics> politicsMock;
    private MockedStatic<PoliticalMapInhabitation> inhabitationMock;
    private MockedStatic<RenderStyleReader> styleReaderMock;
    private MockedStatic<MapVisibilityRules> visibilityRulesMock;

    @BeforeEach
    void openTheSectorAndSettingsSeams() {
        settingsMock = mockStatic(KmuMapLayerSettings.class);

        settingsMock
            .when(KmuMapLayerSettings::getMapBorderWeldTolerance)
            .thenReturn(WELD_TOLERANCE);
        settingsMock
            .when(KmuMapLayerSettings::getMapBorderMiterLimit)
            .thenReturn(MITER_SPIKE_LIMIT);

        politicsMock = mockStatic(SectorPolitics.class);
        visibilityRulesMock = mockStatic(MapVisibilityRules.class);
        inhabitationMock = mockStatic(PoliticalMapInhabitation.class);

        // This overlay opens its own pass, which samples the reveal toggles; no LunaLib answers
        // outside the game, so the no-reveal view stands in for the read.
        visibilityRulesMock
            .when(MapVisibilityRules::readFromLunaSettings)
            .thenReturn(MapVisibilityRules.BASE);
        styleReaderMock = mockStatic(RenderStyleReader.class);

        // An empty sector by default, so a case names only the cells it is about.
        stubHolders(Map.of());
        stubInhabitedSystems();
        stubTheme(buildNoSmoothing(), ElementStyle.NOT_DRAWN, ElementStyle.NOT_DRAWN);
    }

    @AfterEach
    void closeTheSectorAndSettingsSeams() {
        styleReaderMock.close();
        inhabitationMock.close();
        visibilityRulesMock.close();
        politicsMock.close();
        settingsMock.close();
    }

    @Nested
    class BuildDebugDrawables {

        @Test
        void buildDebugDrawablesCapturesOneFusedBaseLoopWithBothSmoothingGatesOff() {
            stubHolders(Map.of(
                HELD_SYSTEM,
                HEGEMONY_OWNER,
                NEIGHBOUR_SYSTEM,
                HEGEMONY_OWNER));

            var drawables = DebugBorderTracingBuilder.buildDebugDrawables(
                listCellsFor(HELD_SYSTEM, NEIGHBOUR_SYSTEM),
                sectorMock);

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
            stubHolders(Map.of(HELD_SYSTEM, HEGEMONY_OWNER));
            stubTheme(buildSandingOnly(), ElementStyle.NOT_DRAWN, ElementStyle.NOT_DRAWN);

            var drawables = DebugBorderTracingBuilder.buildDebugDrawables(
                listCellsFor(HELD_SYSTEM),
                sectorMock);

            assertThat(drawables.baseLoops()).hasSize(1);
            assertThat(drawables.despikedLoops()).hasSize(1);
            assertThat(drawables.roundedLoops()).isEmpty();
        }

        @Test
        void buildDebugDrawablesRoundsTheBaseDirectlyWhenSandingIsGatedOff() {
            stubHolders(Map.of(HELD_SYSTEM, HEGEMONY_OWNER));
            stubTheme(buildRoundingOnly(), ElementStyle.NOT_DRAWN, ElementStyle.NOT_DRAWN);

            var drawables = DebugBorderTracingBuilder.buildDebugDrawables(
                listCellsFor(HELD_SYSTEM), sectorMock);

            // Rounding feeds off whatever the previous stage left, so with sanding off it rounds
            // the base - and the skipped stage stays empty rather than standing in for it.
            assertThat(drawables.roundedLoops()).hasSize(1);
            assertThat(drawables.despikedLoops()).isEmpty();
        }

        @Test
        void buildDebugDrawablesOutlinesOnlyTheFactionlessCategoryWhoseOutlineIsSwitchedOn() {
            stubInhabitedSystems(DEAD_SYSTEM);
            stubTheme(buildNoSmoothing(), DRAWN_OUTLINE, ElementStyle.NOT_DRAWN);

            var drawables = DebugBorderTracingBuilder.buildDebugDrawables(
                listCellsFor(DEAD_SYSTEM, EMPTY_SYSTEM), sectorMock);

            // The dead world's cell resolves to the decivilised bundle and draws; the uninhabited
            // one resolves to the bundle the player switched off and is skipped, which is what
            // keeps the overlay off every empty corner of the sector.
            assertThat(drawables.baseLoops()).hasSize(1);
        }

        @Test
        void buildDebugDrawablesOutlinesNoFactionlessCellWhenNeitherCategoryDraws() {
            stubInhabitedSystems(DEAD_SYSTEM);

            var drawables = DebugBorderTracingBuilder.buildDebugDrawables(
                listCellsFor(DEAD_SYSTEM, EMPTY_SYSTEM), sectorMock);

            assertThat(drawables.isEmpty()).isTrue();
        }

        @Test
        void buildDebugDrawablesLeavesAnOwnedCellToTheClusterPassRatherThanOutliningItTwice() {
            stubHolders(Map.of(HELD_SYSTEM, HEGEMONY_OWNER));
            stubTheme(buildNoSmoothing(), DRAWN_OUTLINE, DRAWN_OUTLINE);

            var drawables = DebugBorderTracingBuilder.buildDebugDrawables(
                listCellsFor(HELD_SYSTEM), sectorMock);

            // One loop with both factionless outlines on: the cell is grouped, so the factionless
            // pass steps over it instead of stroking a second ring inside its cluster border.
            assertThat(drawables.baseLoops()).hasSize(1);
        }

        @Test
        void buildDebugDrawablesCapturesNothingForAClusterThatTracesNoRing() {
            stubHolders(Map.of(HELD_SYSTEM, HEGEMONY_OWNER));
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
            stubInhabitedSystems(TINY_SYSTEM);
            stubTheme(buildNoSmoothing(), DRAWN_OUTLINE, DRAWN_OUTLINE);

            var drawables = DebugBorderTracingBuilder.buildDebugDrawables(
                listCellsFor(TINY_SYSTEM), sectorMock);

            // The cell is narrower than twice the border inset, so insetting leaves no polygon
            // at all - dropped rather than flattened into a degenerate run.
            assertThat(drawables.isEmpty()).isTrue();
        }

        @Test
        void buildDebugDrawablesGivesAFactionlessOutlineNoDespikedStageEvenWithSandingOn() {
            stubInhabitedSystems(DEAD_SYSTEM);
            stubTheme(buildBothGatesOn(), DRAWN_OUTLINE, ElementStyle.NOT_DRAWN);

            var drawables = DebugBorderTracingBuilder.buildDebugDrawables(
                listCellsFor(DEAD_SYSTEM), sectorMock);

            // A lone convex cell has no needle protrusions to sand, so its two stages are the raw
            // inset and its rounded corners - the sanding gate being on does not invent a third.
            assertThat(drawables.baseLoops()).hasSize(1);
            assertThat(drawables.roundedLoops()).hasSize(1);
            assertThat(drawables.despikedLoops()).isEmpty();
        }
    }

    // One cell edge facing the given neighbour system, or the reach bound when it is null.
    private static CellEdge buildEdgeFacing(
            double x1,
            double y1,
            double x2,
            double y2,
            String neighbourSystemId) {
        return new CellEdge(
            x1,
            y1,
            x2,
            y2,
            neighbourSystemId == null
                ? EdgeTarget.REACH_BOUND
                : new EdgeTarget.AcrossSystem(neighbourSystemId));
    }

    // A geometry cache holding just the named cells, each drawing as its own star - the raw
    // partition both the cluster trace and the factionless outline pass walk.
    private static CellGeometryCache listCellsFor(String... systemIds) {
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
    private static BorderSmoothingStyle buildNoSmoothing() {
        return buildSmoothing(false, false);
    }

    private static BorderSmoothingStyle buildSandingOnly() {
        return buildSmoothing(true, false);
    }

    private static BorderSmoothingStyle buildRoundingOnly() {
        return buildSmoothing(false, true);
    }

    private static BorderSmoothingStyle buildBothGatesOn() {
        return buildSmoothing(true, true);
    }

    private static BorderSmoothingStyle buildSmoothing(
            boolean shouldSandSpikes,
            boolean shouldRoundCorners) {
        return new BorderSmoothingStyle(
            new SpikeSandingStyle(shouldSandSpikes, SPIKE_HEIGHT, SPIKE_ANGLE_RADIANS),
            new CornerRoundingStyle(
                shouldRoundCorners,
                CORNER_RADIUS,
                CORNER_SEGMENTS,
                CHAMFER_ANGLE_RADIANS,
                CornerRounding.ROUND_EVERY_CORNER));
    }

    // The theme as the builder reads it: the smoothing profile it stages the passes by, and the
    // two factionless bundles whose outlines decide which unowned cells are drawn at all. The
    // owned bundles never gate anything here, so they take the drawn outline throughout.
    private void stubTheme(
            BorderSmoothingStyle smoothing,
            ElementStyle decivilisedOutline,
            ElementStyle uninhabitedOutline) {

        Map<MapStyleCategory, CategoryStyle> categories = new LinkedHashMap<>();
        categories.put(PoliticalMapCategory.FACTION, buildOutlinedBy(DRAWN_OUTLINE));
        categories.put(PoliticalMapCategory.INDEPENDENT, buildOutlinedBy(DRAWN_OUTLINE));
        categories.put(PoliticalMapCategory.DECIVILISED, buildOutlinedBy(decivilisedOutline));
        categories.put(PoliticalMapCategory.UNINHABITED, buildOutlinedBy(uninhabitedOutline));

        // The smoothing profile is the one sector-wide value these cases vary, so the rest of the
        // tier is the shared inert one.
        var renderStyle = new RenderStyle(
            new GlobalStyle(
                ThemeFixtures.createHatchStyle(0, 0, 0),
                smoothing,
                ThemeFixtures.NO_HIGHLIGHT,
                ThemeFixtures.NO_HIGHLIGHT,
                0.3,
                0.2),
            categories);

        styleReaderMock.when(RenderStyleReader::readBorderSmoothingStyle).thenReturn(smoothing);
        styleReaderMock.when(RenderStyleReader::readRenderStyle).thenReturn(renderStyle);
    }

    private void stubHolders(Map<String, DominantHolder> ownerBySystemId) {
        politicsMock
            .when(() -> SectorPolitics.resolveDominantHolderBySystemId(any(HolderPass.class)))
            .thenReturn(ownerBySystemId);
    }

    // The systems the overlay is to treat as settled. Stubbed at the political layer's
    // inhabitation seam rather than at the market scan behind it, since that seam is where the
    // overlay's own pass is read - and opening one reaches LunaLib for the colony rule, which the
    // test JVM cannot load.
    //
    // Matched on the sector the pass was opened over rather than on the pass itself, the overlay
    // opening its own so no case here holds the instance. That is the half worth pinning: the
    // overlay resolves its holding and its inhabitation from one pass, and one opened over a
    // second sector would classify cells against a sector the trace above never read. A stub
    // matching any pass at all would go on answering for it.
    private void stubInhabitedSystems(String... systemIds) {
        inhabitationMock
            .when(() -> PoliticalMapInhabitation.readInhabitedSystemIds(
                argThat(pass -> pass != null && pass.sector() == sectorMock)))
            .thenReturn(Set.of(systemIds));
    }

    // A bundle the overlay reads only for its outer element: fill and seam never reach the
    // border tracing, so they are inert.
    private static CategoryStyle buildOutlinedBy(ElementStyle outer) {
        return new CategoryStyle(
            ElementStyle.NOT_DRAWN,
            outer,
            1.0,
            ElementStyle.NOT_DRAWN,
            1.0);
    }
}
