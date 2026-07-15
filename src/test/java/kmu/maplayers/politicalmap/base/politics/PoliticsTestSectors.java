package kmu.maplayers.politicalmap.base.politics;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import kmu.maplayers.politicalmap.base.politics.weighting.BaseSizeWeighting;
import kmu.maplayers.politicalmap.base.politics.weighting.DominanceRules;
import kmu.maplayers.politicalmap.base.politics.weighting.PatrolWeighting;
import kmu.maplayers.politicalmap.base.politics.weighting.StationWeighting;
import kmu.settings.HiddenMarketScalingChoice;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Shared Mockito wiring for the {@code base.politics} integration tests: the stubbed factions,
 * markets, and sectors the ownership pipeline reads. One home for these builders so the three
 * integration suites - {@link KnownMarketFootprintsIntegrationTest} (the footprint read),
 * {@link SectorPoliticsIntegrationTest} (the dominance-and-palette resolve), and
 * {@link FilteredPoliticsIntegrationTest} (the presence-aware filter resolve) - wire an economy
 * the same way rather than each carrying its own near-identical copy.
 *
 * <p>Every builder returns the live Mockito mock, so a suite with a specialised need (an attached
 * station, patrol counts, a planet market) adds its own stubs on top and keeps that variant local
 * to the one suite that uses it. The base {@link #market} stubs exactly the fields the "counts as a
 * colony" filter and the weight read touch; {@link #faction} comes in a palette-less form for the
 * footprint read that never resolves colours and a palette form for the resolves that do.
 */
final class PoliticsTestSectors {
    /** The full-stability value a visible colony carries unless a test varies it. */
    static final float FULL_STABILITY = 10.0f;

    // A small stable of authored faction shades, distinct enough that a test telling one palette
    // from another reads clearly. Bright is the fill/border colour; the dark seam shade is derived.
    static final Color HEGEMONY_BRIGHT = new Color(120, 160, 200);
    static final Color TRITACHYON_BRIGHT = new Color(140, 180, 220);
    static final Color PERSEAN_BRIGHT = new Color(160, 200, 240);
    static final Color NEUTRAL_BASE = new Color(150, 150, 150);

    private PoliticsTestSectors() {
    }

    /**
     * The dominance rule the palette-resolving suites share: station and patrol weighting off, the
     * colony-size weight at its identity, and the colony penalty at a full collapse, so these tests
     * pin the stability rule alone. The footprint suite drives its own builder instead, since it
     * varies the station and patrol factors this constant holds off.
     *
     * @return the shared stability-only weighting rule
     */
    static DominanceRules stabilityWeightedRules() {
        return new DominanceRules(true,
                new BaseSizeWeighting(1.0, HiddenMarketScalingChoice.FIXED, 1.0, 1.0),
                new StationWeighting(false, 1.0, 0.5, 0.5),
                new PatrolWeighting(false, 0.25, 0.5, 1.0, 0.5));
    }

    /**
     * The dark UI shade a faction stub returns for its seam colour, a stable transform of its
     * bright colour distinct enough to tell the two apart. The pipeline only forwards whichever
     * colour the faction hands back; it does not compute the shade.
     *
     * @param bright the faction's bright fill colour
     * @return the derived dark seam shade
     */
    static Color dark(Color bright) {
        return bright.darker();
    }

    /**
     * A faction stub resolvable by id with no palette, for the footprint read that keys on the
     * market's faction id and never resolves colours.
     *
     * @param id the faction id
     * @return the faction mock
     */
    static FactionAPI faction(String id) {
        var factionMock = mock(FactionAPI.class);
        when(factionMock.getId()).thenReturn(id);
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
    static FactionAPI faction(String id, Color bright) {
        var factionMock = faction(id);
        when(factionMock.getBrightUIColor()).thenReturn(bright);
        when(factionMock.getDarkUIColor()).thenReturn(dark(bright));
        return factionMock;
    }

    /**
     * A visible owned market at full stability, the plain colony most tests read.
     *
     * @param faction the owning faction
     * @param size    the colony size
     * @return the market mock
     */
    static MarketAPI visibleMarket(FactionAPI faction, int size) {
        return market(faction, size, false, false, false, FULL_STABILITY);
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
    static MarketAPI marketAtStability(FactionAPI faction, int size, float stability) {
        return market(faction, size, false, false, false, stability);
    }

    /**
     * A hidden market on a discovered entity: it still marks its system, but its base rating is
     * chosen by the hidden-market scaling rather than its real size.
     *
     * @param faction the owning faction
     * @param size    the colony size
     * @return the market mock
     */
    static MarketAPI hiddenMarket(FactionAPI faction, int size) {
        return market(faction, size, false, true, false, FULL_STABILITY);
    }

    /**
     * An undiscovered hidden market on a still-discoverable entity, the shape a market wears
     * before the player finds it - it fails the known-to-player filter, so it confers no presence.
     *
     * @param faction the owning faction
     * @param size    the colony size
     * @return the market mock
     */
    static MarketAPI undiscoveredHiddenMarket(FactionAPI faction, int size) {
        return market(faction, size, false, true, true, FULL_STABILITY);
    }

    /**
     * The base market stub the other builders wrap: exactly the fields the "counts as a colony"
     * filter and the weight read touch, so a suite adds only the extra stubs its variant needs.
     *
     * @param faction         the owning faction
     * @param size            the colony size
     * @param isConditionOnly whether the market is a bare planet's condition-only placeholder
     * @param isHidden        whether the market is hidden
     * @param isUndiscovered  whether the market's entity is still discoverable (not yet found)
     * @param stability       the market's stability value
     * @return the market mock
     */
    static MarketAPI market(FactionAPI faction, int size, boolean isConditionOnly,
            boolean isHidden, boolean isUndiscovered, float stability) {
        var entityMock = mock(SectorEntityToken.class);
        when(entityMock.isDiscoverable()).thenReturn(isUndiscovered);
        var marketMock = mock(MarketAPI.class);
        when(marketMock.getFaction()).thenReturn(faction);
        when(marketMock.getSize()).thenReturn(size);
        when(marketMock.getStabilityValue()).thenReturn(stability);
        when(marketMock.isPlanetConditionMarketOnly()).thenReturn(isConditionOnly);
        when(marketMock.isHidden()).thenReturn(isHidden);
        when(marketMock.getPrimaryEntity()).thenReturn(entityMock);
        return marketMock;
    }

    /**
     * The one system of a single-system sector, for a resolve that reads only that system.
     *
     * @param sector the sector to read
     * @return its sole star system
     */
    static StarSystemAPI onlySystem(SectorAPI sector) {
        return sector.getStarSystems().get(0);
    }

    /**
     * Wires a sector with one system whose economy holds the given markets, with no faction
     * palette stubbed - the shape the footprint read uses, which never resolves colours.
     *
     * @param systemId the system id
     * @param markets  the markets its economy holds
     * @return the sector mock
     */
    static SectorAPI sectorWith(String systemId, MarketAPI... markets) {
        var systemMock = mock(StarSystemAPI.class);
        when(systemMock.getId()).thenReturn(systemId);
        var economyMock = mock(EconomyAPI.class);
        when(economyMock.getMarkets(systemMock)).thenReturn(List.of(markets));
        var sectorMock = mock(SectorAPI.class);
        when(sectorMock.getStarSystems()).thenReturn(List.of(systemMock));
        when(sectorMock.getEconomy()).thenReturn(economyMock);
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
    static SectorAPI sectorWith(String systemId, List<FactionAPI> factions, MarketAPI... markets) {
        var sectorMock = sectorWith(systemId, markets);
        for (var faction : factions) {
            when(sectorMock.getFaction(faction.getId())).thenReturn(faction);
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
    static SectorAPI sectorWithSystems(List<FactionAPI> factions, SystemMarkets... systems) {
        var economyMock = mock(EconomyAPI.class);
        var systemMocks = new ArrayList<StarSystemAPI>();
        for (var system : systems) {
            var systemMock = mock(StarSystemAPI.class);
            when(systemMock.getId()).thenReturn(system.id());
            when(economyMock.getMarkets(systemMock)).thenReturn(system.markets());
            systemMocks.add(systemMock);
        }
        var sectorMock = mock(SectorAPI.class);
        when(sectorMock.getStarSystems()).thenReturn(systemMocks);
        when(sectorMock.getEconomy()).thenReturn(economyMock);
        for (var faction : factions) {
            when(sectorMock.getFaction(faction.getId())).thenReturn(faction);
        }
        return sectorMock;
    }

    /**
     * One system's id paired with the markets its economy holds, so a multi-system sector can be
     * wired for the sector-wide accumulation a single-system sector cannot express.
     *
     * @param id      the system id
     * @param markets the markets the system's economy holds
     */
    record SystemMarkets(String id, List<MarketAPI> markets) {
    }

    /**
     * Pairs a system id with its markets for {@link #sectorWithSystems}.
     *
     * @param id      the system id
     * @param markets the markets the system's economy holds
     * @return the system-markets pairing
     */
    static SystemMarkets systemMarkets(String id, MarketAPI... markets) {
        return new SystemMarkets(id, List.of(markets));
    }
}
