package kmu.maplayers.ownermap.owners;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.systems.SectorPassIndex;

import kmu.maplayers.base.visibility.systems.MapVisibilityRules;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Pins the walk a rebuild hands a layer's owner source: both halves are required, and the sector it
 * names is its index's own, so a walk cannot name one sector while answering out of another.
 */
final class SectorWalkTests {

    @Nested
    class Constructor {

        @Test
        void refusesAMissingIndex() {

            assertThatThrownBy(() -> new SectorWalk(null, MapVisibilityRules.BASE))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("sectorIndex");
        }

        @Test
        void refusesMissingRules() {
            // Standing the fog in for them would turn the opener's fault into a map that quietly
            // draws less.
            assertThatThrownBy(() -> new SectorWalk(new SectorPassIndex(null), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("visibilityRules");
        }
    }

    @Nested
    class Sector {

        @Test
        void answersTheSectorItsIndexWasOpenedOver() {

            var sectorMock = mock(SectorAPI.class);

            assertThat(new SectorWalk(new SectorPassIndex(sectorMock), MapVisibilityRules.BASE).sector())
                .isSameAs(sectorMock);
        }

        @Test
        void answersNoSectorForAWalkOverNone() {

            assertThat(new SectorWalk(new SectorPassIndex(null), MapVisibilityRules.BASE).sector())
                .isNull();
        }
    }
}
