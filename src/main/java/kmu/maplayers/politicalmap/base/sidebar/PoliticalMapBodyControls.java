package kmu.maplayers.politicalmap.base.sidebar;

import kmu.maplayers.base.sidebar.SidebarControlKind;
import kmu.maplayers.base.sidebar.SidebarControlSpec;
import kmu.util.KmuStrings;

import java.util.List;

/**
 * The political-map tab's shared body controls - the ones agnostic to which layer colours the
 * map: the uninhabited-systems checkbox and the Short/Full name-format radio. They read and
 * write the same LunaLib settings a faction or (later) an alliances layer both honour, so the
 * controls live here on the tab rather than on any one layer. A layer composes these with its
 * own layer-specific controls (the factions overlay toggle) to form its full body.
 *
 * <p>The specs carry resolved display strings, since the layout snaps each control to its
 * measured text; they are rebuilt per call so a name-format change shows immediately.
 */
public final class PoliticalMapBodyControls {

    private PoliticalMapBodyControls() {
    }

    /**
     * @return the tab's layer-agnostic controls, top to bottom: the uninhabited-systems
     *         checkbox, then the Short/Full name-format radio with its trailing "Names" label
     */
    public static List<SidebarControlSpec> buildSharedControls() {
        return List.of(
                new SidebarControlSpec(SidebarControlKind.CHECKBOX,
                        List.of(KmuStrings.get(KmuStrings.POLITICAL_MAP_CTL_UNINHABITED)), ""),
                new SidebarControlSpec(SidebarControlKind.RADIO,
                        List.of(KmuStrings.get(KmuStrings.POLITICAL_MAP_CTL_NAME_SHORT),
                                KmuStrings.get(KmuStrings.POLITICAL_MAP_CTL_NAME_FULL)),
                        KmuStrings.get(KmuStrings.POLITICAL_MAP_CTL_NAMES)));
    }
}
