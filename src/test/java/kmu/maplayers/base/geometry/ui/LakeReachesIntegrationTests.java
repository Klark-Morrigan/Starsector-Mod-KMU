package kmu.maplayers.base.geometry.ui;

import kmlib.math.geometry.Points;

import kmu.maplayers.base.geometry.SectorFixture;
import kmu.maplayers.base.geometry.v3.Coastlines;
import kmu.maplayers.base.geometry.v4.LakeTier;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static kmu.maplayers.base.geometry.v4.SectorPartitions.KNOBS;
import static kmu.maplayers.base.geometry.v4.SectorPartitions.NOTHING_LAID;
import static kmu.maplayers.base.geometry.v4.SectorPartitions.measureSea;
import static kmu.maplayers.base.geometry.v4.SectorPartitions.measureVoid;
import static kmu.maplayers.base.geometry.v4.SectorPartitions.readPartition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.withinPercentage;

/**
 * Integration coverage for v3's lake coasts handed to v4, over the real sectors.
 *
 * <p>Here rather than beside either construction, because it reads both and the layering keeps
 * each of them from reading the other - in tests as in the code.
 *
 * <p><b>Four things are pinned.</b> That each lake crosses with its own ring of cells, since
 * that is all that tells a lake whose coast lays nothing from a puddle; which steps of a coast
 * cross as reaches, since the channel that decides it is the one knob a caller can get wrong
 * without anything failing; which way each reach runs, since that is what says which side the
 * coast captured; and what the reaches do once laid, since a line that divides water may not
 * take any of it away.
 */
class LakeReachesIntegrationTests {

    private static final String SECTORS =
        "kmu.maplayers.base.geometry.ui.LakeReachesIntegrationTests#provideSectorNames";

    // A channel of nothing, which is what a continent trace carries of its own: it lays no
    // walls, so the channel on its walls is zero.
    private static final double NO_CHANNEL = 0;

    static Stream<String> provideSectorNames() {
        return SectorFixture.listSectorNames().stream();
    }

    @Nested
    class CollectTracedLakes {

        @ParameterizedTest
        @MethodSource(SECTORS)
        void eachLakeHandsOverItsOwnRingOfCells(String sector) {
            // One ring per lake, in the trace's order, each the lake's own: what tells a lake
            // whose coast lays nothing from a puddle on the v4 side.
            var lakes = LakePartitions.layContinents(sector).traceCoasts().lakes();

            assertThat(LakePartitions.readTracedLakes(sector).rings())
                .containsExactlyElementsOf(
                    lakes.stream().map(Coastlines.Lake::ringCells).toList());
        }

        @ParameterizedTest
        @MethodSource(SECTORS)
        void theReachesAreTheOnesTheCoastLays(String sector) {

            var continents = LakePartitions.layContinents(sector);

            assertThat(LakePartitions.readTracedLakes(sector).reaches())
                .containsExactlyElementsOf(LakeReaches.collectLakeReaches(
                    continents.traceCoasts(), KNOBS.borderInset()));
        }
    }

    @Nested
    class CollectLakeReaches {

        @ParameterizedTest
        @MethodSource(SECTORS)
        void everyReachRunsFromOneCellToAnotherAtLeastAChannelLong(String sector) {
            // The two things that make a step of a coast a reach rather than a fillet or a
            // handover: it leaves one cell and arrives on another, and it crosses void wide
            // enough to have two sides.
            var reaches = LakePartitions.readTracedLakes(sector).reaches();

            assertThat(reaches)
                .isNotEmpty()
                .allSatisfy(reach -> {

                    assertThat(reach.fromSite())
                        .isNotEqualTo(reach.toSite());
                    assertThat(Points.computeDistance(reach.start(), reach.end()))
                        .isGreaterThanOrEqualTo(KNOBS.borderInset());
                });
        }

        @ParameterizedTest
        @MethodSource(SECTORS)
        void aChannelOfNothingTakesHandoversForReaches(String sector) {
            // Why the channel is handed in rather than read off the trace. Where two cells touch,
            // the coast hands over from one to the other in a step of no length, and under a
            // channel of nothing that step names two cells and is long enough - so it would be
            // laid as a wall across the point where two cells meet.
            var continents = LakePartitions.layContinents(sector);

            assertThat(LakeReaches.collectLakeReaches(continents.traceCoasts(), NO_CHANNEL))
                .hasSizeGreaterThan(LakePartitions.readTracedLakes(sector).reaches().size());
        }

        @ParameterizedTest
        @MethodSource(SECTORS)
        void theCoastLeavesEveryPieceOfTheVoidOneOpenPart(String sector) {
            // Which side of a reach is the bay rests on each reach running with its lake's
            // water on its left. Read the right way round, a lake keeps its water open and gives
            // up only the bays, and every other piece is untouched - so the open pieces count
            // the pieces there were before the coast went in. Read the wrong way round, every
            // lake's water is captured and its bays left open, and the count comes out as many
            // bays as there are rather than one piece per lake.
            var coastWalls = LakePartitions.layCoastWalls(sector);
            var open = LakePartitions.readCoastPartition(sector).collectPieces().stream()
                .filter(piece -> !LakeTier.isCaptured(piece, coastWalls))
                .count();

            assertThat(open)
                .isEqualTo(readPartition(sector, NOTHING_LAID).countPieces());
        }

        @ParameterizedTest
        @MethodSource(SECTORS)
        void theReachesDivideTheLakes(String sector) {
            // The floor under the two checks below: a coast that divided nothing would pass
            // them both.
            assertThat(LakePartitions.readCoastPartition(sector).countPieces())
                .isGreaterThan(readPartition(sector, NOTHING_LAID).countPieces());
        }

        @ParameterizedTest
        @MethodSource(SECTORS)
        void theReachesTakeNoVoidAwayBarSliversBelowTheMapsResolution(String sector) {
            // A line divides water without consuming any, so the void's area before the lines
            // and after them is the same number - bar the faces the walk does not keep, which
            // are those under a sagitta squared. A reach can shut one in at each end, where it
            // meets the shore; a leak, a lake walked as a tree of no area, costs millions.
            var reachCount = LakePartitions.readTracedLakes(sector).reaches().size();

            assertThat(measureVoid(readPartition(sector, NOTHING_LAID))
                    - measureVoid(LakePartitions.readCoastPartition(sector)))
                .isBetween(0.0, LakePartitions.measureSliverAllowance(reachCount));
        }

        @ParameterizedTest
        @MethodSource(SECTORS)
        void theOpenSeaIsUntouched(String sector) {
            // Every lake is inside a continent, so no lake coast reaches the sea.
            assertThat(measureSea(LakePartitions.readCoastPartition(sector)))
                .isCloseTo(
                    measureSea(readPartition(sector, NOTHING_LAID)),
                    withinPercentage(LakePartitions.SEA_SHARE));
        }
    }
}
