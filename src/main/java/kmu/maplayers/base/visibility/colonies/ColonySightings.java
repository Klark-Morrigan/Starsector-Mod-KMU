package kmu.maplayers.base.visibility.colonies;

/**
 * What has been observed of each colony - where it was seen standing and when - asked by the
 * colony's own id.
 *
 * <p>Says nothing about who did the observing. The player standing in a place and the place's own
 * inhabitants are both observations, and one register holds them alike - which is what keeps a
 * colony known once the neighbours who could see it are gone.
 *
 * <p>Vanilla records only that a system has been entered, which answers a different question: a
 * colony founded after the visit, or moved in since, was never seen there however many times the
 * player has crossed the place. So what is kept is the place a colony was seen standing in, and a
 * reader compares that against where it stands now.
 *
 * <p>No visibility rule reads the time, and that is deliberate. What is kept is "this colony was
 * seen here", and the moment showing it turned on how long ago that was there would be a recency
 * window to fall out of: a colony that appears after the player has gone would be shown for the
 * rest of the day and then taken away again, which is the one behaviour a visibility rule must
 * never have. The time is carried for a reader that states how old the news is, and for nothing
 * else.
 *
 * <p>Stated as a port rather than as a value because what answers it is save state, while the rule
 * read over it is not. A caller holding no register of its own reads {@link #NONE}, under which
 * nothing has ever been seen - the conservative answer, since a register that invented sightings
 * would show colonies nobody has met.
 */
@FunctionalInterface
public interface ColonySightings {

    /**
     * Nothing has ever been seen anywhere. What an unstated register reads as, since an absent
     * record of what was observed is not a reason to suppose anything was.
     */
    ColonySightings NONE = colonyId -> null;

    /**
     * What was last observed of this colony.
     *
     * @param colonyId the colony's market ID, as {@code MarketAPI#getId} reports it; an ID the
     *                 register has never held reads as never seen
     * @return where the colony was last observed standing and when, or null when nobody has seen
     *         it anywhere
     */
    ColonyObservation readObservation(String colonyId);
}
