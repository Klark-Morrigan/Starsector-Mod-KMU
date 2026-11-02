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

            // ARRANGE
            var expectedMessage = "sectorIndex";

            // ACT, ASSERT
            assertThatThrownBy(() -> new SectorWalk(null, MapVisibilityRules.BASE))
                .isInstanceOf(NullPointerException.class)
                .hasMessage(expectedMessage);
        }

        // Standing the fog in for them would turn the opener's fault into a map that quietly draws
        // less.
        @Test
        void refusesMissingRules() {

            // ARRANGE
            var expectedMessage = "visibilityRules";

            // ACT, ASSERT
            assertThatThrownBy(() -> new SectorWalk(new SectorPassIndex(null), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage(expectedMessage);
        }
    }

    @Nested
    class OpenOver {

        // The rebuild and the batch each open their walk through here, so a walk opened over a
        // sector has to answer that sector and the rules it was opened under.
        @Test
        void walksTheSectorUnderTheRulesItWasOpenedWith() {

            // ARRANGE
            var sectorMock = mock(SectorAPI.class);

            // ACT
            var actualWalk = SectorWalk.openOver(sectorMock, MapVisibilityRules.BASE);

            // ASSERT
            assertThat(actualWalk.sector())
                .isSameAs(sectorMock);
            assertThat(actualWalk.visibilityRules())
                .isSameAs(MapVisibilityRules.BASE);
        }

        // An index answers the sector as it stood when the index was made, so two walks sharing one
        // would have the second read the sector the first saw.
        @Test
        void opensEachWalkOverAnIndexOfItsOwn() {

            // ARRANGE
            var sectorMock = mock(SectorAPI.class);

            // ACT
            var actualFirstWalk = SectorWalk.openOver(sectorMock, MapVisibilityRules.BASE);
            var actualSecondWalk = SectorWalk.openOver(sectorMock, MapVisibilityRules.BASE);

            // ASSERT
            assertThat(actualSecondWalk.sectorIndex())
                .isNotSameAs(actualFirstWalk.sectorIndex());
        }
    }

    @Nested
    class Sector {

        @Test
        void answersTheSectorItsIndexWasOpenedOver() {

            // ARRANGE
            var sectorMock = mock(SectorAPI.class);
            var walk = new SectorWalk(new SectorPassIndex(sectorMock), MapVisibilityRules.BASE);

            // ACT
            var actualSector = walk.sector();

            // ASSERT
            assertThat(actualSector)
                .isSameAs(sectorMock);
        }

        @Test
        void answersNoSectorForAWalkOverNone() {

            // ARRANGE
            var walk = new SectorWalk(new SectorPassIndex(null), MapVisibilityRules.BASE);

            // ACT
            var actualSector = walk.sector();

            // ASSERT
            assertThat(actualSector)
                .isNull();
        }
    }

    @Nested
    class ReadReadingOpenedBy {

        // The owners, the per-system answers and the band count of one walk read one reading between
        // them, so a second ask is answered without opening again.
        @Test
        void opensOnceForOneOpener() {

            // ARRANGE
            var walk = new SectorWalk(new SectorPassIndex(null), MapVisibilityRules.BASE);
            var opener = new Object();
            var openings = new AtomicInteger();

            // ACT
            var actualFirstReading = walk.readReadingOpenedBy(opener, Object.class, () -> {

                openings.incrementAndGet();
                return new Object();
            });
            var actualSecondReading = walk.readReadingOpenedBy(opener, Object.class, Object::new);

            // ASSERT
            assertThat(actualSecondReading)
                .isSameAs(actualFirstReading);
            assertThat(openings)
                .hasValue(1);
        }

        // Two sources sampled under different rules are two readings, whatever their rules say. The
        // openers compare equal, so a walk keying its readings by equality would hand the second the
        // first's reading.
        @Test
        void opensApartForTwoOpeners() {

            // ARRANGE
            var walk = new SectorWalk(new SectorPassIndex(null), MapVisibilityRules.BASE);

            // ACT
            var actualFirstReading = walk.readReadingOpenedBy(new EqualOpener(), Object.class, Object::new);
            var actualSecondReading = walk.readReadingOpenedBy(new EqualOpener(), Object.class, Object::new);

            // ASSERT
            assertThat(actualSecondReading)
                .isNotSameAs(actualFirstReading);
        }

        // A reading lives as long as its walk, so the next rebuild never reads this one's.
        @Test
        void opensApartForTwoWalks() {

            // ARRANGE
            var opener = new Object();
            var firstWalk = new SectorWalk(new SectorPassIndex(null), MapVisibilityRules.BASE);
            var secondWalk = new SectorWalk(new SectorPassIndex(null), MapVisibilityRules.BASE);

            // ACT
            var actualFirstReading = firstWalk.readReadingOpenedBy(opener, Object.class, Object::new);
            var actualSecondReading = secondWalk.readReadingOpenedBy(opener, Object.class, Object::new);

            // ASSERT
            assertThat(actualSecondReading)
                .isNotSameAs(actualFirstReading);
        }
    }

    // An opener every instance of which equals every other, standing in for two sources whose
    // sampled rules compare equal.
    private record EqualOpener() {
    }
}
