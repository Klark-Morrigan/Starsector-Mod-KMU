package kmu.maplayers.base.sidebar.runtime;

import kmlib.math.geometry.Rectangle;
import kmlib.starsector.ui.controls.Control;
import kmlib.starsector.ui.controls.ControlAction;
import kmlib.starsector.ui.controls.ControlKind;
import kmlib.starsector.ui.controls.ControlSpec;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the sidebar's click-to-control resolution: a press maps to the control under it and fires
 * that control's action, while a caption label - drawn but not clickable - is passed over so it
 * never swallows a click as if it acted.
 */
final class MapLayerSidebarInputTest {
    private static final Rectangle ROW = new Rectangle(100f, 200f, 120f, 20f);
    // A viewport covering the whole row, so a non-scrolling control's hit-test ignores it; the scrolling
    // cases below pass their own viewport to exercise the clip.
    private static final Rectangle FULL_VIEWPORT = new Rectangle(0f, 0f, 10000f, 10000f);

    @Nested
    class ActivateControlIfHit {

        @Test
        void activateControlIfHitPassesOverACaptionLabelWithoutActing() {
            var fired = new boolean[1];
            // A caption row with a recording action, so a hit that fired it would be caught; the
            // production caption carries no action, but pinning the skip proves the label is passed
            // over before any action is reached.
            var label = buildControl(ControlKind.LABEL, "Non-allied", cell -> fired[0] = true);
            var acted = MapLayerSidebarInput.activateControlIfHit(label, FULL_VIEWPORT,
                    ROW.x() + ROW.width() / 2f, ROW.y() + ROW.height() / 2f);
            assertThat(acted).as("a caption is not a hit target").isFalse();
            assertThat(fired[0]).as("the caption's action must not fire").isFalse();
        }

        @Test
        void activateControlIfHitFiresACheckboxHitAnywhereOnItsRow() {
            var firedCell = new int[]{-1};
            var checkbox = buildControl(ControlKind.CHECKBOX, "Muted", cell -> firedCell[0] = cell);
            var acted = MapLayerSidebarInput.activateControlIfHit(checkbox, FULL_VIEWPORT,
                    ROW.x() + ROW.width() / 2f, ROW.y() + ROW.height() / 2f);
            assertThat(acted).isTrue();
            assertThat(firedCell[0]).as("a single-cell control reports cell 0").isZero();
        }

        @Test
        void activateControlIfHitReportsNoHitForAPressOutsideACheckboxRow() {
            var checkbox = buildControl(ControlKind.CHECKBOX, "Muted", ControlAction.NONE);
            var acted = MapLayerSidebarInput.activateControlIfHit(checkbox, FULL_VIEWPORT,
                    ROW.x() - 10f, ROW.y() + ROW.height() / 2f);
            assertThat(acted).isFalse();
        }

        @Test
        void activateControlIfHitFiresAScrollingListOptionInsideItsViewport() {
            var firedCell = new int[]{-1};
            var list = buildScrollingListAtRow(cell -> firedCell[0] = cell);
            // The press lands on the list's one option and inside a viewport that covers the row, so the
            // option fires as a normal radio hit.
            var acted = MapLayerSidebarInput.activateControlIfHit(list, ROW,
                    ROW.x() + ROW.width() / 2f, ROW.y() + ROW.height() / 2f);
            assertThat(acted).isTrue();
            assertThat(firedCell[0]).isZero();
        }

        @Test
        void activateControlIfHitRejectsAScrollingListOptionScrolledOutOfItsViewport() {
            var fired = new boolean[1];
            var list = buildScrollingListAtRow(cell -> fired[0] = true);
            // The option's segment sits at ROW, but the viewport is a strip well above it - as if the row
            // scrolled up under the header - so the press over the clipped-out row must not fire it.
            var viewportAbove = new Rectangle(ROW.x(), ROW.y() + 100f, ROW.width(), 40f);
            var acted = MapLayerSidebarInput.activateControlIfHit(list, viewportAbove,
                    ROW.x() + ROW.width() / 2f, ROW.y() + ROW.height() / 2f);
            assertThat(acted).as("a row clipped from the viewport is not clickable").isFalse();
            assertThat(fired[0]).as("the clipped-out option's action must not fire").isFalse();
        }
    }

    // A single-row control occupying ROW, so each test states only the kind, label, and action that
    // distinguish its case rather than repeating the spec-and-bounds construction.
    private static Control buildControl(ControlKind kind, String label,
            ControlAction action) {
        return new Control(new ControlSpec(kind, List.of(label), "",
                ControlSpec.NO_SELECTION, action), ROW, List.of());
    }

    // A one-option scrolling list laid out at ROW: a vertical icon-radio marked as the scroll region,
    // its single segment the whole row, so a press at ROW hits option 0 unless the viewport clips it.
    private static Control buildScrollingListAtRow(ControlAction action) {
        var spec = ControlSpec.createIconRadioList(List.of("Opt"), Arrays.asList((String) null),
                ControlSpec.NO_SELECTION, action).buildScrollableCopy();
        return new Control(spec, ROW, List.of(ROW));
    }
}
