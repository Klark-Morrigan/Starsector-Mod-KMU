package kmu.maplayers.ownermap.owners.holders.alliances;

import kmlib.starsector.factions.relation.FactionSetFriendliness;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Pins {@link BlocRelations}' one rule of its own: both axes are required.
 *
 * <p>A box files every bloc it lists against both, so a pair sampled with one missing would fault
 * part-way through a box rather than at the point that failed to sample it.
 */
final class BlocRelationsTests {

    @Nested
    class Constructor {

        @Test
        void refusesAMissingAffiliation() {

            assertThatThrownBy(() -> new BlocRelations(null, mock(FactionSetFriendliness.class)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("affiliation");
        }

        @Test
        void refusesAMissingFriendliness() {

            assertThatThrownBy(() -> new BlocRelations(mock(BlocAffiliation.class), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("friendliness");
        }
    }
}
