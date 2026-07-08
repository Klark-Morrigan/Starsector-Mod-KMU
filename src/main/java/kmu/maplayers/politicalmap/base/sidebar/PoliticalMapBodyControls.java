package kmu.maplayers.politicalmap.base.sidebar;

import kmu.maplayers.base.sidebar.SidebarControlKind;
import kmu.maplayers.base.sidebar.SidebarControlSpec;
import kmu.settings.FactionNameFormatChoice;
import kmu.settings.KmuLunaSettings;
import kmu.util.KmuStrings;

import java.util.List;

/**
 * The political-map tab's shared body controls - the ones agnostic to which layer colours the
 * map: the uninhabited-systems checkbox and the Short/Full name-format radio. They read and
 * write the same LunaLib settings a faction or (later) an alliances layer both honour, so the
 * controls live here on the tab rather than on any one layer. A layer composes these with its
 * own layer-specific controls (the factions overlay toggle) to form its full body.
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
     * @return the tab's layer-agnostic controls, top to bottom: the uninhabited-systems
     *         checkbox (lit when uninhabited systems draw), then the Short/Full name-format radio
     *         (its lit segment the active format) with its trailing "Names" label
     */
    public static List<SidebarControlSpec> buildSharedControls() {
        return List.of(
                new SidebarControlSpec(SidebarControlKind.CHECKBOX,
                        List.of(KmuStrings.get(KmuStrings.POLITICAL_MAP_CTL_UNINHABITED)), "",
                        uninhabitedCheckboxState()),
                new SidebarControlSpec(SidebarControlKind.RADIO,
                        List.of(KmuStrings.get(KmuStrings.POLITICAL_MAP_CTL_NAME_SHORT),
                                KmuStrings.get(KmuStrings.POLITICAL_MAP_CTL_NAME_FULL)),
                        KmuStrings.get(KmuStrings.POLITICAL_MAP_CTL_NAMES),
                        nameFormatRadioState()));
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
