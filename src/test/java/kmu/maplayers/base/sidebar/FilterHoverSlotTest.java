package kmu.maplayers.base.sidebar;

import kmu.maplayers.base.installation.MapLayerInstallation;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the per-scope hover slot: the read reports one scope's live id or none, a record replaces
 * whatever that scope rested on, a null record and a clear both return it to resting, clearing an
 * already-resting scope changes nothing, and a clear is invisible under another scope. Pins its
 * lifetime beside that - one slot per installation, so two sectors' pickers cannot report into one
 * another's, and a disposal leaving nothing of the gone sector's behind.
 */
final class FilterHoverSlotTest {

    // The scope whose slot these tests exercise.
    private static final String SCOPE_ID = "scope_a";

    // A second scope, so a record or a clear against one scope can be shown invisible to the other.
    private static final String OTHER_SCOPE_ID = "scope_b";

    private static final String HOVERED_ID = "hovered_a";

    private static final String OTHER_HOVERED_ID = "hovered_b";

    @Nested
    class ResolveHoverSlotIn {

        @Test
        void resolveHoverSlotInAnswersOneSlotPerInstallation() {
            // The picker reporting a hover and the pass drawing from it resolve separately, so both
            // asks under one sector must land on the same slot or the preview reads nothing.
            var installation = new MapLayerInstallation(null);

            FilterHoverSlot.resolveHoverSlotIn(installation)
                .recordHoveredId(SCOPE_ID, HOVERED_ID);

            assertThat(FilterHoverSlot.resolveHoverSlotIn(installation).getHoveredIdOf(SCOPE_ID))
                .isEqualTo(HOVERED_ID);
        }

        @Test
        void resolveHoverSlotInDoesNotShareASlotBetweenInstallations() {
            // A hovered id comes from one sector's own list, so reading it under another sector
            // would light a set that sector never produced.
            var installation = new MapLayerInstallation(null);
            var otherInstallation = new MapLayerInstallation(null);

            FilterHoverSlot.resolveHoverSlotIn(installation)
                .recordHoveredId(SCOPE_ID, HOVERED_ID);

            assertThat(
                    FilterHoverSlot.resolveHoverSlotIn(otherInstallation).getHoveredIdOf(SCOPE_ID))
                .isNull();
        }

        @Test
        void resolveHoverSlotInAnswersAFreshSlotAfterTheInstallationIsDisposed() {
            // A load disposes the installation, which is what stands in for a discard of this slot's
            // own - the sector after it begins resting rather than on the previous sector's row.
            var installation = new MapLayerInstallation(null);

            FilterHoverSlot.resolveHoverSlotIn(installation)
                .recordHoveredId(SCOPE_ID, HOVERED_ID);
            installation.disposeMachinery();

            assertThat(FilterHoverSlot.resolveHoverSlotIn(installation).getHoveredIdOf(SCOPE_ID))
                .isNull();
        }
    }

    @Nested
    class GetHoveredIdOf {

        @Test
        void getHoveredIdOfReturnsTheRecordedId() {

            var slot = new FilterHoverSlot();

            slot.recordHoveredId(SCOPE_ID, HOVERED_ID);

            assertThat(slot.getHoveredIdOf(SCOPE_ID))
                .isEqualTo(HOVERED_ID);
        }

        @Test
        void getHoveredIdOfIsNullWhenNoHoverIsRecorded() {
            // A scope the pointer never rested in holds nothing, which is the no-preview state.
            assertThat(new FilterHoverSlot().getHoveredIdOf(SCOPE_ID))
                .isNull();
        }

        @Test
        void getHoveredIdOfDoesNotCrossReadAnotherScopesHover() {
            // Per-scope isolation: a hover under one view is meaningless under another, so it must
            // not be readable there.
            var slot = new FilterHoverSlot();

            slot.recordHoveredId(SCOPE_ID, HOVERED_ID);

            assertThat(slot.getHoveredIdOf(OTHER_SCOPE_ID))
                .isNull();
        }
    }

    @Nested
    class RecordHoveredId {

        @Test
        void recordHoveredIdReplacesThePreviousHover() {
            // The pointer rests on one row at a time, so a move down the list overwrites rather
            // than accumulating.
            var slot = new FilterHoverSlot();

            slot.recordHoveredId(SCOPE_ID, HOVERED_ID);
            slot.recordHoveredId(SCOPE_ID, OTHER_HOVERED_ID);

            assertThat(slot.getHoveredIdOf(SCOPE_ID))
                .isEqualTo(OTHER_HOVERED_ID);
        }

        @Test
        void recordHoveredIdClearsTheScopeWhenTheIdIsNull() {
            // A hover channel reports the leave as a null id, which must rest the scope outright.
            var slot = new FilterHoverSlot();

            slot.recordHoveredId(SCOPE_ID, HOVERED_ID);
            slot.recordHoveredId(SCOPE_ID, null);

            assertThat(slot.getHoveredIdOf(SCOPE_ID))
                .isNull();
        }
    }

    @Nested
    class ClearHoveredId {

        @Test
        void clearHoveredIdDropsTheRecordedId() {

            var slot = new FilterHoverSlot();

            slot.recordHoveredId(SCOPE_ID, HOVERED_ID);
            slot.clearHoveredId(SCOPE_ID);

            assertThat(slot.getHoveredIdOf(SCOPE_ID))
                .isNull();
        }

        @Test
        void clearHoveredIdNoOpsOnAnAlreadyClearScope() {
            // A panel standing down clears without knowing whether a hover was ever reported, so a
            // clear on a resting scope must simply leave it resting.
            var slot = new FilterHoverSlot();

            slot.clearHoveredId(SCOPE_ID);

            assertThat(slot.getHoveredIdOf(SCOPE_ID))
                .isNull();
        }

        @Test
        void clearHoveredIdLeavesAnotherScopesHoverUntouched() {

            var slot = new FilterHoverSlot();

            slot.recordHoveredId(OTHER_SCOPE_ID, OTHER_HOVERED_ID);
            slot.clearHoveredId(SCOPE_ID);

            assertThat(slot.getHoveredIdOf(OTHER_SCOPE_ID))
                .isEqualTo(OTHER_HOVERED_ID);
        }
    }

    @Nested
    class DisposeMachinery {

        @Test
        void disposeMachineryDropsEveryScopesHover() {
            // What a caller holding a slot resolved before the disposal reads back: nothing, rather
            // than the hover the gone sector's pointer was last on.
            var slot = new FilterHoverSlot();

            slot.recordHoveredId(SCOPE_ID, HOVERED_ID);
            slot.recordHoveredId(OTHER_SCOPE_ID, OTHER_HOVERED_ID);
            slot.disposeMachinery();

            assertThat(slot.getHoveredIdOf(SCOPE_ID))
                .isNull();
            assertThat(slot.getHoveredIdOf(OTHER_SCOPE_ID))
                .isNull();
        }
    }
}
