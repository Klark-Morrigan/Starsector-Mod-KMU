package kmu.maplayers.base.visibility.colonies;

import java.util.Objects;
import java.util.Optional;

/**
 * One observation of a colony: where it was seen standing, and when it was last seen there.
 *
 * <p>The two halves travel as one value because they are one event. Kept apart - a place register
 * beside a time register - they could disagree about which observation the time belongs to, and a
 * reader stating "last seen here, four cycles ago" would be joining two facts nothing had ever
 * joined.
 *
 * <p>The place is what a visibility rule spends: a colony is unseen again the moment it stands
 * somewhere other than where it was observed. The time is what a reader spends, and nothing else -
 * a rule consulting a clock would take a colony off the map for going stale, which is the one
 * behaviour a visibility rule must never have.
 *
 * <p>An observation may carry no time at all. That is what a value recorded before observations
 * were timed reads as, and it heals at the next observation of the same colony - so a reader states
 * the place it was seen and says nothing about when, rather than inventing a date for it.
 *
 * @param locationId        the ID of the location the colony was observed standing in
 * @param observedTimestamp when the colony was last observed there, on the campaign clock's own
 *                          scale, or empty for an observation made before they were timed
 */
public record ColonyObservation(
    String locationId,
    Optional<Long> observedTimestamp) {

    /** Reads an unstated time as no time, so an undated observation cannot fail late on it. */
    public ColonyObservation {
        Objects.requireNonNull(locationId, "An observation needs somewhere it was made.");
        observedTimestamp = observedTimestamp == null ? Optional.empty() : observedTimestamp;
    }

    /**
     * Builds an observation made at a stated moment, which is every observation recorded now.
     *
     * @param locationId        where the colony was observed standing
     * @param observedTimestamp when it was observed, on the campaign clock's own scale
     * @return the dated observation
     */
    public static ColonyObservation createObservationAt(
            String locationId,
            long observedTimestamp) {

        return new ColonyObservation(locationId, Optional.of(observedTimestamp));
    }

    /**
     * Builds an observation of a place alone - what a register value recorded before observations
     * were timed reads as, and what a sector with no clock to read can record.
     *
     * @param locationId where the colony was observed standing
     * @return the observation, carrying no time
     */
    public static ColonyObservation createUndatedObservation(String locationId) {
        return new ColonyObservation(locationId, Optional.empty());
    }
}
