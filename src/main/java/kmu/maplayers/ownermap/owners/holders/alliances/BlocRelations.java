package kmu.maplayers.ownermap.owners.holders.alliances;

import kmlib.starsector.factions.relation.FactionSetFriendliness;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * The two ways a bloc may stand with whoever holds a hovered system: grouped with it, or merely on
 * good terms with it.
 *
 * <p>Sampled as one value at the moment a box reads its system, because both are live - a group
 * forms, a disposition slides past neutral - and a box that took them one block at a time could file
 * a group as an ally under one heading and as a rival under the next, out of a single hover.
 *
 * <p>Two values rather than one reading, because they are two axes: which of them places a bloc, and
 * in what order, is the asking box's business rather than this pair's. The pair travels whole to
 * every box that routes against it, so the two questions each box asks of it are asked here once.
 *
 * @param blocAffiliation        who stands together, which the holder's own allies are lifted out
 *                               by; it is read over the identity grouping wherever nothing groups
 *                               factions
 * @param factionSetFriendliness whether two blocs are on good terms, read over both memberships
 *                               whole
 */
public record BlocRelations(
    BlocAffiliation blocAffiliation,
    FactionSetFriendliness factionSetFriendliness) {

    public BlocRelations {

        Objects.requireNonNull(blocAffiliation, "blocAffiliation");
        Objects.requireNonNull(factionSetFriendliness, "factionSetFriendliness");
    }

    /**
     * The contestants on one side of a contest, placed against its holder by the alliance axis.
     *
     * @param side          the side wanted
     * @param holderBlocId  the bloc holding the system; none leaves every contestant a rival
     * @param contestants   everyone present but the holder itself
     * @param resolveBlocId what names a contestant's bloc
     * @param <T>           whatever a surface lists a contestant as
     * @return the contestants that side takes, in the order they arrived
     * @see ContestSides#selectSide
     */
    public <T> List<T> selectSide(
            ContestSide side,
            String holderBlocId,
            List<T> contestants,
            Function<T, String> resolveBlocId) {

        return new ContestSides(holderBlocId, blocAffiliation)
            .selectSide(side, contestants, resolveBlocId);
    }

    /**
     * Whether one faction is on good terms with the holder, asked as the bloc of one it would be
     * listed as.
     *
     * <p>Through the same rule a whole bloc is judged by rather than the faction-level answer
     * underneath it, so a lone faction is sorted by exactly the test its bloc would be - the empty
     * holder included, which nobody is friendly with.
     *
     * @param factionId              the faction being sorted
     * @param holderMemberFactionIds the holder's whole membership; empty where nobody holds the
     *                               system
     * @return true when the faction is on good terms with the holder
     */
    public boolean isFactionFriendlyWith(String factionId, Set<String> holderMemberFactionIds) {
        return factionSetFriendliness.areSetsFriendly(Set.of(factionId), holderMemberFactionIds);
    }
}
