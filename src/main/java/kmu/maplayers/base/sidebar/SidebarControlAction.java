package kmu.maplayers.base.sidebar;

/**
 * What activating a sidebar body control does - the behaviour the tab that supplied the control
 * attaches to it. The framework input listener hit-tests a click to a control and its cell, then
 * calls {@link #activateCell}, without learning what the action means: a checkbox flips a setting,
 * a radio segment writes its option, a toggle flips a flag, and the listener treats them all the
 * same. Keeping the action with the control (built alongside its {@link SidebarControlSpec}) is the
 * single source of what a control does, so there is no separate index-to-action map to drift from
 * the control order.
 *
 * <p>The layout and renderer never invoke it - they draw the control's geometry and lit state - so
 * a control built only to be measured or drawn can carry {@link #NONE}.
 */
@FunctionalInterface
public interface SidebarControlAction {
    /** An action that does nothing, for a control that is only laid out or drawn, never clicked. */
    SidebarControlAction NONE = cellIndex -> {
    };

    /**
     * Acts on a click that landed on one of the control's cells.
     *
     * @param cellIndex which cell was clicked: a radio's segment index, or 0 for a single-cell
     *                  checkbox or toggle (whose whole row is one hit target)
     */
    void activateCell(int cellIndex);
}
