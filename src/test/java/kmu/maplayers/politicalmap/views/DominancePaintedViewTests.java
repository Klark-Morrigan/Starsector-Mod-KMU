package kmu.maplayers.politicalmap.views;

import kmlib.starsector.systems.SectorPassIndex;

import kmu.maplayers.ownermap.owners.OwnerAffiliation;
import kmu.maplayers.ownermap.owners.holders.base.HolderGrouping;
import kmu.maplayers.ownermap.owners.holders.base.HolderPass;
import kmu.maplayers.ownermap.owners.holders.base.RibbonPlanInputs;
import kmu.maplayers.politicalmap.dominance.DominancePass;
import kmu.maplayers.politicalmap.dominance.ribbon.HeldOrClaimedSystemRibbonPlanner;
import kmu.maplayers.politicalmap.dominance.weighting.DominanceRules;
import kmu.maplayers.politicalmap.holders.ClaimAugmentedHolderProvider;
import kmu.maplayers.politicalmap.holders.DominanceSystemHolderResolve;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static kmu.maplayers.ownermap.owners.holders.base.ColonyReadRulesFixtures.UNDER_THE_FOG;
import static kmu.maplayers.ownermap.ribbon.RibbonPlanRulesFixtures.STANDARD_RULES;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins what every view the domination contest paints answers alike: the claim-extended holding,
 * the per-system contest, and the band counting by the mechanic that painted each cell.
 *
 * <p>Read off the interface's own defaults rather than off a concrete view, so each is pinned once.
 * A view's own suite then pins only what that view decides.
 */
final class DominancePaintedViewTests {

    // The interface with every default answering for real and nothing else stated, so a case reads
    // the default alone.
    private final DominancePaintedView viewMock = mock(DominancePaintedView.class, CALLS_REAL_METHODS);

    @Nested
    class ResolveHolderProvider {

        @Test
        void extendsHeldDominanceWithClaims() {
            // Each system's dominant holder, extended with the systems a bloc merely claims - the
            // holding both contest-painted views resolve alike.
            assertThat(viewMock.resolveHolderProvider())
                .isSameAs(ClaimAugmentedHolderProvider.INSTANCE);
        }
    }

    @Nested
    class ResolveSystemHolderResolveSource {

        @Test
        void reDerivesAMarkedSystemByTheMarketContest() {
            // The per-system half of the contest, so a refreshed cell lands the bloc the whole-sector
            // resolve would. The weighting rule reaches LunaLib, so the pass it builds is handed over
            // already resolved.
            var holding = new HolderPass(
                new SectorPassIndex(null),
                UNDER_THE_FOG,
                HolderGrouping.identity());

            try (var passMock = mockStatic(DominancePass.class)) {

                passMock
                    .when(() -> DominancePass.readRulesFromLunaSettings(any(HolderPass.class)))
                    .thenReturn(new DominancePass(mock(DominanceRules.class), holding));

                assertThat(viewMock.resolveSystemHolderResolveSource().openResolveOver(holding))
                    .isInstanceOf(DominanceSystemHolderResolve.class);
            }
        }
    }

    @Nested
    class ResolveRibbonPlanner {

        @Test
        void countsEachSystemByTheMechanicThatPaintedIt() {
            // A contest-painted view paints held territory and extends it with claims, so its bands
            // are counted the same way round rather than one mechanic answering for cells the other
            // painted. The held half names the player's live weighting rule, which reaches LunaLib,
            // so the pass it would build is handed over already resolved. Undiscovered colonies do
            // not count, as on the live map.
            var holding = new HolderPass(
                new SectorPassIndex(null),
                UNDER_THE_FOG,
                HolderGrouping.identity());

            try (var passMock = mockStatic(DominancePass.class)) {

                passMock
                    .when(() -> DominancePass.readRulesFromLunaSettings(any(HolderPass.class)))
                    // Built through the constructor rather than the static entry, which is stood
                    // in for here and would answer with the stand-in rather than build a pass.
                    .thenReturn(new DominancePass(mock(DominanceRules.class), holding));

                var planner = viewMock.resolveRibbonPlanner(
                    new RibbonPlanInputs(
                        holding,
                        blocId -> null,
                        // Nothing stands together, the case being about which planner the view
                        // assembles rather than about how any cell of it is judged.
                        OwnerAffiliation.NONE,
                        STANDARD_RULES));

                assertThat(planner)
                    .isInstanceOf(HeldOrClaimedSystemRibbonPlanner.class);
            }
        }
    }
}
