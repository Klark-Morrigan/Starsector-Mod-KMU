package kmu.maplayers.base.sidebar.runtime;

import kmlib.math.geometry.Rectangle;

import kmu.maplayers.base.sidebar.SidebarControl;
import kmu.maplayers.base.sidebar.SidebarControlAction;
import kmu.maplayers.base.sidebar.SidebarControlKind;
import kmu.maplayers.base.sidebar.SidebarControlSpec;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the sidebar's click-to-control resolution: a press maps to the control under it and fires
 * that control's action, while a caption label - drawn but not clickable - is passed over so it
 * never swallows a click as if it acted.
 */
final class MapLayerSidebarInputTest {
    private static final Rectangle ROW = new Rectangle(100f, 200f, 120f, 20f);

    @Nested
    class ActivateControlIfHit {

        @Test
        void activateControlIfHitPassesOverACaptionLabelWithoutActing() {
            var fired = new boolean[1];
            // A caption row with a recording action, so a hit that fired it would be caught; the
            // production caption carries no action, but pinning the skip proves the label is passed
            // over before any action is reached.
            var label = buildControl(SidebarControlKind.LABEL, "Non-allied", cell -> fired[0] = true);
            var acted = MapLayerSidebarInput.activateControlIfHit(label, ROW.x() + ROW.width() / 2f,
                    ROW.y() + ROW.height() / 2f);
            assertThat(acted).as("a caption is not a hit target").isFalse();
            assertThat(fired[0]).as("the caption's action must not fire").isFalse();
        }

        @Test
        void activateControlIfHitFiresACheckboxHitAnywhereOnItsRow() {
            var firedCell = new int[]{-1};
            var checkbox = buildControl(SidebarControlKind.CHECKBOX, "Muted", cell -> firedCell[0] = cell);
            var acted = MapLayerSidebarInput.activateControlIfHit(checkbox, ROW.x() + ROW.width() / 2f,
                    ROW.y() + ROW.height() / 2f);
            assertThat(acted).isTrue();
            assertThat(firedCell[0]).as("a single-cell control reports cell 0").isZero();
        }

        @Test
        void activateControlIfHitReportsNoHitForAPressOutsideACheckboxRow() {
            var checkbox = buildControl(SidebarControlKind.CHECKBOX, "Muted", SidebarControlAction.NONE);
            var acted = MapLayerSidebarInput.activateControlIfHit(checkbox, ROW.x() - 10f,
                    ROW.y() + ROW.height() / 2f);
            assertThat(acted).isFalse();
        }
    }

    // A single-row control occupying ROW, so each test states only the kind, label, and action that
    // distinguish its case rather than repeating the spec-and-bounds construction.
    private static SidebarControl buildControl(SidebarControlKind kind, String label,
            SidebarControlAction action) {
        return new SidebarControl(new SidebarControlSpec(kind, List.of(label), "",
                SidebarControlSpec.NO_SELECTION, action), ROW, List.of());
    }
}
