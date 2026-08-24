package kmu.maplayers;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

import kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The colonies a visibility suite poses, named by what kind of place each one is.
 *
 * <p>Built through named builders rather than by flag, so a case says which shape it means. The
 * flags stay behind that boundary: three adjacent booleans are transposable without failing, and no
 * suite has business setting them directly.
 *
 * <p>Composed from {@link SectorPoliticsFixtures} rather than restating its market stubs, so what a
 * derelict or a concealed base is has one statement in the tree. What is added here is the shapes
 * that read of the sector never needed - a colony on an entity nobody has found, and the dead
 * world's survey levels - and the plain naming of a shape by its kind.
 *
 * <p>It sits at {@code kmu.maplayers} for the reason {@link DecivilisedPlanetFixtures} does: suites
 * across both trees pose these, and the layering gate forbids {@code maplayers.base} reaching into
 * the political map. This is above both, so both may take it.
 */
public final class ColonyShapeFixtures {

    /** The size every colony takes unless a case asks for another. */
    public static final int DEFAULT_COLONY_SIZE = 3;

    private ColonyShapeFixtures() {
    }

    /**
     * A star system for the colonies below to stand in.
     *
     * @param systemId the system's id, as {@code StarSystemAPI#getId} reports it
     * @return the system mock
     */
    public static StarSystemAPI buildSystem(String systemId) {

        var systemMock = mock(StarSystemAPI.class);

        when(systemMock.getId())
            .thenReturn(systemId);

        return systemMock;
    }

    /**
     * Stands the colonies in the system: hung on its entities, and each naming it as where it is.
     *
     * <p>Both halves, because a case posing a colony in a place wants both and either alone is a
     * trap. Hung and not standing, the colony reads as being nowhere - and a colony outside a star
     * system is observed by default, so no gate can be shown to hold anything back against one.
     * Standing and not hung, the colony walk never finds it and the case really poses an empty
     * system.
     *
     * @param system   the system the colonies stand in
     * @param colonies the colonies to stand there
     */
    public static void placeColoniesInSystem(StarSystemAPI system, MarketAPI... colonies) {

        SectorPoliticsFixtures.placeMarketsOnSystemEntities(system, colonies);
        SectorPoliticsFixtures.placeMarketsInSystem(system, colonies);
    }

    /**
     * A sector holding one system, with an economy to list colonies in and nothing else stubbed.
     *
     * @param system  the sector's one star system
     * @param economy the economy the sector answers with
     * @return the sector mock
     */
    public static SectorAPI buildSectorHolding(StarSystemAPI system, EconomyAPI economy) {

        var sectorMock = mock(SectorAPI.class);

        when(sectorMock.getEconomy())
            .thenReturn(economy);
        when(sectorMock.getStarSystems())
            .thenReturn(List.of(system));

        return sectorMock;
    }

    /**
     * Hangs the colonies on the system's own entities without registering any of them with the
     * economy - the shape vanilla builds an unregistered colony in, and the only way a colony walk
     * reaches one.
     *
     * @param system   the system whose entities carry them
     * @param colonies the colonies to hang, each on its own primary entity
     */
    public static void hangColoniesOnSystemEntities(
            StarSystemAPI system,
            MarketAPI... colonies) {

        SectorPoliticsFixtures.placeMarketsOnSystemEntities(system, colonies);
    }

    /**
     * Registers the colonies with the economy, in the order it will list them.
     *
     * <p>Separate from the entity walk above because that split is what several cases turn on: a
     * colony given only the entity is the off-economy shape vanilla builds Galatia Academy in.
     *
     * @param economy  the economy doing the listing
     * @param system   the system the listing is asked about
     * @param colonies the colonies it lists there
     */
    public static void listColoniesInEconomy(
            EconomyAPI economy,
            StarSystemAPI system,
            MarketAPI... colonies) {

        when(economy.getMarkets(system))
            .thenReturn(List.of(colonies));
    }

    /**
     * Backs the sector's memory with a real map, so a production recorder writes where it really
     * writes and a later read finds it there.
     *
     * @param sector the sector to give a memory to
     */
    public static void openSectorMemory(SectorAPI sector) {
        SectorPoliticsFixtures.openSectorMemory(sector);
    }

    /** An ordinary colony: owned, open, on an entity the player has found. */
    public static MarketAPI buildVisibleColony(String factionId) {
        return buildVisibleColonyOfSize(factionId, DEFAULT_COLONY_SIZE);
    }

    /**
     * The same colony at a stated size - what parts two markets sharing one place, and nothing else.
     *
     * @param factionId the owner
     * @param size      the colony size
     * @return the market mock
     */
    public static MarketAPI buildVisibleColonyOfSize(String factionId, int size) {
        return SectorPoliticsFixtures.buildVisibleMarket(
            SectorPoliticsFixtures.buildFaction(factionId),
            size);
    }

    /** A base once raided: its entity is discovered, its market stays hidden for good. */
    public static MarketAPI buildFoundConcealedColony(String factionId) {
        return SectorPoliticsFixtures.buildHiddenMarket(
            SectorPoliticsFixtures.buildFaction(factionId),
            DEFAULT_COLONY_SIZE);
    }

    /**
     * A base still to be found: concealed, and on an entity the player has not discovered.
     * Concealment and discovery agree here, so nothing whatever about it reaches the player.
     */
    public static MarketAPI buildUnfoundConcealedColony(String factionId) {
        return SectorPoliticsFixtures.buildUndiscoveredHiddenMarket(
            SectorPoliticsFixtures.buildFaction(factionId),
            DEFAULT_COLONY_SIZE);
    }

    /**
     * A derelict station's shape, and the sector's most common undiscovered one: nothing conceals
     * it, and its entity is still to be found. Concealment and discovery disagree here, and the fog
     * answers on discovery - declaring itself to an economy the player has no sight of is not being
     * seen.
     */
    public static MarketAPI buildUnfoundOpenColony(String factionId) {
        return SectorPoliticsFixtures.buildUndiscoveredOpenMarket(
            SectorPoliticsFixtures.buildFaction(factionId),
            DEFAULT_COLONY_SIZE);
    }

    /**
     * A derelict adrift: open, on an entity the player has found, and held by nobody - the neutral
     * faction every unowned station falls to. Every read but the condition takes it for a
     * settlement, which is the whole reason the kind is resolved at all.
     */
    public static MarketAPI buildDerelictStation() {
        return SectorPoliticsFixtures.buildAbandonedStationMarket(DEFAULT_COLONY_SIZE);
    }

    /**
     * The same hulk on an entity the player has not found, which is what most of the sector's
     * derelicts are: nothing conceals it, and nobody has been near it.
     *
     * @return the market mock
     */
    public static MarketAPI buildUnfoundDerelictStation() {

        var marketMock = buildDerelictStation();

        when(marketMock.getPrimaryEntity().isDiscoverable())
            .thenReturn(true);

        return marketMock;
    }

    /**
     * A station somebody keeps: the same derelict condition on a market a real faction holds.
     * Nothing but the owner parts it from the hulk above, which is what makes the pair worth posing
     * together - a kind read splitting them on anything else would be reading the wrong thing.
     */
    public static MarketAPI buildOutpost(String factionId) {
        return SectorPoliticsFixtures.buildOutpostMarket(
            SectorPoliticsFixtures.buildFaction(factionId),
            DEFAULT_COLONY_SIZE);
    }

    /**
     * A bare planet's placeholder, the condition-only market every uninhabited world carries to
     * hold its hazard and atmosphere. Owned by nobody in particular, and rejected on that arm.
     */
    public static MarketAPI buildConditionOnlyMarket() {
        return SectorPoliticsFixtures.buildConditionOnlyMarket(
            SectorPoliticsFixtures.buildFaction(Factions.NEUTRAL),
            DEFAULT_COLONY_SIZE);
    }

    /**
     * A second market object on an existing colony's entity, under the same owner - the shape a mod
     * builds when it supersedes a colony by adding beside vanilla's rather than replacing.
     *
     * @param colony the colony being superseded
     * @param size   the newcomer's size, which is what decides which of the two wins the place
     * @return the market mock
     */
    public static MarketAPI buildSiblingMarketOn(MarketAPI colony, int size) {

        // Read off the sibling before the new mock's stubbing opens, so the two do not nest into
        // an unfinished-stubbing error.
        var faction = colony.getFaction();
        var factionId = colony.getFactionId();
        var entity = colony.getPrimaryEntity();
        var marketMock = mock(MarketAPI.class);

        when(marketMock.getFaction())
            .thenReturn(faction);
        when(marketMock.getFactionId())
            .thenReturn(factionId);
        when(marketMock.getPrimaryEntity())
            .thenReturn(entity);
        when(marketMock.getSize())
            .thenReturn(size);

        return marketMock;
    }
}
