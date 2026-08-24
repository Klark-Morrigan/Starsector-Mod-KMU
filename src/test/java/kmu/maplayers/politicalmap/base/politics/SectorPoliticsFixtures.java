package kmu.maplayers.politicalmap.base.politics;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

import kmu.maplayers.base.visibility.SectorColonySightings;
import kmu.maplayers.politicalmap.base.dominance.DominancePass;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.dominance.HolderPass;
import kmu.maplayers.politicalmap.base.dominance.weighting.BaseSizeWeighting;
import kmu.maplayers.politicalmap.base.dominance.weighting.DominanceRules;
import kmu.maplayers.politicalmap.base.dominance.weighting.PatrolWeighting;
import kmu.maplayers.politicalmap.base.dominance.weighting.StationWeighting;
import kmu.settings.HiddenMarketScalingChoice;

import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;
import org.mockito.MockedStatic;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static kmu.maplayers.base.visibility.ColonyVisibility.BASE_FOG;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Shared Mockito wiring for the {@code base.politics} integration tests: the stubbed factions,
 * markets, and sectors the holder pipeline reads, and the pass it reads them through. One home
 * for these builders so the integration suites - {@link KnownMarketFootprintsIntegrationTest}
 * (the footprint read), {@link SectorPoliticsIntegrationTest} (the dominance-and-palette
 * resolve), {@link FilteredPoliticsIntegrationTest} (the presence-aware filter resolve), and the
 * two picker aggregations - wire an economy the same way rather than each carrying its own
 * near-identical copy.
 *
 * <p>Every builder returns the live Mockito mock, so a suite with a specialised need (an attached
 * station, patrol counts, a planet market) adds its own stubs on top and keeps that variant local
 * to the one suite that uses it. Markets are built by what kind of market they are rather than by
 * flag, the flags themselves staying private; {@link #buildFaction} comes in a palette-less form
 * for the footprint read that never resolves colours and a palette form for the resolves that do.
 */
public final class SectorPoliticsFixtures {

    /**
     * The stability band vanilla scores a colony on, at the three points the weighting rule turns on:
     * full worth, half, and a collapse. Named once for every suite that stands a colony up - the
     * weight read's own, the snapshot's, and the boxes that explain a weight - because a suite
     * carrying its own copy of "full stability is ten" is a value that can drift from the band the
     * production read actually divides by while every copy goes on agreeing with itself.
     */
    public static final float FULL_STABILITY = 10.0f;

    /** Halfway up the band: what a factor keeps there is its penalty stated outright. */
    public static final float HALF_STABILITY = 5.0f;

    /** The bottom of the band, where a factor's full penalty applies. */
    public static final float NO_STABILITY = 0.0f;

    // A small stable of authored faction shades, distinct enough that a test telling one palette
    // from another reads clearly. Bright is the fill/border colour; the dark seam shade is derived.
    public static final Color HEGEMONY_BRIGHT = new Color(120, 160, 200);
    public static final Color TRITACHYON_BRIGHT = new Color(140, 180, 220);
    public static final Color PERSEAN_BRIGHT = new Color(160, 200, 240);
    public static final Color NEUTRAL_BASE = new Color(150, 150, 150);

    // Runs across the whole test JVM rather than per sector, which is all it has to do: the ids it
    // hands out only need to differ from one another within whatever sector a case builds.
    private static int builtMarketCount;

    private SectorPoliticsFixtures() {
    }

    /**
     * The dominance rule the palette-resolving suites share: station and patrol weighting off, the
     * colony-size weight at its identity, and the colony penalty at a full collapse, so these tests
     * pin the stability rule alone. The footprint suite drives its own builder instead, since it
     * varies the station and patrol factors this constant holds off.
     *
     * @return the shared stability-only weighting rule
     */
    public static DominanceRules buildStabilityWeightedRules() {
        return new DominanceRules(true,
            new BaseSizeWeighting(1.0, HiddenMarketScalingChoice.FIXED, 1.0, 1.0),
            new StationWeighting(false, 1.0, 0.5, 0.5),
            new PatrolWeighting(false, 0.25, 0.5, 1.0, 0.5));
    }

    /**
     * The rebuild's reading of a stubbed sector these suites pose their cases against: the
     * identity grouping, no dev reveal, and one walk of each system.
     *
     * <p>Built per sector rather than shared as a constant, because a pass carries the walk of the
     * sector it was opened over: one held across cases would answer a later case's system off an
     * earlier case's sector, and the two commonly name their systems alike.
     *
     * @param sector the stubbed sector the pass reads
     * @return a pass over that sector
     */
    public static HolderPass buildHolderPassOver(SectorAPI sector) {
        return HolderPass.over(
            sector,
            BASE_FOG, // Undiscovered markets are not included.
            HolderGrouping.identity());
    }

    /**
     * That same reading under the shared stability-only weighting rule - what a suite exercising a
     * resolve that weighs markets poses its cases against.
     *
     * @param sector the stubbed sector the pass reads
     * @return a dominance pass over that sector
     */
    public static DominancePass buildPassOver(SectorAPI sector) {
        return DominancePass.over(
            buildHolderPassOver(sector),
            buildStabilityWeightedRules());
    }

    /**
     * The dark UI shade a faction stub returns for its seam colour, a stable transform of its
     * bright colour distinct enough to tell the two apart. The pipeline only forwards whichever
     * colour the faction hands back; it does not compute the shade.
     *
     * @param bright the faction's bright fill colour
     * @return the derived dark seam shade
     */
    public static Color buildDarkTheme(Color bright) {
        return bright.darker();
    }

    /**
     * A faction stub resolvable by id with no palette, for the footprint read that keys on the
     * market's faction id and never resolves colours.
     *
     * @param id the faction id
     * @return the faction mock
     */
    public static FactionAPI buildFaction(String id) {

        var factionMock = mock(FactionAPI.class);

        when(factionMock.getId())
            .thenReturn(id);

        // Whether this is the neutral faction is answered off the faction, as the engine answers
        // it, rather than left false: the colony kind read parts an unowned hulk from a station
        // somebody keeps on exactly this question, so a fixture that had neutral deny being
        // neutral would pose every derelict as a manned outpost.
        when(factionMock.isNeutralFaction())
            .thenReturn(Factions.NEUTRAL.equals(id));

        return factionMock;
    }

    /**
     * A faction stub carrying its authored UI palette, for the resolves that colour a cell: the
     * bright colour as the fill/border shade and {@link #dark} of it as the seam shade.
     *
     * @param id     the faction id
     * @param bright the faction's bright fill colour
     * @return the faction mock with its palette stubbed
     */
    public static FactionAPI buildFaction(String id, Color bright) {

        var factionMock = buildFaction(id);

        when(factionMock.getBrightUIColor())
            .thenReturn(bright);
        when(factionMock.getDarkUIColor())
            .thenReturn(buildDarkTheme(bright));

        return factionMock;
    }

    /**
     * A visible owned market at full stability, the plain colony most tests read.
     *
     * @param faction the owning faction
     * @param size    the colony size
     * @return the market mock
     */
    public static MarketAPI buildVisibleMarket(FactionAPI faction, int size) {
        return buildMarket(faction, size, false, false, false, FULL_STABILITY);
    }

    /**
     * A visible owned market at the given stability, for exercising the stability scaling of
     * dominance weights.
     *
     * @param faction   the owning faction
     * @param size      the colony size
     * @param stability the market's stability value
     * @return the market mock
     */
    public static MarketAPI buildMarketAtStability(FactionAPI faction, int size, float stability) {
        return buildMarket(faction, size, false, false, false, stability);
    }

    /**
     * A bare rock's placeholder: the condition-only market procgen hangs on an uninhabited world
     * to carry its hazard and atmosphere. Owned on paper and nobody's colony, so a holder read
     * must not attribute its system to the faction it names.
     *
     * @param faction the faction the placeholder names
     * @param size    the colony size
     * @return the market mock
     */
    public static MarketAPI buildConditionOnlyMarket(FactionAPI faction, int size) {
        return buildMarket(faction, size, true, false, false, FULL_STABILITY);
    }

    /**
     * A hidden market on a discovered entity: it still marks its system, but its base rating is
     * chosen by the hidden-market scaling rather than its real size.
     *
     * @param faction the owning faction
     * @param size    the colony size
     * @return the market mock
     */
    public static MarketAPI buildHiddenMarket(FactionAPI faction, int size) {
        return buildHiddenMarketAtStability(faction, size, FULL_STABILITY);
    }

    /**
     * That same concealed market at the given stability, for exercising how the hidden-market
     * scaling and the stability scaling compose - the one pairing neither single-axis builder can
     * pose.
     *
     * @param faction   the owning faction
     * @param size      the colony size
     * @param stability the market's stability value
     * @return the market mock
     */
    public static MarketAPI buildHiddenMarketAtStability(
            FactionAPI faction,
            int size,
            float stability) {

        return buildMarket(faction, size, false, true, false, stability);
    }

    /**
     * An undiscovered hidden market on a still-discoverable entity, the shape a market wears
     * before the player finds it - it fails the known-to-player filter, so it confers no presence.
     *
     * @param faction the owning faction
     * @param size    the colony size
     * @return the market mock
     */
    public static MarketAPI buildUndiscoveredHiddenMarket(FactionAPI faction, int size) {
        return buildMarket(faction, size, false, true, true, FULL_STABILITY);
    }

    /**
     * An undiscovered market with nothing concealed about it: the colony is openly listed and its
     * entity is still to be found. Concealment and discovery disagree here, which is what makes it
     * worth posing beside the hidden pair above - the fog answers on discovery, so declaring
     * itself to an economy the player cannot see does not admit it.
     *
     * @param faction the owning faction
     * @param size    the colony size
     * @return the market mock
     */
    public static MarketAPI buildUndiscoveredOpenMarket(FactionAPI faction, int size) {
        return buildMarket(faction, size, false, false, true, FULL_STABILITY);
    }

    /**
     * A derelict station's market: un-hidden, on a found entity, held by nobody, and carrying
     * vanilla's abandoned-station condition - the shape Sentinel Gantries and every other hulk
     * wears.
     *
     * <p>The condition is what parts it from a colony, and it is the only thing that does: nobody
     * lives aboard, but the market is owned, sized and stable exactly as a settlement's is. A
     * suite reading habitation therefore cannot pose this shape by varying anything else.
     *
     * <p>The neutral owner is the shape rather than a detail. It is what makes this a derelict
     * instead of a station somebody keeps, so it is fixed here rather than left to a caller.
     *
     * @param size the colony size
     * @return the market mock
     */
    public static MarketAPI buildAbandonedStationMarket(int size) {
        return buildStationCarryingDerelictCondition(buildFaction(Factions.NEUTRAL), size);
    }

    /**
     * A station a faction keeps: the same derelict condition on a market a real faction holds.
     * Nothing but the owner parts it from the hulk above, which is why the pair is worth posing
     * together - a read splitting them on anything else is reading the wrong thing.
     *
     * @param faction the faction keeping the station
     * @param size    the colony size
     * @return the market mock
     */
    public static MarketAPI buildOutpostMarket(FactionAPI faction, int size) {
        return buildStationCarryingDerelictCondition(faction, size);
    }

    // The derelict condition on an ordinary colony's shape, which the owner then decides the kind
    // of. Stated once so the hulk and the kept station cannot drift into differing on anything but
    // that owner, which is the whole of what the kind read parts them on.
    private static MarketAPI buildStationCarryingDerelictCondition(FactionAPI faction, int size) {

        var marketMock = buildMarket(faction, size, false, false, false, FULL_STABILITY);

        when(marketMock.hasCondition(Conditions.ABANDONED_STATION))
            .thenReturn(true);

        return marketMock;
    }

    // The base market stub the named builders above wrap: exactly the fields the "counts as a
    // colony" filter and the weight read touch, so a suite adds only the extra stubs its variant
    // needs.
    //
    // Private, and the flags stay behind that boundary. Three adjacent booleans are transposable
    // without failing, and a suite setting them directly says nothing about which of the resulting
    // shapes it meant - so a case reads as the market it poses rather than as a flag triple the
    // reader has to decode.
    private static MarketAPI buildMarket(
            FactionAPI faction,
            int size,
            boolean isConditionOnly,
            boolean isHidden,
            boolean isUndiscovered,
            float stability) {

        var entityMock = mock(SectorEntityToken.class);

        when(entityMock.isDiscoverable())
            .thenReturn(isUndiscovered);

        // The owner's id is read before the market's stubbing opens, so the two mocks do not nest
        // into an unfinished-stubbing error.
        var factionId = faction == null ? null : faction.getId();
        var marketMock = mock(MarketAPI.class);

        // Every market gets an id of its own, because a sighting is kept against one: markets
        // sharing the null a mock answers by default would share one entry in the register, and a
        // case marking one seen would silently mark the lot.
        builtMarketCount++;

        when(marketMock.getId())
            .thenReturn("market_" + builtMarketCount);
        // The owner answers on both readings, as a real market's does - they are one fact in the
        // game, and a rule that tells owners apart would see none if only one of them answered.
        when(marketMock.getFaction())
            .thenReturn(faction);
        when(marketMock.getFactionId())
            .thenReturn(factionId);
        when(marketMock.getSize())
            .thenReturn(size);
        when(marketMock.getStabilityValue())
            .thenReturn(stability);
        when(marketMock.isPlanetConditionMarketOnly())
            .thenReturn(isConditionOnly);
        when(marketMock.isHidden())
            .thenReturn(isHidden);
        when(marketMock.getPrimaryEntity())
            .thenReturn(entityMock);

        return marketMock;
    }

    /**
     * The one system of a single-system sector, for a resolve that reads only that system.
     *
     * @param sector the sector to read
     * @return its sole star system
     */
    public static StarSystemAPI buildOnlySystem(SectorAPI sector) {
        return sector.getStarSystems().get(0);
    }

    /**
     * A star at a location, resolvable as a star, so a distance-from-centre read can pick the
     * star nearest the system centre.
     *
     * @param x the star's x location
     * @param y the star's y location
     * @return the star mock
     */
    public static PlanetAPI buildStarAt(float x, float y) {

        var starMock = mock(PlanetAPI.class);

        when(starMock.isStar())
            .thenReturn(true);
        when(starMock.getLocation())
            .thenReturn(new Vector2f(x, y));

        return starMock;
    }

    /**
     * A body on a circular orbit of the given radius around a focus, the unit a distance-from-
     * centre read sums up the orbit-focus chain.
     *
     * @param radius the body's circular-orbit radius
     * @param focus  the body it orbits
     * @return the orbiting-entity mock
     */
    public static SectorEntityToken buildOrbitingEntity(float radius, SectorEntityToken focus) {

        var entityMock = mock(SectorEntityToken.class);

        when(entityMock.getCircularOrbitRadius())
            .thenReturn(radius);
        when(entityMock.getOrbitFocus())
            .thenReturn(focus);

        return entityMock;
    }

    /**
     * Re-points a market's primary entity to a body on the given orbit, so a resolve reading
     * orbit geometry places the market that far from the system centre.
     *
     * @param market the market to place
     * @param radius the primary entity's circular-orbit radius
     * @param focus  the body the primary entity orbits
     */
    public static void placeMarketOnOrbit(MarketAPI market, float radius, SectorEntityToken focus) {
        // Build the orbiting entity (which stubs its own orbit) before opening the market's
        // stubbing, so the two do not nest into an unfinished-stubbing error.
        var entityMock = buildOrbitingEntity(radius, focus);

        when(market.getPrimaryEntity())
            .thenReturn(entityMock);
    }

    /**
     * Re-sites the given markets onto one shared primary entity, so they stand for the same
     * place - the shape a mod makes when it supersedes a market by adding its own beside
     * vanilla's rather than replacing it, and the only way two market objects come to name
     * one colony.
     *
     * @param markets the markets to place on one entity
     */
    public static void placeMarketsOnOneEntity(MarketAPI... markets) {

        var entityMock = mock(SectorEntityToken.class);

        for (var market : markets) {
            when(market.getPrimaryEntity()).thenReturn(entityMock);
        }
    }

    /**
     * Hangs the given markets on the system's own entities, each on the entity it already stands on,
     * without registering any of them with the economy - the shape vanilla builds an unregistered
     * colony in, a real market on a real station that the economy's listing never holds.
     *
     * <p>Whether a market is <em>also</em> in the economy is the caller's to decide by which of them
     * it handed {@link #buildSectorWith}, so one system can be wired holding both kinds at once -
     * which is the only wiring under which a read that answers what the economy leaves out can be
     * shown to leave out what it lists.
     *
     * @param system  the system whose entities carry them
     * @param markets the markets to hang, each on its own primary entity
     */
    public static void placeMarketsOnSystemEntities(StarSystemAPI system, MarketAPI... markets) {

        var entities = new ArrayList<SectorEntityToken>();

        for (var market : markets) {
            var entityMock = market.getPrimaryEntity();

            when(entityMock.getMarket())
                .thenReturn(market);

            entities.add(entityMock);
        }

        when(system.getAllEntities())
            .thenReturn(entities);
    }

    /**
     * Stands the given markets in the system, so a read asking where a colony is finds it there.
     *
     * <p>Separate from {@link #placeMarketsOnSystemEntities}, which hangs a market on one of the
     * system's entities: that is how an unregistered colony is <em>found</em>, while this is what
     * the colony answers when asked where it stands. A market wants both only if a case turns on
     * both, and most turn on neither.
     *
     * <p>The one thing that does turn on it is a revelation gate. A colony reads as sighted
     * wherever its containing location is not a star system - hyperspace has no system to have
     * been in - so an unstubbed market mock is sighted by default and no gate can be shown to
     * hold anything back against one. Standing a market in a system that has not been entered is
     * the only way to pose a colony nobody has seen.
     *
     * @param system  the system the markets stand in
     * @param markets the markets to stand there
     */
    public static void placeMarketsInSystem(StarSystemAPI system, MarketAPI... markets) {

        for (var market : markets) {

            when(market.getContainingLocation())
                .thenReturn(system);
        }
    }

    /**
     * Takes the player's fleet through the system, recording every colony standing there as seen
     * where it stands - their own route to having heard of whatever is present.
     *
     * <p>Driven through the production recorder rather than by writing the register directly, so a
     * case saying "the player has been here" is posing the visit the game would record and not a
     * fixture's own idea of what one leaves behind.
     *
     * <p>Its absence is the interesting state rather than its presence: a sector with no register
     * written answers nothing seen already, so a case wanting an unvisited system says nothing and
     * a case wanting a visited one says this.
     *
     * @param sector the sector whose memory carries the register
     * @param system the system the player has been in
     */
    public static void markSystemAsVisitedByPlayer(SectorAPI sector, StarSystemAPI system) {

        openSectorMemory(sector);

        SectorColonySightings.recordSightingsIn(sector, system);
    }

    /**
     * Gives the sector a memory a production write can land in, without also posing a visit.
     *
     * <p>What a case wants when the write under exercise is one of the sector's own routes rather
     * than the player's: the register has to have somewhere to go, and staging a visit to open it
     * would seed the very observations the case means to watch being made.
     *
     * <p>Backed by a real map, so what a recorder writes is what a later read finds. Idempotent: a
     * sector opened twice keeps the register the first call opened, which is the only way a case
     * can pose a colony met in one system and then met again in another.
     *
     * @param sector the sector to give a memory to
     */
    public static void openSectorMemory(SectorAPI sector) {

        if (sector.getMemoryWithoutUpdate() != null) {
            return;
        }
        var storedValues = new HashMap<String, Object>();
        var memoryMock = mock(MemoryAPI.class);

        when(memoryMock.contains(anyString()))
            .thenAnswer(invocation -> storedValues.containsKey(invocation.getArgument(0)));
        when(memoryMock.get(anyString()))
            .thenAnswer(invocation -> storedValues.get(invocation.getArgument(0)));

        doAnswer(invocation -> storedValues.put(
                invocation.getArgument(0),
                invocation.getArgument(1)))
            .when(memoryMock)
            .set(anyString(), any());

        when(sector.getMemoryWithoutUpdate())
            .thenReturn(memoryMock);
    }

    /**
     * Centres a system on a star - both its centre and its one star - so a distance-from-centre
     * read resolves that star as its reference.
     *
     * @param system the system to centre
     * @param star   the star at its centre
     */
    public static void centreSystemOn(StarSystemAPI system, PlanetAPI star) {

        when(system.getCenter())
            .thenReturn(star);
        when(system.getPlanets())
            .thenReturn(List.of(star));
    }

    /**
     * Wires a sector with one system whose economy holds the given markets, with no faction
     * palette stubbed - the shape the footprint read uses, which never resolves colours.
     *
     * @param systemId the system id
     * @param markets  the markets its economy holds
     * @return the sector mock
     */
    public static SectorAPI buildSectorWith(String systemId, MarketAPI... markets) {

        var systemMock = mock(StarSystemAPI.class);

        when(systemMock.getId())
            .thenReturn(systemId);

        var economyMock = mock(EconomyAPI.class);

        when(economyMock.getMarkets(systemMock))
            .thenReturn(List.of(markets));

        var sectorMock = mock(SectorAPI.class);

        when(sectorMock.getStarSystems())
            .thenReturn(List.of(systemMock));
        when(sectorMock.getEconomy())
            .thenReturn(economyMock);

        return sectorMock;
    }

    /**
     * Wires a sector with one system, whose owning factions are also resolvable by id so a resolve
     * can look up each winner's palette.
     *
     * @param systemId the system id
     * @param factions the factions the resolve must resolve by id for their palette
     * @param markets  the markets its economy holds
     * @return the sector mock
     */
    public static SectorAPI buildSectorWith(String systemId, List<FactionAPI> factions, MarketAPI... markets) {

        var sectorMock = buildSectorWith(systemId, markets);

        for (var faction : factions) {

            when(sectorMock.getFaction(faction.getId()))
                .thenReturn(faction);
        }
        return sectorMock;
    }

    /**
     * Wires a sector spanning several systems, each with its own markets and the owning factions
     * resolvable by id, so a bloc's footprint accumulates across the sector and a resolve can
     * see a bloc present in one system and absent from another within one pass.
     *
     * @param factions the factions the resolve must resolve by id for their palette
     * @param systems  each system's id paired with its markets
     * @return the sector mock
     */
    public static SectorAPI buildSectorWithSystems(List<FactionAPI> factions, SystemMarkets... systems) {

        var economyMock = mock(EconomyAPI.class);
        var systemMocks = new ArrayList<StarSystemAPI>();

        for (var system : systems) {
            
            var systemMock = mock(StarSystemAPI.class);

            when(systemMock.getId())
                .thenReturn(system.id());
            when(economyMock.getMarkets(systemMock))
                .thenReturn(system.markets());

            systemMocks.add(systemMock);
        }
        var sectorMock = mock(SectorAPI.class);

        when(sectorMock.getStarSystems())
            .thenReturn(systemMocks);
        when(sectorMock.getEconomy())
            .thenReturn(economyMock);

        for (var faction : factions) {

            when(sectorMock.getFaction(faction.getId()))
                .thenReturn(faction);
        }
        return sectorMock;
    }

    /**
     * The system a sector lists under one id.
     *
     * <p>Beside {@link #buildOnlySystem}, which serves the single-system sectors: a walk over
     * several systems has to name the one it is about, and reading it back off the sector is what
     * lets a case state a change to that system without a second handle on the mock.
     *
     * @param sector   the sector to read
     * @param systemId the id to find
     * @return the system listed under that id
     * @throws IllegalArgumentException when the sector lists no such system
     */
    public static StarSystemAPI findSystemIn(SectorAPI sector, String systemId) {

        for (var system : sector.getStarSystems()) {

            if (systemId.equals(system.getId())) {
                return system;
            }
        }
        throw new IllegalArgumentException("No system staged under the id " + systemId);
    }

    /**
     * Gives a system a hyperspace position, without which a walk collecting drawn-system positions
     * skips it before the drawn-set rule is ever asked about it - so a suite counting what that
     * walk read would be blind to the very systems it staged.
     *
     * <p>Distinct per system so no two share a site, and taken off the id's hash rather than from
     * an argument: nothing that wants this moves a system, so coordinates on the call would look
     * like they meant something.
     *
     * @param system the system to place
     */
    public static void placeSystemInHyperspace(StarSystemAPI system) {

        var idHash = system.getId().hashCode();

        when(system.getLocation())
            .thenReturn(new Vector2f(idHash, -idHash));
    }

    /**
     * Wires a sector whose systems are walkable but whose economy is absent - the shape mid-load,
     * before the economy stands up - so a read can be pinned on what it does without one. The
     * system is there deliberately: it makes the sector one a walk <em>could</em> enter, so a read
     * that yields nothing is shown to have stopped at its guard rather than at an empty sector.
     *
     * @param systemId the id of its one star system
     * @return the sector mock, with no economy stubbed
     */
    public static SectorAPI buildEconomylessSectorWithSystem(String systemId) {

        var systemMock = mock(StarSystemAPI.class);
        when(systemMock.getId())
            .thenReturn(systemId);

        var sectorMock = mock(SectorAPI.class);
        when(sectorMock.getStarSystems())
            .thenReturn(List.of(systemMock));

        return sectorMock;
    }

    /**
     * One system's id paired with the markets its economy holds, so a multi-system sector can be
     * wired for the sector-wide accumulation a single-system sector cannot express.
     *
     * @param id      the system id
     * @param markets the markets the system's economy holds
     */
    public record SystemMarkets(
        String id,
        List<MarketAPI> markets) {
    }

    /**
     * Pairs a system id with its markets for {@link #buildSectorWithSystems}.
     *
     * @param id      the system id
     * @param markets the markets the system's economy holds
     * @return the system-markets pairing
     */
    public static SystemMarkets listSystemMarkets(String id, MarketAPI... markets) {
        return new SystemMarkets(id, List.of(markets));
    }

    /**
     * Wires one system holding an open colony and a derelict standing beside it, the colony seated
     * in the system as an economy event's subject is.
     *
     * <p>The arrangement every observation case needs: a derelict alone is seen by nobody, so a
     * write posed over one records nothing and a working recorder cannot be told from a broken
     * one. The colony is another faction's, which is what lets it vouch for the hulk at all.
     *
     * @param systemId    the system's id, which a recorded sighting names
     * @param colonySize  the open colony's size
     * @param derelictSize the derelict's size
     * @return the sector, holding that one system
     */
    public static SectorAPI buildSystemHoldingAColonyAndADerelict(
            String systemId,
            int colonySize,
            int derelictSize) {

        var hegemony = buildFaction("hegemony");
        var colony = buildVisibleMarket(hegemony, colonySize);

        var sector = buildSectorWithSystems(
            List.of(hegemony),
            listSystemMarkets(systemId, colony));

        var system = findSystemIn(sector, systemId);

        placeMarketsOnSystemEntities(system, buildAbandonedStationMarket(derelictSize));

        // The seat an economy event's subject is read through, which a plain stubbed market
        // answers null for.
        when(colony.getStarSystem())
            .thenReturn(system);

        return sector;
    }

    /**
     * Stubs the statics a listener reaches through: the sector it records into, and a logger for
     * whatever class first touches {@code Global} inside the block.
     *
     * <p>The logger matters more than it looks. A class whose static {@code LOG} field is resolved
     * while {@code Global} is mocked keeps a null logger for the rest of the JVM, so a suite that
     * stubs only the sector can leave every later suite faulting on a log line it never wrote.
     *
     * @param globalMock the open static mock the caller owns and closes
     * @param sector     the sector {@code Global.getSector} answers with
     */
    public static void stubGlobalSector(MockedStatic<Global> globalMock, SectorAPI sector) {

        globalMock.when(Global::getSector)
            .thenReturn(sector);
        globalMock.when(() -> Global.getLogger(any(Class.class)))
            .thenReturn(mock(Logger.class));
    }
}
