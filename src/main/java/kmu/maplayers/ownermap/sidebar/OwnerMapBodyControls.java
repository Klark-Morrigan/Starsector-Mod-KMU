package kmu.maplayers.ownermap.sidebar;

import kmlib.starsector.ui.colour.StarsectorUiColour;
import kmlib.starsector.ui.controls.specs.CheckboxSpec;
import kmlib.starsector.ui.controls.specs.ControlSpec;
import kmlib.starsector.ui.controls.specs.HorizontalRadioSpec;
import kmlib.starsector.ui.text.TextSpan;

import kmu.maplayers.base.layer.ScreenMemoryScope;
import kmu.maplayers.ownermap.FilterSelectionHeal;
import kmu.maplayers.ownermap.MapLayerViewRegistry;
import kmu.maplayers.ownermap.OwnerPaintedView;
import kmu.maplayers.ownermap.preferences.FactionNameFormatChoice;
import kmu.maplayers.ownermap.preferences.NameFormatPreference;
import kmu.maplayers.ownermap.preferences.OwnerMapBodyPreferences;
import kmu.maplayers.ownermap.preferences.UninhabitedOutlinePreference;
import kmu.util.KmuStringKeys;

import java.util.ArrayList;
import java.util.List;

/**
 * An owner-map tab's body controls: the view-selector radio that picks which view paints, and
 * the view-agnostic sub-options every view honours - the uninhabited-systems checkbox and the
 * Full/Short/No name radio. The sub-options read and write the same per-save preferences every view
 * respects, so they live here on the tab rather than on any one view; the selector reads the
 * {@link MapLayerViewRegistry} the host tab hands it, which is that layer's own. The host tab
 * composes these into its full body.
 *
 * <p>The specs carry resolved display strings and the controls' live lit state, since the layout
 * snaps each control to its measured text and the renderer draws each in its current state; they
 * are rebuilt per call so a flipped toggle shows immediately.
 *
 * <p>The sub-options are handed the panel they were placed on, since each repaints by raising a signal on
 * that sector's board and stores under that screen. The selector is handed the screen alone: switching
 * views writes a selection the overlay reads directly and raises nothing, so there is no board for it to
 * be given.
 */
public final class OwnerMapBodyControls {

    // The name radio's segments, in the order the layout lays them out left to right: the two drawn
    // forms longest-first, then No. The lit segment opens the sentence its trailing caption closes -
    // "Full faction names", "No faction names" - which is why that caption is lowercase. The lit
    // segment index maps back to this order.
    private static final int NAME_FULL_SEGMENT = 0;
    private static final int NAME_SHORT_SEGMENT = 1;
    private static final int NAME_NONE_SEGMENT = 2;

    private OwnerMapBodyControls() {
    }

    /**
     * @param preferences the host layer's body preferences, which the controls light off and write to
     * @param target      the panel these controls are being placed on, carried into both writers so a
     *                    flip or a pick repaints that sector's overlay and is filed as that screen's own
     * @return the tab's view-agnostic sub-options, top to bottom: the uninhabited-systems checkbox
     *         (lit when uninhabited systems draw), then the Full/Short/No name radio (its lit
     *         segment the active choice) with its trailing "faction names" label
     */
    public static List<ControlSpec> buildSharedControls(
            OwnerMapBodyPreferences preferences,
            BodyControlTarget target) {

        return List.of(
            CheckboxSpec.lit(
                // The plain text tone: the box states an option rather than calling anything out.
                new TextSpan(
                    KmuStringKeys.get(KmuStringKeys.OWNER_MAP_CTL_UNINHABITED),
                    StarsectorUiColour.VANILLA_TEXT.resolve()),
                preferences.uninhabitedOutline().isOutlineDrawn(target.memoryScope()),
                cellIndex -> toggleUninhabitedSystems(preferences.uninhabitedOutline(), target)),
            HorizontalRadioSpec
                .of(
                    List.of(
                        KmuStringKeys.get(KmuStringKeys.OWNER_MAP_CTL_NAME_FULL),
                        KmuStringKeys.get(KmuStringKeys.OWNER_MAP_CTL_NAME_SHORT),
                        KmuStringKeys.get(KmuStringKeys.OWNER_MAP_CTL_NAME_NONE)),
                    nameFormatRadioState(preferences.nameFormat(), target.memoryScope()),
                    segmentIndex -> selectNameFormatSegment(preferences.nameFormat(), segmentIndex, target))
                .showsCaption(KmuStringKeys.get(KmuStringKeys.OWNER_MAP_CTL_FACTION_NAMES)));
    }

    /**
     * The view-selector radio: one side-by-side segment per registered view, all on one row, the lit
     * segment the active view. A re-pick of the lit view is
     * inert, so the radio always holds a view once one is picked - the tab selector (No Layer) is the
     * one control that turns the map off, and the view axis has no "none" a player can fall into by
     * clicking twice.
     *
     * @param viewRegistry the host layer's own views, which the segments list and a click picks among
     * @param memoryScope  the scope of the screen this body was opened on, for the selector to read
     *                     and write the view under so each panel holds its own
     * @return the horizontal, always-on view-selector radio
     */
    public static ControlSpec buildViewSelector(
            MapLayerViewRegistry viewRegistry,
            ScreenMemoryScope memoryScope) {

        var labels = new ArrayList<String>();
        for (var view : viewRegistry.getViews()) {
            labels.add(KmuStringKeys.get(view.getSegmentLabelKey()));
        }
        // No caption: the segment labels already name the views, so a trailing word would only repeat
        // what the row reads as.
        return HorizontalRadioSpec.of(
            labels,
            viewRegistry.getSelectedViewIndex(memoryScope),
            segmentIndex -> selectViewSegment(viewRegistry, segmentIndex, memoryScope));
    }

    // Activates the view its clicked segment names, on the screen the radio was placed on - where the
    // carried screen becomes the address of the slot written. Any index outside the registered views is
    // ignored, so a stray hit changes nothing. Each view remembers its own spotlight (a faction ID
    // under a view painting lone factions, a group ID under one painting groups), so a switch loads
    // the switched-in view's stored bloc rather than clearing - the choice survives moving between views and the tab
    // being switched away from. The switched-in view's slot is then healed against its current
    // selectable blocs, so a bloc that lapsed since it was last shown (a faction removed, a group
    // dissolved) does not spotlight an empty footprint; the switch itself repaints, so the cleared
    // spotlight shows without its own refresh request. The heal covers every screen, this one included,
    // since what lapsed lapsed for both panels.
    private static void selectViewSegment(
            MapLayerViewRegistry viewRegistry,
            int segmentIndex,
            ScreenMemoryScope memoryScope) {

        List<OwnerPaintedView> views = viewRegistry.getViews();
        if (segmentIndex < 0 || segmentIndex >= views.size()) {
            return;
        }
        viewRegistry.selectView(memoryScope, views.get(segmentIndex));
        FilterSelectionHeal.healStaleSelectionAgainstActiveView(viewRegistry);
    }

    // Flips the uninhabited-systems outline on or off for the panel this box was placed on: if it
    // currently draws there, hide it; otherwise draw it. This is where the panel's screen stops being
    // carried and becomes the address of the slot written.
    private static void toggleUninhabitedSystems(
            UninhabitedOutlinePreference outline,
            BodyControlTarget target) {

        outline.setOutlineDrawn(
            target.memoryScope(),
            !outline.isOutlineDrawn(target.memoryScope()),
            target.board());
    }

    // Writes the name choice for the clicked radio segment, matching the Full-Short-No segment order
    // the labels are supplied in. No is a choice like the other two rather than a separate gate, so
    // turning the names off is the same one write. Any other index is ignored, so a stray hit outside
    // the three known segments changes nothing.
    private static void selectNameFormatSegment(
            NameFormatPreference nameFormat,
            int segmentIndex,
            BodyControlTarget target) {

        if (segmentIndex == NAME_FULL_SEGMENT) {
            nameFormat.selectNameFormat(target.memoryScope(), FactionNameFormatChoice.FULL, target.board());
        } else if (segmentIndex == NAME_SHORT_SEGMENT) {
            nameFormat.selectNameFormat(target.memoryScope(), FactionNameFormatChoice.SHORT, target.board());
        } else if (segmentIndex == NAME_NONE_SEGMENT) {
            nameFormat.selectNameFormat(target.memoryScope(), FactionNameFormatChoice.NONE, target.board());
        }
    }

    // The radio lights the segment for the screen's own name choice, matching the Full-Short-No segment
    // order the labels are supplied in.
    private static int nameFormatRadioState(NameFormatPreference nameFormat, ScreenMemoryScope memoryScope) {
        return switch (nameFormat.getSelectedNameFormat(memoryScope)) {
            case FULL -> NAME_FULL_SEGMENT;
            case SHORT -> NAME_SHORT_SEGMENT;
            case NONE -> NAME_NONE_SEGMENT;
        };
    }
}
