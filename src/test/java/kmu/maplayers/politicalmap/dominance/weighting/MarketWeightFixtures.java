package kmu.maplayers.politicalmap.dominance.weighting;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CustomEntitySpecAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.StatBonus;
import com.fs.starfarer.api.fleet.MutableMarketStatsAPI;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.util.DynamicStatsAPI;

import kmu.settings.HiddenMarketScalingChoice;

import java.awt.Color;
import java.util.Set;

import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.buildHiddenMarket;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.buildMarketAtStability;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.buildVisibleMarket;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Shared wiring for the suites that weigh a colony: the rules it is weighed under, and the
 * stations, patrols, names and glyphs a weight reads off a stubbed market.
 *
 * <p>One market's arithmetic is pinned against these shapes on its own, and the fold over a
 * system is pinned against the same shapes, so both read one economy rather than two copies of it.
 */
final class MarketWeightFixtures {

    // The name every stubbed station answers to, so a breakdown naming its station has something
    // to name.
    static final String STATION_NAME = "Fort Ludd";

    private MarketWeightFixtures() {
    }

    // A fluent builder over DominanceRules and its three weight factors, defaulting every knob to its
    // CSV default so each case states only the axis it pins. The defaults are: colony-size weight 1
    // (raw size), hidden markets Fixed at token weight 1, the stability master on with a full (1.0)
    // colony penalty, stations off (weight 1, hidden rate 0.5, penalty 0.5), and patrols off
    // (0.25/0.5/1 tier weights, penalty 0.5).
    static final class RulesBuilder {

        private double colonySizeWeight = 1.0;
        private HiddenMarketScalingChoice hiddenMarketScaling = HiddenMarketScalingChoice.FIXED;
        private double hiddenMarketFixedWeight = 1.0;
        private boolean stabilityMaster = true;
        private double normalPenalty = 1.0;
        private boolean stationOn = false;
        private double stationWeight = 1.0;
        private double stationHiddenRate = 0.5;
        private double stationPenalty = 0.5;
        private boolean patrolOn = false;
        private double patrolSmall = 0.25;
        private double patrolMedium = 0.5;
        private double patrolLarge = 1.0;
        private double patrolPenalty = 0.5;

        RulesBuilder withColonyWeight(double value) {

            colonySizeWeight = value;

            return this;
        }

        RulesBuilder withHiddenMarketScaling(HiddenMarketScalingChoice value) {

            hiddenMarketScaling = value;

            return this;
        }

        RulesBuilder withHiddenMarketFixedWeight(double value) {

            hiddenMarketFixedWeight = value;

            return this;
        }

        RulesBuilder withStabilityMaster(boolean value) {

            stabilityMaster = value;

            return this;
        }

        RulesBuilder withNormalPenalty(double value) {

            normalPenalty = value;

            return this;
        }

        RulesBuilder withStationWeighting() {

            stationOn = true;

            return this;
        }

        RulesBuilder withStationWeight(double value) {

            stationWeight = value;

            return this;
        }

        RulesBuilder withPatrolWeighting() {

            patrolOn = true;

            return this;
        }

        DominanceRules build() {

            return new DominanceRules(
                stabilityMaster,
                new BaseSizeWeighting(
                    colonySizeWeight,
                    hiddenMarketScaling,
                    hiddenMarketFixedWeight,
                    normalPenalty),
                new StationWeighting(
                    stationOn,
                    stationWeight,
                    stationHiddenRate,
                    stationPenalty),
                new PatrolWeighting(
                    patrolOn,
                    patrolSmall,
                    patrolMedium,
                    patrolLarge,
                    patrolPenalty));
        }
    }

    static RulesBuilder buildRules() {

        return new RulesBuilder();
    }

    // A visible fortress colony at the given stability: one colony that owns a station and fields
    // patrols at once, so a single read exercises all three weight factors and the stability cut
    // each takes.
    static MarketAPI buildFortifiedColonyAtStability(
            FactionAPI faction,
            int size,
            int small,
            int medium,
            int large,
            float stability) {

        return withConnectedEntities(
            buildPatrolMarketAtStability(faction, size, small, medium, large, stability),
            buildStationEntity());
    }

    // Stubs a market's display name - the label a breakdown carries its parts under.
    static MarketAPI withName(MarketAPI market, String name) {

        when(market.getName())
            .thenReturn(name);

        return market;
    }

    // Stubs the glyph the sector map marks a market's own entity with. The plain stubbed market's
    // entity carries no spec at all, so a colony is unmarked unless a case says otherwise.
    static MarketAPI withMapIcon(MarketAPI market, String iconName, Color iconColour) {

        // Read the entity before opening its own stubbing, so the two do not nest into an
        // unfinished-stubbing error.
        var entityMock = market.getPrimaryEntity();

        withEntityMapIcon(entityMock, iconName, iconColour);

        return market;
    }

    // A visible owned market that owns an attached defensive station - a "station"-tagged
    // connected entity, the holding link vanilla itself reads.
    static MarketAPI buildStationedMarket(FactionAPI faction, int size) {

        return withConnectedEntities(buildVisibleMarket(faction, size), buildStationEntity());
    }

    // A visible owned market whose attached station the map marks with a glyph of its own, for
    // pinning that the station part carries the station's icon and not the colony's.
    static MarketAPI buildStationedMarketWithMarkedStation(
            FactionAPI faction,
            int size,
            String iconName,
            Color iconColour) {

        return withConnectedEntities(
            buildVisibleMarket(faction, size),
            withEntityMapIcon(buildStationEntity(), iconName, iconColour));
    }

    // A stationed market at the given stability, for pinning the station factor's own
    // low-stability penalty.
    static MarketAPI buildStationedMarketAtStability(FactionAPI faction, int size, float stability) {

        return withConnectedEntities(buildMarketAtStability(faction, size, stability), buildStationEntity());
    }

    // A hidden market that owns a station, for pinning that it earns the station point at the
    // hidden rate on its token rating.
    static MarketAPI buildHiddenStationedMarket(FactionAPI faction, int size) {

        return withConnectedEntities(buildHiddenMarket(faction, size), buildStationEntity());
    }

    // A market whose only connected "station"-tagged entity is opted out via NO_ORBITAL_STATION,
    // so it is not the market's orbital station and earns no bonus.
    static MarketAPI buildMarketWithOptedOutStation(FactionAPI faction, int size) {

        return withConnectedEntities(buildVisibleMarket(faction, size), buildOptedOutStationEntity());
    }

    // A market that is itself a station: its only connected "station"-tagged entity raises no
    // station fleet, so the place is built on a station rather than defended by one and earns no
    // bonus.
    static MarketAPI buildStationSitedMarket(FactionAPI faction, int size) {

        return withConnectedEntities(buildVisibleMarket(faction, size), buildFleetlessStationEntity());
    }

    // A visible owned market that fields the given small/medium/large patrol counts, stubbed onto
    // its dynamic stats the way vanilla's military industries write them, with the $patrol flag set
    // so it reads as garrisoned by a functional patrol HQ.
    static MarketAPI buildPatrolMarket(
            FactionAPI faction,
            int size,
            int small,
            int medium,
            int large) {

        return withPatrols(buildVisibleMarket(faction, size), small, medium, large);
    }

    // A patrolling market at the given stability, for pinning the patrol factor's own low-stability
    // penalty.
    static MarketAPI buildPatrolMarketAtStability(
            FactionAPI faction,
            int size,
            int small,
            int medium,
            int large,
            float stability) {

        return withPatrols(buildMarketAtStability(faction, size, stability), small, medium, large);
    }

    // A market carrying the patrol-count stats but no $patrol flag - a hidden raider or pather base,
    // which writes the tier counts without a functional patrol HQ. Pins that the patrol contribution
    // gates on the flag, not the raw counts.
    static MarketAPI buildPatrolStatOnlyMarket(
            FactionAPI faction,
            int size,
            int small,
            int medium,
            int large) {

        return withPatrolStats(buildVisibleMarket(faction, size), small, medium, large);
    }

    // Stubs the glyph the sector map marks any entity with - a custom-entity spec's authored path and
    // colour, which is where vanilla keeps a station's icon. Shared by the colony and station shapes,
    // so the two are marked one way and a case reading one against the other is comparing like values.
    private static SectorEntityToken withEntityMapIcon(
            SectorEntityToken entity,
            String iconName,
            Color iconColour) {

        var entitySpecMock = mock(CustomEntitySpecAPI.class);

        when(entitySpecMock.getIconName())
            .thenReturn(iconName);
        when(entitySpecMock.getIconColor())
            .thenReturn(iconColour);

        when(entity.getCustomEntitySpec())
            .thenReturn(entitySpecMock);

        return entity;
    }

    // Stubs the market's connected entities - the holding link the station scan reads - to the
    // given entities.
    private static MarketAPI withConnectedEntities(
            MarketAPI market,
            SectorEntityToken... entities) {

        when(market.getConnectedEntities())
            .thenReturn(Set.of(entities));

        return market;
    }

    // A market a patrol HQ garrisons: the patrol-tier stats it writes plus the $patrol flag it sets,
    // the pair the gated patrol contribution requires together.
    private static MarketAPI withPatrols(MarketAPI market, int small, int medium, int large) {

        return withPatrolFlag(withPatrolStats(market, small, medium, large));
    }

    // Stubs the market's dynamic stats to report the given patrol-tier counts, the seam the patrol
    // read walks (getStats, getDynamic, then getMod(tier).computeEffective). Stats only: a raider
    // base writes these without the $patrol flag.
    private static MarketAPI withPatrolStats(MarketAPI market, int small, int medium, int large) {

        // Build each tier's mock before the getMod stubbing: buildPatrolMod() stubs a mock of its
        // own, and Mockito rejects a nested when(...) inside a thenReturn(...).
        var smallMod = buildPatrolMod(small);
        var mediumMod = buildPatrolMod(medium);
        var largeMod = buildPatrolMod(large);
        var dynamicMock = mock(DynamicStatsAPI.class);

        when(dynamicMock.getMod(Stats.PATROL_NUM_LIGHT_MOD))
            .thenReturn(smallMod);
        when(dynamicMock.getMod(Stats.PATROL_NUM_MEDIUM_MOD))
            .thenReturn(mediumMod);
        when(dynamicMock.getMod(Stats.PATROL_NUM_HEAVY_MOD))
            .thenReturn(largeMod);

        var statsMock = mock(MutableMarketStatsAPI.class);

        when(statsMock.getDynamic())
            .thenReturn(dynamicMock);

        when(market.getStats())
            .thenReturn(statsMock);

        return market;
    }

    // Stubs the market's $patrol flag on, the signal a functional patrol HQ sets and the gate the
    // patrol contribution requires alongside the patrol-count stats.
    private static MarketAPI withPatrolFlag(MarketAPI market) {

        var memoryMock = mock(MemoryAPI.class);

        when(memoryMock.getBoolean(MemFlags.MARKET_PATROL))
            .thenReturn(true);

        when(market.getMemoryWithoutUpdate())
            .thenReturn(memoryMock);

        return market;
    }

    // A patrol-count mod whose effective value at base 0 is the given count.
    private static StatBonus buildPatrolMod(float effective) {

        var modMock = mock(StatBonus.class);

        when(modMock.computeEffective(0.0f))
            .thenReturn(effective);

        return modMock;
    }

    // A station entity: carries the "station" tag, no opt-out, and the station fleet an
    // orbital-station industry raises, so the scan counts it as the market's orbital station.
    private static SectorEntityToken buildStationEntity() {

        return buildStationTaggedEntity(mock(CampaignFleetAPI.class));
    }

    // A "station"-tagged entity raising no station fleet: what the place is, not that it defends
    // anything, so the scan passes over it and no station bonus is earned.
    private static SectorEntityToken buildFleetlessStationEntity() {

        return buildStationTaggedEntity(null);
    }

    // A "station"-tagged entity flagged NO_ORBITAL_STATION, vanilla's own opt-out, so the scan
    // skips it.
    private static SectorEntityToken buildOptedOutStationEntity() {

        var entityMock = buildStationEntity();

        when(entityMock.hasTag("NO_ORBITAL_STATION"))
            .thenReturn(true);

        return entityMock;
    }

    // The shared wiring behind the station shapes: the tag, the name the breakdown labels the
    // factor with, and whatever the entity's memory answers for the station-fleet key. Vanilla's
    // fleet read is a memory lookup the scan runs on every tagged entity, so the memory is always
    // stubbed even where the fleet itself is absent.
    private static SectorEntityToken buildStationTaggedEntity(CampaignFleetAPI stationFleet) {

        var memoryMock = mock(MemoryAPI.class);

        when(memoryMock.get(MemFlags.STATION_FLEET))
            .thenReturn(stationFleet);

        var entityMock = mock(SectorEntityToken.class);

        when(entityMock.hasTag(Tags.STATION))
            .thenReturn(true);
        when(entityMock.getMemoryWithoutUpdate())
            .thenReturn(memoryMock);
        when(entityMock.getName())
            .thenReturn(STATION_NAME);

        return entityMock;
    }
}
