package kmu.maplayers.ownermap.render.clusters;

import kmlib.colour.ColourPair;
import kmlib.profiling.ActiveProfiler;
import kmlib.profiling.ProfileSection;
import kmlib.starsector.systems.SectorPassIndex;
import kmlib.starsector.systems.SystemKey;
import kmlib.testfixtures.profiling.RecordedCapture;

import kmu.maplayers.base.geometry.CellGeometryCache;
import kmu.maplayers.base.profiling.MapBuildCounters;
import kmu.maplayers.base.render.clusters.FillSplit.FillState;
import kmu.maplayers.base.render.clusters.SystemFillStates;
import kmu.maplayers.base.visibility.systems.MapVisibilityRules;
import kmu.maplayers.ownermap.ContentInputs;
import kmu.maplayers.ownermap.ContentInputsFixtures;
import kmu.maplayers.ownermap.OwnerPaintedViewFake;
import kmu.maplayers.ownermap.ViewReading;
import kmu.maplayers.ownermap.owners.OwnerReadingFake;
import kmu.maplayers.ownermap.owners.OwnerSource;
import kmu.maplayers.ownermap.owners.OwnerSourceFake;
import kmu.maplayers.ownermap.owners.ResolvedOwners;
import kmu.maplayers.ownermap.owners.SectorWalk;
import kmu.maplayers.ownermap.owners.SystemOwner;
import kmu.maplayers.ownermap.owners.SystemOwnerResolve;
import kmu.maplayers.ownermap.render.style.MapStyling;
import kmu.maplayers.ownermap.render.style.OwnerCategoriesFake;
import kmu.maplayers.ownermap.ribbon.RibbonPlanRules;
import kmu.maplayers.ownermap.ribbon.SystemRibbonPlanner;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.Map;
import java.util.Set;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;
import static kmu.maplayers.ownermap.render.clusters.OwnerMapClusterFixtures.createInertCategoryStyle;
import static kmu.maplayers.ownermap.render.clusters.OwnerMapClusterFixtures.createRenderStyleForEveryCategory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins what a build hands down and what it keeps: the walk it was given, at the source the owners
 * are asked of, and the view's reading and styling it was handed, retained whole for every stage
 * after it.
 *
 * <p>Two halves, because a rebuild has two. The owners are a reading of the sector, asked of the
 * layer's source on a row of their own; the build paints over whatever owners it is handed, from
 * this rebuild or a previous one, and never asks the source again.
 *
 * <p>The styling and the sidebar picks arrive as stated readings rather than being read at all.
 * The geometry is empty, so the shaping and tracing stages run over nothing and the build reduces
 * to the reads this suite names.
 */
final class OwnerMapBuilderTests {

    // The row the owners resolve lands on, and a row a source of its own opens beneath it.
    private static final String RESOLVE_OWNERS_SECTION = "ownerMap.resolveOwners";
    private static final ProfileSection SOURCE_OWN_SECTION =
        ProfileSection.registerSection("ownerMapBuilderTests.sourceOwnScan");

    // The build's shaping stage.
    private static final String SHAPE_AND_STYLE_SECTION = "ownerMap.shapeAndStyleCells";

    // One held system, for the cases about owners handed in rather than read.
    private static final SystemKey HELD_SYSTEM = buildCellKey("corvus");
    private static final Color NEUTRAL = new Color(150, 150, 150);
    private static final SystemOwner HELD_BY =
        new SystemOwner("hegemony", new ColourPair(NEUTRAL, NEUTRAL));

    // The picks a pass off filter was baked under, and one spotlighting an owner.
    private static final ContentInputs UNFILTERED_INPUTS =
        ContentInputsFixtures.createInertInputs();
    private static final ContentInputs SPOTLIGHTING_INPUTS =
        ContentInputsFixtures.createInputsSpotlighting("hegemony");

    // The scheme every build here is handed: no case is about what it says, only that the build
    // keeps the one it was given.
    private static final MapStyling STYLING = new MapStyling(
        createRenderStyleForEveryCategory(createInertCategoryStyle()),
        OwnerMapClusterFixtures.BORDER_TRACE,
        OwnerCategoriesFake.SHARED_FAKE,
        OwnerMapClusterFixtures.NEUTRAL_PALETTE,
        OwnerMapClusterFixtures.NEUTRAL_PALETTE,
        OwnerMapClusterFixtures.NEUTRAL_PALETTE);

    // A walk over no sector: the source is stood in for, so nothing reads it, and only which walk
    // reached the source matters.
    private final SectorWalk walk = new SectorWalk(new SectorPassIndex(null), MapVisibilityRules.BASE);

    @Nested
    class ResolveOwners {

        @Test
        void asksTheSourceOverTheHandedWalk() {
            // The walk every stage of the rebuild shares: a source asked over one of its own would
            // be a second traversal of the sector, and a second reading of a sector free to have
            // moved between them.
            var sourceFake = new OwnerSourceFake();

            OwnerMapBuilder.resolveOwners(sourceFake, walk, UNFILTERED_INPUTS);

            assertThat(sourceFake.readOwnerWalks())
                .singleElement()
                .isSameAs(walk);
        }

        @Test
        void asksUnderTheSampledSpotlight() {
            // The one pick that reaches the owners, taken off the rebuild's own sampling.
            var sourceFake = new OwnerSourceFake();

            OwnerMapBuilder.resolveOwners(sourceFake, walk, SPOTLIGHTING_INPUTS);

            assertThat(sourceFake.readSpotlitOwnerIdsAsked())
                .containsExactly("hegemony");
        }

        @Test
        void answersWithTheSourcesOwnAnswer() {

            var owners = buildOwnersHolding(HELD_SYSTEM, HELD_BY);

            assertThat(OwnerMapBuilder.resolveOwners(
                    OwnerSourceFake.createAnswering(owners),
                    walk,
                    UNFILTERED_INPUTS))
                .isSameAs(owners);
        }

        @Test
        void isMeasuredOnARowOfItsOwnAboveWhatTheSourceMeasures() {
            // A rebuild that kept its owners shows as missing this row, which is a plainer reading
            // than a source's scans that each happened to cost nothing - and whatever the source
            // times of its own is read inside it.
            var capture = RecordedCapture.recordWhile(() -> OwnerMapBuilder.resolveOwners(
                new ScanningSourceFake(),
                walk,
                UNFILTERED_INPUTS));

            assertThat(capture.findNode(RESOLVE_OWNERS_SECTION).getChildren())
                .extracting(node -> node.getSection().getName())
                .containsExactly(SOURCE_OWN_SECTION.getName());
        }
    }

    @Nested
    class BuildClusters {

        @Test
        void buildsFromTheHandedOwnersWithoutAskingTheSourceAgain() {
            // The whole point of resolving apart from building: a rebuild a style pick owes is
            // handed the owners the last one resolved, and must paint from them rather than ask
            // the source for an answer it already holds.
            var sourceFake = new OwnerSourceFake();

            var clusters = buildClustersOver(
                buildReadingOver(sourceFake),
                buildOwnersHolding(HELD_SYSTEM, HELD_BY));

            assertThat(sourceFake.readOwnerWalks())
                .isEmpty();
            assertThat(clusters.getOccupancy().getOwnerBySystemKey())
                .containsExactly(Map.entry(HELD_SYSTEM, HELD_BY));
        }

        @Test
        void copiesTheHandedOwnersRatherThanAdoptingThem() {
            // The clusters are folded into by the incremental refresh, and owners handed to a later
            // rebuild have to still say what they said - so what the build holds is its own.
            var owners = buildOwnersHolding(HELD_SYSTEM, HELD_BY);

            var clusters = buildClustersOver(buildReadingOver(new OwnerSourceFake()), owners);

            clusters.getOccupancy().recordOwnerOf(HELD_SYSTEM, null);

            assertThat(owners.ownerBySystemKey())
                .containsKey(HELD_SYSTEM);
        }

        @Test
        void carriesTheFillStatesTheOwnersStated() {
            // The fill states the source co-produced with its owner map ride onto the build's inputs
            // whole, which the fill split reads them off.
            var expectedFillStates = SystemFillStates.createOf(Map.of(
                buildCellKey("askonia"),
                FillState.HATCHED,
                buildCellKey("eos"),
                FillState.UNFILLED));

            var clusters = buildClustersOver(
                buildReadingOver(new OwnerSourceFake()),
                new ResolvedOwners(Map.of(), expectedFillStates, Set.of(), Set.of()));

            assertThat(clusters.getBuildInputs().getFillStates())
                .isSameAs(expectedFillStates);
        }

        @Test
        void retainsTheHandedReadingWhole() {
            // The fills, borders and seams are styled off the build inputs and the labels off the
            // snapshot taken from them, and a batch re-derives through the source beside it - so
            // the reading retained here is the very one the owners were resolved beside.
            var viewReading = buildReadingOver(new OwnerSourceFake());

            var clusters = buildClustersOver(viewReading, ResolvedOwners.createEmpty());

            assertThat(clusters.getBuildInputs().getViewReading())
                .isSameAs(viewReading);
        }

        @Test
        void retainsTheHandedStylingWhole() {
            // The rebuild reads its styling once for everything it builds, so the scheme the
            // incremental refresh and the names read back is the very one it was handed.
            var clusters = buildClustersOver(
                buildReadingOver(new OwnerSourceFake()),
                ResolvedOwners.createEmpty());

            assertThat(clusters.getBuildInputs().getStyling())
                .isSameAs(STYLING);
        }

        @Test
        void countsTheCellsItShaped() {
            // The number the shaping stage's duration is read against. Nothing is shaped over an
            // empty geometry, which is what the zero states - the counter is on the row either way,
            // so a reader can tell a stage that shaped nothing from one that never ran.
            var capture = RecordedCapture.recordWhile(() -> buildClustersOver(
                buildReadingOver(new OwnerSourceFake()),
                ResolvedOwners.createEmpty()));

            var shapeRow = capture.findNode(SHAPE_AND_STYLE_SECTION);

            assertThat(shapeRow.findCount(MapBuildCounters.CELLS).getTotals().getTotal())
                .isZero();
            assertThat(shapeRow.getWorstCall().getTag())
                .isEqualTo("styled=0 owners=0");
        }

        // A build over no geometry under the shared picks and styling, differing per case in the
        // reading and the owners alone.
        private static OwnerMapClusters buildClustersOver(ViewReading viewReading, ResolvedOwners owners) {

            return OwnerMapBuilder.buildClusters(
                new CellGeometryCache(),
                viewReading,
                UNFILTERED_INPUTS,
                owners,
                STYLING);
        }
    }

    // A view's reading answering nothing of note, beside the given source - what every build here is
    // handed, the cases differing in the owners rather than in the reading.
    private static ViewReading buildReadingOver(OwnerSource source) {
        return new ViewReading(
            new OwnerPaintedViewFake(source),
            OwnerReadingFake.createAnsweringNothing(),
            source);
    }

    // Owners holding one system and counting it settled, nothing contested, unfilled or spotlit.
    private static ResolvedOwners buildOwnersHolding(SystemKey systemKey, SystemOwner owner) {
        return new ResolvedOwners(
            Map.of(systemKey, owner),
            SystemFillStates.ALL_SOLID,
            Set.of(systemKey),
            Set.of());
    }

    /** A source that times a scan of its own under whatever row its caller opened. */
    private static final class ScanningSourceFake implements OwnerSource {

        @Override
        public ResolvedOwners resolveOwners(SectorWalk walk, String spotlitOwnerId) {

            try (var scanScope = ActiveProfiler.resolveProfiler().open(SOURCE_OWN_SECTION)) {
                return ResolvedOwners.createEmpty();
            }
        }

        @Override
        public SystemOwnerResolve openSystemResolve(SectorWalk walk, String spotlitOwnerId) {
            throw new UnsupportedOperationException("no case here re-derives a system");
        }

        @Override
        public SystemRibbonPlanner resolveRibbonPlanner(SectorWalk walk, RibbonPlanRules rules) {
            throw new UnsupportedOperationException("no case here bakes a band");
        }
    }
}
