package kmu.maplayers.ownermap.picker;

import kmlib.starsector.ui.widgets.lists.ListSortMode;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * What every suite over the owner map's owner sorting needs: an owner built from an ID, a label and a
 * payload, and the IDs a ranking puts a handful of them in. Neither depends on how the ranking was
 * arrived at - off a declared mode, or straight off the assembly behind one - so both are stated here
 * once rather than in each suite either side of that seam.
 *
 * <p>IDs come back rather than owners, so an assertion reads the arrangement without the payload and
 * the crest getting in the way.
 */
public final class OwnerSortFixtures {

    /**
     * The tone a picker offers a mode with no colour opinion of its own. Arbitrary and distinct from
     * any engine shade, since what a value case reads off it is only that the offered tone came back
     * on the run rather than one the mode chose.
     */
    public static final Color ROW_COLOUR = Color.ORANGE;

    private OwnerSortFixtures() {
    }

    /**
     * A listed owner carrying whichever metrics the calling suite ranks by.
     *
     * @param <S>   the metrics record the owner carries
     * @param id    the owner's save-stable ID, which is what a ranking assertion reads back
     * @param name  the owner's label, or null for an owner no name resolved for
     * @param stats the owner's metrics under the calling suite's vocabulary
     * @return the owner as a picker row
     */
    public static <S extends OwnerMetrics> RankedOwner<S> buildOwner(String id, String name, S stats) {

        return new RankedOwner<>(new SelectableOwner(id, name, null), stats);
    }

    /**
     * A listed owner over {@link HazardRating}, the payload no view declares. For the suites saying what
     * the sorting does for any vocabulary rather than what one layer's numbers happen to produce.
     *
     * @param id         the owner's save-stable ID
     * @param name       the owner's label, or null for an owner no name resolved for
     * @param severity   the stand-in payload's leading number
     * @param volatility the stand-in payload's second number, which a tie falls to
     * @return the owner as a picker row
     */
    public static RankedOwner<HazardRating> buildStandInOwner(
            String id,
            String name,
            int severity,
            int volatility) {

        return buildOwner(id, name, new HazardRating(severity, volatility));
    }

    /**
     * The IDs the owners land in under {@code order}.
     *
     * @param <S>    the metrics record the owners carry
     * @param order  the ranking to lay them out by
     * @param owners the owners to rank
     * @return their IDs in the order they ranked
     */
    @SafeVarargs
    public static <S extends OwnerMetrics> List<String> listIdsInOrder(
            Comparator<RankedOwner<S>> order,
            RankedOwner<S>... owners) {

        var sorted = new ArrayList<>(List.of(owners));

        sorted.sort(order);

        var ids = new ArrayList<String>(sorted.size());

        for (var owner : sorted) {
            ids.add(owner.itemId());
        }

        return ids;
    }

    /**
     * The IDs the owners land in under {@code mode}, ranked in the direction that mode naturally runs
     * in - so a case reads the arrangement a player meets on first switching to the mode without
     * spelling the direction out. A flipped direction goes through {@link #listIdsInOrder} instead.
     *
     * @param <S>    the metrics record the owners carry
     * @param mode   the mode to rank them under
     * @param owners the owners to rank
     * @return their IDs in the order they ranked
     */
    @SafeVarargs
    public static <S extends OwnerMetrics> List<String> listIdsInModeOrder(
            ListSortMode<RankedOwner<S>> mode,
            RankedOwner<S>... owners) {

        return listIdsInOrder(mode.comparator(mode.defaultDirection()), owners);
    }
}
