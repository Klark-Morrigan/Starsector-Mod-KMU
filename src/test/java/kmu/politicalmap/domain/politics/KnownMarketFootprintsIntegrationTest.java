package kmu.politicalmap.domain.politics;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static kmu.politicalmap.domain.politics.KnownMarketFootprints.DOMINANCE_WEIGHT_SCALE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration coverage for the economy read: {@link KnownMarketFootprints}
 * folding a stubbed system's markets into per-faction footprints through the
 * known-market filter. Exercises the filter (condition-only skipped, the
 * discovery / un-hidden gate), the hidden-market token sizing, the colony-size
 * weight scaling each base rating (real size and the hidden token, but not the
 * station point), the stability scaling of each market's dominance weight - both
 * toggle states - and the station presence bonus (its weight in size points before
 * stability, the hidden-base rate, the NO_ORBITAL_STATION opt-out, and the toggle
 * off), the zero-weight short-circuits that skip the station scan and the stability
 * read, plus the presence predicate the map's inhabitation test leans on.
 */
class KnownMarketFootprintsIntegrationTest {

    private static final float FULL_STABILITY = 10.0f;
    private static final float HALF_STABILITY = 5.0f;
    // The fraction of the station size point a hidden base earns, exercised by the
    // hidden-station tests below.
    private static final double HIDDEN_STATION_RATE = 0.5;
    // The identity colony-size weight the existing variants pin behaviour at: raw
    // size counts as-is, so those tests read exactly as before the weight existed.
    private static final double DEFAULT_COLONY_SIZE_WEIGHT = 1.0;
    // A doubled colony-size weight, so a size rating (real or the hidden token)
    // contributes twice its points - the shape the colony-weight tests pin.
    private static final double DOUBLE_COLONY_SIZE_WEIGHT = 2.0;
    // The identity station weight: an attached station is worth one size point, so
    // the station variants read as a plain +1 bonus.
    private static final double DEFAULT_STATION_WEIGHT = 1.0;
    // A doubled station weight, so an attached station is worth two size points - the
    // shape the station-weight test pins.
    private static final double DOUBLE_STATION_WEIGHT = 2.0;
    // The dominance-weighting variants each readByFaction call site is exercised
    // with, named so the site says which rule it pins. Station weighting is off in
    // the two stability variants so those tests isolate the stability rule, and the
    // stability toggle is varied in the station variants for the same reason. Every
    // variant holds the colony-size and station weights at their identity so it
    // isolates its own rule.
    private static final DominanceWeighting STABILITY_WEIGHTED =
            new DominanceWeighting(DEFAULT_COLONY_SIZE_WEIGHT, true, false,
                    DEFAULT_STATION_WEIGHT, HIDDEN_STATION_RATE);
    private static final DominanceWeighting NOT_STABILITY_WEIGHTED =
            new DominanceWeighting(DEFAULT_COLONY_SIZE_WEIGHT, false, false,
                    DEFAULT_STATION_WEIGHT, HIDDEN_STATION_RATE);
    private static final DominanceWeighting STATION_AND_STABILITY_WEIGHTED =
            new DominanceWeighting(DEFAULT_COLONY_SIZE_WEIGHT, true, true,
                    DEFAULT_STATION_WEIGHT, HIDDEN_STATION_RATE);
    private static final DominanceWeighting STATION_WEIGHTED_NO_STABILITY =
            new DominanceWeighting(DEFAULT_COLONY_SIZE_WEIGHT, false, true,
                    DEFAULT_STATION_WEIGHT, HIDDEN_STATION_RATE);
    // Colony-size-weight variants: a doubled base-size weight, once isolating the
    // weight (stability on, station off) and once paired with the station bonus to
    // pin that the weight scales size but not the flat station point.
    private static final DominanceWeighting COLONY_SIZE_DOUBLE_WEIGHTED =
            new DominanceWeighting(DOUBLE_COLONY_SIZE_WEIGHT, true, false,
                    DEFAULT_STATION_WEIGHT, HIDDEN_STATION_RATE);
    private static final DominanceWeighting COLONY_SIZE_DOUBLE_WEIGHTED_WITH_STATION =
            new DominanceWeighting(DOUBLE_COLONY_SIZE_WEIGHT, false, true,
                    DEFAULT_STATION_WEIGHT, HIDDEN_STATION_RATE);
    // Station-weight variant: a doubled station weight with stability off, so a
    // stationed market folds in at its size plus two station points.
    private static final DominanceWeighting STATION_DOUBLE_WEIGHTED_NO_STABILITY =
            new DominanceWeighting(DEFAULT_COLONY_SIZE_WEIGHT, false, true,
                    DOUBLE_STATION_WEIGHT, HIDDEN_STATION_RATE);
    // Zero-weight variants pinning the short-circuits: a zeroed colony weight with
    // stability still on (to prove the stability read is skipped) and a zeroed station
    // weight with the station factor still on (to prove the station scan is skipped).
    private static final DominanceWeighting COLONY_SIZE_ZERO_WEIGHTED =
            new DominanceWeighting(0.0, true, false,
                    DEFAULT_STATION_WEIGHT, HIDDEN_STATION_RATE);
    private static final DominanceWeighting STATION_ZERO_WEIGHTED_NO_STABILITY =
            new DominanceWeighting(DEFAULT_COLONY_SIZE_WEIGHT, false, true,
                    0.0, HIDDEN_STATION_RATE);

    @Nested
    class ReadByFaction {

        @Test
        void readByFactionWeighsAKnownMarketUnderItsFaction() {
            // At full stability a market is worth its whole size on the weight
            // grid: size 5 -> 5 grid units.
            var sector = sectorWith("owned-system", visibleMarket(faction("hegemony"), 5));

            var footprints = KnownMarketFootprints.readByFaction(
                    sector, onlySystem(sector), STABILITY_WEIGHTED);

            assertThat(footprints).containsOnlyKeys("hegemony");
            assertThat(footprints.get("hegemony").totalWeight())
                    .isEqualTo(5 * DOMINANCE_WEIGHT_SCALE);
        }

        @Test
        void readByFactionScalesMarketWeightByStability() {
            // Stability scales the size rating before any footprint math: a
            // size-4 market at stability 5 contributes half its size.
            var sector = sectorWith("shaky-system",
                    marketAtStability(faction("hegemony"), 4, HALF_STABILITY));

            var footprints = KnownMarketFootprints.readByFaction(
                    sector, onlySystem(sector), STABILITY_WEIGHTED);

            assertThat(footprints.get("hegemony").totalWeight())
                    .isEqualTo(2 * DOMINANCE_WEIGHT_SCALE);
        }

        @Test
        void readByFactionIgnoresStabilityWhenWeightingIsOff() {
            // With the player's toggle off, a destabilised market folds in at its
            // full size rating - the raw-size dominance the toggle opts back into.
            var sector = sectorWith("shaky-system",
                    marketAtStability(faction("hegemony"), 4, HALF_STABILITY));

            var footprints = KnownMarketFootprints.readByFaction(
                    sector, onlySystem(sector), NOT_STABILITY_WEIGHTED);

            assertThat(footprints.get("hegemony").totalWeight())
                    .isEqualTo(4 * DOMINANCE_WEIGHT_SCALE);
        }

        @Test
        void readByFactionZeroStabilityMarketMarksPresenceAtNoWeight() {
            // At 0 stability a colony is worth nothing to dominance, but it is
            // still a known colony: the faction keeps its footprint entry, so
            // presence (and painting an unopposed system) is unaffected.
            var sector = sectorWith("collapsed-system",
                    marketAtStability(faction("hegemony"), 5, 0.0f));

            var footprints = KnownMarketFootprints.readByFaction(
                    sector, onlySystem(sector), STABILITY_WEIGHTED);

            assertThat(footprints).containsOnlyKeys("hegemony");
            assertThat(footprints.get("hegemony").totalWeight()).isZero();
        }

        @Test
        void readByFactionClampsStabilityIntoTheVanillaBand() {
            // A modded market can report stability above 10; the fraction clamps
            // to full so a market never outweighs its own size.
            var sector = sectorWith("overstable-system",
                    marketAtStability(faction("hegemony"), 3, 12.0f));

            var footprints = KnownMarketFootprints.readByFaction(
                    sector, onlySystem(sector), STABILITY_WEIGHTED);

            assertThat(footprints.get("hegemony").totalWeight())
                    .isEqualTo(3 * DOMINANCE_WEIGHT_SCALE);
        }

        @Test
        void readByFactionSkipsConditionOnlyMarket() {
            // A bare rock's condition-only placeholder is no colony, so it folds
            // into no footprint.
            var sector = sectorWith("bare-system",
                    market(faction("hegemony"), 6, true, false, false, FULL_STABILITY));

            assertThat(KnownMarketFootprints.readByFaction(
                    sector, onlySystem(sector), STABILITY_WEIGHTED)).isEmpty();
        }

        @Test
        void readByFactionCountsHiddenMarketAtTokenSizeOne() {
            // A hidden market (a concealed base) folds in at a token size rating
            // of 1 rather than its real size 5, so it flags presence without
            // skewing dominance.
            var sector = sectorWith("hidden-system", hiddenMarket(faction("hegemony"), 5));

            var footprints = KnownMarketFootprints.readByFaction(
                    sector, onlySystem(sector), STABILITY_WEIGHTED);

            assertThat(footprints.get("hegemony").totalWeight())
                    .isEqualTo(DOMINANCE_WEIGHT_SCALE);
        }

        @Test
        void readByFactionScalesHiddenTokenByStability() {
            // The hidden token is a size rating like any other, so stability
            // scales it too: a destabilised concealed base marks less presence.
            var sector = sectorWith("hidden-shaky-system",
                    hiddenMarketAtStability(faction("hegemony"), 5, HALF_STABILITY));

            var footprints = KnownMarketFootprints.readByFaction(
                    sector, onlySystem(sector), STABILITY_WEIGHTED);

            assertThat(footprints.get("hegemony").totalWeight())
                    .isEqualTo(DOMINANCE_WEIGHT_SCALE / 2);
        }

        @Test
        void readByFactionExcludesUndiscoveredStation() {
            // A concealed station (hidden market on a still-discoverable entity)
            // fails the known-market gate, so it folds into no footprint.
            var sector = sectorWith("undiscovered-system",
                    concealedStation(faction("knights_of_selkie"), 5));

            assertThat(KnownMarketFootprints.readByFaction(
                    sector, onlySystem(sector), STABILITY_WEIGHTED)).isEmpty();
        }

        @Test
        void readByFactionAddsOneSizePointForAnAttachedStation() {
            // A stationed size-5 market at full stability is worth one size point
            // more than its size alone: (5 + 1) grid units.
            var sector = sectorWith("stationed-system", stationedMarket(faction("hegemony"), 5));

            var footprints = KnownMarketFootprints.readByFaction(
                    sector, onlySystem(sector), STATION_AND_STABILITY_WEIGHTED);

            assertThat(footprints.get("hegemony").totalWeight())
                    .isEqualTo(6 * DOMINANCE_WEIGHT_SCALE);
        }

        @Test
        void readByFactionScalesTheStationBonusByStability() {
            // The station point is added before stability scales the sum, so a
            // destabilised fortress keeps only the stability-scaled remnant of it:
            // (5 + 1) at stability 5 -> 3 size points.
            var sector = sectorWith("shaky-fortress-system",
                    stationedMarketAtStability(faction("hegemony"), 5, HALF_STABILITY));

            var footprints = KnownMarketFootprints.readByFaction(
                    sector, onlySystem(sector), STATION_AND_STABILITY_WEIGHTED);

            assertThat(footprints.get("hegemony").totalWeight())
                    .isEqualTo(3 * DOMINANCE_WEIGHT_SCALE);
        }

        @Test
        void readByFactionAddsTheStationPointWithoutStabilityWeighting() {
            // With stability weighting off but station weighting on, the market
            // folds in at its full size plus the flat point: (4 + 1) grid units.
            var sector = sectorWith("stationed-system", stationedMarket(faction("hegemony"), 4));

            var footprints = KnownMarketFootprints.readByFaction(
                    sector, onlySystem(sector), STATION_WEIGHTED_NO_STABILITY);

            assertThat(footprints.get("hegemony").totalWeight())
                    .isEqualTo(5 * DOMINANCE_WEIGHT_SCALE);
        }

        @Test
        void readByFactionDropsTheStationBonusWhenStationWeightingIsOff() {
            // A stationed market read under a station-off weighting is worth exactly
            // its size, matching an unstationed colony - the +1 is gone.
            var sector = sectorWith("stationed-system", stationedMarket(faction("hegemony"), 5));

            var footprints = KnownMarketFootprints.readByFaction(
                    sector, onlySystem(sector), STABILITY_WEIGHTED);

            assertThat(footprints.get("hegemony").totalWeight())
                    .isEqualTo(5 * DOMINANCE_WEIGHT_SCALE);
        }

        @Test
        void readByFactionGivesAHiddenStationedBaseTheStationPointAtTheHiddenRate() {
            // A hidden stationed base gets the station point times the hidden rate on
            // its token rating of 1, before stability: (1 + 0.5) size points.
            var sector = sectorWith("hidden-fortress-system",
                    hiddenStationedMarket(faction("hegemony"), 5));

            var footprints = KnownMarketFootprints.readByFaction(
                    sector, onlySystem(sector), STATION_AND_STABILITY_WEIGHTED);

            assertThat(footprints.get("hegemony").totalWeight())
                    .isEqualTo(3 * DOMINANCE_WEIGHT_SCALE / 2);
        }

        @Test
        void readByFactionIgnoresAStationTaggedNoOrbitalStation() {
            // A "station"-tagged connected entity opted out via NO_ORBITAL_STATION is
            // not a market's orbital station, so it earns no bonus: size 5 alone.
            var sector = sectorWith("opted-out-system",
                    marketWithOptedOutStation(faction("hegemony"), 5));

            var footprints = KnownMarketFootprints.readByFaction(
                    sector, onlySystem(sector), STATION_AND_STABILITY_WEIGHTED);

            assertThat(footprints.get("hegemony").totalWeight())
                    .isEqualTo(5 * DOMINANCE_WEIGHT_SCALE);
        }

        @Test
        void readByFactionSkipsTheStationScanWhenTheStationWeightIsZero() {
            // A zero station weight can add nothing, so the connected-entity station
            // scan is skipped and the stationed market folds in at its size alone.
            var market = stationedMarket(faction("hegemony"), 5);
            var sector = sectorWith("zero-weight-station-system", market);

            var footprints = KnownMarketFootprints.readByFaction(
                    sector, onlySystem(sector), STATION_ZERO_WEIGHTED_NO_STABILITY);

            assertThat(footprints.get("hegemony").totalWeight())
                    .isEqualTo(5 * DOMINANCE_WEIGHT_SCALE);
            verify(market, never()).getConnectedEntities();
        }

        @Test
        void readByFactionSkipsTheStabilityReadWhenTheWeightedRatingIsZero() {
            // A zero colony-size weight with no station bonus zeroes the rating before
            // stability, so the stability read is skipped; the market still folds into
            // the footprint (marking presence) but at no dominance weight.
            var market = visibleMarket(faction("hegemony"), 5);
            var sector = sectorWith("weightless-system", market);

            var footprints = KnownMarketFootprints.readByFaction(
                    sector, onlySystem(sector), COLONY_SIZE_ZERO_WEIGHTED);

            assertThat(footprints).containsOnlyKeys("hegemony");
            assertThat(footprints.get("hegemony").totalWeight()).isZero();
            verify(market, never()).getStabilityValue();
        }

        @Test
        void readByFactionScalesTheStationBonusByTheStationWeight() {
            // The station weight sets how many size points a station is worth: at
            // weight 2 a stationed size-4 market folds in at 4 + 2 -> 6 grid units.
            var sector = sectorWith("heavy-station-system",
                    stationedMarket(faction("hegemony"), 4));

            var footprints = KnownMarketFootprints.readByFaction(
                    sector, onlySystem(sector), STATION_DOUBLE_WEIGHTED_NO_STABILITY);

            assertThat(footprints.get("hegemony").totalWeight())
                    .isEqualTo(6 * DOMINANCE_WEIGHT_SCALE);
        }

        @Test
        void readByFactionScalesVisibleMarketSizeByTheColonyWeight() {
            // The colony-size weight multiplies a visible market's raw size before
            // stability and any station bonus: size 4 at weight 2 -> 8 grid units.
            var sector = sectorWith("weighted-system", visibleMarket(faction("hegemony"), 4));

            var footprints = KnownMarketFootprints.readByFaction(
                    sector, onlySystem(sector), COLONY_SIZE_DOUBLE_WEIGHTED);

            assertThat(footprints.get("hegemony").totalWeight())
                    .isEqualTo(8 * DOMINANCE_WEIGHT_SCALE);
        }

        @Test
        void readByFactionScalesTheHiddenTokenByTheColonyWeight() {
            // The weight scales a hidden base's fixed token of 1 the same way, so a
            // concealed base of any real size contributes weight * 1: at weight 2, 2
            // grid units.
            var sector = sectorWith("weighted-hidden-system",
                    hiddenMarket(faction("hegemony"), 5));

            var footprints = KnownMarketFootprints.readByFaction(
                    sector, onlySystem(sector), COLONY_SIZE_DOUBLE_WEIGHTED);

            assertThat(footprints.get("hegemony").totalWeight())
                    .isEqualTo(2 * DOMINANCE_WEIGHT_SCALE);
        }

        @Test
        void readByFactionAppliesTheColonyWeightToSizeButNotTheStationPoint() {
            // The weight multiplies base size only; the station point is added after,
            // unweighted: (4 * 2) + 1 -> 9 grid units, not (4 + 1) * 2.
            var sector = sectorWith("weighted-fortress-system",
                    stationedMarket(faction("hegemony"), 4));

            var footprints = KnownMarketFootprints.readByFaction(
                    sector, onlySystem(sector), COLONY_SIZE_DOUBLE_WEIGHTED_WITH_STATION);

            assertThat(footprints.get("hegemony").totalWeight())
                    .isEqualTo(9 * DOMINANCE_WEIGHT_SCALE);
        }

        @Test
        void readByFactionIncludesAnUndiscoveredStationWhenIncludingUndiscoveredMarkets() {
            // The show-all-factions dev reveal drops the known-to-player gate, so a
            // concealed station folds in at its hidden token size of 1 rather than being
            // skipped as it is under the normal filter.
            var sector = sectorWith("undiscovered-system",
                    concealedStation(faction("knights_of_selkie"), 5));

            var footprints = KnownMarketFootprints.readByFaction(
                    sector, onlySystem(sector), STABILITY_WEIGHTED, true);

            assertThat(footprints).containsOnlyKeys("knights_of_selkie");
            assertThat(footprints.get("knights_of_selkie").totalWeight())
                    .isEqualTo(DOMINANCE_WEIGHT_SCALE);
        }
    }

    @Nested
    class HasKnownOwnedMarket {

        @Test
        void hasKnownOwnedMarketIsTrueForVisibleOwnedMarket() {
            var sector = sectorWith("owned-system", visibleMarket(faction("hegemony"), 5));

            assertThat(KnownMarketFootprints.hasKnownOwnedMarket(sector, onlySystem(sector))).isTrue();
        }

        @Test
        void hasKnownOwnedMarketIsTrueForZeroStabilityMarket() {
            // Presence is about knowing the colony exists, not its worth: a
            // weightless (stability 0) colony still inhabits its system.
            var sector = sectorWith("collapsed-system",
                    marketAtStability(faction("hegemony"), 5, 0.0f));

            assertThat(KnownMarketFootprints.hasKnownOwnedMarket(sector, onlySystem(sector))).isTrue();
        }

        @Test
        void hasKnownOwnedMarketIsFalseForConditionOnlyMarket() {
            // A bare rock's condition-only market is no colony, so the system has
            // no faction presence.
            var sector = sectorWith("bare-system",
                    market(faction("hegemony"), 6, true, false, false, FULL_STABILITY));

            assertThat(KnownMarketFootprints.hasKnownOwnedMarket(sector, onlySystem(sector))).isFalse();
        }

        @Test
        void hasKnownOwnedMarketIsFalseForUndiscoveredStation() {
            // A concealed station the player has not found yet - hidden market on a
            // still-discoverable entity - is absent from the map, so it confers no
            // presence.
            var sector = sectorWith("undiscovered-system",
                    concealedStation(faction("knights_of_selkie"), 5));

            assertThat(KnownMarketFootprints.hasKnownOwnedMarket(sector, onlySystem(sector))).isFalse();
        }

        @Test
        void hasKnownOwnedMarketIsTrueForRevealedColonyAwaitingApproach() {
            // A colony un-hidden ahead of its entity being found is public
            // knowledge, so it confers presence before the fleet closes in.
            var sector = sectorWith("revealed-system",
                    revealedColonyAwaitingApproach(faction("aEP_FSF"), 5));

            assertThat(KnownMarketFootprints.hasKnownOwnedMarket(sector, onlySystem(sector))).isTrue();
        }

        @Test
        void hasKnownOwnedMarketIsFalseForUninhabitedSystem() {
            var sector = sectorWith("empty-system");

            assertThat(KnownMarketFootprints.hasKnownOwnedMarket(sector, onlySystem(sector))).isFalse();
        }

        @Test
        void hasKnownOwnedMarketIsFalseForNullSector() {
            assertThat(KnownMarketFootprints.hasKnownOwnedMarket(null, mock(StarSystemAPI.class)))
                    .isFalse();
        }

        @Test
        void hasKnownOwnedMarketIsTrueForUndiscoveredStationWhenIncludingUndiscoveredMarkets() {
            // With the show-all-factions dev reveal the concealed station counts as
            // presence, though it fails the normal known-to-player gate.
            var sector = sectorWith("undiscovered-system",
                    concealedStation(faction("knights_of_selkie"), 5));

            assertThat(KnownMarketFootprints.hasKnownOwnedMarket(
                    sector, onlySystem(sector), true)).isTrue();
        }
    }

    private static StarSystemAPI onlySystem(SectorAPI sector) {
        return sector.getStarSystems().get(0);
    }

    private static MarketAPI visibleMarket(FactionAPI faction, int size) {
        return market(faction, size, false, false, false, FULL_STABILITY);
    }

    // A visible owned market at the given stability, the shape the weight
    // scaling tests vary.
    private static MarketAPI marketAtStability(FactionAPI faction, int size, float stability) {
        return market(faction, size, false, false, false, stability);
    }

    // A hidden market (vanilla concealed base) on a discovered entity: it still
    // marks its system, but folds into dominance at a token size rating of 1.
    private static MarketAPI hiddenMarket(FactionAPI faction, int size) {
        return market(faction, size, false, true, false, FULL_STABILITY);
    }

    // A hidden market at the given stability, for pinning that the token rating
    // scales with stability like any other size rating.
    private static MarketAPI hiddenMarketAtStability(FactionAPI faction, int size,
            float stability) {
        return market(faction, size, false, true, false, stability);
    }

    // A concealed station: a hidden market on a still-discoverable entity, the
    // shape a base wears before the player finds it. Fails both known-market arms,
    // so it confers no presence until discovery un-hides or reveals it.
    private static MarketAPI concealedStation(FactionAPI faction, int size) {
        return market(faction, size, false, true, true, FULL_STABILITY);
    }

    // A colony surfaced ahead of its entity being physically found: the market
    // un-hidden but the entity still discoverable. Public knowledge already, so it
    // counts as presence (FSF's DWR43 colonies between entry and approach).
    private static MarketAPI revealedColonyAwaitingApproach(FactionAPI faction, int size) {
        return market(faction, size, false, false, true, FULL_STABILITY);
    }

    // A visible owned market that owns an attached defensive station - a
    // "station"-tagged connected entity, the ownership link vanilla itself reads.
    private static MarketAPI stationedMarket(FactionAPI faction, int size) {
        return withConnectedEntities(visibleMarket(faction, size), stationEntity());
    }

    // A stationed market at the given stability, for pinning that the station point
    // is scaled by stability along with the rest of the size.
    private static MarketAPI stationedMarketAtStability(FactionAPI faction, int size,
            float stability) {
        return withConnectedEntities(marketAtStability(faction, size, stability), stationEntity());
    }

    // A hidden (concealed) base that owns a station, for pinning that a hidden
    // stationed base earns the station point at the hidden rate on its token rating.
    private static MarketAPI hiddenStationedMarket(FactionAPI faction, int size) {
        return withConnectedEntities(hiddenMarket(faction, size), stationEntity());
    }

    // A market whose only connected "station"-tagged entity is opted out via
    // NO_ORBITAL_STATION, so it is not the market's orbital station and earns no bonus.
    private static MarketAPI marketWithOptedOutStation(FactionAPI faction, int size) {
        return withConnectedEntities(visibleMarket(faction, size), optedOutStationEntity());
    }

    // Stubs the market's connected entities - the ownership link the station scan
    // reads - to the given entities.
    private static MarketAPI withConnectedEntities(MarketAPI market,
            SectorEntityToken... entities) {
        when(market.getConnectedEntities()).thenReturn(Set.of(entities));
        return market;
    }

    // A station entity: carries the "station" tag and no opt-out, so the scan counts
    // it as the market's orbital station.
    private static SectorEntityToken stationEntity() {
        var entityMock = mock(SectorEntityToken.class);
        when(entityMock.hasTag(Tags.STATION)).thenReturn(true);
        return entityMock;
    }

    // A "station"-tagged entity flagged NO_ORBITAL_STATION, vanilla's own opt-out, so
    // the scan skips it.
    private static SectorEntityToken optedOutStationEntity() {
        var entityMock = stationEntity();
        when(entityMock.hasTag("NO_ORBITAL_STATION")).thenReturn(true);
        return entityMock;
    }

    private static MarketAPI market(FactionAPI faction, int size, boolean isConditionOnly,
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

    private static FactionAPI faction(String id) {
        var factionMock = mock(FactionAPI.class);
        when(factionMock.getId()).thenReturn(id);
        return factionMock;
    }

    // Wires a sector with one system whose economy holds the given markets. No
    // faction palette or neutral faction is stubbed: the footprint read keys on
    // each market's own faction id and never resolves colors.
    private static SectorAPI sectorWith(String systemId, MarketAPI... markets) {
        var systemMock = mock(StarSystemAPI.class);
        when(systemMock.getId()).thenReturn(systemId);
        var economyMock = mock(EconomyAPI.class);
        when(economyMock.getMarkets(systemMock)).thenReturn(List.of(markets));
        var sectorMock = mock(SectorAPI.class);
        when(sectorMock.getStarSystems()).thenReturn(List.of(systemMock));
        when(sectorMock.getEconomy()).thenReturn(economyMock);
        return sectorMock;
    }
}
