package kmu.maplayers.politicalmap.base.sidebar;

import kmlib.starsector.ui.widgets.RadioAlignment;

import kmu.maplayers.base.sidebar.SidebarControlKind;
import kmu.maplayers.base.sidebar.SidebarControlSpec;
import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.PoliticalMapViewRegistry;
import kmu.settings.FactionNameFormatChoice;
import kmu.settings.KmuLunaSettings;
import kmu.settings.NeutralColorChoice;
import kmu.util.KmuStrings;

import java.util.ArrayList;
import java.util.List;

/**
 * The political-map tab's body controls: the view-selector radio that picks which view paints, and
 * the view-agnostic sub-options both a faction and a later alliances view honour - the
 * uninhabited-systems checkbox and the Short/Full name-format radio. The sub-options read and write
 * the same LunaLib settings every view respects, so they live here on the tab rather than on any one
 * view; the selector reads the shared {@link PoliticalMapViewRegistry}. The host tab composes these
 * into its full body.
 *
 * <p>The specs carry resolved display strings and the controls' live lit state, since the layout
 * snaps each control to its measured text and the renderer draws each in its current state; they
 * are rebuilt per call so a settings change shows immediately.
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
    public static List<SidebarControlSpec> buildSharedControls() {
        return List.of(
                new SidebarControlSpec(SidebarControlKind.CHECKBOX,
                        List.of(KmuStrings.get(KmuStrings.POLITICAL_MAP_CTL_UNINHABITED)), "",
                        uninhabitedCheckboxState(), cellIndex -> toggleUninhabitedSystems()),
                new SidebarControlSpec(SidebarControlKind.RADIO,
                        List.of(KmuStrings.get(KmuStrings.POLITICAL_MAP_CTL_NAME_SHORT),
                                KmuStrings.get(KmuStrings.POLITICAL_MAP_CTL_NAME_FULL)),
                        KmuStrings.get(KmuStrings.POLITICAL_MAP_CTL_FACTION_NAMES),
                        nameFormatRadioState(), PoliticalMapBodyControls::selectNameFormatSegment));
    }

    /**
     * The view-selector radio: one stacked segment per registered political-map view, the lit
     * segment the active view, or none when the map is off. It deselects on a re-pick, so clicking
     * the lit view turns the overlay off while the tab stays open - the behaviour the old faction
     * toggle had. With only the faction view registered the radio is one segment, reading as the
     * political-map on/off.
     *
     * @return the vertical, deselectable view-selector radio
     */
    public static SidebarControlSpec buildViewSelector() {
        var labels = new ArrayList<String>();
        for (var view : PoliticalMapViewRegistry.getViews()) {
            labels.add(KmuStrings.get(view.getSegmentLabelKey()));
        }
        return new SidebarControlSpec(SidebarControlKind.RADIO, List.copyOf(labels), "",
                PoliticalMapViewRegistry.getSelectedViewIndex(),
                PoliticalMapBodyControls::selectViewSegment, RadioAlignment.VERTICAL, true);
    }

    // Toggles the view its clicked segment names - activating it, or turning the map off when it is
    // already the active view. Any index outside the registered views is ignored, so a stray hit
    // changes nothing.
    private static void selectViewSegment(int segmentIndex) {
        List<PoliticalMapView> views = PoliticalMapViewRegistry.getViews();
        if (segmentIndex < 0 || segmentIndex >= views.size()) {
            return;
        }
        PoliticalMapViewRegistry.toggleView(views.get(segmentIndex));
    }

    // Flips the uninhabited-systems outline on or off: if it currently draws (the neutral colour),
    // hide it; otherwise draw it. Written back to LunaLib, which fires the settings-changed event so
    // the settings screen and the live map both follow the sidebar's checkbox.
    private static void toggleUninhabitedSystems() {
        var next = KmuLunaSettings.getUninhabitedBorderColor().isDrawn()
                ? NeutralColorChoice.NONE
                : NeutralColorChoice.NEUTRAL;
        KmuLunaSettings.setUninhabitedBorderColor(next);
    }

    // Writes the name format for the clicked radio segment - Short for segment 0, Full for segment 1
    // - matching the Short-then-Full segment order the labels are supplied in. Any other index is
    // ignored, so a stray hit outside the two known segments changes nothing.
    private static void selectNameFormatSegment(int segmentIndex) {
        if (segmentIndex == NAME_SHORT_SEGMENT) {
            KmuLunaSettings.setPoliticalMapFactionNameFormat(FactionNameFormatChoice.SHORT);
        } else if (segmentIndex == NAME_FULL_SEGMENT) {
            KmuLunaSettings.setPoliticalMapFactionNameFormat(FactionNameFormatChoice.FULL);
        }
    }

    // The checkbox is lit (cell 0) when uninhabited systems draw their outline - the "Neutral
    // color" choice - and off when they are hidden.
    private static int uninhabitedCheckboxState() {
        return KmuLunaSettings.getUninhabitedBorderColor().isDrawn()
                ? 0
                : SidebarControlSpec.NO_SELECTION;
    }

    // The radio lights the segment for the active name format, matching the Short-then-Full
    // segment order the labels are supplied in.
    private static int nameFormatRadioState() {
        return KmuLunaSettings.getPoliticalMapFactionNameFormat() == FactionNameFormatChoice.SHORT
                ? NAME_SHORT_SEGMENT
                : NAME_FULL_SEGMENT;
    }
}
