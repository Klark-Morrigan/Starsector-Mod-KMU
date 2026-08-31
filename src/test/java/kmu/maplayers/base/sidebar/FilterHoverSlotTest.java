package kmu.maplayers.base.sidebar;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the per-scope hover slot: the read reports one scope's live id or none, a record replaces
 * whatever that scope rested on, a null record and a clear both return it to resting, clearing an
 * already-resting scope changes nothing, and neither a record nor a clear is visible under another
 * scope.
 */
final class FilterHoverSlotTest {

    // The scope whose slot these tests exercise.
    private static final String SCOPE_ID = "scope_a";

    // A second scope, so a record or a clear against one scope can be shown invisible to the other.
    private static final String OTHER_SCOPE_ID = "scope_b";

    private static final String HOVERED_ID = "hovered_a";

    private static final String OTHER_HOVERED_ID = "hovered_b";

    // The slot is process-wide and holds no save behind it, so a test's leftover hover would be the
    // next test's starting state; both scopes rest before each one.
    @BeforeEach
    void clearBothScopes() {

        FilterHoverSlot.clearHoveredId(SCOPE_ID);
        FilterHoverSlot.clearHoveredId(OTHER_SCOPE_ID);
    }

    @Nested
    class GetHoveredIdOf {

        @Test
        void getHoveredIdOfReturnsTheRecordedId() {

            FilterHoverSlot.recordHoveredId(SCOPE_ID, HOVERED_ID);

            assertThat(FilterHoverSlot.getHoveredIdOf(SCOPE_ID))
                .isEqualTo(HOVERED_ID);
        }

        @Test
        void getHoveredIdOfIsNullWhenNoHoverIsRecorded() {
            // A scope the pointer never rested in holds nothing, which is the no-preview state.
            assertThat(FilterHoverSlot.getHoveredIdOf(SCOPE_ID))
                .isNull();
        }

        @Test
        void getHoveredIdOfDoesNotCrossReadAnotherScopesHover() {
            // Per-scope isolation: a hover under one view is meaningless under another, so it must
            // not be readable there.
            FilterHoverSlot.recordHoveredId(SCOPE_ID, HOVERED_ID);

            assertThat(FilterHoverSlot.getHoveredIdOf(OTHER_SCOPE_ID))
                .isNull();
        }
    }

    @Nested
    class RecordHoveredId {

        @Test
        void recordHoveredIdReplacesThePreviousHover() {
            // The pointer rests on one row at a time, so a move down the list overwrites rather
            // than accumulating.
            FilterHoverSlot.recordHoveredId(SCOPE_ID, HOVERED_ID);
            FilterHoverSlot.recordHoveredId(SCOPE_ID, OTHER_HOVERED_ID);

            assertThat(FilterHoverSlot.getHoveredIdOf(SCOPE_ID))
                .isEqualTo(OTHER_HOVERED_ID);
        }

        @Test
        void recordHoveredIdClearsTheScopeWhenTheIdIsNull() {
            // A hover channel reports the leave as a null id, which must rest the scope outright.
            FilterHoverSlot.recordHoveredId(SCOPE_ID, HOVERED_ID);
            FilterHoverSlot.recordHoveredId(SCOPE_ID, null);

            assertThat(FilterHoverSlot.getHoveredIdOf(SCOPE_ID))
                .isNull();
        }

        @Test
        void recordHoveredIdLeavesAnotherScopesHoverUntouched() {

            FilterHoverSlot.recordHoveredId(OTHER_SCOPE_ID, OTHER_HOVERED_ID);
            FilterHoverSlot.recordHoveredId(SCOPE_ID, HOVERED_ID);

            assertThat(FilterHoverSlot.getHoveredIdOf(OTHER_SCOPE_ID))
                .isEqualTo(OTHER_HOVERED_ID);
        }
    }

    @Nested
    class ClearHoveredId {

        @Test
        void clearHoveredIdDropsTheRecordedId() {

            FilterHoverSlot.recordHoveredId(SCOPE_ID, HOVERED_ID);
            FilterHoverSlot.clearHoveredId(SCOPE_ID);

            assertThat(FilterHoverSlot.getHoveredIdOf(SCOPE_ID))
                .isNull();
        }

        @Test
        void clearHoveredIdNoOpsOnAnAlreadyClearScope() {
            // A panel standing down clears without knowing whether a hover was ever reported, so a
            // clear on a resting scope must simply leave it resting.
            FilterHoverSlot.clearHoveredId(SCOPE_ID);

            assertThat(FilterHoverSlot.getHoveredIdOf(SCOPE_ID))
                .isNull();
        }

        @Test
        void clearHoveredIdLeavesAnotherScopesHoverUntouched() {

            FilterHoverSlot.recordHoveredId(OTHER_SCOPE_ID, OTHER_HOVERED_ID);
            FilterHoverSlot.clearHoveredId(SCOPE_ID);

            assertThat(FilterHoverSlot.getHoveredIdOf(OTHER_SCOPE_ID))
                .isEqualTo(OTHER_HOVERED_ID);
        }
    }
}
