package kmu.maplayers.base.visibility;

import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import kmlib.starsector.colonies.Colonies;
import kmlib.starsector.colonies.Colony;
import kmlib.starsector.colonies.SystemColonies;
import kmlib.starsector.markets.LocationMarkets;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The sighting register kept in the sector's own memory: reading it, adding to it wherever an
 * observation is made, and shedding what it no longer describes.
 *
 * <p>Sector memory rather than anything of vanilla's, because vanilla keeps no such fact. It
 * serialises into the save alongside everything else there, so an observation survives reload the
 * way the visit that produced it does.
 *
 * <p>Two routes write it, because both are observations and knowledge does not evaporate when the
 * informant dies. {@link #recordSightingsIn} takes what an observer standing in a place can see,
 * and is driven by the player's own journeys - a fact that only ever changes when they move, so it
 * costs nothing between moves. {@link #recordSightingsByInhabitants} takes what a place's own
 * population can see, and has to be swept for, there being no event to hang it on when a colony
 * arrives among witnesses - so it is written place by place over sets the sweeping caller has
 * already read, rather than sweeping the sector a second time from in here.
 *
 * <p>That sweep rides the political map's staleness poll, which already walks every system once a
 * tick and holds each system's colonies as it goes. A cadence of its own would be a second reading
 * of the whole sector to reach sets a walk already in progress is holding - and the gate keeps its
 * live reading of a place precisely so that a sector whose poll never ran still shows what the
 * player can plainly see.
 *
 * <p>Only the shapes a {@link RevelationGate} holds back are recorded at all. Nothing ever asks
 * the register about an open colony the economy lists, so an entry for one would answer nothing
 * while costing an entry per colony in the sector.
 *
 * <p>Only star systems are recorded. A colony standing anywhere else reads sighted whatever the
 * register says - there is no system to have been in - so recording out there would buy nothing,
 * and hyperspace holds by far the largest entity list in the sector to walk for it.
 *
 * <p>The stored form is this class's own. Everything above it is handed a {@link ColonyObservation}
 * carrying both the place and the moment, so a reader cannot pair one entry's place with another's
 * time - and the two cannot be stored apart and drift.
 *
 * <p>Final class with a private constructor: pure-function utility, no instance state, and
 * null-defensive like the rest of the map framework.
 */
public final class SectorColonySightings {

    // The save-serialised identity of the register. Stable once shipped: renaming it silently
    // drops every sighting in every existing save, and with them every colony a gate holds back.
    private static final String SIGHTINGS_KEY = "$kmu_colony_sightings";

    // What parts an observation's time from the place it names, in the one stored entry that
    // carries both.
    //
    // Text in a map of text rather than a value class of ours, because a class put into a save
    // bakes its own name into every save holding one: moving or renaming it later fails the load
    // rather than the read, and there is nowhere in a loaded game to repair that from.
    //
    // The time leads, so whatever follows the first separator is the location id verbatim -
    // punctuation and all. An entry with no separator, or one whose leading part is not a time,
    // is a place alone: that is what every value written before observations were timed looks
    // like, and it reads as an undated observation rather than as a corrupt one.
    private static final String OBSERVATION_SEPARATOR = "@";

    private SectorColonySightings() {
        // utility class, no instances.
    }

    /**
     * Opens the sector's sighting register for reading.
     *
     * <p>Handed out as a lookup over the stored map rather than as a copy of it. The register is
     * read once per pass, and a copy taken there would cost the whole sector's sightings to answer
     * about one place.
     *
     * @param sector the sector whose memory holds the register; null - or one holding no
     *               register yet - yields {@link ColonySightings#NONE}
     * @return what was last observed of each colony, by colony id; never null
     */
    public static ColonySightings readSightings(SectorAPI sector) {

        var storedSightings = readStoredSightings(sector);

        if (storedSightings == null) {
            return ColonySightings.NONE;
        }
        return colonyId -> decodeObservation(storedSightings.get(colonyId));
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
        putSightings(
            sector,
            system.getId(),
            ColonyKnowledge
                .observingUnderTheFog()
                .readGatedColonies(SystemColonies.readColoniesIn(sector, system)));
    }

    /**
     * Records every gated colony in one system that the system's own inhabitants can see as
     * observed where it stands.
     *
     * <p>The other route by which an observation is made, and the reason it is written down rather
     * than merely tested where the rule is applied. A colony standing among people who are not its
     * owner is common knowledge there; leaving that as a live test alone would take the colony off
     * the map the moment its last neighbour decivilised, for a player who had known it was there
     * for years.
     *
     * <p>Written one place at a time, over a colony set the caller already holds, rather than swept
     * for here. This route has to be noticed by a walk - nothing in the engine announces a colony
     * arriving among witnesses - and every caller in a position to run it is a pass that has just
     * walked the sector for its own reasons. Sweeping again here would walk every system a second
     * time to reach sets already in hand.
     *
     * <p>Nothing depends on this having run: the rule keeps its own live reading of the place for
     * exactly that reason, so a caller that never writes still shows what stands among witnesses
     * now. What the write buys is that the reading survives the witnesses.
     *
     * @param sector   the sector whose memory holds the register; null is a no-op
     * @param system   where the colonies stand, and what a sighting names; null is a no-op. A star
     *                 system alone, a colony outside one reading sighted whatever the register
     *                 holds
     * @param colonies the colonies selected for that system, as the caller's own walk read them;
     *                 null is a no-op
     */
    public static void recordSightingsByInhabitants(
            SectorAPI sector,
            StarSystemAPI system,
            Colonies colonies) {

        if (system == null || colonies == null) {
            return;
        }
        putSightings(
            sector,
            system.getId(),
            ColonyKnowledge
                .observingUnderTheFog()
                .readColoniesObservedByInhabitants(colonies));
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

        var storedSightings = readStoredSightings(sector);

        if (storedSightings == null || storedSightings.isEmpty()) {
            return;
        }
        storedSightings
            .keySet()
            .retainAll(readColonyIdsIn(sector));
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

        dropSightingsOfAbsentColonies(sector);

        if (sector != null) {
            recordSightingsIn(
                sector,
                sector.getCurrentLocation());
        }
    }

    // Stamps a set of colonies as observed in one place, at this moment, opening the register on
    // the first write of a campaign.
    //
    // Nothing to stamp opens nothing, so the vast majority of systems - which hold no gated colony
    // at all - never put an empty register into a save between them.
    //
    // The clock is read once for the whole set rather than per colony, so every colony observed in
    // one moment is stamped with that one moment - two of them a tick apart would say the observer
    // saw one before the other.
    private static void putSightings(
            SectorAPI sector,
            String locationId,
            List<Colony> observedColonies) {

        if (locationId == null || observedColonies.isEmpty()) {
            return;
        }
        var storedSightings = openStoredSightings(sector);

        if (storedSightings == null) {
            return;
        }
        var storedObservation = encodeObservation(locationId, readClockTimestamp(sector));

        for (var colony : observedColonies) {

            var colonyId = colony.market().getId();

            if (colonyId != null) {
                storedSightings.put(colonyId, storedObservation);
            }
        }
    }

    // When the observation is being made, or null where there is no clock to ask - which is no
    // reason to lose the observation itself, the place being the half every visibility rule
    // spends. Such an entry reads as undated and is dated at the next observation.
    //
    // The sector is not re-checked: the only caller has already opened the register through it,
    // which no absent sector can survive.
    private static Long readClockTimestamp(SectorAPI sector) {

        var clock = sector.getClock();

        return clock == null ? null : clock.getTimestamp();
    }

    // One observation as the register stores it: its time, then the place it names.
    private static String encodeObservation(String locationId, Long observedTimestamp) {

        if (observedTimestamp == null) {
            return locationId;
        }
        return observedTimestamp + OBSERVATION_SEPARATOR + locationId;
    }

    // One stored entry read back. An entry that carries no time - every one written before
    // observations were timed - reads as an undated observation of the place it names, which is
    // exactly what it is: somebody saw the colony there, and nobody wrote down when.
    private static ColonyObservation decodeObservation(String storedObservation) {

        if (storedObservation == null) {
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
            // A location id that happens to hold the separator, which only an entry written
            // before observations were timed can be. The whole of it is the place.
            return ColonyObservation.createUndatedObservation(storedObservation);
        }
    }

    // The markets present in one place, both listings together: the economy's, and the ones hung
    // on the place's own entities that it never registered. Read for the reconciliation alone,
    // which asks what the sector still holds rather than what may be shown of it, so the ownership
    // and kind rules a colony set applies are beside the point - an id present under any market at
    // all is an id a sighting may still be about.
    private static List<MarketAPI> readMarketsIn(SectorAPI sector, LocationAPI location) {

        var markets = new ArrayList<MarketAPI>(LocationMarkets.readMarkets(sector, location));

        markets.addAll(LocationMarkets.readMarketsUnlistedByEconomy(sector, location));

        return markets;
    }

    // Every colony id the sector still holds, hyperspace included. Read the same two ways a
    // sighting is recorded, so the reconciliation cannot drop a colony merely for having been
    // found by a listing the walk here forgot about.
    private static Set<String> readColonyIdsIn(SectorAPI sector) {

        var colonyIds = new HashSet<String>();

        if (sector == null) {
            return colonyIds;
        }
        var systems = sector.getStarSystems();

        if (systems != null) {
            for (var system : systems) {
                collectColonyIdsIn(sector, system, colonyIds);
            }
        }
        collectColonyIdsIn(sector, sector.getHyperspace(), colonyIds);

        return colonyIds;
    }

    private static void collectColonyIdsIn(
            SectorAPI sector,
            LocationAPI location,
            Set<String> colonyIds) {

        for (var market : readMarketsIn(sector, location)) {

            var colonyId = market.getId();

            if (colonyId != null) {
                colonyIds.add(colonyId);
            }
        }
    }

    // The stored register as it stands, or null when there is nothing to read - no sector, no
    // memory, or a save written before any sighting was made. A caller reading null treats it as
    // "nothing has been seen", which is the answer an absent record deserves.
    private static Map<String, String> readStoredSightings(SectorAPI sector) {

        if (sector == null) {
            return null;
        }
        var memory = sector.getMemoryWithoutUpdate();

        if (memory == null || !memory.contains(SIGHTINGS_KEY)) {
            return null;
        }
        return castStoredSightings(memory.get(SIGHTINGS_KEY));
    }

    // The stored register, created and stored on first use. Writers go through here rather than
    // through the read above so that the first sighting of a campaign has somewhere to land.
    private static Map<String, String> openStoredSightings(SectorAPI sector) {

        var storedSightings = readStoredSightings(sector);

        if (storedSightings != null) {
            return storedSightings;
        }
        var memory = sector == null ? null : sector.getMemoryWithoutUpdate();

        if (memory == null) {
            return null;
        }
        var openedSightings = new HashMap<String, String>();

        memory.set(SIGHTINGS_KEY, openedSightings);

        return openedSightings;
    }

    // Sector memory is untyped, so what comes back out of it is taken on trust - and refused
    // outright when it is not a map at all, since another party writing over the key would
    // otherwise take down every read of the register rather than merely emptying it.
    @SuppressWarnings("unchecked")
    private static Map<String, String> castStoredSightings(Object storedValue) {

        if (!(storedValue instanceof Map)) {
            return null;
        }
        return (Map<String, String>) storedValue;
    }
}
