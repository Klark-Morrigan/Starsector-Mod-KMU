package kmu.maplayers.ownermap.owners.holders.base;

import com.fs.starfarer.api.campaign.econ.MarketAPI;

import kmlib.starsector.markets.colonies.Colony;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for {@link BlocHabitation}: that a colony adds its owner to the list and its own
 * size to the sum, that two members' habitations merge field by field, and that the owners always
 * read in faction ID order.
 */
final class BlocHabitationTests {

    @Nested
    class Constructor {

        @Test
        void ordersTheOwnersByFactionId() {
            // A band lays one member's colonies together, so the order must not depend on which
            // member the walk reached first.
            assertThat(new BlocHabitation(List.of("tritachyon", "hegemony", "tritachyon"), 0).colonyOwnerIds())
                .containsExactly("hegemony", "tritachyon", "tritachyon");
        }
    }

    @Nested
    class AddColony {

        @Test
        void holdsOneColonyByItsOwnerAtItsOwnSize() {
            // The owners are what the band draws and the size is what the picker shows, so a colony
            // of any size is still one segment.
            assertThat(BlocHabitation.EMPTY.addColony(buildColonyOf("hegemony", 6)))
                .isEqualTo(new BlocHabitation(List.of("hegemony"), 6));
        }

        @Test
        void addsToWhatTheBlocAlreadyLivesOn() {

            assertThat(new BlocHabitation(List.of("hegemony", "tritachyon"), 7)
                    .addColony(buildColonyOf("persean", 3)))
                .isEqualTo(new BlocHabitation(List.of("hegemony", "persean", "tritachyon"), 10));
        }
    }

    @Nested
    class AddHabitation {

        @Test
        void sumsTwoMembersFieldByField() {

            assertThat(new BlocHabitation(List.of("tritachyon", "tritachyon"), 7)
                    .addHabitation(new BlocHabitation(List.of("hegemony"), 4)))
                .isEqualTo(new BlocHabitation(List.of("hegemony", "tritachyon", "tritachyon"), 11));
        }

        @Test
        void leavesAHabitationUnchangedWhenMergedWithEmpty() {
            // The regroup starts each bloc from EMPTY, so EMPTY must be the merge's identity.
            assertThat(BlocHabitation.EMPTY.addHabitation(new BlocHabitation(List.of("hegemony", "hegemony"), 7)))
                .isEqualTo(new BlocHabitation(List.of("hegemony", "hegemony"), 7));
        }
    }

    private static Colony buildColonyOf(String ownerId, int size) {

        var marketMock = mock(MarketAPI.class);

        when(marketMock.getFactionId())
            .thenReturn(ownerId);
        when(marketMock.getSize())
            .thenReturn(size);

        return new Colony(marketMock, true);
    }
}
