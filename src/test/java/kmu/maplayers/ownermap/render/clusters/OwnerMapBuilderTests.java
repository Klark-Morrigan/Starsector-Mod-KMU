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
import kmu.maplayers.base.ribbon.SystemRibbonPlanner;
import kmu.maplayers.base.visibility.systems.MapVisibilityRules;
import kmu.maplayers.ownermap.ContentInputs;
import kmu.maplayers.ownermap.ContentInputsFixtures;
import kmu.maplayers.ownermap.OwnerPaintedViewFake;
import kmu.maplayers.ownermap.ViewReading;
import kmu.maplayers.ownermap.owners.OwnerReadingFake;
import kmu.maplayers.ownermap.owners.OwnerSource;
import kmu.maplayers.ownermap.owners.OwnerSourceFake;
import kmu.maplayers.ownermap.owners.ResolvedOwners;
import kmu.maplayers.ownermap.owners.ResolvedSystemOwner;
import kmu.maplayers.ownermap.owners.SectorWalk;
import kmu.maplayers.ownermap.owners.SystemOwner;
import kmu.maplayers.ownermap.owners.SystemOwnerResolver;
import kmu.maplayers.ownermap.render.style.MapStyling;
import kmu.maplayers.ownermap.render.style.OwnerCategoriesFake;
import kmu.maplayers.ownermap.ribbon.RibbonPlanRules;

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
 * are asked of, and the build inputs it was handed, retained whole for every stage after it.
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

        // The walk every stage of the rebuild shares: a source asked over one of its own would be a
        // second traversal of the sector, and a second reading of a sector free to have moved between
        // them.
        @Test
        void asksTheSourceOverTheHandedWalk() {

            // ARRANGE
            var sourceFake = new OwnerSourceFake();

            // ACT
            OwnerMapBuilder.resolveOwners(sourceFake, walk, UNFILTERED_INPUTS);

            // ASSERT
            assertThat(sourceFake.readOwnerWalks())
                .singleElement()
                .isSameAs(walk);
        }

        // The one pick that reaches the owners, taken off the rebuild's own sampling.
        @Test
        void asksUnderTheSampledSpotlight() {

            // ARRANGE
            var sourceFake = new OwnerSourceFake();

            // ACT
            OwnerMapBuilder.resolveOwners(sourceFake, walk, SPOTLIGHTING_INPUTS);

            // ASSERT
            assertThat(sourceFake.readSpotlitOwnerIdsAsked())
                .containsExactly("hegemony");
        }

        @Test
        void answersWithTheSourcesOwnAnswer() {

            // ARRANGE
            var owners = buildOwnersHolding(HELD_SYSTEM, HELD_BY);
            var sourceFake = OwnerSourceFake.createAnswering(owners);

            // ACT
            var actualOwners = OwnerMapBuilder.resolveOwners(sourceFake, walk, UNFILTERED_INPUTS);

            // ASSERT
            assertThat(actualOwners)
                .isSameAs(owners);
        }

        // A rebuild that kept its owners shows as missing this row, which is a plainer reading than a
        // source's scans that each happened to cost nothing - and whatever the source times of its
        // own is read inside it.
        @Test
        void isMeasuredOnARowOfItsOwnAboveWhatTheSourceMeasures() {

            // ARRANGE
            var sourceFake = new ScanningSourceFake();
            var expectedChildSectionName = "ownerMapBuilderTests.sourceOwnScan";

            // ACT
            var capture = RecordedCapture.recordWhile(
                () -> OwnerMapBuilder.resolveOwners(sourceFake, walk, UNFILTERED_INPUTS));
            var actualChildren = capture.findNode(RESOLVE_OWNERS_SECTION).getChildren();

            // ASSERT
            assertThat(actualChildren)
                .extracting(node -> node.getSection().getName())
                .containsExactly(expectedChildSectionName);
        }
    }

    @Nested
    class BuildClusters {

        // The whole point of resolving apart from building: a rebuild a style pick owes is handed the
        // owners the last one resolved, and must paint from them rather than ask the source for an
        // answer it already holds.
        @Test
        void buildsFromTheHandedOwnersWithoutAskingTheSourceAgain() {

            // ARRANGE
            var sourceFake = new OwnerSourceFake();
            var viewReading = buildReadingOver(sourceFake);
            var owners = buildOwnersHolding(HELD_SYSTEM, HELD_BY);

            // ACT
            var actualClusters = buildClustersOver(viewReading, owners);

            // ASSERT
            assertThat(sourceFake.readOwnerWalks())
                .isEmpty();
            assertThat(actualClusters.getOccupancy().getOwnerBySystemKey())
                .containsExactly(Map.entry(HELD_SYSTEM, HELD_BY));
        }

        // The clusters are folded into by the incremental refresh, and owners handed to a later
        // rebuild have to still say what they said - so what the build holds is its own.
        @Test
        void copiesTheHandedOwnersRatherThanAdoptingThem() {

            // ARRANGE
            var owners = buildOwnersHolding(HELD_SYSTEM, HELD_BY);
            var clusters = buildClustersOver(buildReadingOver(new OwnerSourceFake()), owners);

            // ACT
            clusters.getOccupancy().recordOwnerOf(HELD_SYSTEM, ResolvedSystemOwner.UNOWNED);

            // ASSERT
            assertThat(owners.ownerBySystemKey())
                .containsKey(HELD_SYSTEM);
        }

        // The fill states the source co-produced with its owner map ride into the build's occupancy
        // beside the owners, which a batch folds them in and the fill split reads them off.
        @Test
        void carriesTheFillStatesTheOwnersStated() {

            // ARRANGE
            var viewReading = buildReadingOver(new OwnerSourceFake());
            var owners = new ResolvedOwners(
                Map.of(buildCellKey("askonia"), HELD_BY, buildCellKey("eos"), HELD_BY),
                SystemFillStates.createOf(Map.of(
                    buildCellKey("askonia"),
                    FillState.HATCHED,
                    buildCellKey("eos"),
                    FillState.UNFILLED)),
                Set.of(),
                Set.of());

            // ACT
            var actualClusters = buildClustersOver(viewReading, owners);

            // ASSERT
            assertThat(actualClusters.getOccupancy().snapshotFillStates().getNonSolidFillStateBySystemKey())
                .containsOnly(
                    Map.entry(buildCellKey("askonia"), FillState.HATCHED),
                    Map.entry(buildCellKey("eos"), FillState.UNFILLED));
        }

        // The fills, borders and seams are styled off the build inputs and the labels off the
        // snapshot taken from them, and a batch re-derives through the source and fits under the
        // overlay beside them - so the inputs retained here are the very ones the rebuild sampled.
        @Test
        void retainsTheHandedBuildInputsWhole() {

            // ARRANGE
            var buildInputs = buildInputsOver(buildReadingOver(new OwnerSourceFake()));

            // ACT
            var actualClusters = OwnerMapBuilder.buildClusters(
                new CellGeometryCache(),
                buildInputs,
                ResolvedOwners.createEmpty());

            // ASSERT
            assertThat(actualClusters.getBuildInputs())
                .isSameAs(buildInputs);
        }

        // The number the shaping stage's duration is read against. Nothing is shaped over an empty
        // geometry, which is what the zero states - the counter is on the row either way, so a reader
        // can tell a stage that shaped nothing from one that never ran.
        @Test
        void countsTheCellsItShaped() {

            // ARRANGE
            var viewReading = buildReadingOver(new OwnerSourceFake());
            var expectedTag = "styled=0 owners=0";

            // ACT
            var capture = RecordedCapture.recordWhile(
                () -> buildClustersOver(viewReading, ResolvedOwners.createEmpty()));
            var actualShapeRow = capture.findNode(SHAPE_AND_STYLE_SECTION);

            // ASSERT
            assertThat(actualShapeRow.findCount(MapBuildCounters.CELLS).getTotals().getTotal())
                .isZero();
            assertThat(actualShapeRow.getWorstCall().getTag())
                .isEqualTo(expectedTag);
        }

        // A build over no geometry under the shared picks and styling, differing per case in the
        // reading and the owners alone.
        private static OwnerMapClusters buildClustersOver(ViewReading viewReading, ResolvedOwners owners) {

            return OwnerMapBuilder.buildClusters(
                new CellGeometryCache(),
                buildInputsOver(viewReading),
                owners);
        }

        // The inputs every build here is handed: the shared picks and styling beside the case's own
        // reading, with the overlay on.
        private static OwnerMapBuildInputs buildInputsOver(ViewReading viewReading) {

            return new OwnerMapBuildInputs(
                STYLING,
                viewReading,
                UNFILTERED_INPUTS,
                true);
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
        public SystemOwnerResolver openSystemResolver(SectorWalk walk, String spotlitOwnerId) {
            throw new UnsupportedOperationException("no case here re-derives a system");
        }

        @Override
        public SystemRibbonPlanner resolveRibbonPlanner(SectorWalk walk, RibbonPlanRules rules) {
            throw new UnsupportedOperationException("no case here bakes a band");
        }
    }
}
