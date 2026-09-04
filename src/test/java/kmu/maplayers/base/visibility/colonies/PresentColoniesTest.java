package kmu.maplayers.base.visibility.colonies;

import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import kmlib.testfixtures.starsector.colonies.ColonyMarketFixture;
import kmlib.testfixtures.starsector.colonies.ColonyPlacementFixture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the walk a reconciliation asks before it sheds anything: which colonies the sector still
 * holds at all.
 *
 * <p>Every case poses a colony one of the ways the sector can hold one - listed by the economy,
 * hung on an entity it never registered, standing in hyperspace - because what the walk is for is
 * that no listing is forgotten. A suite posing only economy-listed colonies would read the same
 * against a walk that found half of them.
 *
 * <p>What a colony <em>is</em> does not matter here, so the shapes are whatever
 * {@link ColonyMarketFixture} builds most plainly: the walk asks what the sector holds rather than
 * what may be shown of it, and a gate's own rules are read nowhere in it.
 */
final class PresentColoniesTest {

    private static final String HYPERSPACE_COLONY_ID = "abyssal_station";
    private static final String LISTED_COLONY_ID = "jangala";
    private static final String UNLISTED_COLONY_ID = "sentinel_gantries";

    private EconomyAPI economyMock;
    private LocationAPI hyperspaceMock;
    private SectorAPI sectorMock;
    private StarSystemAPI systemMock;

    @BeforeEach
    void setUp() {

        economyMock = mock(EconomyAPI.class);
        hyperspaceMock = mock(LocationAPI.class);
        sectorMock = mock(SectorAPI.class);
        systemMock = mock(StarSystemAPI.class);

        when(sectorMock.getEconomy())
            .thenReturn(economyMock);
        when(sectorMock.getHyperspace())
            .thenReturn(hyperspaceMock);
        when(sectorMock.getStarSystems())
            .thenReturn(List.of(systemMock));
    }

    @Nested
    class ReadColonyIds {

        @Test
        void readsAColonyTheEconomyLists() {

            ColonyPlacementFixture.listColonies(
                economyMock,
                systemMock,
                nameColony(ColonyMarketFixture.buildVisibleColony("hegemony"), LISTED_COLONY_ID));

            assertThat(PresentColonies.readColonyIds(sectorMock))
                .containsExactly("jangala");
        }

        @Test
        void readsAColonyHungOnAnEntityTheEconomyNeverRegistered() {
            // The listing a sighting is most often about: a derelict station the economy has never
            // heard of, which a walk over the economy alone would shed the moment a save loaded.
            ColonyPlacementFixture.hangColoniesOnEntitiesIn(
                systemMock,
                nameColony(ColonyMarketFixture.buildDerelictStation(), UNLISTED_COLONY_ID));

            assertThat(PresentColonies.readColonyIds(sectorMock))
                .containsExactly("sentinel_gantries");
        }

        @Test
        void readsAColonyStandingInHyperspace() {
            // Nothing is ever recorded out there - a colony outside a star system reads sighted
            // whatever the register says - but a sighting made before one moved out there is still
            // about a colony the sector holds, and shedding it would be wrong.
            ColonyPlacementFixture.hangColoniesOnEntitiesIn(
                hyperspaceMock,
                nameColony(ColonyMarketFixture.buildDerelictStation(), HYPERSPACE_COLONY_ID));

            assertThat(PresentColonies.readColonyIds(sectorMock))
                .containsExactly("abyssal_station");
        }

        @Test
        void readsEveryColonyOnceWhereBothListingsHoldIt() {
            // The economy and the entity walk overlap on a registered colony, and an id counted
            // twice would say nothing different - so the answer is a set rather than a tally.
            var colony = nameColony(
                ColonyMarketFixture.buildVisibleColony("hegemony"),
                LISTED_COLONY_ID);

            ColonyPlacementFixture.listColonies(economyMock, systemMock, colony);
            ColonyPlacementFixture.hangColoniesOnEntitiesIn(systemMock, colony);

            assertThat(PresentColonies.readColonyIds(sectorMock))
                .containsExactly("jangala");
        }

        @Test
        void readsNothingOfAColonyTheGameNamesWithNothing() {
            // A sighting is filed under the very id this walk is compared against, so a colony with
            // no id can hold none - and putting a null into the answer would only shed by accident.
            ColonyPlacementFixture.hangColoniesOnEntitiesIn(
                systemMock,
                nameColony(ColonyMarketFixture.buildDerelictStation(), null));

            assertThat(PresentColonies.readColonyIds(sectorMock))
                .isEmpty();
        }

        @Test
        void readsHyperspaceWhereTheSectorListsNoStarSystemAtAll() {
            // A sector mid-teardown answers nothing for its systems. Faulting there would take down
            // a load, and skipping hyperspace with it would shed every sighting the register holds.
            when(sectorMock.getStarSystems())
                .thenReturn(null);

            ColonyPlacementFixture.hangColoniesOnEntitiesIn(
                hyperspaceMock,
                nameColony(ColonyMarketFixture.buildDerelictStation(), HYPERSPACE_COLONY_ID));

            assertThat(PresentColonies.readColonyIds(sectorMock))
                .containsExactly("abyssal_station");
        }

        @Test
        void readsNothingWhereThereIsNoSectorToWalk() {
            // Nothing rather than a fault, since the caller shedding by this answer would otherwise
            // take a load down over a sector it never had.
            assertThat(PresentColonies.readColonyIds(null))
                .isEmpty();
        }
    }

    // The id a sighting is kept against. Given here rather than by the colony builders, none of
    // which needs one - only a register does, and only because a stored entry has to be named.
    private static MarketAPI nameColony(MarketAPI colony, String colonyId) {

        when(colony.getId())
            .thenReturn(colonyId);

        return colony;
    }
}
