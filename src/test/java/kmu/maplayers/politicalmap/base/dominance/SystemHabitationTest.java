package kmu.maplayers.politicalmap.base.dominance;

import com.fs.starfarer.api.campaign.econ.MarketAPI;

import kmlib.starsector.colonies.Colony;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Unit coverage for {@link SystemHabitation}: that the value a render pass hands to two readers
 * cannot change under either of them, that an unstated side reads as empty, and that the emptiness
 * the cell classification turns on is the colonies' rather than the blocs'.
 *
 * <p>The last of those is the one that could be got wrong silently. The blocs are folded from the
 * colonies, so they empty together for every system but one - the colony whose owner the grouping
 * can name no bloc for - and a classification reading the bloc set would call that system empty
 * space while the box over it named somebody.
 */
final class SystemHabitationTest {

    private static final String BLOC_ID = "hegemony";

    @Nested
    class Constructor {

        @Test
        void keepsTheCallersLaterEditsOutOfTheValue() {
            // A pass opens this once and hands it to the classification and the spotlight alike, so
            // a side the caller went on mutating would have one of them answering off a set the
            // other never saw.
            var colonies = new ArrayList<Colony>();
            var blocIds = new LinkedHashSet<String>();

            colonies.add(buildColony());
            blocIds.add(BLOC_ID);

            var habitation = new SystemHabitation(colonies, blocIds);

            colonies.clear();
            blocIds.clear();

            assertThat(habitation.colonies())
                .hasSize(1);
            assertThat(habitation.blocIds())
                .containsExactly(BLOC_ID);
        }

        @Test
        void refusesToBeEditedThroughEitherSide() {

            var habitation = new SystemHabitation(List.of(), Set.of(BLOC_ID));

            assertThatThrownBy(() -> habitation.blocIds().add("tritachyon"))
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
    class HasInhabitingColony {

        @Test
        void readsTheColoniesRatherThanTheBlocsFoldedFromThem() {
            // The divergent case: a colony whose owner the grouping can name no bloc for. Somebody
            // lives there, so the cell is settled - where a read taken off the bloc set would call
            // the system empty space and draw it as backdrop.
            var habitation = new SystemHabitation(List.of(buildColony()), Set.of());

            assertThat(habitation.hasInhabitingColony())
                .isTrue();
        }

        @Test
        void reportsNobodyLivingWhereNoColonyStands() {

            assertThat(new SystemHabitation(List.of(), Set.of()).hasInhabitingColony())
                .isFalse();
        }
    }

    // A colony this value merely carries. Nothing here reads a market, so the kind and the listing
    // are the ordinary ones and no case reads as being about which colony it holds.
    private static Colony buildColony() {
        return new Colony(mock(MarketAPI.class), true);
    }
}
