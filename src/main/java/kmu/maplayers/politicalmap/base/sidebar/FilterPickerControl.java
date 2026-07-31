package kmu.maplayers.politicalmap.base.sidebar;

import kmlib.starsector.ui.controls.ControlSpec;

import kmu.maplayers.base.sidebar.FilterSelection;
import kmu.maplayers.politicalmap.base.BlocListColumns;
import kmu.maplayers.politicalmap.base.BlocSort;
import kmu.maplayers.politicalmap.base.BlocSortMode;
import kmu.maplayers.politicalmap.base.RecedePreferences;
import kmu.maplayers.politicalmap.base.SelectableBloc;
import kmu.util.KmuStrings;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The spotlight picker's body controls, top to bottom: a rule heading the block, the columns selector,
 * a row pairing the sort selector beside the recede control, then the vertical icon-radio list of
 * selectable blocs. It turns the active view's {@link SelectableBloc} options into one clickable list
 * and wires each click straight to {@link FilterSelection}, so picking a row spotlights that bloc and
 * re-picking the lit row clears the filter. It draws the same list under either view; which blocs the
 * list holds is the view's decision, read before this is called, so this names no concrete view.
 *
 * <p>The rule parts the view-level controls above (the view selector) from the picker below, marking
 * the section a caption string used to. The columns selector rides directly under the rule, so how many
 * columns the list wraps across is chosen for the block as a whole. Below it a paired row sets the sort
 * beside the recede: the sort selector on the left picks the metric the list ranks by (the picker sorts
 * the blocs by that metric and labels each row with its value), and the recede control on the right sets
 * how the rest of the sector fades behind a spotlight. The recede sits by the sort rather than above the
 * list to keep the picker compact, and it is always shown - a change there simply has no visible effect
 * until a bloc is spotlighted, so the knobs stay put whether or not a filter is active. It is the same
 * reusable {@link RecedeControl} the alliances view places under its own caption, here bound to the
 * filter recede set - the "rest of the sector" behind a spotlight, its own toggles independent of the
 * alliances view's non-allied recede.
 */
public final class FilterPickerControl {

    private FilterPickerControl() {
    }

    /**
     * Builds the picker block for the current view's selectable blocs, filter selection, and sort
     * mode, top to bottom: the section rule, the columns selector, a row pairing the sort selector
     * beside the filter recede control, then the icon-radio list ranked by the sort mode (its lit row
     * the spotlighted bloc, or none when the stored id is not among these blocs). Returns an empty list
     * when there are no selectable blocs, so a view with nothing to spotlight contributes no picker
     * rather than an empty list widget.
     *
     * @param viewId         the active view's id, the slot a pick or clear writes into so the choice is
     *                       remembered against this view alone
     * @param blocs          the selectable blocs under the active view; order here is immaterial since
     *                       the sort mode reorders them for display
     * @param selectedBlocId the currently spotlighted bloc's id, or null when no filter is active
     * @param sort           the metric and direction the list is ranked by, which also picks each row's
     *                       trailing value and the sort selector previews
     * @param columns        how many columns the bloc list wraps its rows across, which the columns
     *                       selector lights and the list lays out under
     * @return the picker body controls, top to bottom; empty when {@code blocs} is empty
     */
    public static List<ControlSpec> buildControls(
            String viewId,
            List<SelectableBloc> blocs,
            String selectedBlocId,
            BlocSort sort,
            BlocListColumns columns) {
        if (blocs.isEmpty()) {
            return List.of();
        }
        // Rank a copy under the active mode and direction, leaving the caller's (cached) list
        // untouched, so the rows draw in the chosen order and the lit index below is resolved against
        // that same order.
        var rankedBlocs = new ArrayList<>(blocs);
        rankedBlocs.sort(sort.comparator());
        var selectedIndex = resolveSelectedIndex(rankedBlocs, selectedBlocId);
        var controls = new ArrayList<ControlSpec>();
        // A rule heads the block, parting the view-level controls above from the picker below - the
        // section break a caption used to mark, now carrying no text.
        controls.add(new ControlSpec.Divider());
        // The columns selector rides directly under the rule, so the column count is chosen for the
        // block as a whole; the list below then wraps its rows across that many columns.
        controls.add(ColumnsSelectorControl.buildSelector(columns));
        // The sort selector and the recede control share one row, the sort on the left picking the
        // metric the list ranks by and the recede on the right setting how the rest of the sector fades
        // behind a spotlight. Pairing them keeps the picker compact. The recede is always shown - it
        // simply has no visible effect until a bloc is spotlighted, so the knobs stay put whether or not
        // a filter is active.
        controls.add(new ControlSpec.SideBySide(
                List.of(SortSelectorControl.buildSelector(sort)),
                RecedeControl.buildControls(
                        RecedePreferences.FILTER,
                        KmuStrings.get(KmuStrings.POLITICAL_MAP_CTL_FILTER_RECEDE_CAPTION))));
        // The bloc list is the body's one scrolling cluster: when the picker plus the controls above and
        // below it would run the box past the bottom margin, the list gives up the difference and
        // scrolls while everything around it stays pinned. asScrolling marks the list; the capped
        // layout, renderer, and input listener all read that one flag.
        controls.add(
                ControlSpec.VerticalTable.iconList(
                        resolveLabels(rankedBlocs),
                        resolveIconPaths(rankedBlocs),
                        resolveTrailingValues(rankedBlocs, sort.mode()),
                        selectedIndex,
                        cellIndex -> pickBloc(viewId, rankedBlocs, selectedIndex, cellIndex),
                        columns.columnCount())
                        .asScrolling());
        return List.copyOf(controls);
    }

    // Spotlights the clicked bloc, or clears the filter when the click landed on the already-lit row.
    // The list is deselectable, so a press on the lit option reaches here with its own index; re-
    // picking it means "stop spotlighting". Any index outside the bloc list is ignored, so a stray
    // hit changes nothing.
    private static void pickBloc(
            String viewId, List<SelectableBloc> blocs, int selectedIndex, int cellIndex) {
        if (cellIndex < 0 || cellIndex >= blocs.size()) {
            return;
        }
        if (cellIndex == selectedIndex) {
            FilterSelection.clearSelection(viewId);
        } else {
            FilterSelection.selectId(viewId, blocs.get(cellIndex).blocId());
        }
    }

    // The lit row: the index of the bloc whose id is stored, or no selection when the stored id is
    // absent (no filter) or names a bloc no longer in the list (a stale id the load heal has not yet
    // cleared). An unlit list still shows every option, so the player can pick one.
    private static int resolveSelectedIndex(List<SelectableBloc> blocs, String selectedBlocId) {
        if (selectedBlocId == null) {
            return ControlSpec.NO_SELECTION;
        }
        for (var index = 0; index < blocs.size(); index++) {
            if (selectedBlocId.equals(blocs.get(index).blocId())) {
                return index;
            }
        }
        return ControlSpec.NO_SELECTION;
    }

    // Each option's label, in list order; a bloc with no resolved name draws as an unlabelled row
    // rather than a null the width measurer would choke on, so an empty string stands in.
    private static List<String> resolveLabels(List<SelectableBloc> blocs) {
        return mapBlocs(blocs, bloc -> bloc.displayName() == null ? "" : bloc.displayName());
    }

    // Each option's crest path, in list order, keeping the nulls: a bloc with no crest (every
    // alliance, and a crestless faction) contributes a null the row draws without an icon, so the
    // list stays aligned to the labels index for index.
    private static List<String> resolveIconPaths(List<SelectableBloc> blocs) {
        return mapBlocs(blocs, SelectableBloc::crestSpritePath);
    }

    // Each option's trailing value, in list order: the active sort metric's number for the bloc, drawn
    // right-aligned so the rows read as a ranked table sorted by the value shown. Kept aligned to the
    // labels index for index, so every row carries a value (a bloc with a zero metric shows "0" rather
    // than dropping the column). Under the name mode there is no numeric metric, so the value is blank
    // and the rows read as a plain alphabetical list.
    private static List<String> resolveTrailingValues(List<SelectableBloc> blocs,
            BlocSortMode sortMode) {
        return mapBlocs(blocs, bloc -> sortMode.resolveTrailingValue(bloc.stats()));
    }

    // One column of the picker table: each bloc mapped to a cell string, in list order, so the label,
    // crest, and value columns stay aligned index for index. A null entry is kept (a crestless bloc's
    // null path is a real "no icon"), so callers that need to null-guard do it in their own mapping.
    private static List<String> mapBlocs(List<SelectableBloc> blocs,
            Function<SelectableBloc, String> resolveCell) {
        var cells = new ArrayList<String>(blocs.size());
        for (var bloc : blocs) {
            cells.add(resolveCell.apply(bloc));
        }
        return cells;
    }
}
