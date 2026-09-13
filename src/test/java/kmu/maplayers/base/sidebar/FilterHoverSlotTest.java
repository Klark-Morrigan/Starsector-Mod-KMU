package kmu.maplayers.base.sidebar;

import kmu.KmuMod;
import kmu.maplayers.base.machinery.SectorMapMachinery;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the per-picker hover slot: the read reports one list's live ID or none, a record replaces
 * whatever that list rested on, a null record and a clear both return it to resting, clearing an
 * already-resting list changes nothing, and a clear is invisible under another list - another mod's
 * included, which is the axis nothing else here would part. Pins its
 * lifetime beside that - one slot per machinery, so two sectors' pickers cannot report into one
 * another's, and a disposal leaving nothing of the gone sector's behind.
 */
final class FilterHoverSlotTest {

    // The list whose hover these cases exercise, under a stand-in mod: what this slot does is the
    // same whichever mod's picker reported into it.
    private static final PickerScope PICKER_SCOPE = new PickerScope(
        MapLayerStoreNamespaces.createStandInNamespace(),
        "scope_a");

    // A second list on the same mod's panel, so a record or a clear against one can be shown
    // invisible to the other.
    private static final PickerScope OTHER_PICKER_SCOPE = new PickerScope(
        MapLayerStoreNamespaces.createStandInNamespace(),
        "scope_b");

    // The same scope ID under another mod. Nothing here is persisted, so this is the only thing
    // keeping two mods that both listed a view called "scope_a" from previewing each other's rows.
    private static final PickerScope OTHER_MOD_SCOPE = new PickerScope(
        KmuMod.MAP_STORE_NAMESPACE,
        "scope_a");

    private static final String HOVERED_ID = "hovered_a";

    private static final String OTHER_HOVERED_ID = "hovered_b";

    @Nested
    class ResolveHoverSlotIn {

        @Test
        void resolveHoverSlotInAnswersOneSlotPerMachinery() {
            // The picker reporting a hover and the pass drawing from it resolve separately, so both
            // asks under one sector must land on the same slot or the preview reads nothing.
            var machinery = new SectorMapMachinery(null);

            FilterHoverSlot.resolveHoverSlotIn(machinery)
                .recordHoveredId(PICKER_SCOPE, HOVERED_ID);

            assertThat(FilterHoverSlot.resolveHoverSlotIn(machinery).getHoveredIdOf(PICKER_SCOPE))
                .isEqualTo(HOVERED_ID);
        }

        @Test
        void resolveHoverSlotInDoesNotShareASlotBetweenMachinery() {
            // A hovered ID comes from one sector's own list, so reading it under another sector
            // would light a set that sector never produced.
            var machinery = new SectorMapMachinery(null);
            var otherMachinery = new SectorMapMachinery(null);

            FilterHoverSlot.resolveHoverSlotIn(machinery)
                .recordHoveredId(PICKER_SCOPE, HOVERED_ID);

            assertThat(
                    FilterHoverSlot.resolveHoverSlotIn(otherMachinery).getHoveredIdOf(PICKER_SCOPE))
                .isNull();
        }

        @Test
        void resolveHoverSlotInAnswersAFreshSlotAfterTheMachineryIsDisposed() {
            // A load disposes the machinery, which is what stands in for a discard of this slot's
            // own - the sector after it begins resting rather than on the previous sector's row.
            var machinery = new SectorMapMachinery(null);

            FilterHoverSlot.resolveHoverSlotIn(machinery)
                .recordHoveredId(PICKER_SCOPE, HOVERED_ID);
            machinery.disposeMachinery();

            assertThat(FilterHoverSlot.resolveHoverSlotIn(machinery).getHoveredIdOf(PICKER_SCOPE))
                .isNull();
        }
    }

    @Nested
    class GetHoveredIdOf {

        @Test
        void getHoveredIdOfReturnsTheRecordedId() {

            var slot = new FilterHoverSlot();

            slot.recordHoveredId(PICKER_SCOPE, HOVERED_ID);

            assertThat(slot.getHoveredIdOf(PICKER_SCOPE))
                .isEqualTo(HOVERED_ID);
        }

        @Test
        void getHoveredIdOfIsNullWhenNoHoverIsRecorded() {
            // A scope the pointer never rested in holds nothing, which is the no-preview state.
            assertThat(new FilterHoverSlot().getHoveredIdOf(PICKER_SCOPE))
                .isNull();
        }

        @Test
        void getHoveredIdOfDoesNotCrossReadAnotherScopesHover() {
            // Per-scope isolation: a hover under one view is meaningless under another, so it must
            // not be readable there.
            var slot = new FilterHoverSlot();

            slot.recordHoveredId(PICKER_SCOPE, HOVERED_ID);

            assertThat(slot.getHoveredIdOf(OTHER_PICKER_SCOPE))
                .isNull();
        }

        @Test
        void getHoveredIdOfDoesNotCrossReadAnotherModsHover() {
            // The scope ID is opaque and every mod picks its own, so two pickers listing under one
            // name are two lists: previewing the other's row would light systems this layer's walk
            // never offered.
            var slot = new FilterHoverSlot();

            slot.recordHoveredId(PICKER_SCOPE, HOVERED_ID);

            assertThat(slot.getHoveredIdOf(OTHER_MOD_SCOPE))
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

            slot.recordHoveredId(PICKER_SCOPE, HOVERED_ID);
            slot.recordHoveredId(PICKER_SCOPE, OTHER_HOVERED_ID);

            assertThat(slot.getHoveredIdOf(PICKER_SCOPE))
                .isEqualTo(OTHER_HOVERED_ID);
        }

        @Test
        void recordHoveredIdClearsTheScopeWhenTheIdIsNull() {
            // A hover channel reports the leave as a null ID, which must rest the scope outright.
            var slot = new FilterHoverSlot();

            slot.recordHoveredId(PICKER_SCOPE, HOVERED_ID);
            slot.recordHoveredId(PICKER_SCOPE, null);

            assertThat(slot.getHoveredIdOf(PICKER_SCOPE))
                .isNull();
        }
    }

    @Nested
    class ClearHoveredId {

        @Test
        void clearHoveredIdDropsTheRecordedId() {

            var slot = new FilterHoverSlot();

            slot.recordHoveredId(PICKER_SCOPE, HOVERED_ID);
            slot.clearHoveredId(PICKER_SCOPE);

            assertThat(slot.getHoveredIdOf(PICKER_SCOPE))
                .isNull();
        }

        @Test
        void clearHoveredIdNoOpsOnAnAlreadyClearScope() {
            // A panel standing down clears without knowing whether a hover was ever reported, so a
            // clear on a resting scope must simply leave it resting.
            var slot = new FilterHoverSlot();

            slot.clearHoveredId(PICKER_SCOPE);

            assertThat(slot.getHoveredIdOf(PICKER_SCOPE))
                .isNull();
        }

        @Test
        void clearHoveredIdLeavesAnotherScopesHoverUntouched() {

            var slot = new FilterHoverSlot();

            slot.recordHoveredId(OTHER_PICKER_SCOPE, OTHER_HOVERED_ID);
            slot.clearHoveredId(PICKER_SCOPE);

            assertThat(slot.getHoveredIdOf(OTHER_PICKER_SCOPE))
                .isEqualTo(OTHER_HOVERED_ID);
        }

        @Test
        void clearHoveredIdLeavesAnotherModsHoverUntouched() {
            // A panel standing down clears its own lists, and a mod's stand-down must not rest the
            // row another mod's pointer is still on.
            var slot = new FilterHoverSlot();

            slot.recordHoveredId(OTHER_MOD_SCOPE, OTHER_HOVERED_ID);
            slot.clearHoveredId(PICKER_SCOPE);

            assertThat(slot.getHoveredIdOf(OTHER_MOD_SCOPE))
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

            slot.recordHoveredId(PICKER_SCOPE, HOVERED_ID);
            slot.recordHoveredId(OTHER_PICKER_SCOPE, OTHER_HOVERED_ID);
            slot.disposeMachinery();

            assertThat(slot.getHoveredIdOf(PICKER_SCOPE))
                .isNull();
            assertThat(slot.getHoveredIdOf(OTHER_PICKER_SCOPE))
                .isNull();
        }
    }
}
