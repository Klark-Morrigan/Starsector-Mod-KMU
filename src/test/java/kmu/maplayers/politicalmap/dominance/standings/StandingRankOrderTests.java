package kmu.maplayers.politicalmap.dominance.standings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the one order the box's standings and the band beneath a cell both rank by.
 */
final class StandingRankOrderTests {

    @Nested
    class CreateRankOrder {

        @Test
        void ranksTheHeaviestFirst() {

            var ranked = sortByRankOrder(List.of(
                new WeighedThing("hegemony", 400),
                new WeighedThing("tritachyon", 1_200)));

            assertThat(ranked)
                .extracting(WeighedThing::id)
                .containsExactly("tritachyon", "hegemony");
        }

        @Test
        void breaksATieOnTheLowestId() {
            // The ID settles a tie so the order never depends on the order the weights arrived in.
            var ranked = sortByRankOrder(List.of(
                new WeighedThing("tritachyon", 800),
                new WeighedThing("hegemony", 800)));

            assertThat(ranked)
                .extracting(WeighedThing::id)
                .containsExactly("hegemony", "tritachyon");
        }
    }

    private static List<WeighedThing> sortByRankOrder(List<WeighedThing> things) {

        return things
            .stream()
            .sorted(StandingRankOrder.createRankOrder(WeighedThing::weight, WeighedThing::id))
            .toList();
    }

    // Anything ranked: an ID and a weight, which is all the order reads.
    private record WeighedThing(String id, int weight) {
    }
}
