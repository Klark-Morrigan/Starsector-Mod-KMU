package kmu.maplayers.politicalmap.base.dominance;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.systems.SystemColoniesIndex;

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
