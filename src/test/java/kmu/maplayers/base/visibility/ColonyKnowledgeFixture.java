package kmu.maplayers.base.visibility;

import com.fs.starfarer.api.campaign.econ.MarketAPI;

import kmlib.testfixtures.starsector.colonies.ColonyFixture;

import java.util.HashMap;
import java.util.Map;

/**
 * The world a projection is posed against: KMLib's colony world, plus what has been observed of
 * the colonies standing in it.
 *
 * <p>Extends that world rather than wrapping it, so the shapes and the placement a case poses are
 * the library's own statement of them and this adds the one fact the library has no business
 * holding - what somebody has seen, which is the map's framing and nothing the sector answers.
 *
 * <p>Presence and observation stay separate calls, because that split is the thing under test. A
 * colony is stood in the system by {@code placeColoniesInSystem} and recorded as seen by
 * {@link #markColoniesAsSighted}, and a colony given only the first is the shape a gate holds
 * back. Folding them into one call would leave every case unable to pose that difference.
 *
 * <p>Nothing here states a kind. The classification is resolved off the market, so a case posing a
 * derelict poses the shape a derelict really wears - which is what keeps a case from passing on a
 * kind no sector would ever produce.
 */
public final class ColonyKnowledgeFixture extends ColonyFixture {

    private final Map<String, ColonyObservation> observationsByColonyId = new HashMap<>();

    /**
     * Opens a world holding exactly one system, with nothing observed of it yet.
     *
     * @param systemId the system's id, as {@code StarSystemAPI#getId} reports it
     */
    public ColonyKnowledgeFixture(String systemId) {
        super(systemId);
    }

    /**
     * What has been observed and where, as a projection reads it. Live rather than a snapshot, so a
     * case may build its set first and record the observation after.
     */
    public ColonySightings getSightings() {
        return observationsByColonyId::get;
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
        recordObservations(getSystem().getId(), colonies);
    }

    /**
     * Records these colonies as seen in some other system - an observation that no longer describes
     * where they stand, which is what a colony that has moved since carries.
     */
    public void markColoniesAsSightedElsewhere(String otherSystemId, MarketAPI... colonies) {
        recordObservations(otherSystemId, colonies);
    }

    // One observation apiece, naming where it was made. Undated throughout: no rule reads the
    // time, and a case that is about how old the news is stamps its own.
    private void recordObservations(String locationId, MarketAPI... colonies) {

        for (var colony : colonies) {
            observationsByColonyId.put(
                colony.getId(),
                ColonyObservation.createUndatedObservation(locationId));
        }
    }
}
