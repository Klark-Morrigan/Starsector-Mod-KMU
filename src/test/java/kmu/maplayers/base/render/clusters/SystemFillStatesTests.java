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

            // ARRANGE
            var fillStateBySystemKey = Map.of(SYSTEM, FillState.SOLID);

            // ACT
            var actualFillStates = SystemFillStates.createOf(fillStateBySystemKey);

            // ASSERT
            assertThat(actualFillStates.getNonSolidFillStateBySystemKey())
                .isEmpty();
        }

        // The layer goes on building its own map once it has handed one over - a claim augmenting
        // another resolution's answer copies it and writes more - so the answer here must not
        // follow.
        @Test
        void keepsItsAnswerAfterTheLayerWritesToItsOwnMap() {

            // ARRANGE
            var fillStateBySystemKey = new LinkedHashMap<SystemKey, FillState>();

            fillStateBySystemKey.put(SYSTEM, FillState.HATCHED);

            var fillStates = SystemFillStates.createOf(fillStateBySystemKey);

            fillStateBySystemKey.put(OTHER_SYSTEM, FillState.UNFILLED);

            // ACT
            var actualFillState = fillStates.resolveFillStateOf(OTHER_SYSTEM);

            // ASSERT
            assertThat(actualFillState)
                .isEqualTo(FillState.SOLID);
        }
    }

    @Nested
    class RecordFillStateIn {

        // A holder folding one system at a time lists only what is not solid, as this type does, so
        // a system that went back to solid leaves the map rather than staying named.
        @Test
        void dropsASystemThatNowFillsSolid() {

            // ARRANGE
            var nonSolidFillStateBySystemKey = new LinkedHashMap<SystemKey, FillState>();

            nonSolidFillStateBySystemKey.put(SYSTEM, FillState.HATCHED);

            // ACT
            SystemFillStates.recordFillStateIn(nonSolidFillStateBySystemKey, SYSTEM, FillState.SOLID);

            // ASSERT
            assertThat(nonSolidFillStateBySystemKey)
                .isEmpty();
        }

        @Test
        void namesASystemThatNowFillsOtherThanSolid() {

            // ARRANGE
            var nonSolidFillStateBySystemKey = new LinkedHashMap<SystemKey, FillState>();

            nonSolidFillStateBySystemKey.put(SYSTEM, FillState.HATCHED);

            // ACT
            SystemFillStates.recordFillStateIn(nonSolidFillStateBySystemKey, SYSTEM, FillState.UNFILLED);

            // ASSERT
            assertThat(nonSolidFillStateBySystemKey)
                .containsExactly(Map.entry(SYSTEM, FillState.UNFILLED));
        }
    }

    @Nested
    class ResolveFillStateIn {

        @Test
        void returnsSolidForASystemTheMapDoesNotName() {

            // ARRANGE
            var nonSolidFillStateBySystemKey = Map.of(OTHER_SYSTEM, FillState.HATCHED);

            // ACT
            var actualFillState = SystemFillStates.resolveFillStateIn(nonSolidFillStateBySystemKey, SYSTEM);

            // ASSERT
            assertThat(actualFillState)
                .isEqualTo(FillState.SOLID);
        }

        @Test
        void returnsTheStateTheMapNames() {

            // ARRANGE
            var nonSolidFillStateBySystemKey = Map.of(SYSTEM, FillState.UNFILLED);

            // ACT
            var actualFillState = SystemFillStates.resolveFillStateIn(nonSolidFillStateBySystemKey, SYSTEM);

            // ASSERT
            assertThat(actualFillState)
                .isEqualTo(FillState.UNFILLED);
        }

        // An immutable map refuses a null key outright, so a cell with no star of its own has to be
        // answered before the map is asked.
        @Test
        void returnsSolidForACellWithNoStarOfItsOwn() {

            // ARRANGE
            var nonSolidFillStateBySystemKey = Map.of(SYSTEM, FillState.UNFILLED);

            // ACT
            var actualFillState = SystemFillStates.resolveFillStateIn(nonSolidFillStateBySystemKey, null);

            // ASSERT
            assertThat(actualFillState)
                .isEqualTo(FillState.SOLID);
        }
    }

    @Nested
    class ResolveFillStateOf {

        @Test
        void returnsSolidForASystemTheLayerDidNotName() {

            // ARRANGE
            var fillStates = SystemFillStates.ALL_SOLID;

            // ACT
            var actualFillState = fillStates.resolveFillStateOf(SYSTEM);

            // ASSERT
            assertThat(actualFillState)
                .isEqualTo(FillState.SOLID);
        }

        @Test
        void returnsHatchedForASystemNamedHatched() {

            // ARRANGE
            var fillStates = SystemFillStates.createOf(Map.of(SYSTEM, FillState.HATCHED));

            // ACT
            var actualFillState = fillStates.resolveFillStateOf(SYSTEM);

            // ASSERT
            assertThat(actualFillState)
                .isEqualTo(FillState.HATCHED);
        }

        @Test
        void returnsUnfilledForASystemNamedUnfilled() {

            // ARRANGE
            var fillStates = SystemFillStates.createOf(Map.of(SYSTEM, FillState.UNFILLED));

            // ACT
            var actualFillState = fillStates.resolveFillStateOf(SYSTEM);

            // ASSERT
            assertThat(actualFillState)
                .isEqualTo(FillState.UNFILLED);
        }

        // A null system has no per-system fill state, so it fills solid with the rest of the
        // cluster rather than probing the map with a null key.
        @Test
        void returnsSolidForACellWithNoStarOfItsOwn() {

            // ARRANGE
            var fillStates = SystemFillStates.createOf(Map.of(OTHER_SYSTEM, FillState.UNFILLED));

            // ACT
            var actualFillState = fillStates.resolveFillStateOf(null);

            // ASSERT
            assertThat(actualFillState)
                .isEqualTo(FillState.SOLID);
        }

        // The map names a system the way the members do, so a colliding pair can draw in two
        // different states - where a map keyed by ID could only have hatched both or neither.
        @Test
        void drawsTwoSystemsSharingAnIdInTheirOwnStates() {

            // ARRANGE
            var hatched = new SystemKey(SYSTEM_ID, "", "8b3");
            var solid = new SystemKey(SYSTEM_ID, "", "38d53");
            var fillStates = SystemFillStates.createOf(Map.of(hatched, FillState.HATCHED));

            // ACT
            var actualHatchedState = fillStates.resolveFillStateOf(hatched);
            var actualSolidState = fillStates.resolveFillStateOf(solid);

            // ASSERT
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

            // ARRANGE
            var fillStates = SystemFillStates.createOf(Map.of(
                SYSTEM,
                FillState.HATCHED,
                OTHER_SYSTEM,
                FillState.UNFILLED));

            // ACT
            var actualHatchedCount = fillStates.countSystemsIn(FillState.HATCHED);

            // ASSERT
            assertThat(actualHatchedCount)
                .isEqualTo(1);
        }
    }

    @Nested
    class Equals {

        // A resolution holding these compares by what it says, as the records around it do.
        @Test
        void holdsForTwoValuesNamingTheSameSystemsInTheSameStates() {

            // ARRANGE
            var fillStates = SystemFillStates.createOf(Map.of(SYSTEM, FillState.HATCHED));
            var sameFillStates = SystemFillStates.createOf(Map.of(SYSTEM, FillState.HATCHED));

            // ACT
            var actualIsEqual = fillStates.equals(sameFillStates);

            // ASSERT
            assertThat(actualIsEqual)
                .isTrue();
        }

        @Test
        void failsForTheSameSystemInAnotherState() {

            // ARRANGE
            var fillStates = SystemFillStates.createOf(Map.of(SYSTEM, FillState.HATCHED));
            var otherFillStates = SystemFillStates.createOf(Map.of(SYSTEM, FillState.UNFILLED));

            // ACT
            var actualIsEqual = fillStates.equals(otherFillStates);

            // ASSERT
            assertThat(actualIsEqual)
                .isFalse();
        }
    }
}
