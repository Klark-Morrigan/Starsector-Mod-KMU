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

        // A band lays one member's colonies together, so the order must not depend on which
        // member the walk reached first.
        @Test
        void ordersTheOwnersByFactionId() {

            // ARRANGE
            var colonyOwnerIds = List.of("tritachyon", "hegemony", "tritachyon");
            var expectedOwnerIds = List.of("hegemony", "tritachyon", "tritachyon");

            // ACT
            var actualOwnerIds = new BlocHabitation(colonyOwnerIds, 0).colonyOwnerIds();

            // ASSERT
            assertThat(actualOwnerIds)
                .containsExactlyElementsOf(expectedOwnerIds);
        }
    }

    @Nested
    class AddColony {

        // The owners are what the band draws and the size is what the picker shows, so a colony
        // of any size is still one segment.
        @Test
        void holdsOneColonyByItsOwnerAtItsOwnSize() {

            // ARRANGE
            var colony = buildColonyOf("hegemony", 6);
            var expectedHabitation = new BlocHabitation(List.of("hegemony"), 6);

            // ACT
            var actualHabitation = BlocHabitation.EMPTY.addColony(colony);

            // ASSERT
            assertThat(actualHabitation)
                .isEqualTo(expectedHabitation);
        }

        @Test
        void addsToWhatTheBlocAlreadyLivesOn() {

            // ARRANGE
            var habitation = new BlocHabitation(List.of("hegemony", "tritachyon"), 7);
            var colony = buildColonyOf("persean", 3);
            var expectedHabitation = new BlocHabitation(List.of("hegemony", "persean", "tritachyon"), 10);

            // ACT
            var actualHabitation = habitation.addColony(colony);

            // ASSERT
            assertThat(actualHabitation)
                .isEqualTo(expectedHabitation);
        }
    }

    @Nested
    class AddHabitation {

        @Test
        void sumsTwoMembersFieldByField() {

            // ARRANGE
            var habitation = new BlocHabitation(List.of("tritachyon", "tritachyon"), 7);
            var otherHabitation = new BlocHabitation(List.of("hegemony"), 4);
            var expectedHabitation = new BlocHabitation(List.of("hegemony", "tritachyon", "tritachyon"), 11);

            // ACT
            var actualHabitation = habitation.addHabitation(otherHabitation);

            // ASSERT
            assertThat(actualHabitation)
                .isEqualTo(expectedHabitation);
        }

        // The regroup starts each bloc from EMPTY, so EMPTY must be the merge's identity.
        @Test
        void leavesAHabitationUnchangedWhenMergedWithEmpty() {

            // ARRANGE
            var habitation = new BlocHabitation(List.of("hegemony", "hegemony"), 7);
            var expectedHabitation = new BlocHabitation(List.of("hegemony", "hegemony"), 7);

            // ACT
            var actualHabitation = BlocHabitation.EMPTY.addHabitation(habitation);

            // ASSERT
            assertThat(actualHabitation)
                .isEqualTo(expectedHabitation);
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
