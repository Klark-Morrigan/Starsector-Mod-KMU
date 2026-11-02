package kmu.maplayers.ownermap.owners;

import kmlib.colour.ColourPair;

import kmu.maplayers.base.render.clusters.FillSplit.FillState;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.awt.Color;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Pins the per-system answer's two invariants and its named form: every answer states a fill, a
 * system nobody owns fills solid, and a solid answer for an owner carries that owner while the
 * answer for nobody is the unowned constant.
 */
final class ResolvedSystemOwnerTests {

    private static final SystemOwner OWNER = new SystemOwner("owner", new ColourPair(Color.RED, Color.BLUE));

    @Nested
    class Constructor {

        // There is no owner's border for a hatch or an empty fill to draw inside, so a rule
        // answering one for nobody has a fault the map would otherwise draw.
        @Test
        void refusesASystemNobodyOwnsFillingOtherThanSolid() {

            // ACT
            var actualThrown = catchThrowable(() -> new ResolvedSystemOwner(null, FillState.UNFILLED));

            // ASSERT
            assertThat(actualThrown)
                .isInstanceOf(IllegalArgumentException.class);
        }

        // A missing fill is a rule that forgot to decide one, and every reader past here takes the
        // state as given - the occupancy folds it and the fill split draws it.
        @Test
        void refusesAMissingFillState() {

            // ACT
            var actualThrown = catchThrowable(() -> new ResolvedSystemOwner(OWNER, null));

            // ASSERT
            assertThat(actualThrown)
                .isInstanceOf(NullPointerException.class)
                .hasMessage("fillState");
        }

        // An owner's border holds any fill, so the guard above must refuse nobody but the unowned.
        @ParameterizedTest
        @EnumSource(FillState.class)
        void acceptsAnOwnedSystemInAnyFillState(FillState fillState) {

            // ACT
            var actualResolved = new ResolvedSystemOwner(OWNER, fillState);

            // ASSERT
            assertThat(actualResolved.fillState())
                .isEqualTo(fillState);
        }
    }

    @Nested
    class CreateSolidlyFilledBy {

        // Every whole-sector rule that fills solid answers a nobody as a null owner, so the
        // per-system form takes one and lands on the one unowned answer.
        @Test
        void answersUnownedForNobody() {

            // ACT
            var actualResolved = ResolvedSystemOwner.createSolidlyFilledBy(null);

            // ASSERT
            assertThat(actualResolved)
                .isSameAs(ResolvedSystemOwner.UNOWNED);
        }

        @Test
        void fillsAnOwnersSystemSolid() {

            // ARRANGE
            var expectedResolved = new ResolvedSystemOwner(OWNER, FillState.SOLID);

            // ACT
            var actualResolved = ResolvedSystemOwner.createSolidlyFilledBy(OWNER);

            // ASSERT
            assertThat(actualResolved)
                .isEqualTo(expectedResolved);
        }
    }
}
