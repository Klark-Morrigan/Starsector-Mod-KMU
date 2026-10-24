package kmu.maplayers.ownermap.render.rebuild;

import kmlib.starsector.systems.SectorPassIndex;
import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.base.visibility.systems.MapVisibilityRules;
import kmu.maplayers.ownermap.owners.SectorWalk;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins the two stock fillers of the rebuild's slot: the full rebuild folds nothing and owes the
 * rebuild, and the incremental re-derive hands the batch to the fold and reports it folded.
 *
 * <p>What the fold does with a batch is {@link IncrementalOwnerRefreshTests}'s, and what the cache does
 * with each outcome is the cache's suite's; what belongs here is which way each filler answers.
 */
final class OwnerMapRefreshStrategyTests {

    private static final Set<SystemKey> MARKED_SYSTEM_KEYS = Set.of(buildCellKey("marked"));

    private final SectorWalk walk = new SectorWalk(new SectorPassIndex(null), MapVisibilityRules.BASE);

    // Handed through and never read by either filler, so its parts can stand empty.
    private final StandingOwnerMap standingMap = new StandingOwnerMap(null, null, List.of(), null);

    @Nested
    class FullRebuild {

        // The marks are already drained, so the outcome is the only record of them: it has to say
        // the rebuild is owed, and nothing may have been folded on the way.
        @Test
        void owesTheRebuildAndFoldsNothing() {

            try (var refreshMock = mockStatic(IncrementalOwnerRefresh.class)) {

                // ACT
                var actualOutcome = OwnerMapRefreshStrategy.FULL_REBUILD.refreshMarkedSystems(
                    walk,
                    standingMap,
                    MARKED_SYSTEM_KEYS);

                // ASSERT
                assertThat(actualOutcome)
                    .isEqualTo(MarkedSystemsOutcome.REBUILD_OWED);

                refreshMock.verifyNoInteractions();
            }
        }
    }

    @Nested
    class Incremental {

        @Test
        void foldsTheBatchIntoTheStandingMapOverTheHandedWalk() {

            try (var refreshMock = mockStatic(IncrementalOwnerRefresh.class)) {

                // ACT
                var actualOutcome = OwnerMapRefreshStrategy.INCREMENTAL.refreshMarkedSystems(
                    walk,
                    standingMap,
                    MARKED_SYSTEM_KEYS);

                // ASSERT
                assertThat(actualOutcome)
                    .isEqualTo(MarkedSystemsOutcome.FOLDED);

                refreshMock.verify(() -> IncrementalOwnerRefresh.applyStaleOwnerUpdates(
                    same(walk),
                    same(standingMap),
                    same(MARKED_SYSTEM_KEYS)));
            }
        }
    }
}
