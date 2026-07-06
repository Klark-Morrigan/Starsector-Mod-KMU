package kmu.politicalmap.ui;

import java.util.List;

/**
 * A tab's description of one body control before it is laid out: its {@link SidebarControlKind}, the
 * label(s) it carries, and an optional trailing label. The layout measures these to snap the control
 * to its text and the renderer draws them, while the tab that supplies the spec owns what the
 * control does. Keeping the description generic lets any tab compose a body without the layout
 * learning the control's meaning.
 *
 * @param kind          which widget the control is
 * @param labels        the control's own label(s): one for a checkbox or toggle, one per option for
 *                      a radio (in segment order)
 * @param trailingLabel a label drawn after the control (a radio's caption), or blank for none;
 *                      reserved in the body width so it clears the border though it is not clicked
 */
public record SidebarControlSpec(SidebarControlKind kind, List<String> labels,
        String trailingLabel) {
}
