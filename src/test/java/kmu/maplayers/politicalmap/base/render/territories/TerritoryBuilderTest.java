package kmu.maplayers.politicalmap.base.render.territories;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.factions.StarsectorFactionColours;
import kmlib.starsector.systems.SystemKey;
import kmlib.testfixtures.profiling.RecordedCapture;

import kmu.maplayers.base.geometry.CellGeometryCache;
import kmu.maplayers.base.profiling.MapBuildCounters;
import kmu.maplayers.politicalmap.base.PoliticalMapInhabitation;
import kmu.maplayers.politicalmap.base.PoliticalMapViewFake;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.dominance.HolderPass;
import kmu.maplayers.politicalmap.base.politics.DominantHolder;
import kmu.maplayers.politicalmap.base.politics.FilteredPolitics;
import kmu.maplayers.politicalmap.base.politics.holders.HolderResolution;
import kmu.maplayers.politicalmap.base.render.ContentInputs;
import kmu.maplayers.politicalmap.base.render.ContentInputsFixtures;
import kmu.maplayers.politicalmap.base.render.style.MapPalettes;
import kmu.maplayers.politicalmap.base.render.style.RenderStyleReader;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;
import static kmu.maplayers.politicalmap.base.dominance.ColonyReadRulesFixtures.UNDER_THE_FOG;
import static kmu.maplayers.politicalmap.base.render.territories.PoliticalMapTerritoryFixtures.createInertCategoryStyle;
import static kmu.maplayers.politicalmap.base.render.territories.PoliticalMapTerritoryFixtures.createRenderStyleForEveryCategory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins what a build hands down: the one reading of the sector it was given, at every reader
 * beneath it.
 *
 * <p>The arrangement the whole pass exists for, and the one thing no reader below can state for
 * itself. Each of them takes a pass and shares its walk of a system with whatever else reads that
 * system through it - but only this build decides which pass they are handed, and one opened here
 * would have the sector walked again while every reader below went on looking correct.
 *
 * <p>Everything the build reads apart from that is stood in for: the theme, the palettes and the
 * inhabitation scan all read live sources no test JVM answers, and the sidebar picks arrive as a
 * stated reading rather than being read at all. None of them is what a case here is about. The
 * geometry is empty, so the shaping and tracing stages run over nothing and the build reduces to
 * the reads this suite names.
 */
final class TerritoryBuilderTest {

    private static final Color NEUTRAL = new Color(150, 150, 150);

    // The rows a resolve's three scans and their parent land on, and the build's shaping stage.
    private static final String RESOLVE_HOLDING_SECTION = "politicalMap.resolveHolding";
    private static final String RESOLVE_POLITICS_SECTION = "politicalMap.resolvePolitics";
    private static final String FIND_INHABITED_SECTION = "politicalMap.findInhabited";
    private static final String FIND_SPOTLIT_PRESENCE_SECTION = "politicalMap.findSpotlitPresence";
    private static final String SHAPE_AND_STYLE_SECTION = "politicalMap.shapeAndStyleCells";

    // One held system, for the cases about a holding handed in rather than read.
    private static final SystemKey HELD_SYSTEM = buildCellKey("corvus");
    private static final DominantHolder HELD_BY = new DominantHolder("hegemony", NEUTRAL, NEUTRAL);

    // The picks a pass off filter was baked under. No case here spotlights a bloc, so the whole
    // reading is inert and the build reduces to the passes it hands down.
    private static final ContentInputs UNFILTERED_INPUTS =
        ContentInputsFixtures.createInertInputs();

    private MockedStatic<PoliticalMapInhabitation> inhabitationMock;
    private MockedStatic<FilteredPolitics> filteredPoliticsMock;
    private MockedStatic<RenderStyleReader> styleReaderMock;
    private MockedStatic<StarsectorFactionColours> factionColoursMock;
    private MockedStatic<MapPalettes> palettesMock;

    // The passes the presence scan was asked for, in the order the build asked.
    private final List<HolderPass> presenceScanPasses = new ArrayList<>();

    // The same for the inhabitation scan, which walks every system for its colonies as the other
    // two readers do.
    private final List<HolderPass> inhabitationScanPasses = new ArrayList<>();

    @BeforeEach
    void openTheLiveSeams() {

        inhabitationMock = mockStatic(PoliticalMapInhabitation.class);
        filteredPoliticsMock = mockStatic(FilteredPolitics.class);
        styleReaderMock = mockStatic(RenderStyleReader.class);
        factionColoursMock = mockStatic(StarsectorFactionColours.class);
        palettesMock = mockStatic(MapPalettes.class);

        // The inhabitation scan's pass is kept on the same terms the presence scan's is: it walks
        // every system for its colonies, so which pass reached it decides whether the rebuild
        // read the sector once or twice.
        inhabitationMock
            .when(() -> PoliticalMapInhabitation.readInhabitedSystemKeys(any(HolderPass.class)))
            .thenAnswer(invocation -> {
                inhabitationScanPasses.add(invocation.getArgument(0));
                return Set.of(buildCellKey("inhabited-system"));
            });
        styleReaderMock
            .when(() -> RenderStyleReader.readRenderStyle(anyBoolean()))
            .thenReturn(createRenderStyleForEveryCategory(createInertCategoryStyle()));
        factionColoursMock
            .when(() -> StarsectorFactionColours.resolveNeutralColour(any()))
            .thenReturn(NEUTRAL);

        // Each pass the presence scan is handed, kept rather than answered about: the case is
        // about which pass reached it, not what it reported.
        filteredPoliticsMock
            .when(() -> FilteredPolitics.findPresentSystemKeys(any(HolderPass.class), any(), any()))
            .thenAnswer(invocation -> {
                presenceScanPasses.add(invocation.getArgument(0));
                return Set.of();
            });
    }

    @AfterEach
    void closeTheLiveSeams() {
        palettesMock.close();
        factionColoursMock.close();
        styleReaderMock.close();
        filteredPoliticsMock.close();
        inhabitationMock.close();
    }

    @Nested
    class ResolveHolding {

        @Test
        void resolveHoldingReadsTheSectorThroughTheHandedPassForEveryReaderBeneathIt() {
            // A resolve reads who holds what and then asks where the spotlit bloc lives outside it.
            // Both walk every system, so a pass apiece is a second traversal of the sector for
            // colonies the first one has already read - and, less visibly, a second reading of a
            // sector that is free to have moved between them.
            var holderPasses = new ArrayList<HolderPass>();
            var viewFake = new PoliticalMapViewFake(
                Map.of(),
                (pass, selectedBlocId) -> {
                    holderPasses.add(pass);
                    return new HolderResolution(Map.of(), Set.of(), Set.of());
                });
            var rebuildPass = buildPassOverAnEmptySector();

            TerritoryBuilder.resolveHolding(rebuildPass, viewFake, UNFILTERED_INPUTS);

            // The handed pass at every reader - stated as identity rather than as equality, since
            // two passes over one sector carry two separate walks of it while agreeing about
            // everything they were built from.
            assertThat(holderPasses)
                .containsExactly(rebuildPass);
            assertThat(presenceScanPasses)
                .containsExactly(rebuildPass);

            // The inhabitation scan is the third of them, and the one that read the sector for
            // itself until the habitation value gave it the pass's own walk to answer off.
            assertThat(inhabitationScanPasses)
                .containsExactly(rebuildPass);
        }

        @Test
        void resolveHoldingNamesWhatTheHoldingResolveFound() {
            // The counts the resolve used to print in a log line the profiler never saw. They ride
            // on the call rather than as counters: none is a volume of work its duration divides
            // by, and the row is read against what the readers beneath it walked.
            var viewFake = new PoliticalMapViewFake(
                Map.of(),
                (pass, selectedBlocId) -> new HolderResolution(Map.of(), Set.of(), Set.of()));

            var capture = RecordedCapture.recordWhile(() -> TerritoryBuilder.resolveHolding(
                buildPassOverAnEmptySector(),
                viewFake,
                UNFILTERED_INPUTS));

            assertThat(capture.findNode(RESOLVE_POLITICS_SECTION).getWorstCall().getTag())
                .isEqualTo("owned=0 filtering=false contested=0 unfilled=0");
        }

        @Test
        void resolveHoldingNamesWhatEachSystemScanSelected() {
            // Both scans report identically, which is what one shared helper is for: two spellings
            // would be two chances for one of them to state its cost differently from the other.
            var viewFake = new PoliticalMapViewFake(
                Map.of(),
                (pass, selectedBlocId) -> new HolderResolution(Map.of(), Set.of(), Set.of()));

            var capture = RecordedCapture.recordWhile(() -> TerritoryBuilder.resolveHolding(
                buildPassOverAnEmptySector(),
                viewFake,
                UNFILTERED_INPUTS));

            assertThat(capture.findNode(FIND_INHABITED_SECTION).getWorstCall().getTag())
                .isEqualTo("systems=1");
            assertThat(capture.findNode(FIND_SPOTLIT_PRESENCE_SECTION).getWorstCall().getTag())
                .isEqualTo("systems=0");
        }

        @Test
        void resolveHoldingIsMeasuredOnARowOfItsOwnAboveItsThreeScans() {
            // A rebuild that kept the standing holding shows as missing this row, which is a
            // plainer reading than three scan rows that each happened to cost nothing.
            var viewFake = new PoliticalMapViewFake(
                Map.of(),
                (pass, selectedBlocId) -> new HolderResolution(Map.of(), Set.of(), Set.of()));

            var capture = RecordedCapture.recordWhile(() -> TerritoryBuilder.resolveHolding(
                buildPassOverAnEmptySector(),
                viewFake,
                UNFILTERED_INPUTS));

            assertThat(capture.findNode(RESOLVE_HOLDING_SECTION).getChildren())
                .extracting(node -> node.getSection().getName())
                .containsExactly(
                    RESOLVE_POLITICS_SECTION,
                    FIND_INHABITED_SECTION,
                    FIND_SPOTLIT_PRESENCE_SECTION);
        }
    }

    @Nested
    class BuildTerritories {

        @Test
        void buildTerritoriesBuildsFromTheHandedHoldingWithoutReadingTheSectorAgain() {
            // The whole point of resolving apart from building: a rebuild a style pick owes is
            // handed the holding the last one read, and must paint from it rather than walk the
            // economy for an answer it already holds.
            var holderPasses = new ArrayList<HolderPass>();
            var viewFake = new PoliticalMapViewFake(
                Map.of(),
                (pass, selectedBlocId) -> {
                    holderPasses.add(pass);
                    return new HolderResolution(Map.of(), Set.of(), Set.of());
                });

            var territories = TerritoryBuilder.buildTerritories(
                new CellGeometryCache(),
                buildPassOverAnEmptySector(),
                viewFake,
                UNFILTERED_INPUTS,
                new ResolvedHolding(
                    new HolderResolution(
                        Map.of(HELD_SYSTEM, HELD_BY), Set.of(), Set.of()),
                    Set.of(HELD_SYSTEM),
                    Set.of()));

            assertThat(holderPasses)
                .isEmpty();
            assertThat(inhabitationScanPasses)
                .isEmpty();
            assertThat(territories.getOccupancy().getHolderBySystemKey())
                .containsExactly(Map.entry(HELD_SYSTEM, HELD_BY));
        }

        @Test
        void buildTerritoriesCopiesTheHandedHoldingRatherThanAdoptingIt() {
            // The territories are folded into by the incremental refresh, and a holding handed to a
            // later rebuild has to still say what it said - so what the build holds is its own.
            var viewFake = new PoliticalMapViewFake(
                Map.of(),
                (pass, selectedBlocId) -> new HolderResolution(Map.of(), Set.of(), Set.of()));
            var holding = new ResolvedHolding(
                new HolderResolution(Map.of(HELD_SYSTEM, HELD_BY), Set.of(), Set.of()),
                Set.of(HELD_SYSTEM),
                Set.of());

            var territories = TerritoryBuilder.buildTerritories(
                new CellGeometryCache(),
                buildPassOverAnEmptySector(),
                viewFake,
                UNFILTERED_INPUTS,
                holding);

            territories.getOccupancy().recordHolderOf(HELD_SYSTEM, null);

            assertThat(holding.resolution().ownerBySystemKey())
                .containsKey(HELD_SYSTEM);
        }

        @Test
        void buildTerritoriesCountsTheCellsItShaped() {
            // The number the shaping stage's duration is read against. Nothing is shaped over an
            // empty geometry, which is what the zero states - the counter is on the row either way,
            // so a reader can tell a stage that shaped nothing from one that never ran.
            var viewFake = new PoliticalMapViewFake(
                Map.of(),
                (pass, selectedBlocId) -> new HolderResolution(Map.of(), Set.of(), Set.of()));

            var capture = RecordedCapture.recordWhile(() -> buildOverAnEmptySector(viewFake));

            var shapeRow = capture.findNode(SHAPE_AND_STYLE_SECTION);

            assertThat(shapeRow.findCount(MapBuildCounters.CELLS).getTotals().getTotal())
                .isZero();
            assertThat(shapeRow.getWorstCall().getTag())
                .isEqualTo("styled=0 blocs=0");
        }

        @Test
        void buildTerritoriesResolvesUnderTheHandedPassesGroupingRatherThanTheViewsOwn() {
            // The grouping the build retains has to be the one its holding was resolved under, or
            // an incremental re-shape would classify a cell against blocs the fills never drew.
            // Taking it off the pass is what makes that so: the view is asked for a grouping only
            // where the pass is opened, which is above this build.
            var grouping = HolderGrouping.identity();
            var viewFake = new PoliticalMapViewFake(
                Map.of(),
                (pass, selectedBlocId) -> new HolderResolution(Map.of(), Set.of(), Set.of()));
            var pass = HolderPass.over(mock(SectorAPI.class), UNDER_THE_FOG, grouping);

            var territories = TerritoryBuilder.buildTerritories(
                new CellGeometryCache(),
                pass,
                viewFake,
                UNFILTERED_INPUTS,
                TerritoryBuilder.resolveHolding(pass, viewFake, UNFILTERED_INPUTS));

            assertThat(territories.getBuildInputs().viewGrouping().grouping())
                .isSameAs(grouping);
        }
    }

    // A build over nothing from a holding read for it, which is the whole of a rebuild that owes a
    // new reading - the shape every case not about the split takes.
    private static PoliticalMapTerritories buildOverAnEmptySector(PoliticalMapViewFake viewFake) {

        var pass = buildPassOverAnEmptySector();

        return TerritoryBuilder.buildTerritories(
            new CellGeometryCache(),
            pass,
            viewFake,
            UNFILTERED_INPUTS,
            TerritoryBuilder.resolveHolding(pass, viewFake, UNFILTERED_INPUTS));
    }

    // A reading of a sector holding nothing, which is every case here: the readers are stood in
    // for, so what a pass would answer reaches nothing this suite asserts and only which pass
    // reached them does.
    private static HolderPass buildPassOverAnEmptySector() {
        return HolderPass.over(
            mock(SectorAPI.class),
            UNDER_THE_FOG,
            HolderGrouping.identity());
    }
}
