package kmu.maplayers.base.render.clusters;

import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.base.render.clusters.FillSplit.FillState;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the fill state each system draws in: solid for every system the layer did not name, the
 * named state for each one it did, and a copy taken on the way in so the layer's map can move on
 * without moving the answer.
 *
 * <p>The states and the members a fill split asks about share one address, so a case states its
 * systems by key throughout.
 */
final class SystemFillStatesTests {

    private static final String SYSTEM_ID = "some-system";
    private static final SystemKey SYSTEM = buildCellKey(SYSTEM_ID);
    private static final SystemKey OTHER_SYSTEM = buildCellKey("other-system");

    @Nested
    class CreateOf {

        @Test
        void dropsASolidEntryAsWhatEveryUnnamedSystemAnswers() {

            var fillStates = SystemFillStates.createOf(Map.of(SYSTEM, FillState.SOLID));
            var actualFillStates = fillStates.getNonSolidFillStateBySystemKey();

            assertThat(actualFillStates)
                .isEmpty();
        }

        @Test
        void keepsItsAnswerAfterTheLayerWritesToItsOwnMap() {
            // The layer goes on building its own map once it has handed one over - a claim
            // augmenting another resolution's answer copies it and writes more - so the answer here
            // must not follow.
            var fillStateBySystemKey = new LinkedHashMap<SystemKey, FillState>();

            fillStateBySystemKey.put(SYSTEM, FillState.HATCHED);

            var fillStates = SystemFillStates.createOf(fillStateBySystemKey);

            fillStateBySystemKey.put(OTHER_SYSTEM, FillState.UNFILLED);

            var actualFillState = fillStates.resolveFillStateOf(OTHER_SYSTEM);

            assertThat(actualFillState)
                .isEqualTo(FillState.SOLID);
        }
    }

    @Nested
    class ResolveFillStateOf {

        @Test
        void returnsSolidForASystemTheLayerDidNotName() {

            var actualFillState = SystemFillStates.ALL_SOLID.resolveFillStateOf(SYSTEM);

            assertThat(actualFillState)
                .isEqualTo(FillState.SOLID);
        }

        @Test
        void returnsHatchedForASystemNamedHatched() {

            var fillStates = SystemFillStates.createOf(Map.of(SYSTEM, FillState.HATCHED));
            var actualFillState = fillStates.resolveFillStateOf(SYSTEM);

            assertThat(actualFillState)
                .isEqualTo(FillState.HATCHED);
        }

        @Test
        void returnsUnfilledForASystemNamedUnfilled() {

            var fillStates = SystemFillStates.createOf(Map.of(SYSTEM, FillState.UNFILLED));
            var actualFillState = fillStates.resolveFillStateOf(SYSTEM);

            assertThat(actualFillState)
                .isEqualTo(FillState.UNFILLED);
        }

        @Test
        void returnsSolidForACellWithNoStarOfItsOwn() {
            // A null system has no per-system fill state, so it fills solid with the rest of the
            // cluster rather than probing the map with a null key.
            var fillStates = SystemFillStates.createOf(Map.of(OTHER_SYSTEM, FillState.UNFILLED));
            var actualFillState = fillStates.resolveFillStateOf(null);

            assertThat(actualFillState)
                .isEqualTo(FillState.SOLID);
        }

        @Test
        void drawsTwoSystemsSharingAnIdInTheirOwnStates() {
            // The map names a system the way the members do, so a colliding pair can draw in two
            // different states - where a map keyed by ID could only have hatched both or neither.
            var hatched = new SystemKey(SYSTEM_ID, "", "8b3");
            var solid = new SystemKey(SYSTEM_ID, "", "38d53");
            var fillStates = SystemFillStates.createOf(Map.of(hatched, FillState.HATCHED));

            var actualHatchedState = fillStates.resolveFillStateOf(hatched);
            var actualSolidState = fillStates.resolveFillStateOf(solid);

            assertThat(actualHatchedState)
                .isEqualTo(FillState.HATCHED);
            assertThat(actualSolidState)
                .isEqualTo(FillState.SOLID);
        }
    }

    @Nested
    class CountSystemsIn {

        @Test
        void countsOnlyTheSystemsNamedInThatState() {

            var fillStates = SystemFillStates.createOf(Map.of(
                SYSTEM,
                FillState.HATCHED,
                OTHER_SYSTEM,
                FillState.UNFILLED));

            var actualHatchedCount = fillStates.countSystemsIn(FillState.HATCHED);

            assertThat(actualHatchedCount)
                .isEqualTo(1);
        }
    }

    @Nested
    class Equals {

        @Test
        void holdsForTwoValuesNamingTheSameSystemsInTheSameStates() {
            // A resolution holding these compares by what it says, as the records around it do.
            var fillStates = SystemFillStates.createOf(Map.of(SYSTEM, FillState.HATCHED));
            var sameFillStates = SystemFillStates.createOf(Map.of(SYSTEM, FillState.HATCHED));
            var actualIsEqual = fillStates.equals(sameFillStates);

            assertThat(actualIsEqual)
                .isTrue();
        }

        @Test
        void failsForTheSameSystemInAnotherState() {

            var fillStates = SystemFillStates.createOf(Map.of(SYSTEM, FillState.HATCHED));
            var otherFillStates = SystemFillStates.createOf(Map.of(SYSTEM, FillState.UNFILLED));
            var actualIsEqual = fillStates.equals(otherFillStates);

            assertThat(actualIsEqual)
                .isFalse();
        }
    }
}
