package kmu.maplayers.politicalmap.base.politics;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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

            builder.recordPresence("hegemony", "system-a");
            builder.recordPresence("hegemony", "system-b");
            builder.recordPresence("hegemony", "system-c");

            assertThat(builder.buildIndex().readPresentSystemIds("hegemony"))
                .containsExactly("system-a", "system-b", "system-c");
        }

        @Test
        void recordPresenceLeavesOneEntryForAPairRecordedTwice() {
            // An arm that meets a bloc more than once in a system - two colonies of one faction -
            // records the pair twice, and the system must still be one lit cell rather than two.
            var builder = new BlocPresenceIndexBuilder();

            builder.recordPresence("hegemony", "system-a");
            builder.recordPresence("hegemony", "system-a");

            assertThat(builder.buildIndex().readPresentSystemIds("hegemony"))
                .containsExactly("system-a");
        }

        @Test
        void recordPresenceKeepsOneBlocsSystemsOutOfAnothers() {
            // The whole point of the keying: hovering one row lights that bloc's systems and no
            // neighbour's, however interleaved the walk met them.
            var builder = new BlocPresenceIndexBuilder();

            builder.recordPresence("hegemony", "system-a");
            builder.recordPresence("tritachyon", "system-b");
            builder.recordPresence("hegemony", "system-c");

            var index = builder.buildIndex();

            assertThat(index.readPresentSystemIds("hegemony"))
                .containsExactly("system-a", "system-c");

            assertThat(index.readPresentSystemIds("tritachyon"))
                .containsExactly("system-b");
        }
    }

    @Nested
    class BuildIndex {

        @Test
        void buildIndexAnswersAnEmptyIndexForAWalkThatRecordedNothing() {
            // A sector nobody is present in yields an index that answers empty rather than null, so
            // the caller needs no guard between an empty walk and a lookup.
            assertThat(new BlocPresenceIndexBuilder().buildIndex().systemIdsByBlocId())
                .isEmpty();
        }

        @Test
        void buildIndexKeepsTheBlocsInTheOrderTheyWereFirstMet() {
            // Walk order at the outer level too, and taken from where a bloc was first met rather
            // than last, so a bloc met again does not jump the list.
            var builder = new BlocPresenceIndexBuilder();

            builder.recordPresence("tritachyon", "system-b");
            builder.recordPresence("hegemony", "system-a");
            builder.recordPresence("tritachyon", "system-c");

            assertThat(builder.buildIndex().systemIdsByBlocId().keySet())
                .containsExactly("tritachyon", "hegemony");
        }

        @Test
        void buildIndexAnswersAnIndexLaterRecordingCannotMove() {
            // The handover the whole split rests on: the walk goes on holding the builder, and the
            // index it already gave out is read back per hover until the next rebuild replaces it.
            var builder = new BlocPresenceIndexBuilder();

            builder.recordPresence("hegemony", "system-a");

            var index = builder.buildIndex();

            builder.recordPresence("hegemony", "system-b");
            builder.recordPresence("tritachyon", "system-c");

            assertThat(index.readPresentSystemIds("hegemony"))
                .containsExactly("system-a");

            assertThat(index.readPresentSystemIds("tritachyon"))
                .isEmpty();
        }
    }
}
