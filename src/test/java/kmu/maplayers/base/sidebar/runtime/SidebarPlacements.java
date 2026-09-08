package kmu.maplayers.base.sidebar.runtime;

import kmlib.math.geometry.Rectangle;
import kmlib.starsector.ui.controls.Control;
import kmlib.starsector.ui.widgets.BoxBorder;
import kmlib.starsector.ui.widgets.PanelPlacement;
import kmlib.starsector.ui.widgets.scroll.ScrollbarThickness;
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
        // One control laid across the body, because a placement's footprint is drawn from whether it
        // has a body at all: a box with no controls in it describes a panel that is its tab row and
        // nothing else, so its box would claim no screen however large the rect is.
        return placeSidebar(
            null,
            UNREAD_BOX,
            bodyBox,
            List.of(new Control(null, bodyBox, List.of())),
            notch);
    }

    /**
     * A panel laid out as a screen actually draws it: a tab row standing on the body box, with the
     * collapse handle on the box's right edge - the shape a pass reading the whole panel's
     * footprint needs, where the builder above leaves the row sized to nothing.
     *
     * @param drawnHeaderBand the part of the tab row on screen
     * @param bodyBox         the box the panel's body occupies
     * @param notch           the collapse handle's rect
     * @return the placement
     */
    static TabPanelPlacement placeSidebarUnderDrawnRow(
            Rectangle drawnHeaderBand,
            Rectangle bodyBox,
            Rectangle notch) {

        return placeSidebar(
            null,
            drawnHeaderBand,
            bodyBox,
            List.of(new Control(null, bodyBox, List.of())),
            notch);
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
            UNREAD_BOX,
            List.of(),
            null);
    }

    // The one assembly every shape above is a named case of, so the parts no case reads cannot
    // drift between them. A case that does not read the drawn tab row passes the unread box for it,
    // for the same reason it passes one for the body's box: a rect sized to nothing keeps that piece
    // of the panel's footprint out of an assertion that never arranged for it.
    //
    // The body's controls are a part of the shape rather than a constant, because the placement reads
    // them to decide whether it has a body at all - and a shape meaning "panel with a body" and one
    // meaning "tab row alone" differ in exactly that, not in the size of the box they carry.
    private static TabPanelPlacement placeSidebar(
            Control tabsHeader,
            Rectangle drawnHeaderBand,
            Rectangle bodyBox,
            List<Control> bodyControls,
            Rectangle notch) {

        return new TabPanelPlacement(
            tabsHeader,
            // No band button: every shape here is about what the panel's own gates and hit-tests answer,
            // and the bar's opener takes no part in any of them.
            null,
            drawnHeaderBand,
            new PanelPlacement(
                bodyBox,
                bodyBox,
                bodyControls,
                bodyBox,
                0f,
                0f,
                ScrollbarThickness.DEFAULT),
            new BoxBorder(BORDER_WIDTH),
            notch);
    }
}
