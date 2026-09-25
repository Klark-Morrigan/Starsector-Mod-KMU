package kmu.maplayers.ownermap.holding;

import com.fs.starfarer.api.campaign.econ.MarketAPI;

import kmlib.starsector.markets.colonies.Colony;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.mock;

/**
 * Unit coverage for {@link SystemHabitation}: that the value a render pass hands to three readers
 * cannot change under any of them, that an unstated side reads as empty, that the blocs are the size
 * fold's own keys, and that the emptiness the cell classification turns on is the colonies' rather
 * than the blocs'.
 *
 * <p>The last of those is the one that could be got wrong silently. The blocs are folded from the
 * colonies, so they empty together for every system but one - the colony whose owner the grouping
 * can name no bloc for - and a classification reading the bloc set would call that system empty
 * space while the box over it named somebody.
 */
final class SystemHabitationTest {

    private static final String BLOC_ID = "hegemony";

    private static final int COLONY_SIZE = 5;

    @Nested
    class Constructor {

        @Test
        void keepsTheCallersLaterEditsOutOfTheValue() {
            // A pass opens this once and hands it to the classification, the spotlight and the
            // picker alike, so a side the caller went on mutating would have one of them answering
            // off a set the others never saw.
            var colonies = new ArrayList<Colony>();
            var colonySizeByBlocId = new LinkedHashMap<String, Integer>();

            colonies.add(buildColony());
            colonySizeByBlocId.put(BLOC_ID, COLONY_SIZE);

            var habitation = new SystemHabitation(colonies, colonySizeByBlocId);

            colonies.clear();
            colonySizeByBlocId.clear();

            assertThat(habitation.colonies())
                .hasSize(1);
            assertThat(habitation.colonySizeByBlocId())
                .containsExactly(entry(BLOC_ID, COLONY_SIZE));
        }

        @Test
        void refusesToBeEditedThroughEitherSide() {

            var habitation = buildHabitationOf(BLOC_ID);

            assertThatThrownBy(() -> habitation.colonySizeByBlocId().put("tritachyon", 1))
                .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> habitation.colonies().add(buildColony()))
                .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void readsAnUnstatedSideAsEmpty() {
            // A caller with nothing to report says so by handing nothing, rather than by being
            // obliged to name two empty collections.
            var habitation = new SystemHabitation(null, null);

            assertThat(habitation.colonies())
                .isEmpty();
            assertThat(habitation.blocIds())
                .isEmpty();
        }
    }

    @Nested
    class BlocIds {

        @Test
        void namesTheBlocsTheSizeFoldCounted() {
            // The blocs are the fold's own keys rather than a set beside it, so "who is here" and
            // "what each of them lives on" cannot come back naming different blocs.
            assertThat(buildHabitationOf(BLOC_ID, "tritachyon").blocIds())
                .containsExactly(BLOC_ID, "tritachyon");
        }

        @Test
        void refusesToBeEditedThroughTheBlocSet() {

            assertThatThrownBy(() -> buildHabitationOf(BLOC_ID).blocIds().add("tritachyon"))
                .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    class HasInhabitingColony {

        @Test
        void readsTheColoniesRatherThanTheBlocsFoldedFromThem() {
            // The divergent case: a colony whose owner the grouping can name no bloc for. Somebody
            // lives there, so the cell is settled - where a read taken off the bloc set would call
            // the system empty space and draw it as backdrop.
            var habitation = new SystemHabitation(List.of(buildColony()), Map.of());

            assertThat(habitation.hasInhabitingColony())
                .isTrue();
        }

        @Test
        void reportsNobodyLivingWhereNoColonyStands() {

            assertThat(new SystemHabitation(List.of(), Map.of()).hasInhabitingColony())
                .isFalse();
        }
    }

    // A habitation whose blocs are the ones named, each living on one colony of the shared size.
    // What each is worth is nothing to this value, so the size is fixed here rather than varied per
    // case and no assertion reads as being about the arithmetic.
    private static SystemHabitation buildHabitationOf(String... blocIds) {

        var colonySizeByBlocId = new LinkedHashMap<String, Integer>();
        var colonies = new ArrayList<Colony>();

        for (var blocId : blocIds) {
            colonySizeByBlocId.put(blocId, COLONY_SIZE);
            colonies.add(buildColony());
        }
        return new SystemHabitation(colonies, colonySizeByBlocId);
    }

    // A colony this value merely carries. Nothing here reads a market, so the kind and the listing
    // are the ordinary ones and no case reads as being about which colony it holds.
    private static Colony buildColony() {
        return new Colony(mock(MarketAPI.class), true);
    }
}
