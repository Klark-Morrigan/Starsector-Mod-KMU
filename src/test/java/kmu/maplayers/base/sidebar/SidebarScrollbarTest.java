package kmu.maplayers.base.sidebar;

import kmlib.math.geometry.Rectangle;
import kmlib.starsector.ui.widgets.TabPanelPlacement;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Pins the sidebar's scrollbar-geometry adapter: it maps a laid-out {@link SidebarPlacement} (body,
 * viewport, offset, overflow) onto the pure KMLib scrollbar math, so the track sits in the body's right
 * gutter, the thumb sizes to the visible fraction and drops as the list scrolls, the grab column runs the
 * gutter right of the list, and a pointer maps to a scroll offset. A body wider than its list column, and
 * a list twice its viewport, make the expected geometry hand-checkable.
 */
final class SidebarScrollbarTest {
    private static final float TOLERANCE = 0.01f;
    // A 200-wide body (right edge at 300) over a viewport covering a narrower 120-wide list column (right
    // edge at 228), and a list that overruns its 100-tall viewport by 100 (content 200 tall).
    private static final Rectangle BODY = new Rectangle(100f, 200f, 200f, 300f);
    private static final Rectangle VIEWPORT = new Rectangle(108f, 250f, 120f, 100f);
    private static final float OVERFLOW = 100f;

    private static SidebarPlacement placementAt(float scrollOffset) {
        // The box is not read by the scrollbar geometry, so the body stands in for it; only body,
        // viewport, offset, and overflow drive the adapter.
        var panel = new TabPanelPlacement(BODY, List.of(), BODY);
        return new SidebarPlacement(panel, List.of(), VIEWPORT, scrollOffset, OVERFLOW);
    }

    @Nested
    class ComputeTrack {

        @Test
        void computeTrackSpansTheViewportInTheBodyRightGutter() {
            var track = SidebarScrollbar.computeTrack(placementAt(0f));
            // The track spans the viewport vertically and sits in the gutter - right of the list column
            // (228) and within the body's right edge (300).
            assertThat(track.y()).isCloseTo(VIEWPORT.y(), within(TOLERANCE));
            assertThat(track.height()).isCloseTo(VIEWPORT.height(), within(TOLERANCE));
            assertThat(track.x()).isGreaterThan(VIEWPORT.x() + VIEWPORT.width());
            assertThat(track.x() + track.width())
                    .isLessThanOrEqualTo(BODY.x() + BODY.width() + TOLERANCE);
        }
    }

    @Nested
    class ComputeThumb {

        @Test
        void computeThumbSizesTheThumbToTheVisibleFractionOfTheContent() {
            var track = SidebarScrollbar.computeTrack(placementAt(0f));
            var thumb = SidebarScrollbar.computeThumb(placementAt(0f), track);
            // Half the content is visible (viewport 100 of content 200), so the thumb is half the track -
            // the adapter feeds "content = viewport + overflow" to the thumb math.
            assertThat(thumb.height()).isCloseTo(track.height() / 2f, within(TOLERANCE));
        }

        @Test
        void computeThumbDropsTheThumbAsTheListScrolls() {
            var track = SidebarScrollbar.computeTrack(placementAt(0f));
            var atTop = SidebarScrollbar.computeThumb(placementAt(0f), track);
            var scrolled = SidebarScrollbar.computeThumb(placementAt(OVERFLOW), track);
            // Fully scrolled, the thumb's bottom meets the track bottom, below where it sat at the top.
            assertThat(scrolled.y()).isLessThan(atTop.y());
            assertThat(scrolled.y()).isCloseTo(track.y(), within(TOLERANCE));
        }
    }

    @Nested
    class ComputeGrabColumn {

        @Test
        void computeGrabColumnRunsTheGutterRightOfTheListAtTheTrackHeight() {
            var track = SidebarScrollbar.computeTrack(placementAt(0f));
            var grab = SidebarScrollbar.computeGrabColumn(placementAt(0f), track);
            // The grab column starts at the list's right edge (228) and runs to the body's right edge
            // (300), at the track's height - wider than the thin track so the thumb need not be hit
            // exactly, and right of the list so it never competes with a row click.
            assertThat(grab.x()).isCloseTo(VIEWPORT.x() + VIEWPORT.width(), within(TOLERANCE));
            assertThat(grab.x() + grab.width())
                    .isCloseTo(BODY.x() + BODY.width(), within(TOLERANCE));
            assertThat(grab.width()).isGreaterThan(track.width());
            assertThat(grab.y()).isCloseTo(track.y(), within(TOLERANCE));
            assertThat(grab.height()).isCloseTo(track.height(), within(TOLERANCE));
        }
    }

    @Nested
    class ResolveOffsetForPointer {

        @Test
        void resolveOffsetForPointerIsZeroAtTheTrackTop() {
            var track = SidebarScrollbar.computeTrack(placementAt(0f));
            var offset = SidebarScrollbar.resolveOffsetForPointer(placementAt(0f), track,
                    track.y() + track.height());
            assertThat(offset).isCloseTo(0f, within(TOLERANCE));
        }

        @Test
        void resolveOffsetForPointerIsTheOverflowAtTheTrackBottom() {
            var track = SidebarScrollbar.computeTrack(placementAt(0f));
            var offset = SidebarScrollbar.resolveOffsetForPointer(placementAt(0f), track, track.y());
            assertThat(offset).isCloseTo(OVERFLOW, within(TOLERANCE));
        }
    }
}
