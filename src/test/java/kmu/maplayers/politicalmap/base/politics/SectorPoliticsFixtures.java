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

import kmlib.testfixtures.starsector.StubbedGlobalLogger;
import kmlib.testfixtures.starsector.markets.colonies.ColonyMarketFixture;
import kmlib.testfixtures.starsector.systems.StarSystemFixture;

import kmu.maplayers.base.visibility.colonies.SectorColonySightings;
import kmu.maplayers.politicalmap.base.dominance.DominancePass;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.dominance.HolderPass;
import kmu.maplayers.politicalmap.base.dominance.weighting.BaseSizeWeighting;
import kmu.maplayers.politicalmap.base.dominance.weighting.DominanceRules;
import kmu.maplayers.politicalmap.base.dominance.weighting.PatrolWeighting;
import kmu.maplayers.politicalmap.base.dominance.weighting.StationWeighting;
import kmu.settings.HiddenMarketScalingChoice;

import org.lwjgl.util.vector.Vector2f;
import org.mockito.MockedStatic;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static kmu.maplayers.politicalmap.base.dominance.ColonyReadRulesFixtures.UNDER_THE_FOG;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Shared Mockito wiring for the suites driving the holder pipeline: the stubbed factions,
 * markets, and sectors it reads, and the pass it reads them through. One home for these builders
 * so the footprint read, the dominance-and-palette resolve, the presence-aware filter resolve and
 * the picker aggregations wire an economy the same way rather than each carrying its own
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

    private SectorPoliticsFixtures() {
        // fixture of static wiring, no instances.
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
        // Undiscovered markets are not included, and a decivilised world inhabits its system.
        return HolderPass.over(sector, UNDER_THE_FOG, HolderGrouping.identity());
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
     * A faction stub resolvable by ID with no palette, for the footprint read that keys on the
     * market's faction ID and never resolves colours.
     *
     * @param id the faction ID
     * @return the faction mock
     */
    public static FactionAPI buildFaction(String id) {
        return ColonyMarketFixture.buildFaction(id);
    }

    /**
     * A faction stub carrying its authored UI palette, for the resolves that colour a cell: the
     * bright colour as the fill/border shade and {@link #buildDarkTheme} of it as the seam shade.
     *
     * @param id     the faction ID
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
        return atStability(ColonyMarketFixture.buildVisibleColonyOfSize(faction, size));
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
        return atStability(
            ColonyMarketFixture.buildVisibleColonyOfSize(faction, size),
            stability);
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
        return atStability(ColonyMarketFixture.buildConditionOnlyMarket(faction, size));
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

        return atStability(
            ColonyMarketFixture.buildFoundConcealedColony(faction, size),
            stability);
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
        return atStability(ColonyMarketFixture.buildUndiscoveredConcealedColony(faction, size));
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
        return atStability(ColonyMarketFixture.buildUndiscoveredOpenColony(faction, size));
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
        return atStability(ColonyMarketFixture.buildDerelictStation(size));
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
        return atStability(ColonyMarketFixture.buildOutpost(faction, size));
    }

    // The one fact a dominance weight reads that a market read does not, laid over a colony the
    // shared fixture built. Stability is this layer's alone: KMLib poses what a market *is*, and
    // what it is worth to a weighting rule is the political map's question.
    //
    // Full unless a case says otherwise, so a suite about any other term is not also posing a
    // scaling factor it never mentions.
    private static MarketAPI atStability(MarketAPI market) {
        return atStability(market, FULL_STABILITY);
    }

    private static MarketAPI atStability(MarketAPI market, float stability) {

        when(market.getStabilityValue())
            .thenReturn(stability);

        nameStagedColony(market);

        return market;
    }

    // Gives a staged colony a name, on the market and on the entity carrying it alike - the two
    // readings a box takes a colony's name through, a listed colony being drawn off its market and
    // an unlisted one off the entity it was found on.
    //
    // Every colony the game runs has a name and a box read past its shallowest level draws each of
    // them by it, so a market left unnamed is a shape no sector produces - and one that fails inside
    // a drawn line rather than in the fixture that staged it. A case that reads a name stubs its own
    // afterwards, which wins over this.
    //
    // Named after the ID identifying the market, so no two staged colonies of one system share a
    // name: a station is told from the colony it stands over by name, and two of them alike would
    // pose a system the fixture never meant to build.
    private static void nameStagedColony(MarketAPI market) {

        // Both reads are taken before either stubbing opens: a call on a mock made inside when(...)
        // lands in the middle of an unfinished stubbing and fails the next interaction rather than
        // this line.
        var stagedName = market.getId();
        var entityMock = market.getPrimaryEntity();

        when(market.getName())
            .thenReturn(stagedName);

        if (entityMock != null) {
            when(entityMock.getName())
                .thenReturn(stagedName);
        }
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
     * @param systemId the system ID
     * @param markets  the markets its economy holds
     * @return the sector mock
     */
    public static SectorAPI buildSectorWith(String systemId, MarketAPI... markets) {
        return buildSectorWith(systemId, List.of(), markets);
    }

    /**
     * Wires a sector with one system, whose owning factions are also resolvable by ID so a resolve
     * can look up each winner's palette.
     *
     * @param systemId the system ID
     * @param factions the factions the resolve must resolve by ID for their palette
     * @param markets  the markets its economy holds
     * @return the sector mock
     */
    public static SectorAPI buildSectorWith(String systemId, List<FactionAPI> factions, MarketAPI... markets) {
        return buildSectorWithSystems(factions, listSystemMarkets(systemId, markets));
    }

    /**
     * Wires a sector spanning several systems, each with its own markets and the owning factions
     * resolvable by ID, so a bloc's footprint accumulates across the sector and a resolve can
     * see a bloc present in one system and absent from another within one pass.
     *
     * @param factions the factions the resolve must resolve by ID for their palette
     * @param systems  each system's ID paired with its markets
     * @return the sector mock
     */
    public static SectorAPI buildSectorWithSystems(List<FactionAPI> factions, SystemMarkets... systems) {

        // Each system finishes its own wiring before the sector's opens, so Mockito sees no
        // stubbing nested inside another; the sector is then wired the one way every posed
        // sector is.
        var heldSystems = new HeldSystemMarkets[systems.length];

        for (var i = 0; i < systems.length; i++) {
            heldSystems[i] = new HeldSystemMarkets(
                StarSystemFixture.buildSystem(systems[i].id()),
                systems[i].markets());
        }
        return buildSectorHoldingSystems(factions, heldSystems);
    }

    /**
     * Wires a sector spanning systems a case has already posed, each with its own markets and
     * the owning factions resolvable by ID - the shape for a case whose systems carry more than
     * an ID, such as two sharing one ID and told apart by the entities they are built around.
     *
     * @param factions the factions the resolve must resolve by ID for their palette
     * @param systems  each posed system paired with its markets
     * @return the sector mock
     */
    public static SectorAPI buildSectorHoldingSystems(
            List<FactionAPI> factions,
            HeldSystemMarkets... systems) {

        var economyMock = mock(EconomyAPI.class);
        var systemMocks = new ArrayList<StarSystemAPI>();

        for (var system : systems) {

            when(economyMock.getMarkets(system.system()))
                .thenReturn(system.markets());

            systemMocks.add(system.system());
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
     * @param systemId the ID to find
     * @return the system listed under that ID
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
     * <p>Distinct per system so no two share a site, and taken off the ID's hash rather than from
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
     * Places every one of a sector's systems, which is what a suite driving a real cut or a real
     * motion walk wants: either skips an unplaced system before the drawn-set rule is asked about
     * it, so a sector staged with one system left out reads as a rule decision rather than as a
     * fixture gap.
     *
     * @param sector the sector whose systems to place
     */
    public static void placeEverySystemInHyperspace(SectorAPI sector) {

        for (var system : sector.getStarSystems()) {
            placeSystemInHyperspace(system);
        }
    }

    /**
     * Wires a sector whose systems are walkable but whose economy is absent - the shape mid-load,
     * before the economy stands up - so a read can be pinned on what it does without one. The
     * system is there deliberately: it makes the sector one a walk <em>could</em> enter, so a read
     * that yields nothing is shown to have stopped at its guard rather than at an empty sector.
     *
     * @param systemId the ID of its one star system
     * @return the sector mock, with no economy stubbed
     */
    public static SectorAPI buildEconomylessSectorWithSystem(String systemId) {

        // The system finishes its own wiring before the sector's opens, so Mockito sees no
        // stubbing nested inside another.
        var systemMock = StarSystemFixture.buildSystem(systemId);
        var sectorMock = mock(SectorAPI.class);

        when(sectorMock.getStarSystems())
            .thenReturn(List.of(systemMock));

        return sectorMock;
    }

    /**
     * One system's ID paired with the markets its economy holds, so a multi-system sector can be
     * wired for the sector-wide accumulation a single-system sector cannot express.
     *
     * @param id      the system ID
     * @param markets the markets the system's economy holds
     */
    public record SystemMarkets(
        String id,
        List<MarketAPI> markets) {
    }

    /**
     * Pairs a system ID with its markets for {@link #buildSectorWithSystems}.
     *
     * @param id      the system ID
     * @param markets the markets the system's economy holds
     * @return the system-markets pairing
     */
    public static SystemMarkets listSystemMarkets(String id, MarketAPI... markets) {
        return new SystemMarkets(id, List.of(markets));
    }

    /**
     * A posed system paired with the markets its economy holds, for
     * {@link #buildSectorHoldingSystems}.
     *
     * @param system  the system, as a case posed it
     * @param markets the markets the system's economy holds
     */
    public record HeldSystemMarkets(
        StarSystemAPI system,
        List<MarketAPI> markets) {
    }

    /**
     * Pairs a posed system with its markets for {@link #buildSectorHoldingSystems}.
     *
     * @param system  the system, as a case posed it
     * @param markets the markets the system's economy holds
     * @return the system-markets pairing
     */
    public static HeldSystemMarkets listMarketsIn(StarSystemAPI system, MarketAPI... markets) {
        return new HeldSystemMarkets(system, List.of(markets));
    }

    /**
     * Wires one system holding an open colony and a derelict standing beside it, the colony seated
     * in the system as an economy event's subject is.
     *
     * <p>The arrangement every observation case needs: a derelict alone is seen by nobody, so a
     * write posed over one records nothing and a working recorder cannot be told from a broken
     * one. The colony is another faction's, which is what lets it vouch for the hulk at all.
     *
     * @param systemId    the system's ID, which a recorded sighting names
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
        StubbedGlobalLogger.answerLoggersOn(globalMock);
    }
}
