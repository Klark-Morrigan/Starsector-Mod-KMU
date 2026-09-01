package kmu.maplayers.politicalmap.base;

import kmlib.starsector.ui.widgets.lists.ListSortMode;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * What every suite over the political map's bloc sorting needs: a bloc built from an id, a label and a
 * payload, and the ids a ranking puts a handful of them in. Neither depends on how the ranking was
 * arrived at - off a declared mode, or straight off the assembly behind one - so both are stated here
 * once rather than in each suite either side of that seam.
 *
 * <p>Ids come back rather than blocs, so an assertion reads the arrangement without the payload and
 * the crest getting in the way.
 */
public final class BlocSortFixtures {

    /**
     * The tone a picker offers a mode with no colour opinion of its own. Arbitrary and distinct from
     * any engine shade, since what a value case reads off it is only that the offered tone came back
     * on the run rather than one the mode chose.
     */
    public static final Color ROW_COLOUR = Color.ORANGE;

    private BlocSortFixtures() {
    }

    /**
     * A listed bloc carrying whichever metrics the calling suite ranks by.
     *
     * @param <S>   the metrics record the bloc carries
     * @param id    the bloc's save-stable id, which is what a ranking assertion reads back
     * @param name  the bloc's label, or null for a bloc no name resolved for
     * @param stats the bloc's metrics under the calling suite's vocabulary
     * @return the bloc as a picker row
     */
    public static <S extends BlocMetrics> RankedBloc<S> buildBloc(String id, String name, S stats) {
        return new RankedBloc<>(new SelectableBloc(id, name, null), stats);
    }

    /**
     * A listed bloc over {@link HazardRating}, the payload no view declares. For the suites saying what
     * the sorting does for any vocabulary rather than what one layer's numbers happen to produce.
     *
     * @param id         the bloc's save-stable id
     * @param name       the bloc's label, or null for a bloc no name resolved for
     * @param severity   the stand-in payload's leading number
     * @param volatility the stand-in payload's second number, which a tie falls to
     * @return the bloc as a picker row
     */
    public static RankedBloc<HazardRating> buildStandInBloc(
            String id,
            String name,
            int severity,
            int volatility) {

        return buildBloc(id, name, new HazardRating(severity, volatility));
    }

    /**
     * The ids the blocs land in under {@code order}.
     *
     * @param <S>   the metrics record the blocs carry
     * @param order the ranking to lay them out by
     * @param blocs the blocs to rank
     * @return their ids in the order they ranked
     */
    @SafeVarargs
    public static <S extends BlocMetrics> List<String> listIdsInOrder(
            Comparator<RankedBloc<S>> order,
            RankedBloc<S>... blocs) {

        var sorted = new ArrayList<>(List.of(blocs));
        sorted.sort(order);

        var ids = new ArrayList<String>(sorted.size());
        for (var bloc : sorted) {
            ids.add(bloc.itemId());
        }
        return ids;
    }

    /**
     * The ids the blocs land in under {@code mode}, ranked in the direction that mode naturally runs
     * in - so a case reads the arrangement a player meets on first switching to the mode without
     * spelling the direction out. A flipped direction goes through {@link #listIdsInOrder} instead.
     *
     * @param <S>   the metrics record the blocs carry
     * @param mode  the mode to rank them under
     * @param blocs the blocs to rank
     * @return their ids in the order they ranked
     */
    @SafeVarargs
    public static <S extends BlocMetrics> List<String> listIdsInModeOrder(
            ListSortMode<RankedBloc<S>> mode,
            RankedBloc<S>... blocs) {

        return listIdsInOrder(mode.comparator(mode.defaultDirection()), blocs);
    }
}
