package kmu.maplayers.base.sidebar;

import kmlib.starsector.ui.widgets.RadioAlignment;

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
 * <p>The {@code action} is the same idea for input: the tab attaches what a click does when it
 * builds the spec, so the framework input listener acts on a click without learning the meaning.
 * The layout and renderer ignore it (they only measure and draw), so a spec built purely to be laid
 * out carries {@link SidebarControlAction#NONE}.
 *
 * <p>{@code alignment} and {@code canDeselect} refine a radio and are inert for the other kinds. The
 * alignment flows a radio's segments horizontally (the default a compact option pair reads best in)
 * or vertically (a stacked view selector). {@code canDeselect} lets a click on the already-lit
 * segment turn the whole control off ({@link #NO_SELECTION}) - the view radio's "map off while the
 * tab stays open" - rather than being inert as a standard always-selected radio's re-pick is.
 *
 * @param kind          which widget the control is
 * @param labels        the control's own label(s): one for a checkbox or toggle, one per option for
 *                      a radio (in segment order)
 * @param trailingLabel a label drawn after the control (a radio's caption), or blank for none;
 *                      reserved in the body width so it clears the border though it is not clicked
 * @param selectedIndex the index of the control's lit cell - the active radio segment, or 0 for a
 *                      checked checkbox / a lit toggle - or {@link #NO_SELECTION} when the control
 *                      is off and no cell is lit
 * @param action        what a click on the control does, keyed by which cell was hit; the tab
 *                      supplies it, the input listener invokes it, the layout and renderer ignore it
 * @param alignment     the direction a radio's segments flow; ignored by the other kinds
 * @param canDeselect   whether a click on a radio's lit segment turns the control off; ignored by
 *                      the other kinds
 */
public record SidebarControlSpec(SidebarControlKind kind, List<String> labels,
        String trailingLabel, int selectedIndex, SidebarControlAction action,
        RadioAlignment alignment, boolean canDeselect) {
    /** {@code selectedIndex} value meaning the control is off - no cell is lit. */
    public static final int NO_SELECTION = -1;

    /**
     * Builds a horizontal, always-selected control with the given action - the shape a checkbox,
     * toggle, or a standard option radio (Short/Full) takes. A stacked or deselectable radio uses
     * the full constructor to set its {@link RadioAlignment} and {@code canDeselect}.
     */
    public SidebarControlSpec(SidebarControlKind kind, List<String> labels, String trailingLabel,
            int selectedIndex, SidebarControlAction action) {
        this(kind, labels, trailingLabel, selectedIndex, action, RadioAlignment.HORIZONTAL, false);
    }

    /**
     * Builds a spec with no action, for a layout-only or measurement context (the layout and
     * renderer never invoke the action). A clickable control uses a constructor that supplies its
     * own {@link SidebarControlAction}.
     */
    public SidebarControlSpec(SidebarControlKind kind, List<String> labels, String trailingLabel,
            int selectedIndex) {
        this(kind, labels, trailingLabel, selectedIndex, SidebarControlAction.NONE);
    }

    /**
     * Builds a caption row: a {@link SidebarControlKind#LABEL} carrying its text as its single
     * label, with no lit cell and no action, since a caption is drawn but never clicked. Composing
     * it here keeps the LABEL shape - text in the first label, {@link #NO_SELECTION}, {@link
     * SidebarControlAction#NONE} - in one place for any tab that heads its controls with a caption.
     */
    public static SidebarControlSpec createLabel(String text) {
        return new SidebarControlSpec(SidebarControlKind.LABEL, List.of(text), "", NO_SELECTION);
    }
}
