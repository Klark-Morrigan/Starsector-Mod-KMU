package kmu.maplayers.politicalmap.base.sidebar;

import kmlib.starsector.ui.controls.ControlSpec;

import kmu.maplayers.politicalmap.base.BlocSortMode;
import kmu.maplayers.politicalmap.base.SelectableBloc;
import kmu.maplayers.politicalmap.base.SortDirection;
import kmu.maplayers.politicalmap.base.refresh.FilterSelection;
import kmu.util.KmuStrings;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The spotlight picker's body controls, top to bottom: a rule heading the block, then - only while a
 * bloc is spotlighted - the shared recede control that sets how the rest of the sector fades behind
 * it, then the vertical icon-radio list of selectable blocs. It turns the active view's {@link
 * SelectableBloc} options into one clickable list and wires each click straight to {@link
 * FilterSelection}, so picking a row spotlights that bloc and re-picking the lit row clears the
 * filter. It draws the same list under either view; which blocs the list holds is the view's
 * decision, read before this is called, so this names no concrete view.
 *
 * <p>The rule parts the view-level controls above (the view selector) from the picker below, marking
 * the section a caption string used to. Under the rule rides the sort selector, so the metric the list
 * ranks by is chosen right above the list it orders; the picker sorts the blocs by that metric and
 * labels each row with its value. The recede control only does anything while a bloc is spotlighted
 * (with none selected the map draws exactly as un-filtered), so it is shown solely then and hidden
 * otherwise, and it rides between the sort selector and the list so the "rest of the sector" knobs sit
 * by the section they modify. It is the same reusable {@link RecedeControl} the alliances view places
 * under its own caption, bound to the one shared toggle set, so a flip here and a flip there move the
 * same recede.
 */
public final class FilterPickerControl {

    private FilterPickerControl() {
    }

    /**
     * Builds the picker block for the current view's selectable blocs, filter selection, and sort
     * mode, top to bottom: the section rule, the sort selector, the recede control when a bloc is
     * spotlighted, then the icon-radio list ranked by the sort mode (its lit row the spotlighted bloc,
     * or none when the stored id is not among these blocs). Returns an empty list when there are no
     * selectable blocs, so a view with nothing to spotlight contributes no picker rather than an empty
     * list widget.
     *
     * @param blocs          the selectable blocs under the active view; order here is immaterial since
     *                       the sort mode reorders them for display
     * @param selectedBlocId the currently spotlighted bloc's id, or null when no filter is active
     * @param sortMode       the metric the list is ranked by, which also picks each row's trailing
     *                       value
     * @param sortDirection  the direction the sort mode runs in, which the ranking follows and the
     *                       sort selector previews
     * @return the picker body controls, top to bottom; empty when {@code blocs} is empty
     */
    public static List<ControlSpec> buildControls(List<SelectableBloc> blocs, String selectedBlocId,
            BlocSortMode sortMode, SortDirection sortDirection) {
        if (blocs.isEmpty()) {
            return List.of();
        }
        // Rank a copy under the active mode and direction, leaving the caller's (cached) list
        // untouched, so the rows draw in the chosen order and the lit index below is resolved against
        // that same order.
        var rankedBlocs = new ArrayList<>(blocs);
        rankedBlocs.sort(sortMode.comparator(sortDirection));
        var selectedIndex = resolveSelectedIndex(rankedBlocs, selectedBlocId);
        var controls = new ArrayList<ControlSpec>();
        // A rule heads the block, parting the view-level controls above from the picker below - the
        // section break a caption used to mark, now carrying no text.
        controls.add(ControlSpec.createDivider());
        // The sort selector rides directly under the rule, so the metric is chosen right above the
        // list it orders.
        controls.add(SortSelectorControl.buildSelector(sortMode, sortDirection));
        // The recede control only bites while a bloc is spotlighted, so it shows solely then - with
        // no filter the sector is drawn normally and there is nothing to recede. It rides between the
        // sort selector and the list so the "rest of the sector" knobs sit next to the section.
        if (selectedBlocId != null) {
            controls.addAll(RecedeControl.buildControls(
                    KmuStrings.get(KmuStrings.POLITICAL_MAP_CTL_FILTER_RECEDE_CAPTION)));
        }
        controls.add(ControlSpec.createIconRadioList(resolveLabels(rankedBlocs),
                resolveIconPaths(rankedBlocs), resolveTrailingValues(rankedBlocs, sortMode),
                selectedIndex, cellIndex -> pickBloc(rankedBlocs, selectedIndex, cellIndex)));
        return List.copyOf(controls);
    }

    // Spotlights the clicked bloc, or clears the filter when the click landed on the already-lit row.
    // The list is deselectable, so a press on the lit option reaches here with its own index; re-
    // picking it means "stop spotlighting". Any index outside the bloc list is ignored, so a stray
    // hit changes nothing.
    private static void pickBloc(List<SelectableBloc> blocs, int selectedIndex, int cellIndex) {
        if (cellIndex < 0 || cellIndex >= blocs.size()) {
            return;
        }
        if (cellIndex == selectedIndex) {
            FilterSelection.clearSelection();
        } else {
            FilterSelection.selectBloc(blocs.get(cellIndex).blocId());
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
