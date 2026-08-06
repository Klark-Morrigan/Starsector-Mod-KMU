package kmu.maplayers.base.sidebar.runtime;

import kmlib.math.geometry.Rectangle;
import kmlib.starsector.ui.controls.Control;
import kmlib.starsector.ui.widgets.BoxBorder;
import kmlib.starsector.ui.widgets.PanelPlacement;
import kmlib.starsector.ui.widgets.tabs.TabPanelPlacement;

import java.util.List;

/**
 * Test-only builders for a laid-out sidebar. A {@link TabPanelPlacement} is five nested values of
 * which each test reads one or two, so a test writing its own assembles three it does not care
 * about - and the ones it does not care about are where two fixtures quietly disagree about what a
 * drawn panel looks like.
 *
 * <p>Each builder is named for the shape it makes rather than taking the parts as arguments,
 * because the shapes differ in what is deliberately absent: a panel that lays no tabs still carries
 * a header control (an absent one is a different case, and would throw where an empty one answers
 * "no tab here"), while a panel with no body carries no handle at all.
 */
final class SidebarPlacements {

    // The frame the placements are laid out around. No assertion reads it - the body's box already
    // spans the border - so it only has to be a width a real placement could carry.
    private static final float BORDER_WIDTH = 1f;

    // The box a builder stands in for geometry its case never reads, sized to nothing so a stray
    // hit-test against it lands outside rather than reporting a hit the test never arranged.
    private static final Rectangle UNREAD_BOX = new Rectangle(0f, 0f, 0f, 0f);

    private SidebarPlacements() {
    }

    /**
     * A panel laid over the given body box, with the given collapse handle - the shape a hit-test
     * reads. Its header is absent rather than empty, since a hit-test that reached for a tab on it
     * would be testing geometry this shape does not describe.
     *
     * @param bodyBox the box the panel's body occupies, the rect a point is tested against
     * @param notch   the collapse handle's rect, or null for a bodyless panel that exposes none
     * @return the placement
     */
    static TabPanelPlacement placeSidebarOverBody(Rectangle bodyBox, Rectangle notch) {
        return placeSidebar(null, bodyBox, notch);
    }

    /**
     * A panel with no tabs laid in its header and no collapse handle - the shape a per-frame
     * advance runs against when the motion under test answers to no pointer. Every hit-test the
     * advance makes then misses, so a tab that lights can only have been lit by something other
     * than the cursor.
     *
     * @return the placement
     */
    static TabPanelPlacement placeSidebarWithNoTabsLaid() {
        return placeSidebar(
            new Control(null, UNREAD_BOX, List.of()),
            UNREAD_BOX,
            null);
    }

    // The one assembly every shape above is a named case of, so the parts no case reads cannot
    // drift between them. The drawn tab row is the unread box for the same reason the box itself is
    // where a case does not read it: these shapes describe a body and a handle, so a row sized to
    // nothing keeps the panel's own footprint out of an assertion that never arranged for it.
    private static TabPanelPlacement placeSidebar(
            Control tabsHeader,
            Rectangle bodyBox,
            Rectangle notch) {

        return new TabPanelPlacement(
            tabsHeader,
            UNREAD_BOX,
            new PanelPlacement(bodyBox, bodyBox, List.of(), bodyBox, 0f, 0f),
            new BoxBorder(BORDER_WIDTH),
            notch);
    }
}
