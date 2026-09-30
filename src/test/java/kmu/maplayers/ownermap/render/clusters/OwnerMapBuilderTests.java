package kmu.maplayers.ownermap.render.clusters;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.systems.SystemKey;
import kmlib.testfixtures.profiling.RecordedCapture;

import kmu.maplayers.base.geometry.CellGeometryCache;
import kmu.maplayers.base.profiling.MapBuildCounters;
import kmu.maplayers.ownermap.ContentInputs;
import kmu.maplayers.ownermap.ContentInputsFixtures;
import kmu.maplayers.ownermap.OwnerPaintedView;
import kmu.maplayers.ownermap.OwnerPaintedViewFake;
import kmu.maplayers.ownermap.holding.HolderGrouping;
import kmu.maplayers.ownermap.holding.HolderPass;
import kmu.maplayers.ownermap.holding.OwnerMapInhabitation;
import kmu.maplayers.ownermap.owners.OwnerPalette;
import kmu.maplayers.ownermap.owners.OwnerReadingFake;
import kmu.maplayers.ownermap.owners.SpotlitBlocs;
import kmu.maplayers.ownermap.owners.SystemOwner;
import kmu.maplayers.ownermap.owners.holders.HolderResolution;
import kmu.maplayers.ownermap.render.style.HolderCategories;
import kmu.maplayers.ownermap.render.style.MapPalettes;
import kmu.maplayers.ownermap.render.style.RenderStyleReader;

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
import static kmu.maplayers.ownermap.holding.ColonyReadRulesFixtures.UNDER_THE_FOG;
import static kmu.maplayers.ownermap.render.clusters.OwnerMapClusterFixtures.createInertCategoryStyle;
import static kmu.maplayers.ownermap.render.clusters.OwnerMapClusterFixtures.createRenderStyleForEveryCategory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Pins what a build hands down: the one reading of the sector it was given, at every reader
 * beneath it.
 *
 * <p>The arrangement the whole pass exists for, and the one thing no reader below can state for
 * itself. Each of them takes a pass and shares its walk of a system with whatever else reads that
 * system through it - but only this build decides which pass they are handed, and one opened here
 * would have the sector walked again while every reader below went on looking correct.
 *
 * <p>It also pins the one owner reading a build resolves: asked of the view once, over the pass's
 * own sector and grouping, and retained whole for every stage after it.
 *
 * <p>Everything the build reads apart from that is stood in for: the theme, the palettes and the
 * inhabitation scan all read live sources no test JVM answers, and the sidebar picks arrive as a
 * stated reading rather than being read at all. None of them is what a case here is about. The
 * geometry is empty, so the shaping and tracing stages run over nothing and the build reduces to
 * the reads this suite names.
 */
final class OwnerMapBuilderTests {

    private static final Color NEUTRAL = new Color(150, 150, 150);

    // The rows a resolve's three scans and their parent land on, and the build's shaping stage.
    private static final String RESOLVE_HOLDING_SECTION = "ownerMap.resolveHolding";
    private static final String RESOLVE_HOLDERS_SECTION = "ownerMap.resolveHolders";
    private static final String FIND_INHABITED_SECTION = "ownerMap.findInhabited";
    private static final String FIND_SPOTLIT_PRESENCE_SECTION = "ownerMap.findSpotlitPresence";
    private static final String SHAPE_AND_STYLE_SECTION = "ownerMap.shapeAndStyleCells";

    // One held system, for the cases about a holding handed in rather than read.
    private static final SystemKey HELD_SYSTEM = buildCellKey("corvus");
    private static final SystemOwner HELD_BY = new SystemOwner("hegemony", new OwnerPalette(NEUTRAL, NEUTRAL));

    // The picks a pass off filter was baked under. No case here spotlights a bloc, so the whole
    // reading is inert and the build reduces to the passes it hands down.
    private static final ContentInputs UNFILTERED_INPUTS =
        ContentInputsFixtures.createInertInputs();

    private MockedStatic<OwnerMapInhabitation> inhabitationMock;
    private MockedStatic<SpotlitBlocs> spotlitBlocsMock;
    private MockedStatic<RenderStyleReader> styleReaderMock;
    private MockedStatic<MapPalettes> palettesMock;

    // The passes the presence scan was asked for, in the order the build asked.
    private final List<HolderPass> presenceScanPasses = new ArrayList<>();

    // The same for the inhabitation scan, which walks every system for its colonies as the other
    // two readers do.
    private final List<HolderPass> inhabitationScanPasses = new ArrayList<>();

    @BeforeEach
    void openTheLiveSeams() {

        inhabitationMock = mockStatic(OwnerMapInhabitation.class);
        spotlitBlocsMock = mockStatic(SpotlitBlocs.class);
        styleReaderMock = mockStatic(RenderStyleReader.class);
        palettesMock = mockStatic(MapPalettes.class);

        // The inhabitation scan's pass is kept on the same terms the presence scan's is: it walks
        // every system for its colonies, so which pass reached it decides whether the rebuild
        // read the sector once or twice.
        inhabitationMock
            .when(() -> OwnerMapInhabitation.readInhabitedSystemKeys(any(HolderPass.class)))
            .thenAnswer(invocation -> {
                inhabitationScanPasses.add(invocation.getArgument(0));
                return Set.of(buildCellKey("inhabited-system"));
            });
        styleReaderMock
            .when(() -> RenderStyleReader.readRenderStyle(any(), any()))
            .thenReturn(createRenderStyleForEveryCategory(createInertCategoryStyle()));

        // Each pass the presence scan is handed, kept rather than answered about: the case is
        // about which pass reached it, not what it reported.
        spotlitBlocsMock
            .when(() -> SpotlitBlocs.findPresentSystemKeys(any(HolderPass.class), any(), any()))
            .thenAnswer(invocation -> {
                presenceScanPasses.add(invocation.getArgument(0));
                return Set.of();
            });
    }

    @AfterEach
    void closeTheLiveSeams() {
        palettesMock.close();
        styleReaderMock.close();
        spotlitBlocsMock.close();
        inhabitationMock.close();
    }

    @Nested
    class ResolveHolding {

        @Test
        void readsTheSectorThroughTheHandedPassForEveryReaderBeneathIt() {
            // A resolve reads who holds what and then asks where the spotlit bloc lives outside it.
            // Both walk every system, so a pass apiece is a second traversal of the sector for
            // colonies the first one has already read - and, less visibly, a second reading of a
            // sector that is free to have moved between them.
            var holderPasses = new ArrayList<HolderPass>();
            var viewFake = new OwnerPaintedViewFake(
                Map.of(),
                (pass, selectedBlocId) -> {
                    holderPasses.add(pass);
                    return new HolderResolution(Map.of(), Set.of(), Set.of());
                });
            var rebuildPass = buildPassOverAnEmptySector();

            OwnerMapBuilder.resolveHolding(rebuildPass, viewFake, UNFILTERED_INPUTS);

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
        void namesWhatTheHoldingResolveFound() {
            // The counts the resolve found, reported where the profiler sees them. They ride on
            // the call rather than as counters: none is a volume of work its duration divides
            // by, and the row is read against what the readers beneath it walked.
            var viewFake = new OwnerPaintedViewFake(
                Map.of(),
                (pass, selectedBlocId) -> new HolderResolution(Map.of(), Set.of(), Set.of()));

            var capture = RecordedCapture.recordWhile(() -> OwnerMapBuilder.resolveHolding(
                buildPassOverAnEmptySector(),
                viewFake,
                UNFILTERED_INPUTS));

            assertThat(capture.findNode(RESOLVE_HOLDERS_SECTION).getWorstCall().getTag())
                .isEqualTo("owned=0 filtering=false contested=0 unfilled=0");
        }

        @Test
        void namesWhatEachSystemScanSelected() {
            // Both scans report identically, which is what one shared helper is for: two spellings
            // would be two chances for one of them to state its cost differently from the other.
            var viewFake = new OwnerPaintedViewFake(
                Map.of(),
                (pass, selectedBlocId) -> new HolderResolution(Map.of(), Set.of(), Set.of()));

            var capture = RecordedCapture.recordWhile(() -> OwnerMapBuilder.resolveHolding(
                buildPassOverAnEmptySector(),
                viewFake,
                UNFILTERED_INPUTS));

            assertThat(capture.findNode(FIND_INHABITED_SECTION).getWorstCall().getTag())
                .isEqualTo("systems=1");
            assertThat(capture.findNode(FIND_SPOTLIT_PRESENCE_SECTION).getWorstCall().getTag())
                .isEqualTo("systems=0");
        }

        @Test
        void isMeasuredOnARowOfItsOwnAboveItsThreeScans() {
            // A rebuild that kept the standing holding shows as missing this row, which is a
            // plainer reading than three scan rows that each happened to cost nothing.
            var viewFake = new OwnerPaintedViewFake(
                Map.of(),
                (pass, selectedBlocId) -> new HolderResolution(Map.of(), Set.of(), Set.of()));

            var capture = RecordedCapture.recordWhile(() -> OwnerMapBuilder.resolveHolding(
                buildPassOverAnEmptySector(),
                viewFake,
                UNFILTERED_INPUTS));

            assertThat(capture.findNode(RESOLVE_HOLDING_SECTION).getChildren())
                .extracting(node -> node.getSection().getName())
                .containsExactly(
                    RESOLVE_HOLDERS_SECTION,
                    FIND_INHABITED_SECTION,
                    FIND_SPOTLIT_PRESENCE_SECTION);
        }
    }

    @Nested
    class BuildClusters {

        @Test
        void buildsFromTheHandedHoldingWithoutReadingTheSectorAgain() {
            // The whole point of resolving apart from building: a rebuild a style pick owes is
            // handed the holding the last one read, and must paint from it rather than walk the
            // economy for an answer it already holds.
            var holderPasses = new ArrayList<HolderPass>();
            var viewFake = new OwnerPaintedViewFake(
                Map.of(),
                (pass, selectedBlocId) -> {
                    holderPasses.add(pass);
                    return new HolderResolution(Map.of(), Set.of(), Set.of());
                });

            var clusters = OwnerMapBuilder.buildClusters(
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
            assertThat(clusters.getOccupancy().getHolderBySystemKey())
                .containsExactly(Map.entry(HELD_SYSTEM, HELD_BY));
        }

        @Test
        void copiesTheHandedHoldingRatherThanAdoptingIt() {
            // The clusters are folded into by the incremental refresh, and a holding handed to a
            // later rebuild has to still say what it said - so what the build holds is its own.
            var viewFake = new OwnerPaintedViewFake(
                Map.of(),
                (pass, selectedBlocId) -> new HolderResolution(Map.of(), Set.of(), Set.of()));
            var holding = new ResolvedHolding(
                new HolderResolution(Map.of(HELD_SYSTEM, HELD_BY), Set.of(), Set.of()),
                Set.of(HELD_SYSTEM),
                Set.of());

            var clusters = OwnerMapBuilder.buildClusters(
                new CellGeometryCache(),
                buildPassOverAnEmptySector(),
                viewFake,
                UNFILTERED_INPUTS,
                holding);

            clusters.getOccupancy().recordHolderOf(HELD_SYSTEM, null);

            assertThat(holding.resolution().ownerBySystemKey())
                .containsKey(HELD_SYSTEM);
        }

        @Test
        void countsTheCellsItShaped() {
            // The number the shaping stage's duration is read against. Nothing is shaped over an
            // empty geometry, which is what the zero states - the counter is on the row either way,
            // so a reader can tell a stage that shaped nothing from one that never ran.
            var viewFake = new OwnerPaintedViewFake(
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
        void resolvesUnderTheHandedPassesGroupingRatherThanTheViewsOwn() {
            // The grouping the build retains has to be the one its holding was resolved under, or
            // an incremental re-shape would classify a cell against blocs the fills never drew.
            // Taking it off the pass is what makes that so: the view is asked for a grouping only
            // where the pass is opened, which is above this build.
            var grouping = HolderGrouping.identity();
            var viewFake = new OwnerPaintedViewFake(
                Map.of(),
                (pass, selectedBlocId) -> new HolderResolution(Map.of(), Set.of(), Set.of()));
            var pass = HolderPass.over(mock(SectorAPI.class), UNDER_THE_FOG, grouping);

            var clusters = OwnerMapBuilder.buildClusters(
                new CellGeometryCache(),
                pass,
                viewFake,
                UNFILTERED_INPUTS,
                OwnerMapBuilder.resolveHolding(pass, viewFake, UNFILTERED_INPUTS));

            assertThat(clusters.getBuildInputs().viewReading().grouping())
                .isSameAs(grouping);
        }

        @Test
        void resolvesTheOwnerReadingOnceOverThePassesOwnSnapshot() {
            // One reading per rebuild, asked with the very sector and grouping the holding was read
            // under: a reading taken over a second sampling of the grouping could name, colour and
            // categorise the owners of a fold the holding never made.
            var viewSpy = spy(new OwnerPaintedViewFake(
                Map.of(),
                (pass, selectedBlocId) -> new HolderResolution(Map.of(), Set.of(), Set.of())));
            var pass = buildPassOverAnEmptySector();

            OwnerMapBuilder.buildClusters(
                new CellGeometryCache(),
                pass,
                viewSpy,
                UNFILTERED_INPUTS,
                OwnerMapBuilder.resolveHolding(pass, viewSpy, UNFILTERED_INPUTS));

            verify(viewSpy, times(1)).resolveOwnerReading(same(pass.sector()), same(pass.grouping()));
        }

        @Test
        void retainsTheReadingItResolvedForEveryLaterStage() {
            // The fills, borders and seams are styled off the build inputs and the labels off the
            // snapshot taken from them, so the reading retained here is the one all of them read.
            var reading = OwnerReadingFake.createAnsweringNothing();
            var viewSpy = spy(new OwnerPaintedViewFake(
                Map.of(),
                (pass, selectedBlocId) -> new HolderResolution(Map.of(), Set.of(), Set.of())));
            doReturn(reading).when(viewSpy).resolveOwnerReading(any(), any());

            var clusters = buildOverAnEmptySector(viewSpy);

            assertThat(clusters.getBuildInputs().viewReading().reading())
                .isSameAs(reading);
        }

        @Test
        void derivesTheUnownedPaletteFromTheReadingsUnownedShade() {
            // The shade an unowned cell paints in is the layer's answer, not a faction the tier
            // reads for itself.
            var clusters = buildOverAnEmptySector(new OwnerPaintedViewFake(
                Map.of(),
                (pass, selectedBlocId) -> new HolderResolution(Map.of(), Set.of(), Set.of())));

            palettesMock.verify(() -> MapPalettes.resolveNeutralPalette(OwnerReadingFake.UNOWNED_COLOUR));
            palettesMock.verify(() -> MapPalettes.resolveDesaturationPalette(
                same(OwnerReadingFake.RECEDE_PALETTE),
                anyDouble()));
            assertThat(clusters.getBuildInputs().styling().categories())
                .isSameAs(HolderCategories.INSTANCE);
        }
    }

    // A build over nothing from a holding read for it, which is the whole of a rebuild that owes a
    // new reading - the shape every case not about the split takes.
    private static OwnerMapClusters buildOverAnEmptySector(OwnerPaintedView viewFake) {

        var pass = buildPassOverAnEmptySector();

        return OwnerMapBuilder.buildClusters(
            new CellGeometryCache(),
            pass,
            viewFake,
            UNFILTERED_INPUTS,
            OwnerMapBuilder.resolveHolding(pass, viewFake, UNFILTERED_INPUTS));
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
