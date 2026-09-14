package kmu.maplayers.politicalmap.base.dominance;

import kmlib.starsector.markets.colonies.Colony;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What one star system's habitation amounts to: the colonies somebody lives on that the player may
 * be shown, the blocs those very colonies fold into, and how much colony each bloc lives on there.
 *
 * <p>One value rather than three reads, because three surfaces answer off it and must not be able to
 * part: the cell classification asks the colonies' emptiness, the filter's spotlight asks the bloc
 * set for who to spare, and the picker asks who to list and how large to call them. Folding the
 * blocs and their sizes from the same colonies makes presence a partition of the set emptiness is
 * asked of, so a cell cannot be drawn as empty space while a bloc's fill is kept over it, nor a bloc
 * listed at a size taken from colonies it was not counted present for.
 *
 * @param colonies           the system's known colonies somebody lives on, in the projection's own
 *                           order
 * @param colonySizeByBlocId those colonies' summed raw sizes, keyed by the bloc the active view
 *                           paints their owners as, in the fold's own order; an owner the grouping
 *                           can name no bloc for is left out, as it is from every other fold the
 *                           grouping makes
 */
public record SystemHabitation(
    List<Colony> colonies,
    Map<String, Integer> colonySizeByBlocId) {

    /**
     * Takes immutable copies of both sides, and reads either absent one as empty, so a value handed
     * to three readers cannot change under any of them. The bloc copy keeps insertion order rather
     * than discarding the ordering the fold arrived in.
     */
    public SystemHabitation {
        colonies = colonies == null ? List.of() : List.copyOf(colonies);
        colonySizeByBlocId = colonySizeByBlocId == null
            ? Map.of()
            : Collections.unmodifiableMap(new LinkedHashMap<>(colonySizeByBlocId));
    }

    /**
     * The blocs living in the system, each named once - what a reader deciding per bloc asks, with
     * no interest in how much colony any of them holds.
     *
     * <p>The size fold's own keys rather than a set beside it, so "which blocs are here" and "what
     * each of them lives on" cannot report different blocs.
     *
     * @return the blocs present, in the fold's own order
     */
    public Set<String> blocIds() {
        return colonySizeByBlocId.keySet();
    }

    /**
     * Whether anybody the player knows of lives in the system - the emptiness the cell
     * classification turns on, asked of this value rather than of a read of its own.
     *
     * @return true when the system holds at least one colony somebody lives on
     */
    public boolean hasInhabitingColony() {
        return !colonies.isEmpty();
    }
}
