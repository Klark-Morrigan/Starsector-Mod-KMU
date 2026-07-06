package kmu.politicalmap.ui;

/**
 * The kind of a sidebar body control - which raw-GL widget lays it out and paints it. The body is a
 * generic column of these: a tab supplies whichever controls it needs, and the layout snaps and
 * stacks them by kind without knowing what any one control means. What a control does on a click
 * stays with the tab that supplied it, so the sidebar hosts one tab's controls as readily as
 * another's.
 */
public enum SidebarControlKind {
    /** A tick box with a trailing label; the whole row is the hit target. */
    CHECKBOX,

    /** A row of mutually exclusive option segments, each segment its own hit target. */
    RADIO,

    /** A single button that lights when on; the button is the hit target. */
    TOGGLE
}
