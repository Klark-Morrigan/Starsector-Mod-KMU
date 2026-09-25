package kmu.maplayers.politicalmap.dominance.standings;

/**
 * One faction's ranked place in a single hovered system where the pass weighed nothing for it:
 * every colony it holds there is one the economy does not list, so it is present without ever
 * having been weighed.
 *
 * <p>Every term of a dominance weight is economy-fed - industries, conditions, computed stability -
 * so an unregistered colony has nothing for the arithmetic to read. A faction holding nothing else
 * in the system raises no footprint at all, and this is what it gets instead of no standing: the
 * alternative is a box naming nobody while the map plainly draws a station in that faction's
 * colours and the band beneath the cell draws its run.
 *
 * <p>The score is a named nought rather than anything derived from the colonies behind it. Nothing
 * was worked out for them, so there is no sum to report, and printing what they would have been
 * worth would rank a faction the pass never weighed against ones it did. It also settles the
 * mechanic question outright: holding is resolved off footprints, which an unregistered colony
 * raises none of, so such a standing can neither take a system, tie for one, nor displace whoever
 * holds it.
 *
 * @param factionId the faction present in the hovered system through unweighed colonies alone
 */
public record PresenceOnlyFactionStanding(
    String factionId) implements FactionStanding {

    // What the pass weighed this faction at. Nought rather than absent, because the faction is on
    // the list and the number states what its presence came to - and because holding is resolved
    // apart from these standings, which is what makes that nought unable to move a fill.
    private static final int NO_WEIGHT = 0;

    /**
     * {@inheritDoc}
     *
     * <p>Nought however large the colonies behind it: the pass weighed none of them, so the size of
     * what is present says nothing about the contest.
     */
    @Override
    public int score() {
        return NO_WEIGHT;
    }
}
