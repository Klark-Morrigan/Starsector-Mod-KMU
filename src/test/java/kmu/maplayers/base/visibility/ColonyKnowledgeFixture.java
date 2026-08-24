package kmu.maplayers.base.visibility;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import kmlib.testfixtures.starsector.colonies.ColonyFixture;

import kmu.maplayers.DecivilisedPlanetFixtures;

import java.util.HashMap;
import java.util.Map;

/**
 * The world a projection is posed against: one star system, the colonies standing in it, and what
 * has been observed of each.
 *
 * <p>Presence and observation are stated separately, because that split is the thing under test. A
 * colony is stood in the system by {@link #placeColoniesInSystem} and recorded as seen by
 * {@link #markColoniesAsSighted}, and a colony given only the first is the shape a gate holds back.
 * Folding them into one call would leave every case unable to pose that difference.
 *
 * <p>Colony shapes themselves are {@link ColonyFixture}'s and {@link DecivilisedPlanetFixtures}'s,
 * forwarded here so a suite holding this fixture builds and places them through the one object.
 * What a colony is does not depend on the world it stands in.
 *
 * <p>Nothing here states a kind. The classification is resolved off the market, so a case posing a
 * derelict poses the shape a derelict really wears - which is what keeps a case from passing on a
 * kind no sector would ever produce.
 */
public final class ColonyKnowledgeFixture {

    /** The size every colony takes unless a case asks for another. */
    public static final int DEFAULT_COLONY_SIZE = ColonyFixture.DEFAULT_COLONY_SIZE;

    private final Map<String, ColonyObservation> observationsByColonyId = new HashMap<>();
    private final ColonyFixture world;

    /**
     * Opens a world holding exactly one system.
     *
     * @param systemId the system's id, as {@code StarSystemAPI#getId} reports it
     */
    public ColonyKnowledgeFixture(String systemId) {
        world = new ColonyFixture(systemId);
    }

    /** The sector the world stands in. */
    public SectorAPI getSector() {
        return world.getSector();
    }

    /** The one system the world holds. */
    public StarSystemAPI getSystem() {
        return world.getSystem();
    }

    /**
     * Backs the sector's memory with a real map, so a production recorder writes where it really
     * writes and a later read finds it there.
     *
     * <p>What a case exercising the recorders needs, as against one merely stating what has been
     * seen: {@link #markColoniesAsSighted} hands a projection an answer, while a recorder has to be
     * given somewhere to put one.
     */
    public void openSectorMemory() {
        world.openSectorMemory();
    }

    /** Registers the colonies with the economy, in the order it will list them. */
    public void listColoniesInEconomy(MarketAPI... colonies) {
        world.listColoniesInEconomy(colonies);
    }

    /**
     * What has been observed and where, as a projection reads it. Live rather than a snapshot, so a
     * case may build its set first and record the observation after.
     */
    public ColonySightings getSightings() {
        return observationsByColonyId::get;
    }

    /**
     * Stands the colonies in the system, each on the entity it was built with - which is what makes
     * a gate answerable about them at all.
     */
    public void placeColoniesInSystem(MarketAPI... colonies) {
        world.placeColoniesInSystem(colonies);
    }

    /**
     * Records these colonies as seen standing in this system - the player's own route to having
     * heard of whatever is here.
     *
     * <p>Stated rather than defaulted, because the unseen half is the interesting one: it is where
     * a colony that would otherwise leak has to be held back.
     *
     * <p>Per colony rather than per system, which is the whole of what the rule turns on: a colony
     * that has since moved, or was founded after the player passed through, is one this system
     * carries no observation of however often the player has crossed it.
     */
    public void markColoniesAsSighted(MarketAPI... colonies) {

        for (var colony : colonies) {
            observationsByColonyId.put(
                colony.getId(),
                ColonyObservation.createUndatedObservation(world.getSystem().getId()));
        }
    }

    /**
     * Records these colonies as seen in some other system - an observation that no longer describes
     * where they stand, which is what a colony that has moved since carries.
     */
    public void markColoniesAsSightedElsewhere(String otherSystemId, MarketAPI... colonies) {

        for (var colony : colonies) {
            observationsByColonyId.put(
                colony.getId(),
                ColonyObservation.createUndatedObservation(otherSystemId));
        }
    }

    // Each colony builder below forwards to the one named for it on the shared shape fixtures,
    // which is where the shape is described. Restating those descriptions here would put two
    // accounts of one colony a rename apart.

    public MarketAPI buildConditionOnlyMarket() {
        return world.buildConditionOnlyMarket();
    }

    public MarketAPI buildDecivilisedWorld() {
        return DecivilisedPlanetFixtures.buildRevealedDecivilisedMarket();
    }

    public MarketAPI buildSeenDecivilisedWorld() {
        return DecivilisedPlanetFixtures.buildSeenDecivilisedMarket();
    }

    public MarketAPI buildDerelictStation() {
        return world.buildDerelictStation();
    }

    public MarketAPI buildUnfoundDerelictStation() {
        return world.buildUnfoundDerelictStation();
    }

    public MarketAPI buildOutpost(String factionId) {
        return world.buildOutpost(factionId);
    }

    public MarketAPI buildFoundConcealedColony(String factionId) {
        return world.buildFoundConcealedColony(factionId);
    }

    public MarketAPI buildSiblingMarketOn(MarketAPI colony, int size) {
        return world.buildSiblingMarketOn(colony, size);
    }

    public MarketAPI buildUnfoundConcealedColony(String factionId) {
        return world.buildUnfoundConcealedColony(factionId);
    }

    public MarketAPI buildUnfoundUnsurveyedDecivilisedWorld() {
        return DecivilisedPlanetFixtures.buildUnfoundUnsurveyedDecivilisedMarket();
    }

    public MarketAPI buildUnsurveyedDecivilisedWorld() {
        return DecivilisedPlanetFixtures.buildUnsurveyedDecivilisedMarket();
    }

    public MarketAPI buildUnfoundOpenColony(String factionId) {
        return world.buildUnfoundOpenColony(factionId);
    }

    public MarketAPI buildVisibleColony(String factionId) {
        return world.buildVisibleColony(factionId);
    }

    public MarketAPI buildVisibleColonyOfSize(String factionId, int size) {
        return world.buildVisibleColonyOfSize(factionId, size);
    }
}
