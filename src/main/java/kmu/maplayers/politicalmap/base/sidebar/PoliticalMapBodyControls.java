package kmu.maplayers.politicalmap.base.sidebar;

import kmlib.starsector.ui.colour.StarsectorUiColour;
import kmlib.starsector.ui.controls.ControlSpec;
import kmlib.starsector.ui.text.TextSpan;

import kmu.maplayers.base.refresh.MapLayerRefreshBoard;
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
 * uninhabited-systems checkbox and the Full/Short/No name radio. The sub-options read and write
 * the same per-save preferences every view respects, so they live here on the tab rather than on any
 * one view; the selector reads the shared {@link PoliticalMapViewRegistry}. The host tab composes
 * these into its full body.
 *
 * <p>The specs carry resolved display strings and the controls' live lit state, since the layout
 * snaps each control to its measured text and the renderer draws each in its current state; they
 * are rebuilt per call so a flipped toggle shows immediately.
 *
 * <p>The sub-options are handed the refresh board of the sector they are built for, since each
 * repaints by raising a signal. The selector takes none: switching views writes a selection the
 * overlay reads directly and raises nothing.
 */
public final class PoliticalMapBodyControls {

    // The name radio's segments, in the order the layout lays them out left to right: the two drawn
    // forms longest-first, then No. The lit segment opens the sentence its trailing caption closes -
    // "Full faction names", "No faction names" - which is why that caption is lowercase. The lit
    // segment index maps back to this order.
    private static final int NAME_FULL_SEGMENT = 0;
    private static final int NAME_SHORT_SEGMENT = 1;
    private static final int NAME_NONE_SEGMENT = 2;

    private PoliticalMapBodyControls() {
    }

    /**
     * @param board the refresh board of the sector these controls are built for, carried into both
     *              writers so a flip or a pick repaints that sector's overlay
     * @return the tab's view-agnostic sub-options, top to bottom: the uninhabited-systems checkbox
     *         (lit when uninhabited systems draw), then the Full/Short/No name radio (its lit
     *         segment the active choice) with its trailing "faction names" label
     */
    public static List<ControlSpec> buildSharedControls(MapLayerRefreshBoard board) {
        return List.of(
            ControlSpec.Checkbox.lit(
                // The plain text tone: the box states an option rather than calling anything out.
                new TextSpan(
                    KmuStrings.get(KmuStrings.POLITICAL_MAP_CTL_UNINHABITED),
                    StarsectorUiColour.VANILLA_TEXT.resolve()),
                UninhabitedOutlinePreference.isOutlineDrawn(),
                cellIndex -> toggleUninhabitedSystems(board)),
            ControlSpec.HorizontalRadio
                .of(
                    List.of(
                        KmuStrings.get(KmuStrings.POLITICAL_MAP_CTL_NAME_FULL),
                        KmuStrings.get(KmuStrings.POLITICAL_MAP_CTL_NAME_SHORT),
                        KmuStrings.get(KmuStrings.POLITICAL_MAP_CTL_NAME_NONE)),
                    nameFormatRadioState(),
                    segmentIndex -> selectNameFormatSegment(segmentIndex, board))
                .showsCaption(KmuStrings.get(KmuStrings.POLITICAL_MAP_CTL_FACTION_NAMES)));
    }

    /**
     * The view-selector radio: one side-by-side segment per registered political-map view (Factions |
     * Alliances | Claims on one row), the lit segment the active view. A re-pick of the lit view is
     * inert, so the radio always holds a view once one is picked - the tab selector (No Layer) is the
     * one control that turns the map off, and the view axis has no "none" a player can fall into by
     * clicking twice.
     *
     * @return the horizontal, always-on view-selector radio
     */
    public static ControlSpec buildViewSelector() {
        var labels = new ArrayList<String>();
        for (var view : PoliticalMapViewRegistry.getViews()) {
            labels.add(KmuStrings.get(view.getSegmentLabelKey()));
        }
        // No caption: the segment labels already name the views, so a trailing word would only repeat
        // what the row reads as.
        return ControlSpec.HorizontalRadio.of(
            labels,
            PoliticalMapViewRegistry.getSelectedViewIndex(),
            PoliticalMapBodyControls::selectViewSegment);
    }

    // Activates the view its clicked segment names. Any index outside the registered views is ignored,
    // so a stray hit changes nothing. Each view remembers its own spotlight (a faction id under the
    // factions view, an alliance id under the alliances view), so a switch loads the switched-in view's
    // stored bloc rather than clearing - the choice survives moving between views and the tab being
    // switched away from. The switched-in view's slot is then healed against its current selectable
    // blocs, so a bloc that lapsed since it was last shown (a faction removed, an alliance dissolved)
    // does not spotlight an empty footprint; the switch itself repaints, so the cleared spotlight shows
    // without its own refresh request.
    private static void selectViewSegment(int segmentIndex) {
        List<PoliticalMapView> views = PoliticalMapViewRegistry.getViews();
        if (segmentIndex < 0 || segmentIndex >= views.size()) {
            return;
        }
        PoliticalMapViewRegistry.selectView(views.get(segmentIndex));
        FilterSelectionHeal.healStaleSelectionAgainstActiveView();
    }

    // Flips the uninhabited-systems outline on or off: if it currently draws, hide it; otherwise draw
    // it. The preference persists the flip in this save and repaints through the board it is handed.
    private static void toggleUninhabitedSystems(MapLayerRefreshBoard board) {
        UninhabitedOutlinePreference.setOutlineDrawn(
            !UninhabitedOutlinePreference.isOutlineDrawn(),
            board);
    }

    // Writes the name choice for the clicked radio segment, matching the Full-Short-No segment order
    // the labels are supplied in. No is a choice like the other two rather than a separate gate, so
    // turning the names off is the same one write. Any other index is ignored, so a stray hit outside
    // the three known segments changes nothing.
    private static void selectNameFormatSegment(int segmentIndex, MapLayerRefreshBoard board) {
        if (segmentIndex == NAME_FULL_SEGMENT) {
            NameFormatPreference.selectNameFormat(FactionNameFormatChoice.FULL, board);
        } else if (segmentIndex == NAME_SHORT_SEGMENT) {
            NameFormatPreference.selectNameFormat(FactionNameFormatChoice.SHORT, board);
        } else if (segmentIndex == NAME_NONE_SEGMENT) {
            NameFormatPreference.selectNameFormat(FactionNameFormatChoice.NONE, board);
        }
    }

    // The radio lights the segment for the active name choice, matching the Full-Short-No segment
    // order the labels are supplied in.
    private static int nameFormatRadioState() {
        return switch (NameFormatPreference.getSelectedNameFormat()) {
            case FULL -> NAME_FULL_SEGMENT;
            case SHORT -> NAME_SHORT_SEGMENT;
            case NONE -> NAME_NONE_SEGMENT;
        };
    }
}
