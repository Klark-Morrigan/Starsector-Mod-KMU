package kmu.maplayers.ownermap.sidebar;

import com.fs.starfarer.api.campaign.SectorAPI;

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
    // "Full faction names", "No faction names" - which is why that caption is lowercase. The labels,
    // the lit segment and a click's choice are all read off this one order.
    private static final List<FactionNameFormatChoice> NAME_SEGMENT_CHOICES = List.of(
        FactionNameFormatChoice.FULL,
        FactionNameFormatChoice.SHORT,
        FactionNameFormatChoice.NONE);

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

        var outline = preferences.uninhabitedOutline();
        var nameFormat = preferences.nameFormat();

        return List.of(
            CheckboxSpec.lit(
                // The plain text tone: the box states an option rather than calling anything out.
                new TextSpan(
                    KmuStringKeys.get(KmuStringKeys.OWNER_MAP_CTL_UNINHABITED),
                    StarsectorUiColour.VANILLA_TEXT.resolve()),
                outline.isOutlineDrawn(target.memoryScope()),
                cellIndex -> PanelToggles.flipToggle(
                    target,
                    outline::isOutlineDrawn,
                    outline::setOutlineDrawn)),
            HorizontalRadioSpec
                .of(
                    NAME_SEGMENT_CHOICES.stream()
                        .map(OwnerMapBodyControls::resolveNameSegmentLabel)
                        .toList(),
                    NAME_SEGMENT_CHOICES.indexOf(nameFormat.getSelectedNameFormat(target.memoryScope())),
                    segmentIndex -> selectNameFormatSegment(nameFormat, segmentIndex, target))
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
     * @param sector       the sector this body was built for, which a click heals the switched-in
     *                     view's spotlights against - resolved with the body rather than at the click,
     *                     so a click cannot judge against a sector loaded since
     * @param memoryScope  the scope of the screen this body was opened on, for the selector to read
     *                     and write the view under so each panel holds its own
     * @return the horizontal, always-on view-selector radio
     */
    public static ControlSpec buildViewSelector(
            MapLayerViewRegistry viewRegistry,
            SectorAPI sector,
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
            segmentIndex -> selectViewSegment(viewRegistry, sector, segmentIndex, memoryScope));
    }

    // The resolved label of one name-radio segment.
    private static String resolveNameSegmentLabel(FactionNameFormatChoice choice) {
        return switch (choice) {
            case FULL -> KmuStringKeys.get(KmuStringKeys.OWNER_MAP_CTL_NAME_FULL);
            case SHORT -> KmuStringKeys.get(KmuStringKeys.OWNER_MAP_CTL_NAME_SHORT);
            case NONE -> KmuStringKeys.get(KmuStringKeys.OWNER_MAP_CTL_NAME_NONE);
        };
    }

    // Writes the name choice for the clicked radio segment. No is a choice like the other two rather
    // than a separate gate, so turning the names off is the same one write. Any index outside the
    // segments is ignored, so a stray hit changes nothing.
    private static void selectNameFormatSegment(
            NameFormatPreference nameFormat,
            int segmentIndex,
            BodyControlTarget target) {

        if (segmentIndex < 0 || segmentIndex >= NAME_SEGMENT_CHOICES.size()) {
            return;
        }
        nameFormat.selectNameFormat(
            target.memoryScope(),
            NAME_SEGMENT_CHOICES.get(segmentIndex),
            target.board());
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
    // since what lapsed lapsed for both panels, and judges against the sector the body was built for.
    private static void selectViewSegment(
            MapLayerViewRegistry viewRegistry,
            SectorAPI sector,
            int segmentIndex,
            ScreenMemoryScope memoryScope) {

        List<OwnerPaintedView> views = viewRegistry.getViews();
        if (segmentIndex < 0 || segmentIndex >= views.size()) {
            return;
        }
        viewRegistry.selectView(memoryScope, views.get(segmentIndex));
        FilterSelectionHeal.healStaleSelectionAgainstActiveView(sector, viewRegistry);
    }
}
