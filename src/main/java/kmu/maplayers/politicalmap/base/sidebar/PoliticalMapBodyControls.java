package kmu.maplayers.politicalmap.base.sidebar;

import kmlib.starsector.ui.controls.ControlSpec;

import kmu.maplayers.politicalmap.base.FactionNameFormatChoice;
import kmu.maplayers.politicalmap.base.FilterSelectionHeal;
import kmu.maplayers.politicalmap.base.NameFormatPreference;
import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.PoliticalMapViewRegistry;
import kmu.maplayers.politicalmap.base.UninhabitedOutlinePreference;
import kmu.util.KmuStrings;

import java.util.ArrayList;
import java.util.List;

/**
 * The political-map tab's body controls: the view-selector radio that picks which view paints, and
 * the view-agnostic sub-options both a faction and a later alliances view honour - the
 * uninhabited-systems checkbox and the Short/Full name-format radio. The sub-options read and write
 * the same per-save preferences every view respects, so they live here on the tab rather than on any
 * one view; the selector reads the shared {@link PoliticalMapViewRegistry}. The host tab composes
 * these into its full body.
 *
 * <p>The specs carry resolved display strings and the controls' live lit state, since the layout
 * snaps each control to its measured text and the renderer draws each in its current state; they
 * are rebuilt per call so a flipped toggle shows immediately.
 */
public final class PoliticalMapBodyControls {
    // The name-format radio's segments, in the order the layout lays them out left to right: Short
    // then Full. The lit segment index maps back to this order.
    private static final int NAME_SHORT_SEGMENT = 0;
    private static final int NAME_FULL_SEGMENT = 1;

    private PoliticalMapBodyControls() {
    }

    /**
     * @return the tab's view-agnostic sub-options, top to bottom: the uninhabited-systems checkbox
     *         (lit when uninhabited systems draw), then the Short/Full name-format radio (its lit
     *         segment the active format) with its trailing "Names" label
     */
    public static List<ControlSpec> buildSharedControls() {
        return List.of(
                ControlSpec.Checkbox.lit(
                        KmuStrings.get(KmuStrings.POLITICAL_MAP_CTL_UNINHABITED),
                        UninhabitedOutlinePreference.isOutlineDrawn(),
                        cellIndex -> toggleUninhabitedSystems()),
                ControlSpec.HorizontalRadio.uniform(
                        List.of(
                                KmuStrings.get(KmuStrings.POLITICAL_MAP_CTL_NAME_SHORT),
                                KmuStrings.get(KmuStrings.POLITICAL_MAP_CTL_NAME_FULL)),
                        KmuStrings.get(KmuStrings.POLITICAL_MAP_CTL_FACTION_NAMES),
                        nameFormatRadioState(),
                        PoliticalMapBodyControls::selectNameFormatSegment));
    }

    /**
     * The view-selector radio: one side-by-side segment per registered political-map view (Factions |
     * Alliances on one row), the lit segment the active view, or none when the map is off. It deselects
     * on a re-pick, so clicking the lit view turns the overlay off while the tab stays open - the
     * behaviour the old faction toggle had. With only the faction view registered the radio is one
     * segment, reading as the political-map on/off.
     *
     * @return the horizontal, deselectable view-selector radio
     */
    public static ControlSpec buildViewSelector() {
        var labels = new ArrayList<String>();
        for (var view : PoliticalMapViewRegistry.getViews()) {
            labels.add(KmuStrings.get(view.getSegmentLabelKey()));
        }
        return ControlSpec.HorizontalRadio.deselectable(
                labels,
                PoliticalMapViewRegistry.getSelectedViewIndex(),
                PoliticalMapBodyControls::selectViewSegment);
    }

    // Toggles the view its clicked segment names - activating it, or turning the map off when it is
    // already the active view. Any index outside the registered views is ignored, so a stray hit
    // changes nothing. Each view remembers its own spotlight (a faction id under the factions view, an
    // alliance id under the alliances view), so a switch loads the switched-in view's stored bloc
    // rather than clearing - the choice survives moving between views and the map being toggled off.
    // The switched-in view's slot is then healed against its current selectable blocs, so a bloc that
    // lapsed since it was last shown (a faction removed, an alliance dissolved) does not spotlight an
    // empty footprint; the heal is a no-op when the map toggled off, since no view is active to
    // validate against, and the switch itself repaints so the cleared spotlight shows without its own
    // refresh request.
    private static void selectViewSegment(int segmentIndex) {
        List<PoliticalMapView> views = PoliticalMapViewRegistry.getViews();
        if (segmentIndex < 0 || segmentIndex >= views.size()) {
            return;
        }
        PoliticalMapViewRegistry.toggleView(views.get(segmentIndex));
        FilterSelectionHeal.healStaleSelectionAgainstActiveView();
    }

    // Flips the uninhabited-systems outline on or off: if it currently draws, hide it; otherwise draw
    // it. The preference persists the flip in this save and requests the restyle, so the live map
    // follows the checkbox on the next frame.
    private static void toggleUninhabitedSystems() {
        UninhabitedOutlinePreference.setOutlineDrawn(
                !UninhabitedOutlinePreference.isOutlineDrawn());
    }

    // Writes the name format for the clicked radio segment - Short for segment 0, Full for segment 1
    // - matching the Short-then-Full segment order the labels are supplied in. Any other index is
    // ignored, so a stray hit outside the two known segments changes nothing.
    private static void selectNameFormatSegment(int segmentIndex) {
        if (segmentIndex == NAME_SHORT_SEGMENT) {
            NameFormatPreference.selectNameFormat(FactionNameFormatChoice.SHORT);
        } else if (segmentIndex == NAME_FULL_SEGMENT) {
            NameFormatPreference.selectNameFormat(FactionNameFormatChoice.FULL);
        }
    }

    // The radio lights the segment for the active name format, matching the Short-then-Full
    // segment order the labels are supplied in.
    private static int nameFormatRadioState() {
        return NameFormatPreference.getSelectedNameFormat() == FactionNameFormatChoice.SHORT
                ? NAME_SHORT_SEGMENT
                : NAME_FULL_SEGMENT;
    }
}
