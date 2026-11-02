package kmu.maplayers.ownermap.owners.holders.base;

import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.colour.ColourPair;
import kmlib.testfixtures.starsector.systems.StarSystemFixture;

import kmu.maplayers.base.render.clusters.FillSplit.FillState;
import kmu.maplayers.ownermap.owners.ResolvedSystemOwner;
import kmu.maplayers.ownermap.owners.SystemOwner;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the one loop every whole-sector holding is built by: the layer's per-system rule asked of
 * each system the pass walks, and its answers kept as the holding the tier shapes. The pass and the
 * rule are stood in for, so what belongs here is what the loop keeps of each answer.
 */
final class HolderResolutionTests {

    private static final SystemOwner HEGEMONY = new SystemOwner(
        "hegemony",
        new ColourPair(Color.RED, Color.BLUE));

    private static final SystemOwner TRITACHYON = new SystemOwner(
        "tritachyon",
        new ColourPair(Color.GREEN, Color.YELLOW));

    private final StarSystemAPI corvus = StarSystemFixture.buildSystem("corvus");
    private final StarSystemAPI askonia = StarSystemFixture.buildSystem("askonia");
    private final StarSystemAPI eos = StarSystemFixture.buildSystem("eos");
    private final StarSystemAPI hybrasil = StarSystemFixture.buildSystem("hybrasil");

    @Nested
    class CollectOver {

        // The holding is keyed in the order the pass walks, so a rebuild over one sector shapes its
        // clusters in the same order every time.
        @Test
        void keysEverySystemTheRuleGivesSomebodyInWalkOrder() {

            // ARRANGE
            var pass = stubPassWalking(eos, corvus);
            var resolver = stubRuleAnswering(Map.of(
                corvus,
                ResolvedSystemOwner.createSolidlyFilledBy(HEGEMONY),
                eos,
                ResolvedSystemOwner.createSolidlyFilledBy(TRITACHYON)));

            // ACT
            var actualOwnerBySystemKey = HolderResolution.collectOver(pass, resolver).ownerBySystemKey();

            // ASSERT
            assertThat(actualOwnerBySystemKey)
                .containsExactly(
                    Map.entry(buildCellKey("eos"), TRITACHYON),
                    Map.entry(buildCellKey("corvus"), HEGEMONY));
        }

        // A system nobody holds draws as an uninhabited cell, which is what being absent says.
        @Test
        void leavesASystemNobodyHoldsOutOfTheHolding() {

            // ARRANGE
            var pass = stubPassWalking(corvus, hybrasil);
            var resolver = stubRuleAnswering(Map.of(
                corvus,
                ResolvedSystemOwner.createSolidlyFilledBy(HEGEMONY),
                hybrasil,
                ResolvedSystemOwner.UNOWNED));

            // ACT
            var actualOwnerBySystemKey = HolderResolution.collectOver(pass, resolver).ownerBySystemKey();

            // ASSERT
            assertThat(actualOwnerBySystemKey)
                .containsOnlyKeys(buildCellKey("corvus"));
        }

        // The rule decides how a held system fills in the same breath as who holds it, so a
        // hatched or unfilled answer reaches the fill split as the rule gave it.
        @Test
        void keepsEachHeldSystemsFillStateThatIsNotSolid() {

            // ARRANGE
            var pass = stubPassWalking(corvus, askonia, eos);
            var resolver = stubRuleAnswering(Map.of(
                corvus,
                ResolvedSystemOwner.createSolidlyFilledBy(HEGEMONY),
                askonia,
                new ResolvedSystemOwner(HEGEMONY, FillState.HATCHED),
                eos,
                new ResolvedSystemOwner(TRITACHYON, FillState.UNFILLED)));

            var expectedFillStateBySystemKey = Map.of(
                buildCellKey("askonia"),
                FillState.HATCHED,
                buildCellKey("eos"),
                FillState.UNFILLED);

            // ACT
            var actualFillStates = HolderResolution.collectOver(pass, resolver).fillStates();

            // ASSERT
            assertThat(actualFillStates.getNonSolidFillStateBySystemKey())
                .isEqualTo(expectedFillStateBySystemKey);
        }

        // Each system is asked once: the rule may read the economy for every answer, so a second ask
        // is a second read paid for nothing.
        @Test
        void asksTheRuleOfEverySystemThePassWalksOnce() {

            // ARRANGE
            var pass = stubPassWalking(corvus, askonia, hybrasil);
            var askedSystems = new ArrayList<StarSystemAPI>();

            SystemHolderResolver resolver = system -> {

                askedSystems.add(system);
                return ResolvedSystemOwner.UNOWNED;
            };

            // ACT
            HolderResolution.collectOver(pass, resolver);

            // ASSERT
            assertThat(askedSystems)
                .containsExactly(corvus, askonia, hybrasil);
        }

        @Test
        void answersEmptyHoldingWhereThePassWalksNoSystem() {

            // ARRANGE
            var pass = stubPassWalking();
            SystemHolderResolver resolver = system -> ResolvedSystemOwner.createSolidlyFilledBy(HEGEMONY);

            // ACT
            var actualResolution = HolderResolution.collectOver(pass, resolver);

            // ASSERT
            assertThat(actualResolution.ownerBySystemKey())
                .isEmpty();
            assertThat(actualResolution.fillStates().getNonSolidFillStateBySystemKey())
                .isEmpty();
        }
    }

    // A pass walking the given systems, in the order given.
    private static HolderPass stubPassWalking(StarSystemAPI... systems) {

        var passMock = mock(HolderPass.class);

        when(passMock.readSystems())
            .thenReturn(List.of(systems));

        return passMock;
    }

    // A rule answering each stated system as stated, and every other one unowned.
    private static SystemHolderResolver stubRuleAnswering(Map<StarSystemAPI, ResolvedSystemOwner> answerBySystem) {
        return system -> answerBySystem.getOrDefault(system, ResolvedSystemOwner.UNOWNED);
    }
}
