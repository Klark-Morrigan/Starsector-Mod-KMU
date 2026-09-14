package kmu.maplayers.politicalmap.base.politics;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;
import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKeys;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the accumulation rule both sector walks now share, apart from either of them: keep a bloc's
 * systems in the order they were met, add rather than replace when a bloc is met again, and hand
 * over an index the walk can no longer move.
 */
final class BlocPresenceIndexBuilderTest {

    @Nested
    class RecordPresence {

        @Test
        void recordPresenceKeepsABlocsSystemsInTheOrderTheyWereMet() {
            // Walk order, so a lit set is assembled the same way twice over one sector.
            var builder = new BlocPresenceIndexBuilder();

            builder.recordPresence("hegemony", buildCellKey("system-a"));
            builder.recordPresence("hegemony", buildCellKey("system-b"));
            builder.recordPresence("hegemony", buildCellKey("system-c"));

            assertThat(builder.buildIndex().readPresentSystemKeys("hegemony"))
                .containsExactlyElementsOf(buildCellKeys("system-a", "system-b", "system-c"));
        }

        @Test
        void recordPresenceLeavesOneEntryForAPairRecordedTwice() {
            // An arm that meets a bloc more than once in a system - two colonies of one faction -
            // records the pair twice, and the system must still be one lit cell rather than two.
            var builder = new BlocPresenceIndexBuilder();

            builder.recordPresence("hegemony", buildCellKey("system-a"));
            builder.recordPresence("hegemony", buildCellKey("system-a"));

            assertThat(builder.buildIndex().readPresentSystemKeys("hegemony"))
                .containsExactlyElementsOf(buildCellKeys("system-a"));
        }

        @Test
        void recordPresenceKeepsOneBlocsSystemsOutOfAnothers() {
            // The whole point of the keying: hovering one row lights that bloc's systems and no
            // neighbour's, however interleaved the walk met them.
            var builder = new BlocPresenceIndexBuilder();

            builder.recordPresence("hegemony", buildCellKey("system-a"));
            builder.recordPresence("tritachyon", buildCellKey("system-b"));
            builder.recordPresence("hegemony", buildCellKey("system-c"));

            var index = builder.buildIndex();

            assertThat(index.readPresentSystemKeys("hegemony"))
                .containsExactlyElementsOf(buildCellKeys("system-a", "system-c"));

            assertThat(index.readPresentSystemKeys("tritachyon"))
                .containsExactlyElementsOf(buildCellKeys("system-b"));
        }
    }

    @Nested
    class BuildIndex {

        @Test
        void buildIndexAnswersAnEmptyIndexForAWalkThatRecordedNothing() {
            // A sector nobody is present in yields an index that answers empty rather than null, so
            // the caller needs no guard between an empty walk and a lookup.
            assertThat(new BlocPresenceIndexBuilder().buildIndex().systemKeysByBlocId())
                .isEmpty();
        }

        @Test
        void buildIndexKeepsTheBlocsInTheOrderTheyWereFirstMet() {
            // Walk order at the outer level too, and taken from where a bloc was first met rather
            // than last, so a bloc met again does not jump the list.
            var builder = new BlocPresenceIndexBuilder();

            builder.recordPresence("tritachyon", buildCellKey("system-b"));
            builder.recordPresence("hegemony", buildCellKey("system-a"));
            builder.recordPresence("tritachyon", buildCellKey("system-c"));

            assertThat(builder.buildIndex().systemKeysByBlocId().keySet())
                .containsExactly("tritachyon", "hegemony");
        }

        @Test
        void buildIndexAnswersAnIndexLaterRecordingCannotMove() {
            // The handover the whole split rests on: the walk goes on holding the builder, and the
            // index it already gave out is read back per hover until the next rebuild replaces it.
            var builder = new BlocPresenceIndexBuilder();

            builder.recordPresence("hegemony", buildCellKey("system-a"));

            var index = builder.buildIndex();

            builder.recordPresence("hegemony", buildCellKey("system-b"));
            builder.recordPresence("tritachyon", buildCellKey("system-c"));

            assertThat(index.readPresentSystemKeys("hegemony"))
                .containsExactlyElementsOf(buildCellKeys("system-a"));

            assertThat(index.readPresentSystemKeys("tritachyon"))
                .isEmpty();
        }
    }
}
