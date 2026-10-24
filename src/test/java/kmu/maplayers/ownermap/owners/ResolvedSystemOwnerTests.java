package kmu.maplayers.ownermap.owners;

import kmlib.colour.ColourPair;

import kmu.maplayers.base.render.clusters.FillSplit.FillState;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins the per-system answer's one invariant and its two named forms: a system nobody owns fills
 * solid, the answer for nobody is the unowned constant, and a solid answer for an owner carries that
 * owner.
 */
final class ResolvedSystemOwnerTests {

    private static final SystemOwner OWNER = new SystemOwner("owner", new ColourPair(Color.RED, Color.BLUE));

    @Nested
    class Constructor {

        // There is no owner's border for a hatch or an empty fill to draw inside, so a rule
        // answering one for nobody has a fault the map would otherwise draw.
        @Test
        void refusesASystemNobodyOwnsFillingOtherThanSolid() {

            assertThatThrownBy(() -> new ResolvedSystemOwner(null, FillState.UNFILLED))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void acceptsAnOwnedSystemInAnyFillState() {

            var actualResolved = new ResolvedSystemOwner(OWNER, FillState.HATCHED);

            assertThat(actualResolved.fillState())
                .isEqualTo(FillState.HATCHED);
        }
    }

    @Nested
    class CreateSolidlyFilledBy {

        // Every whole-sector rule that fills solid answers a nobody as a null owner, so the
        // per-system form takes one and lands on the one unowned answer.
        @Test
        void answersUnownedForNobody() {

            assertThat(ResolvedSystemOwner.createSolidlyFilledBy(null))
                .isSameAs(ResolvedSystemOwner.UNOWNED);
        }

        @Test
        void fillsAnOwnersSystemSolid() {

            assertThat(ResolvedSystemOwner.createSolidlyFilledBy(OWNER))
                .isEqualTo(new ResolvedSystemOwner(OWNER, FillState.SOLID));
        }
    }

    @Nested
    class Unowned {

        @Test
        void namesNobodyFillingSolid() {

            assertThat(ResolvedSystemOwner.UNOWNED)
                .extracting(ResolvedSystemOwner::owner, ResolvedSystemOwner::fillState)
                .containsExactly(null, FillState.SOLID);
        }
    }
}
