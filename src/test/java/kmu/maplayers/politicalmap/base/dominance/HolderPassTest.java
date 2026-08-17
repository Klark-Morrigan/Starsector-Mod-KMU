package kmu.maplayers.politicalmap.base.dominance;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.systems.SystemColoniesIndex;

import kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Unit coverage for {@link HolderPass}: the construction guard, and that the pass names the sector
 * its own walk was opened over rather than one carried beside it.
 *
 * <p>The naming matters because every resolver behind the holder seam takes its sector from here.
 * A pass that could report one sector while answering colonies out of another would let a resolver
 * walk the systems of one sector and price them against a second - which with one sector in play
 * would show as nothing at all.
 *
 * <p>What the per-system colony read answers is covered where it is consumed, through the resolves
 * in the {@code base.politics} integration suites.
 */
final class HolderPassTest {

    private static final String SYSTEM_ID = "corvus";

    // The one owner the projection cases read a colony back for.
    private static final FactionAPI HEGEMONY_FACTION = SectorPoliticsFixtures
        .buildFaction("hegemony");

    // The size every posed colony carries. The projection reads ownership and discovery, so a case
    // varying size would vary nothing it can see.
    private static final int COLONY_SIZE = 5;

    @Nested
    class Constructor {

        @Test
        void rejectsNullGrouping() {

            assertThatThrownBy(() ->
                    new HolderPass(null, false, new SystemColoniesIndex(null)))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void rejectsNullColonies() {
            // A pass with no walk behind it would fault on the first system it read rather than
            // here, and a pass over a sector that cannot be reached is a different thing entirely -
            // an index over a null sector, which answers an empty set and is perfectly legal.
            assertThatThrownBy(() ->
                    new HolderPass(HolderGrouping.identity(), false, null))
                .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class ReadKnownColoniesIn {

        @Test
        void withholdsAColonyThePlayerHasNotFound() {
            // The projection every display reader below takes, stated where it is named: a pass with
            // the fog in force reports the colonies the player may be shown and no others, so a
            // band, a fill and a box over one cell cannot each withhold a different set.
            var sector = SectorPoliticsFixtures.buildSectorWith(
                SYSTEM_ID,
                SectorPoliticsFixtures.buildVisibleMarket(HEGEMONY_FACTION, COLONY_SIZE),
                SectorPoliticsFixtures.buildUndiscoveredHiddenMarket(
                    SectorPoliticsFixtures.buildFaction("tritachyon"),
                    COLONY_SIZE));

            var knownColonies = HolderPass
                .over(sector, false, HolderGrouping.identity())
                .readKnownColoniesIn(SectorPoliticsFixtures.buildOnlySystem(sector));

            assertThat(knownColonies)
                .extracting(colony -> colony.market().getFaction().getId())
                .containsExactly("hegemony");
        }

        @Test
        void reportsAnUnfoundColonyWhereTheRevealLiftsTheFog() {
            // The same pass with the dev reveal on, which is the one knob the projection reads.
            var sector = SectorPoliticsFixtures.buildSectorWith(
                SYSTEM_ID,
                SectorPoliticsFixtures.buildUndiscoveredHiddenMarket(
                    HEGEMONY_FACTION,
                    COLONY_SIZE));

            var knownColonies = HolderPass
                .over(sector, true, HolderGrouping.identity())
                .readKnownColoniesIn(SectorPoliticsFixtures.buildOnlySystem(sector));

            assertThat(knownColonies)
                .hasSize(1);
        }

        @Test
        void reportsNoColoniesForASystemThatIsNotThere() {
            // The null-system answer every read on the pass holds to, so a reader handed a system
            // the sector no longer lists is not obliged to guard before asking.
            assertThat(HolderPass
                    .over(mock(SectorAPI.class), false, HolderGrouping.identity())
                    .readKnownColoniesIn(null))
                .isEmpty();
        }
    }

    @Nested
    class Sector {

        @Test
        void namesTheSectorTheWalkWasOpenedOver() {

            var sectorMock = mock(SectorAPI.class);

            assertThat(HolderPass.over(sectorMock, false, HolderGrouping.identity()).sector())
                .isSameAs(sectorMock);
        }

        @Test
        void namesNoSectorForAPassOverNone() {
            // The unreachable-sector case every resolve already guards on, reported rather than
            // stood in for.
            assertThat(HolderPass.over(null, false, HolderGrouping.identity()).sector())
                .isNull();
        }
    }
}
