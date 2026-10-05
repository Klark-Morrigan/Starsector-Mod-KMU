package kmu.maplayers.ownermap.owners;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.systems.SectorPassIndex;

import kmu.maplayers.base.visibility.systems.MapVisibilityRules;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Pins the walk a rebuild hands a layer's owner source: both halves are required, the sector it
 * names is its index's own, so a walk cannot name one sector while answering out of another, and a
 * reading opened over it is one per opener for the walk's life.
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

    @Nested
    class ReadReadingOpenedBy {

        @Test
        void opensOnceForOneOpener() {
            // The owners, the per-system answers and the band count of one walk read one reading
            // between them, so a second ask is answered without opening again.
            var walk = new SectorWalk(new SectorPassIndex(null), MapVisibilityRules.BASE);
            var opener = new Object();
            var openings = new AtomicInteger();

            var first = walk.readReadingOpenedBy(opener, Object.class, () -> {
                openings.incrementAndGet();
                return new Object();
            });
            var second = walk.readReadingOpenedBy(opener, Object.class, Object::new);

            assertThat(second).isSameAs(first);
            assertThat(openings).hasValue(1);
        }

        @Test
        void opensApartForTwoOpeners() {
            // Two sources sampled under different rules are two readings, whatever their rules say.
            var walk = new SectorWalk(new SectorPassIndex(null), MapVisibilityRules.BASE);

            var first = walk.readReadingOpenedBy(new Object(), Object.class, Object::new);
            var second = walk.readReadingOpenedBy(new Object(), Object.class, Object::new);

            assertThat(second).isNotSameAs(first);
        }

        @Test
        void opensApartForTwoWalks() {
            // A reading lives as long as its walk, so the next rebuild never reads this one's.
            var opener = new Object();

            var first = new SectorWalk(new SectorPassIndex(null), MapVisibilityRules.BASE)
                .readReadingOpenedBy(opener, Object.class, Object::new);
            var second = new SectorWalk(new SectorPassIndex(null), MapVisibilityRules.BASE)
                .readReadingOpenedBy(opener, Object.class, Object::new);

            assertThat(second).isNotSameAs(first);
        }
    }
}
