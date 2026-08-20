package kmu.maplayers.politicalmap.base.dominance;

import kmlib.starsector.colonies.Colony;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What one star system's habitation amounts to: the colonies somebody lives on that the player may
 * be shown, and the blocs those very colonies fold into.
 *
 * <p>One value rather than two reads, because two surfaces answer off it and must not be able to
 * part: the cell classification asks the colonies' emptiness, and the filter's spotlight asks the
 * bloc set for who to spare. Folding the blocs from the same colonies makes presence a partition of
 * the set emptiness is asked of, so a cell cannot be drawn as empty space while a bloc's fill is
 * kept over it.
 *
 * @param colonies the system's known colonies somebody lives on, in the projection's own order
 * @param blocIds  those colonies' owners folded into the blocs the active view paints them as,
 *                 each named once; an owner the grouping can name no bloc for is left out, as it
 *                 is from every other fold the grouping makes
 */
public record SystemHabitation(
    List<Colony> colonies,
    Set<String> blocIds) {

    /**
     * Takes immutable copies of both sides, and reads either absent one as empty, so a value handed
     * to two readers cannot change under either. The bloc copy keeps insertion order rather than
     * discarding the ordering the fold arrived in.
     */
    public SystemHabitation {
        colonies = colonies == null ? List.of() : List.copyOf(colonies);
        blocIds = blocIds == null
            ? Set.of()
            : Collections.unmodifiableSet(new LinkedHashSet<>(blocIds));
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
