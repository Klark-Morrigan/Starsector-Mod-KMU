package kmu.maplayers.ownermap.owners.holders.base;

import com.fs.starfarer.api.campaign.econ.MarketAPI;

import kmlib.starsector.markets.colonies.Colony;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for {@link BlocHabitation}: that a colony adds one to the count and its own size to
 * the sum, and that two members' habitations merge field by field.
 */
final class BlocHabitationTests {

    @Nested
    class AddColony {

        @Test
        void countsOneColonyAtItsOwnSize() {
            // The count is what the band draws and the size is what the picker shows, so a colony
            // of any size is still one segment.
            assertThat(BlocHabitation.EMPTY.addColony(buildColonyOfSize(6)))
                .isEqualTo(new BlocHabitation(1, 6));
        }

        @Test
        void addsToWhatTheBlocAlreadyLivesOn() {

            assertThat(new BlocHabitation(2, 7).addColony(buildColonyOfSize(3)))
                .isEqualTo(new BlocHabitation(3, 10));
        }
    }

    @Nested
    class AddHabitation {

        @Test
        void sumsTwoMembersFieldByField() {

            assertThat(new BlocHabitation(2, 7).addHabitation(new BlocHabitation(1, 4)))
                .isEqualTo(new BlocHabitation(3, 11));
        }

        @Test
        void leavesAHabitationUnchangedWhenMergedWithEmpty() {
            // The regroup starts each bloc from EMPTY, so EMPTY must be the merge's identity.
            assertThat(BlocHabitation.EMPTY.addHabitation(new BlocHabitation(2, 7)))
                .isEqualTo(new BlocHabitation(2, 7));
        }
    }

    private static Colony buildColonyOfSize(int size) {

        var marketMock = mock(MarketAPI.class);

        when(marketMock.getSize())
            .thenReturn(size);

        return new Colony(marketMock, true);
    }
}
