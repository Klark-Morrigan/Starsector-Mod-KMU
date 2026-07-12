package kmu.maplayers.politicalmap.base.sidebar;

import kmlib.starsector.ui.controls.ControlSpec;

import kmu.maplayers.politicalmap.base.SelectableBloc;
import kmu.maplayers.politicalmap.base.refresh.FilterSelection;
import kmu.util.KmuStrings;

import java.util.ArrayList;
import java.util.List;

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
 * the section a caption string used to. The recede control only does anything while a bloc is
 * spotlighted (with none selected the map draws exactly as un-filtered), so it is shown solely then
 * and hidden otherwise, and it rides above the list so the "rest of the sector" knobs sit by the rule
 * that opens the section. It is the same reusable {@link RecedeControl} the alliances view places
 * under its own caption, bound to the one shared toggle set, so a flip here and a flip there move the
 * same recede.
 */
public final class FilterPickerControl {

    private FilterPickerControl() {
    }

    /**
     * Builds the picker block for the current view's selectable blocs and filter selection, top to
     * bottom: the section rule, the recede control when a bloc is spotlighted, then the icon-radio
     * list (its lit row the spotlighted bloc, or none when the stored id is not among these blocs).
     * Returns an empty list when there are no selectable blocs, so a view with nothing to spotlight
     * contributes no picker rather than an empty list widget.
     *
     * @param blocs          the selectable blocs under the active view, in the order they list
     * @param selectedBlocId the currently spotlighted bloc's id, or null when no filter is active
     * @return the picker body controls, top to bottom; empty when {@code blocs} is empty
     */
    public static List<ControlSpec> buildControls(List<SelectableBloc> blocs,
            String selectedBlocId) {
        if (blocs.isEmpty()) {
            return List.of();
        }
        var selectedIndex = resolveSelectedIndex(blocs, selectedBlocId);
        var controls = new ArrayList<ControlSpec>();
        // A rule heads the block, parting the view-level controls above from the picker below - the
        // section break a caption used to mark, now carrying no text.
        controls.add(ControlSpec.createDivider());
        // The recede control only bites while a bloc is spotlighted, so it shows solely then - with
        // no filter the sector is drawn normally and there is nothing to recede. It rides above the
        // list so the "rest of the sector" knobs sit next to the divider that opens the section.
        if (selectedBlocId != null) {
            controls.addAll(RecedeControl.buildControls(
                    KmuStrings.get(KmuStrings.POLITICAL_MAP_CTL_FILTER_RECEDE_CAPTION)));
        }
        controls.add(ControlSpec.createIconRadioList(resolveLabels(blocs), resolveIconPaths(blocs),
                selectedIndex, cellIndex -> pickBloc(blocs, selectedIndex, cellIndex)));
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
        var labels = new ArrayList<String>(blocs.size());
        for (var bloc : blocs) {
            labels.add(bloc.displayName() == null ? "" : bloc.displayName());
        }
        return labels;
    }

    // Each option's crest path, in list order, keeping the nulls: a bloc with no crest (every
    // alliance, and a crestless faction) contributes a null the row draws without an icon, so the
    // list stays aligned to the labels index for index.
    private static List<String> resolveIconPaths(List<SelectableBloc> blocs) {
        var iconPaths = new ArrayList<String>(blocs.size());
        for (var bloc : blocs) {
            iconPaths.add(bloc.crestSpritePath());
        }
        return iconPaths;
    }
}
