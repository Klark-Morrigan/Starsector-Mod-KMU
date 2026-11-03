package kmu.maplayers.ownermap.owners.holders.base;

import com.fs.starfarer.api.campaign.econ.MarketAPI;

import kmlib.starsector.markets.colonies.Colony;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.mock;

/**
 * Unit coverage for {@link SystemHabitation}: that the value a render pass hands to three readers
 * cannot change under any of them, that an unstated side reads as empty, that the blocs are the
 * fold's own keys, and that the emptiness the cell classification turns on is the colonies' rather
 * than the blocs'.
 *
 * <p>The last of those is the one that could be got wrong silently. The blocs are folded from the
 * colonies, so they empty together for every system but one - the colony whose owner the grouping
 * can name no bloc for - and a classification reading the bloc set would call that system empty
 * space while the box over it named somebody.
 */
final class SystemHabitationTests {

    private static final String BLOC_ID = "hegemony";
    private static final int COLONY_SIZE = 5;

    // What each posed bloc lives on: one colony of the shared size.
    private static final BlocHabitation ONE_COLONY = new BlocHabitation(List.of(BLOC_ID), COLONY_SIZE);

    @Nested
    class Constructor {

        // A pass opens this once and hands it to the classification, the spotlight and the
        // picker alike, so a side the caller went on mutating would have one of them answering
        // off a set the others never saw.
        @Test
        void keepsTheCallersLaterEditsOutOfTheValue() {

            // ARRANGE
            var colonies = new ArrayList<Colony>();
            var habitationByBlocId = new LinkedHashMap<String, BlocHabitation>();
            var expectedColonyCount = 1;
            var expectedEntry = entry(BLOC_ID, ONE_COLONY);

            colonies.add(buildColony());
            habitationByBlocId.put(BLOC_ID, ONE_COLONY);

            // ACT
            var actualHabitation = new SystemHabitation(colonies, habitationByBlocId);

            colonies.clear();
            habitationByBlocId.clear();

            // ASSERT
            assertThat(actualHabitation.colonies())
                .hasSize(expectedColonyCount);
            assertThat(actualHabitation.habitationByBlocId())
                .containsExactly(expectedEntry);
        }

        @Test
        void refusesToBeEditedThroughEitherSide() {

            // ARRANGE
            var habitation = buildHabitationOf(BLOC_ID);
            var colony = buildColony();

            // ACT
            var actualPutThrown = catchThrowable(() -> habitation.habitationByBlocId().put("tritachyon", ONE_COLONY));
            var actualAddThrown = catchThrowable(() -> habitation.colonies().add(colony));

            // ASSERT
            assertThat(actualPutThrown)
                .isInstanceOf(UnsupportedOperationException.class);
            assertThat(actualAddThrown)
                .isInstanceOf(UnsupportedOperationException.class);
        }

        // A caller with nothing to report says so by handing nothing, rather than by being
        // obliged to name two empty collections.
        @Test
        void readsAnUnstatedSideAsEmpty() {

            // ACT
            var actualHabitation = new SystemHabitation(null, null);

            // ASSERT
            assertThat(actualHabitation.colonies())
                .isEmpty();
            assertThat(actualHabitation.blocIds())
                .isEmpty();
        }
    }

    @Nested
    class BlocIds {

        // The blocs are the fold's own keys rather than a set beside it, so "who is here" and
        // "what each of them lives on" cannot come back naming different blocs.
        @Test
        void namesTheBlocsTheSizeFoldCounted() {

            // ARRANGE
            var habitation = buildHabitationOf(BLOC_ID, "tritachyon");
            var expectedBlocIds = List.of(BLOC_ID, "tritachyon");

            // ACT
            var actualBlocIds = habitation.blocIds();

            // ASSERT
            assertThat(actualBlocIds)
                .containsExactlyElementsOf(expectedBlocIds);
        }

        @Test
        void refusesToBeEditedThroughTheBlocSet() {

            // ARRANGE
            var habitation = buildHabitationOf(BLOC_ID);

            // ACT
            var actualThrown = catchThrowable(() -> habitation.blocIds().add("tritachyon"));

            // ASSERT
            assertThat(actualThrown)
                .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    class HasInhabitingColony {

        // The divergent case: a colony whose owner the grouping can name no bloc for. Somebody
        // lives there, so the cell is settled - where a read taken off the bloc set would call
        // the system empty space and draw it as backdrop.
        @Test
        void readsTheColoniesRatherThanTheBlocsFoldedFromThem() {

            // ARRANGE
            var habitation = new SystemHabitation(List.of(buildColony()), Map.of());

            // ACT
            var actualInhabited = habitation.hasInhabitingColony();

            // ASSERT
            assertThat(actualInhabited)
                .isTrue();
        }

        @Test
        void reportsNobodyLivingWhereNoColonyStands() {

            // ARRANGE
            var habitation = new SystemHabitation(List.of(), Map.of());

            // ACT
            var actualInhabited = habitation.hasInhabitingColony();

            // ASSERT
            assertThat(actualInhabited)
                .isFalse();
        }
    }

    // A habitation whose blocs are the ones named, each living on one colony of the shared size.
    // What each is worth is nothing to this value, so the size is fixed here rather than varied per
    // case and no assertion reads as being about the arithmetic.
    private static SystemHabitation buildHabitationOf(String... blocIds) {

        var habitationByBlocId = new LinkedHashMap<String, BlocHabitation>();
        var colonies = new ArrayList<Colony>();

        for (var blocId : blocIds) {
            habitationByBlocId.put(blocId, ONE_COLONY);
            colonies.add(buildColony());
        }
        return new SystemHabitation(colonies, habitationByBlocId);
    }

    // A colony this value merely carries. Nothing here reads a market, so the kind and the listing
    // are the ordinary ones and no case reads as being about which colony it holds.
    private static Colony buildColony() {
        return new Colony(mock(MarketAPI.class), true);
    }
}
