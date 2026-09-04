package kmu.maplayers.base.visibility.colonies;

import kmu.maplayers.base.visibility.observations.ObservationCodec;
import kmu.util.KmuValues;

/**
 * What one colony sighting says, written as the register holds it and read back out of it: the
 * moment the observation was made, then the place the colony was seen standing in.
 *
 * <p><strong>The stored form is save state.</strong> Every entry in every existing save is spelt
 * this way, and nothing in a loaded game could tell an entry it can no longer read from a colony
 * nobody has ever met - so the separator and the field order are fixed once and for good.
 *
 * <p>The time leads, so whatever follows the first separator is the location id verbatim -
 * punctuation and all. That is the fixed-fields-first convention the register underneath states,
 * and the place is the family's free-form field: an id the game composed, which may be spelt with
 * anything at all.
 *
 * <p>An entry with no separator, or one whose leading field is not a time, is a place alone. That
 * is what every value written before observations were timed looks like, and reading it as an
 * undated observation is both the honest reading and the one healed at the next observation of the
 * same colony.
 *
 * <p>Package-private: what a colony sighting means is this family's own, and the register beside it
 * is the only thing that ever spells one.
 */
final class ColonyObservationCodec implements ObservationCodec<ColonyObservation> {

    // What parts an observation's time from the place it names, in the one stored entry that
    // carries both. Stable once shipped, being part of how every save already reads.
    private static final String OBSERVATION_SEPARATOR = "@";

    @Override
    public ColonyObservation decodeObservation(String storedObservation) {

        if (!KmuValues.hasText(storedObservation)) {
            // An entry naming nowhere is not a weaker observation but no observation: the place is
            // the half every visibility rule spends, and there is nothing here to spend.
            return null;
        }
        var separatorIndex = storedObservation.indexOf(OBSERVATION_SEPARATOR);

        if (separatorIndex < 0) {
            return ColonyObservation.createUndatedObservation(storedObservation);
        }
        try {
            return ColonyObservation.createObservationAt(
                storedObservation.substring(separatorIndex + OBSERVATION_SEPARATOR.length()),
                Long.parseLong(storedObservation.substring(0, separatorIndex)));

        } catch (NumberFormatException notATimestamp) {
            // A location id that happens to hold the separator, which only an entry written before
            // observations were timed can be. The whole of it is the place.
            return ColonyObservation.createUndatedObservation(storedObservation);
        }
    }

    @Override
    public String encodeObservation(ColonyObservation observation) {

        var locationId = observation.locationId();

        return observation
            .observedTimestamp()
            .map(observedTimestamp -> observedTimestamp + OBSERVATION_SEPARATOR + locationId)
            .orElse(locationId);
    }
}
