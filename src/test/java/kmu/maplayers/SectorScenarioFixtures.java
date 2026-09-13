package kmu.maplayers;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures;

import java.util.ArrayList;
import java.util.List;

/**
 * Whole situations a map surface is asked to read, as against the parts one is staged from.
 *
 * <p>Beside {@link SectorPoliticsFixtures} rather than inside it, because the two answer different
 * questions. That class builds a <em>thing</em> - a faction, a market of some shape, a sector whose
 * economy lists what it was handed - and leaves what the arrangement means to the suite. Everything
 * here stages an arrangement that already means something: a situation with a name, staged the way
 * the sector really produces it, which several suites need to be identical about.
 *
 * <p>It sits at {@code kmu.maplayers} for the reason {@link DecivilisedPlanetFixtures} does: suites
 * across both trees stage these, and the layering gate forbids {@code maplayers.base} reaching into
 * the political map. This is above both, so both may take it.
 *
 * <p>Unlike that class, this one is composed from the political map's own fixtures rather than
 * built from raw mocks. What it stages is markets and sectors, which those builders already state
 * once - restating them here would put a second answer to "what shape is a derelict" in the tree,
 * which is the drift the shared fixture exists to prevent.
 */
public final class SectorScenarioFixtures {

    /**
     * The faction holding the concealed base in {@link #buildUnvisitedSectorHoldingGatedPair}, so
     * a case can read back whether that holder surfaced without restating the id.
     */
    public static final String CONCEALED_HOLDER_ID = "pirates";

    // What a staged colony is worth. A derelict is never weighed - the economy does not list one,
    // so no term of a dominance weight can read it - and the concealed base's weight is nobody's
    // subject here either, so both are the fixture's business rather than a caller's. A size
    // argument at each call site would be a number that reads as load-bearing and is not.
    private static final int DERELICT_SIZE = 3;
    private static final int CONCEALED_BASE_SIZE = 4;

    private SectorScenarioFixtures() {
    }

    /**
     * Stands a derelict in the system: a market carrying vanilla's abandoned-station condition,
     * held by nobody, hanging on one of the system's own entities and absent from the economy's
     * listing.
     *
     * <p>That absence is the shape rather than a detail of it. The routine vanilla builds an
     * abandoned station through pointedly never registers one, so a fixture that listed a derelict
     * with the economy would pose a colony no sector ever holds - and every read that walks the
     * listing would find it by the wrong route.
     *
     * <p>Hanging one on an entity takes two stubs, the entity appearing in the system's own walk
     * and the entity carrying the market back. Miss the second and the market is dropped in
     * silence, so a case meaning to pose a derelict poses an empty system and passes under
     * whichever projection it was written to catch. Stated once here so no suite has to remember
     * both halves.
     *
     * <p>The system's entities are replaced by this one, as {@link
     * DecivilisedPlanetFixtures#placeRevealedDecivilisedPlanetIn} replaces its planets - so a
     * system meant to hold a derelict beside another entity-hung market wants one call staging
     * both, not two calls.
     *
     * @param system the system the derelict stands in; its entities are replaced by the one hulk
     * @return the derelict's market, for a case that has something to say about it
     */
    public static MarketAPI placeDerelictIn(StarSystemAPI system) {

        var derelict = SectorPoliticsFixtures.buildAbandonedStationMarket(DERELICT_SIZE);

        SectorPoliticsFixtures.placeMarketsOnSystemEntities(system, derelict);

        return derelict;
    }

    /**
     * A sector of one star system nobody has visited, holding the two shapes a revelation gate
     * covers - a derelict nobody ever lived on, and a concealed base - beside whatever open
     * colonies the caller founds there.
     *
     * <p>Staged as one situation because the gates can only be shown to hold anything back against
     * all three of its parts at once: the markets must be unseen, which means standing in a system
     * that has not been entered; they must be found, which means entities that are not
     * discoverable; and they must be the covered shapes. Get the first wrong and every gate
     * assertion passes vacuously, since a colony whose containing location is not a star system
     * reads as already sighted.
     *
     * <p>The open colonies are what a caller varies, because they are the other route to
     * revelation: somewhere people live is somewhere with inhabitants who would have seen whatever
     * else is there. Founding one turns the same system from a place that reveals nothing into a
     * place that reveals both.
     *
     * @param systemId     the ID of the sector's one star system
     * @param openColonies the openly-settled colonies its economy lists, if any
     * @return the sector mock
     */
    public static SectorAPI buildUnvisitedSectorHoldingGatedPair(
            String systemId,
            MarketAPI... openColonies) {

        var economyMarkets = new ArrayList<>(List.of(openColonies));

        economyMarkets.add(SectorPoliticsFixtures.buildHiddenMarket(
            SectorPoliticsFixtures.buildFaction(CONCEALED_HOLDER_ID),
            CONCEALED_BASE_SIZE));

        var sector = SectorPoliticsFixtures.buildSectorWith(
            systemId,
            economyMarkets.toArray(new MarketAPI[0]));

        var system = SectorPoliticsFixtures.buildOnlySystem(sector);
        var standingMarkets = new ArrayList<>(economyMarkets);

        standingMarkets.add(placeDerelictIn(system));

        // Where each market stands, which is a separate question from which listing found it - and
        // one every market here answers alike. The system is left unentered, which an unstubbed
        // system mock already is, so nothing has to say so.
        SectorPoliticsFixtures.placeMarketsInSystem(
            system,
            standingMarkets.toArray(new MarketAPI[0]));

        return sector;
    }
}
