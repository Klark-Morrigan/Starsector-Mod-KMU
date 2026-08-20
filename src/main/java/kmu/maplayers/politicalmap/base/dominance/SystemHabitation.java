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
 * <p>One value rather than two reads, because two surfaces answer off it and they must not be able
 * to part. A cell is classified as empty backdrop by the emptiness of the colonies, and a
 * spotlighted bloc is spared that cell's recede by its presence in the blocs - so a cell drawn as
 * empty space while the filter keeps a bloc's fill over it is the disagreement the pairing has to
 * make unstateable. Folding the blocs from the same colonies the emptiness is asked of makes
 * presence a partition of that set: a non-empty bloc set implies a non-empty colony set, whatever
 * either reader goes on to do.
 *
 * <p>Habitation rather than the wider listing of what the player may be told about, which is what a
 * hover box names its factions out of. The two part over the derelict: a hulk somebody has seen
 * belongs in a listing and settles nothing, so a system holding one and nothing else is empty space
 * with a wreck in it - and no bloc is living there for the spotlight to spare.
 *
 * @param colonies the system's known colonies somebody lives on, in the projection's own order
 * @param blocIds  those colonies' owners folded into the blocs the active view paints them as,
 *                 each named once however many colonies it holds; an owner the grouping can name
 *                 no bloc for is left out, as it is from every other fold the grouping makes
 */
public record SystemHabitation(
    List<Colony> colonies,
    Set<String> blocIds) {

    /**
     * Takes immutable copies of both sides, and reads either absent one as empty, so a value
     * handed around a render pass cannot change under its readers.
     *
     * <p>The blocs keep the order they were folded in rather than being copied into an unordered
     * set, so a reader listing them reports the walk's own ordering rather than a hash order that
     * would shuffle between runs.
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
