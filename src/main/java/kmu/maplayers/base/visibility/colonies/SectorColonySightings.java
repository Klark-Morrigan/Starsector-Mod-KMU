package kmu.maplayers.base.visibility.colonies;

import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.markets.colonies.Colonies;
import kmlib.starsector.markets.colonies.Colony;
import kmlib.starsector.markets.colonies.SystemColonies;

import kmu.maplayers.base.visibility.observations.ObservationStore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The sighting register kept in the sector's own memory: reading it, adding to it wherever an
 * observation is made, and shedding what it no longer describes.
 *
 * <p>Two routes write it, because both are observations and knowledge does not evaporate when the
 * informant dies. {@link #recordSightingsIn} takes what an observer standing in a place can see,
 * and is driven by the player's own journeys - a fact that only ever changes when they move, so it
 * costs nothing between moves. {@link #recordSightingsByInhabitants} takes what a place's own
 * population can see, and has to be swept for, there being no event to hang it on when a colony
 * arrives among witnesses - so it is written place by place over sets and a knowledge the sweeping
 * caller has already opened, rather than sweeping the sector a second time from in here or opening
 * a reading of it per place.
 *
 * <p>That sweep is driven by the map substrate's own poll rather than by any one layer's, the
 * register being shared by every map family that keeps observations: accrual that followed one
 * layer would have its gaps decided by whichever map the player last chose to look at. The gate
 * keeps its live reading of a place regardless, precisely so that a sector whose poll never ran
 * still shows what the player can plainly see.
 *
 * <p>Only the shapes an observation decides are recorded at all: those a {@link RevelationGate}
 * holds back until somebody has seen them, and those a report from the inhabitants can find because
 * somebody has. Nothing ever asks the register about an open colony the economy lists, so an entry
 * for one would answer nothing while costing an entry per colony in the sector.
 *
 * <p>Only star systems are recorded. A colony standing anywhere else reads sighted whatever the
 * register says - there is no system to have been in - so recording out there would buy nothing,
 * and hyperspace holds by far the largest entity list in the sector to walk for it.
 *
 * <p>The bytes and the load lifecycle underneath are an {@link ObservationStore}'s, which every map
 * family that keeps observations shares, and which colonies the sector still holds is
 * {@link PresentColonies}'. What one entry means stays here, in {@link ColonyObservationCodec}:
 * everything above this class is handed a {@link ColonyObservation} carrying both the place and the
 * moment, so a reader cannot pair one entry's place with another's time - and the two cannot be
 * stored apart and drift.
 *
 * <p>Final class with a private constructor: pure-function utility, no instance state, and
 * null-defensive like the rest of the map framework.
 */
public final class SectorColonySightings {

    // The save-serialised identity of the register. Stable once shipped: renaming it silently
    // drops every sighting in every existing save, and with them every colony a gate holds back.
    private static final String SIGHTINGS_KEY = "$kmu_colony_sightings";

    // The register itself. Held once rather than opened per call: it carries the key and the codec
    // and nothing of any one sector, so a store per sector would only be the same two fields again.
    private static final ObservationStore<ColonyObservation> SIGHTINGS_REGISTER =
        new ObservationStore<>(SIGHTINGS_KEY, new ColonyObservationCodec());

    private SectorColonySightings() {
        // utility class, no instances.
    }

    /**
     * Opens the sector's sighting register for reading.
     *
     * @param sector the sector whose memory holds the register; null - or one holding no
     *               register yet - reads as nothing having been seen anywhere
     * @return what was last observed of each colony, by colony id; never null
     */
    public static ColonySightings readSightings(SectorAPI sector) {

        return SIGHTINGS_REGISTER
            .readObservations(sector)::readObservationOrNull;
    }

    /**
     * Records every gated colony standing in {@code location} as observed there - what somebody
     * arriving in a place sees, being here amounting to seeing what is here.
     *
     * <p>Only the shapes a gate holds back. Nothing ever asks the register about an open colony
     * the economy lists, that being permanently in the sector's own sight, so recording one would
     * cost an entry per colony in the sector and answer nothing.
     *
     * <p>Recorded off the resolved colony set rather than off the raw markets, because gated is a
     * fact about a colony's kind and concealment, and kind is only well defined once a place has
     * settled which of the markets on it is the colony. A market superseded on its own entity
     * therefore records nothing of its own - it is not the colony standing here - and gains its
     * entry when it wins the place and is next observed.
     *
     * @param sector   the sector whose economy is read and whose memory holds the register; null
     *                 is a no-op
     * @param location where the observer is; anything that is not a star system is a no-op, a
     *                 colony outside one reading sighted whatever the register holds
     */
    public static void recordSightingsIn(SectorAPI sector, LocationAPI location) {

        if (!(location instanceof StarSystemAPI system)) {
            return;
        }
        recordObservedColonies(
            sector,
            system.getId(),
            ColonyKnowledge
                .observingUnderTheFog()
                .readGatedColonies(SystemColonies.readColoniesIn(sector, system)));
    }

    /**
     * Records every colony in one system that an observation decides and the system's own
     * inhabitants can see, as observed where it stands.
     *
     * <p>The other route by which an observation is made, and the reason it is written down rather
     * than merely tested where the rule is applied. A colony standing among people who are not its
     * owner is common knowledge there; leaving that as a live test alone would take the colony off
     * the map the moment its last neighbour decivilised, for a player who had known it was there
     * for years.
     *
     * <p>Wider by one shape than what an arriving observer records: the collapsed world, which
     * only this route can lose - {@link ColonyKnowledge#readGatedColonies} says why.
     *
     * <p>Written one place at a time, over a colony set the caller already holds, rather than swept
     * for here. This route has to be noticed by a walk - nothing in the engine announces a colony
     * arriving among witnesses - and the caller driving that walk holds one reading of the sector
     * for the whole of it. Sweeping again here would open a second reading to reach sets already in
     * hand.
     *
     * <p>Nothing depends on this having run: the rule keeps its own live reading of the place for
     * exactly that reason, so a caller that never writes still shows what stands among witnesses
     * now. What the write buys is that the reading survives the witnesses.
     *
     * <p>The knowledge is handed in for the same reason the colony set is. A caller sweeping the
     * sector holds one for the whole sweep - which folds the alliances that decide who would speak
     * once, rather than once per place, and lets one classification of each colony serve the whole
     * of it.
     *
     * @param sector    the sector whose memory holds the register; null is a no-op
     * @param system    where the colonies stand, and what a sighting names; null is a no-op. A star
     *                  system alone, a colony outside one reading sighted whatever the register
     *                  holds
     * @param colonies  the colonies selected for that system, as the caller's own walk read them;
     *                  null is a no-op
     * @param observing what an observer standing here reads under - the fog alone, whatever rule
     *                  the caller shows colonies under elsewhere; null opens one for this place,
     *                  which is right for a caller with a single place to record and wasteful for
     *                  one sweeping the sector
     */
    public static void recordSightingsByInhabitants(
            SectorAPI sector,
            StarSystemAPI system,
            Colonies colonies,
            ColonyKnowledge observing) {

        if (system == null || colonies == null) {
            return;
        }
        var knowledge = observing == null
            ? ColonyKnowledge.observingUnderTheFog()
            : observing;

        recordObservedColonies(
            sector,
            system.getId(),
            knowledge.readColoniesObservedByInhabitants(colonies));
    }

    /**
     * Drops every sighting whose colony is no longer anywhere in the sector.
     *
     * <p>Run once against a loaded save. A sighting outliving the colony it was about would go on
     * answering for whatever next took the id, which is an observation nobody ever made.
     *
     * @param sector the sector to reconcile the register against; null is a no-op
     */
    public static void dropSightingsOfAbsentColonies(SectorAPI sector) {

        SIGHTINGS_REGISTER.dropObservationsOfAbsentSubjects(
            sector,
            PresentColonies::readColonyIds);
    }

    /**
     * Brings the register into step with a loaded save: sheds the sightings whose colonies have
     * gone, then records what the player is currently standing among.
     *
     * <p>The second half is what a load owes the register. A save opened in a system produces no
     * location change until the player leaves it, so without this the place they are looking at is
     * the one place the register has nothing to say about - and on the first load of a save written
     * before any sighting was ever made, that is the only place it could learn anything at all.
     *
     * <p>Nothing here seeds the register from vanilla's memory of which systems have been entered.
     * That fact says the player was once here, not that they saw what is here now, so importing it
     * would put back the very reading the register exists to replace.
     *
     * @param sector the sector to reconcile; null is a no-op
     */
    public static void reconcileWithLoadedSave(SectorAPI sector) {

        SIGHTINGS_REGISTER.reconcileWithLoadedSave(
            sector,
            PresentColonies::readColonyIds,
            loadedSector -> recordSightingsIn(loadedSector, loadedSector.getCurrentLocation()));
    }

    // Stamps a set of colonies as observed in one place, at this moment.
    //
    // Nothing to stamp is weighed here rather than by the register, so a system holding nothing an
    // observation decides - which is most of them - costs no reading of the clock on the way to
    // recording nothing.
    //
    // The clock is read once for the whole set rather than per colony, so every colony observed in
    // one moment is stamped with that one moment - two of them a tick apart would say the observer
    // saw one before the other.
    private static void recordObservedColonies(
            SectorAPI sector,
            String locationId,
            List<Colony> observedColonies) {

        if (locationId == null || observedColonies.isEmpty()) {
            return;
        }
        SIGHTINGS_REGISTER.recordObservations(
            sector,
            buildObservationsByColonyId(locationId, readClockTimestamp(sector), observedColonies));
    }

    // One observation of a place, filed against every colony seen standing in it. The observation
    // is built once and shared, being the same event for all of them.
    //
    // A colony the game names with nothing is left to the register, which files entries by the very
    // id a read would ask for and so refuses one that could never be reached.
    private static Map<String, ColonyObservation> buildObservationsByColonyId(
            String locationId,
            Long observedTimestamp,
            List<Colony> observedColonies) {

        var observation = observedTimestamp == null
            ? ColonyObservation.createUndatedObservation(locationId)
            : ColonyObservation.createObservationAt(locationId, observedTimestamp);

        var observationsByColonyId = new HashMap<String, ColonyObservation>();

        for (var colony : observedColonies) {
            observationsByColonyId.put(colony.market().getId(), observation);
        }
        return observationsByColonyId;
    }

    // When the observation is being made, or null where there is no clock to ask - which is no
    // reason to lose the observation itself, the place being the half every visibility rule
    // spends. Such an entry reads as undated and is dated at the next observation.
    private static Long readClockTimestamp(SectorAPI sector) {

        var clock = sector == null ? null : sector.getClock();

        return clock == null ? null : clock.getTimestamp();
    }

}
