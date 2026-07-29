package kmu.maplayers.politicalmap.base.render.territories;

import kmu.maplayers.base.geometry.CellGrouping;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A bloc footprint's members partitioned into the three states its fill paints apart: solid
 * where the bloc holds, hatched where it is present but dominated, and unfilled where it is
 * held for border and label but drawn empty.
 *
 * <p>Each state carries its members at both levels the fill needs them - the cells a region
 * is traced from, and the systems its keys and coincident set are addressed by. The two
 * differ wherever the footprint holds a cell with no star of its own: such a cell joins a
 * region's outline but names no system to key or to mark coincident.
 *
 * <p>Pure partition, free of geometry: which state a system draws in is decided from plain id
 * sets here, and turning that partition into triangles is {@link SplitFillBuilder}'s job.
 */
record FillSplit(
        FillMembers solid,
        FillMembers hatched,
        FillMembers unfilled) {

    /**
     * A split holding no members yet - the accumulator the walk over a footprint's cells
     * fills, and the shape a footprint with nothing in it comes back as.
     *
     * @return three empty states
     */
    static FillSplit createEmpty() {
        return new FillSplit(
                FillMembers.createEmpty(),
                FillMembers.createEmpty(),
                FillMembers.createEmpty());
    }

    /**
     * Splits a footprint's member cells into the three states, resolving each cell to the
     * system it draws as and classifying that system.
     *
     * @param cellGrouping        resolves which system each member cell draws as
     * @param memberCellIds       the footprint's cells
     * @param contestedSystemIds  systems the bloc is present in but does not dominate
     * @param unfilledSystemIds   systems the bloc holds but paints no fill for
     * @return the three states, each holding its own cells and systems
     */
    static FillSplit splitMembersByFillState(
            CellGrouping cellGrouping,
            List<String> memberCellIds,
            Set<String> contestedSystemIds,
            Set<String> unfilledSystemIds) {

        var split = createEmpty();
        for (var cellId : memberCellIds) {
            var systemId = cellGrouping.resolveDrawnSystemIdOf(cellId);
            var members = split.resolveMembersOf(classifyFillState(
                    systemId,
                    contestedSystemIds,
                    unfilledSystemIds));
            members.cellIds().add(cellId);
            if (systemId != null) {
                members.systemIds().add(systemId);
            }
        }
        return split;
    }

    /**
     * The fill state one member's system draws in - the pure rule the split turns on.
     *
     * <p>Unfilled takes precedence over hatched, since a system drawn empty is empty however
     * dominance falls. A cell with no star of its own has no per-system fill state, so it
     * fills solid with the bloc's held ground rather than probing either set with a null key.
     */
    static FillState classifyFillState(
            String systemId,
            Set<String> contestedSystemIds,
            Set<String> unfilledSystemIds) {

        if (systemId == null) {
            return FillState.SOLID;
        }
        if (unfilledSystemIds.contains(systemId)) {
            return FillState.UNFILLED;
        }
        if (contestedSystemIds.contains(systemId)) {
            return FillState.HATCHED;
        }
        return FillState.SOLID;
    }

    /**
     * @return whether any member draws as something other than solid, so a territory that is
     *         not spotlit still takes the per-state split when it holds hatched or unfilled
     *         ground
     */
    boolean hasNonSolidMembers() {
        return !hatched.systemIds().isEmpty()
                || !unfilled.systemIds().isEmpty();
    }

    /**
     * @param state the state to read
     * @return that state's members
     */
    FillMembers resolveMembersOf(FillState state) {
        return switch (state) {
            case SOLID -> solid;
            case HATCHED -> hatched;
            case UNFILLED -> unfilled;
        };
    }

    /**
     * The systems the other two states hold - the coincident set one state's trace names so
     * their shared boundary insets by nothing and the fills abut flush along the raw cell
     * edge rather than opening a channel between them.
     *
     * <p>Includes the unfilled state's systems even though it paints nothing: a drawn fill
     * must still stop flush against held-but-empty ground instead of receding from it.
     *
     * @param state the state whose neighbours are wanted
     * @return the systems the other two states hold, as a membership test
     */
    Set<String> resolveCoincidentSystemIdsOf(FillState state) {
        var coincident = new LinkedHashSet<String>();
        for (var other : FillState.values()) {
            if (other != state) {
                coincident.addAll(resolveMembersOf(other).systemIds());
            }
        }
        return coincident;
    }

    /**
     * The three states a bloc's system can draw its fill in: solid where the bloc holds,
     * hatched where it is present but dominated, unfilled where it is held but painted empty.
     */
    enum FillState {
        SOLID,
        HATCHED,
        UNFILLED
    }

    /**
     * One fill state's members, held as both the cells a region is traced from and the
     * systems its keys and coincident set are addressed by.
     *
     * <p>Both sets are insertion-ordered and mutable: the split fills them cell by cell as it
     * walks the footprint, and insertion order keeps a rebuild's traced rings reproducible.
     */
    record FillMembers(Set<String> cellIds, Set<String> systemIds) {

        static FillMembers createEmpty() {
            return new FillMembers(
                    new LinkedHashSet<>(),
                    new LinkedHashSet<>());
        }
    }
}
