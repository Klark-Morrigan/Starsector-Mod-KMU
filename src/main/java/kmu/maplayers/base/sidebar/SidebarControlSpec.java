package kmu.maplayers.base.sidebar;

import java.util.List;

/**
 * A tab's description of one body control: its {@link SidebarControlKind}, the label(s) it carries,
 * an optional trailing label, and the {@code selectedIndex} of its currently lit cell. The layout
 * measures the labels to snap the control to its text and the renderer draws it in that state,
 * while the tab that supplies the spec owns what the control means and does. Keeping the
 * description generic - state and all - lets any tab compose a body without the layout or the
 * renderer learning the control's meaning.
 *
 * <p>The state rides along as data because the tab, not the renderer, knows how to read it: a
 * generic renderer shared across tabs cannot map a bare {@code CHECKBOX} back to which setting it
 * reflects. The tab reads its live value when it builds the spec (specs are rebuilt each frame),
 * so the carried state is current, and every control reduces to "which of my cells is lit" - a
 * checkbox and a toggle each have one cell (index 0) that is either lit (on) or {@link
 * #NO_SELECTION}, a radio has one lit cell among its segments.
 *
 * @param kind          which widget the control is
 * @param labels        the control's own label(s): one for a checkbox or toggle, one per option for
 *                      a radio (in segment order)
 * @param trailingLabel a label drawn after the control (a radio's caption), or blank for none;
 *                      reserved in the body width so it clears the border though it is not clicked
 * @param selectedIndex the index of the control's lit cell - the active radio segment, or 0 for a
 *                      checked checkbox / a lit toggle - or {@link #NO_SELECTION} when the control
 *                      is off and no cell is lit
 */
public record SidebarControlSpec(SidebarControlKind kind, List<String> labels,
        String trailingLabel, int selectedIndex) {
    /** {@code selectedIndex} value meaning the control is off - no cell is lit. */
    public static final int NO_SELECTION = -1;
}
