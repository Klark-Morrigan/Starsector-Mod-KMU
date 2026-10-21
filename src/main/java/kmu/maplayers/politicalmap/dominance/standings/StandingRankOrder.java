package kmu.maplayers.politicalmap.dominance.standings;

import java.util.Comparator;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * The one order everything in a system ranks by on the domination layer: descending weight, a tie
 * falling to the lowest ID.
 *
 * <p>Stated once because the box's standings and the band beneath the cell both rank the same blocs.
 * Two copies of the rule could rank them apart, and the band would then disagree with the box about
 * who stands second in a system.
 *
 * <p>The ID settles a tie so the order is total and never depends on the economy walk order the
 * weights arrive in.
 */
public final class StandingRankOrder {

    private StandingRankOrder() {
    }

    /**
     * The order over one kind of ranked thing.
     *
     * @param readWeight what a ranked thing weighs
     * @param readId     what names it, which settles a tie
     * @param <T>        what is ranked
     * @return the order, heaviest first and the lowest ID first among equals
     */
    public static <T> Comparator<T> createRankOrder(
            ToIntFunction<T> readWeight,
            Function<T, String> readId) {

        return Comparator
            .comparingInt(readWeight)
            .reversed()
            .thenComparing(readId);
    }
}
