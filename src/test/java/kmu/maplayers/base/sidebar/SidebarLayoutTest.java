package kmu.maplayers.base.sidebar;

import kmlib.math.geometry.Rectangle;
import kmlib.starsector.ui.font.LineWidthMeasurer;
import kmlib.starsector.ui.controls.RadioAlignment;
import kmlib.starsector.ui.widgets.VanillaTabContent;
import kmlib.testfixtures.starsector.ui.font.LineWidthMeasurerFake;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Pins {@link SidebarLayout#computePlacement}: the box hangs from the screen's top-left by its
 * padding, each tab snaps to its measured label, the border insets the content, and the body stacks
 * whatever controls the active tab supplies - so the rectangles the renderer draws are the ones the
 * click hit-test reads.
 */
final class SidebarLayoutTest {
    private static final float SCREEN_HEIGHT = 1080f;
    private static final int PADDING_TOP = 46;
    private static final int PADDING_LEFT = 12;
    private static final int BORDER_WIDTH = 2;
    private static final float TOLERANCE = 0.01f;

    // A round per-character width makes every snapped rectangle a hand-checkable multiple, so the
    // expected geometry is arithmetic rather than a measured constant.
    private static final float WIDTH_PER_CHAR = 10f;

    private final LineWidthMeasurer measurerFake = new LineWidthMeasurerFake(WIDTH_PER_CHAR);

    // Two tabs, matching the shipped registry's shape, stated here so the geometry does not depend
    // on the registry's contents. The strip composes "label  [shortcut]" before measuring.
    private static final List<VanillaTabContent> TABS = List.of(
            new VanillaTabContent("No Layer", "N"),
            new VanillaTabContent("Political Map", "P"));

    // A political-map-shaped body, but supplied as generic control specs the way any tab would: a
    // checkbox, a two-option radio with a trailing caption, and a toggle. The layout snaps and
    // stacks by geometry alone, so each control's lit state is left unset here.
    private static final List<SidebarControlSpec> BODY = List.of(
            new SidebarControlSpec(SidebarControlKind.CHECKBOX, List.of("Uninhabited systems"), "",
                    SidebarControlSpec.NO_SELECTION),
            new SidebarControlSpec(SidebarControlKind.RADIO, List.of("Short", "Full"), "Names",
                    SidebarControlSpec.NO_SELECTION),
            new SidebarControlSpec(SidebarControlKind.TOGGLE, List.of("Factions"), "",
                    SidebarControlSpec.NO_SELECTION));

    // Content is inset from the box by the border on every edge.
    private static final float CONTENT_X = PADDING_LEFT + BORDER_WIDTH;
    private static final float BOX_TOP_Y = SCREEN_HEIGHT - PADDING_TOP;
    private static final float CONTENT_TOP_Y = BOX_TOP_Y - BORDER_WIDTH;
    private static final float TAB_ROW_BOTTOM_Y = CONTENT_TOP_Y - SidebarLayout.TAB_HEIGHT;

    // "No Layer  [N]" is 13 characters, "Political Map  [P]" is 18, each snapped to its width plus
    // the tab text padding; both clear the minimum width.
    private static final float FIRST_TAB_WIDTH = 13 * WIDTH_PER_CHAR + SidebarLayout.TAB_TEXT_PADDING;
    private static final float SECOND_TAB_WIDTH =
            18 * WIDTH_PER_CHAR + SidebarLayout.TAB_TEXT_PADDING;
    private static final float TAB_ROW_WIDTH = FIRST_TAB_WIDTH + SECOND_TAB_WIDTH;

    @Nested
    class ComputePlacement {

        @Test
        void computePlacementHangsTheBoxFromTheTopLeftByItsPadding() {
            var box = place(List.of()).box();
            assertThat(box.x()).isCloseTo(PADDING_LEFT, within(TOLERANCE));
            assertThat(box.y() + box.height())
                    .as("box top edge sits paddingTop below the screen top")
                    .isCloseTo(BOX_TOP_Y, within(TOLERANCE));
        }

        @Test
        void computePlacementSnapsEachTabToItsMeasuredLabelWidthLeftToRight() {
            var tabs = place(List.of()).tabs();
            assertThat(tabs).hasSize(TABS.size());

            var first = tabs.get(0).bounds();
            assertThat(first.x()).isCloseTo(CONTENT_X, within(TOLERANCE));
            assertThat(first.width()).isCloseTo(FIRST_TAB_WIDTH, within(TOLERANCE));

            var second = tabs.get(1).bounds();
            assertThat(second.x()).isCloseTo(CONTENT_X + FIRST_TAB_WIDTH, within(TOLERANCE));
            assertThat(second.width()).isCloseTo(SECOND_TAB_WIDTH, within(TOLERANCE));
        }

        @Test
        void computePlacementSharesTheContentTopEdgeAcrossEveryTab() {
            for (var tab : place(List.of()).tabs()) {
                var bounds = tab.bounds();
                assertThat(bounds.y() + bounds.height())
                        .as("each tab's top edge is the inset content top")
                        .isCloseTo(CONTENT_TOP_Y, within(TOLERANCE));
                assertThat(bounds.height()).isCloseTo(SidebarLayout.TAB_HEIGHT, within(TOLERANCE));
            }
        }

        @Test
        void computePlacementCarriesEachTabsDisplayContentInOrder() {
            var tabs = place(List.of()).tabs();
            assertThat(tabs.get(0).content()).isEqualTo(TABS.get(0));
            assertThat(tabs.get(1).content()).isEqualTo(TABS.get(1));
        }

        @Test
        void computePlacementLeavesNoBodyWhenNoControlsAreGiven() {
            var placement = place(List.of());
            assertThat(placement.bodyControls()).isEmpty();
            // The box is only the bordered tab row when there is no body beneath it.
            assertThat(placement.box().height())
                    .isCloseTo(SidebarLayout.TAB_HEIGHT + 2f * BORDER_WIDTH, within(TOLERANCE));
        }

        @Test
        void computePlacementStacksTheBodyControlsAsAColumnBeneathTheTabRow() {
            var controls = place(BODY).bodyControls();
            assertThat(controls).extracting(control -> control.spec().kind()).containsExactly(
                    SidebarControlKind.CHECKBOX,
                    SidebarControlKind.RADIO,
                    SidebarControlKind.TOGGLE);

            var rowsLeft = CONTENT_X + SidebarLayout.BODY_PADDING;
            var checkbox = controls.get(0).bounds();
            // "Uninhabited systems" is 19 characters; the row is the tick box, a gap, then the label.
            var expectedCheckboxWidth = SidebarLayout.CONTROL_ROW_HEIGHT
                    + SidebarLayout.CHECKBOX_LABEL_GAP + 19 * WIDTH_PER_CHAR;
            assertThat(checkbox.x()).isCloseTo(rowsLeft, within(TOLERANCE));
            assertThat(checkbox.width()).isCloseTo(expectedCheckboxWidth, within(TOLERANCE));
            assertThat(checkbox.y() + checkbox.height())
                    .as("the first control row hangs one body inset below the tab row")
                    .isCloseTo(TAB_ROW_BOTTOM_Y - SidebarLayout.BODY_PADDING, within(TOLERANCE));
        }

        @Test
        void computePlacementSplitsARadioIntoAbuttingEqualSegments() {
            var radio = place(BODY).bodyControls().get(1);
            assertThat(radio.segments()).hasSize(2);
            var shortSegment = radio.segments().get(0);
            var fullSegment = radio.segments().get(1);
            // Segments are sized to the wider option ("Short", 5 chars) plus the segment padding.
            var expectedSegmentWidth = 5 * WIDTH_PER_CHAR + SidebarLayout.RADIO_SEGMENT_PADDING;
            assertThat(shortSegment.width()).isCloseTo(expectedSegmentWidth, within(TOLERANCE));
            assertThat(fullSegment.width()).isCloseTo(expectedSegmentWidth, within(TOLERANCE));
            assertThat(fullSegment.x())
                    .as("the full segment abuts the right edge of the short segment")
                    .isCloseTo(shortSegment.x() + shortSegment.width(), within(TOLERANCE));
            assertThat(fullSegment.y()).isCloseTo(shortSegment.y(), within(TOLERANCE));
        }

        @Test
        void computePlacementLeavesSingleHitControlsWithoutSegments() {
            var controls = place(BODY).bodyControls();
            assertThat(controls.get(0).segments()).as("checkbox has no segments").isEmpty();
            assertThat(controls.get(2).segments()).as("toggle has no segments").isEmpty();
        }

        @Test
        void computePlacementEnclosesEveryControlWithinTheBody() {
            var placement = place(BODY);
            var body = placement.body();
            assertThat(body.x()).isCloseTo(CONTENT_X, within(TOLERANCE));
            for (var control : placement.bodyControls()) {
                assertRectWithin(control.bounds(), body);
                for (var segment : control.segments()) {
                    assertRectWithin(segment, body);
                }
            }
        }

        @Test
        void computePlacementSizesTheBoxToTheWiderOfTheTabRowAndTheBody() {
            // The tab row is wider than the control body here, so the box tracks the tab row.
            var box = place(BODY).box();
            assertThat(box.width()).isCloseTo(TAB_ROW_WIDTH + 2f * BORDER_WIDTH, within(TOLERANCE));
        }

        @Test
        void computePlacementGrowsTheBodyDownwardWhenAViewAppendsAControl() {
            // A view contributing its own control appends one row beneath the shared body, the way
            // the selected political-map view's controls trail the selector.
            var basePlacement = place(BODY);
            var appended = new ArrayList<>(BODY);
            appended.add(new SidebarControlSpec(SidebarControlKind.CHECKBOX, List.of("Muted"), "",
                    SidebarControlSpec.NO_SELECTION));
            var grownPlacement = place(List.copyOf(appended));

            // The extra row makes the body taller by exactly one control row plus its leading gap.
            assertThat(grownPlacement.body().height())
                    .isCloseTo(basePlacement.body().height()
                            + SidebarLayout.CONTROL_ROW_HEIGHT + SidebarLayout.ROW_GAP,
                            within(TOLERANCE));
            // Growth is downward: the body's top edge, and so everything above it, does not move.
            assertThat(grownPlacement.body().y() + grownPlacement.body().height())
                    .as("the body top edge is fixed; the extra row extends the bottom")
                    .isCloseTo(basePlacement.body().y() + basePlacement.body().height(),
                            within(TOLERANCE));
            var baseFirstRow = basePlacement.bodyControls().get(0).bounds();
            var grownFirstRow = grownPlacement.bodyControls().get(0).bounds();
            assertThat(grownFirstRow.y() + grownFirstRow.height())
                    .as("the first control row is unshifted by an appended row below it")
                    .isCloseTo(baseFirstRow.y() + baseFirstRow.height(), within(TOLERANCE));
        }

        private void assertRectWithin(Rectangle inner, Rectangle outer) {
            assertThat(inner.x()).isGreaterThanOrEqualTo(outer.x() - TOLERANCE);
            assertThat(inner.x() + inner.width())
                    .isLessThanOrEqualTo(outer.x() + outer.width() + TOLERANCE);
            assertThat(inner.y()).isGreaterThanOrEqualTo(outer.y() - TOLERANCE);
            assertThat(inner.y() + inner.height())
                    .isLessThanOrEqualTo(outer.y() + outer.height() + TOLERANCE);
        }

        private SidebarPlacement place(List<SidebarControlSpec> bodyControls) {
            return SidebarLayout.computePlacement(SCREEN_HEIGHT, PADDING_TOP, PADDING_LEFT,
                    BORDER_WIDTH, TABS, bodyControls, measurerFake);
        }
    }

    @Nested
    class VerticalRadio {
        // A two-option view-selector radio, laid out on its own so the stacked geometry is checked
        // without the other controls' rows in the way. "Alliances" (9 chars) is the wider option.
        private static final List<SidebarControlSpec> VERTICAL_BODY = List.of(
                new SidebarControlSpec(SidebarControlKind.RADIO, List.of("Factions", "Alliances"),
                        "", SidebarControlSpec.NO_SELECTION, SidebarControlAction.NONE,
                        RadioAlignment.VERTICAL, true));

        private static final float EXPECTED_ROW_WIDTH =
                9 * WIDTH_PER_CHAR + SidebarLayout.RADIO_SEGMENT_PADDING;

        @Test
        void computePlacementStandsAVerticalRadioOneRowTallPerOption() {
            var radio = place().bodyControls().get(0);
            // One option-row of height per segment, so a two-option radio is twice a control row.
            assertThat(radio.bounds().height())
                    .isCloseTo(2 * SidebarLayout.CONTROL_ROW_HEIGHT, within(TOLERANCE));
            assertThat(radio.bounds().width()).isCloseTo(EXPECTED_ROW_WIDTH, within(TOLERANCE));
        }

        @Test
        void computePlacementStacksTheSegmentsTopToBottomInOneColumn() {
            var radio = place().bodyControls().get(0);
            assertThat(radio.segments()).hasSize(2);
            var top = radio.segments().get(0);
            var bottom = radio.segments().get(1);
            // Every segment shares the row's left edge and full width - a single column.
            assertThat(top.x()).isCloseTo(radio.bounds().x(), within(TOLERANCE));
            assertThat(bottom.x()).isCloseTo(radio.bounds().x(), within(TOLERANCE));
            assertThat(top.width()).isCloseTo(EXPECTED_ROW_WIDTH, within(TOLERANCE));
            assertThat(bottom.width()).isCloseTo(EXPECTED_ROW_WIDTH, within(TOLERANCE));
            // Each segment is one control-row tall, the first (Factions) on top of the second.
            assertThat(top.height()).isCloseTo(SidebarLayout.CONTROL_ROW_HEIGHT, within(TOLERANCE));
            assertThat(top.y())
                    .as("the top segment abuts the bottom segment's upper edge")
                    .isCloseTo(bottom.y() + bottom.height(), within(TOLERANCE));
        }

        @Test
        void computePlacementKeepsEverySegmentWithinTheBody() {
            var placement = place();
            var body = placement.body();
            for (var segment : placement.bodyControls().get(0).segments()) {
                assertThat(segment.x()).isGreaterThanOrEqualTo(body.x() - TOLERANCE);
                assertThat(segment.x() + segment.width())
                        .isLessThanOrEqualTo(body.x() + body.width() + TOLERANCE);
                assertThat(segment.y()).isGreaterThanOrEqualTo(body.y() - TOLERANCE);
                assertThat(segment.y() + segment.height())
                        .isLessThanOrEqualTo(body.y() + body.height() + TOLERANCE);
            }
        }

        private SidebarPlacement place() {
            return SidebarLayout.computePlacement(SCREEN_HEIGHT, PADDING_TOP, PADDING_LEFT,
                    BORDER_WIDTH, TABS, VERTICAL_BODY, measurerFake);
        }
    }

    @Nested
    class LabelRow {
        // A caption row on its own, so the label's plain-text geometry is checked without other
        // controls' rows in the way. The caption heads the alliances view's two checkboxes.
        private static final String CAPTION = "Non-allied factions are";
        private static final List<SidebarControlSpec> LABEL_BODY =
                List.of(SidebarControlSpec.createLabel(CAPTION));

        @Test
        void computePlacementSnapsALabelRowToItsMeasuredText() {
            var label = place().bodyControls().get(0);
            // A caption has no widget chrome, so its row is exactly its text width and one row tall.
            assertThat(label.bounds().width())
                    .isCloseTo(CAPTION.length() * WIDTH_PER_CHAR, within(TOLERANCE));
            assertThat(label.bounds().height())
                    .isCloseTo(SidebarLayout.CONTROL_ROW_HEIGHT, within(TOLERANCE));
        }

        @Test
        void computePlacementLeavesALabelRowWithoutSegments() {
            assertThat(place().bodyControls().get(0).segments())
                    .as("a caption is never clicked, so it splits into no hit segments").isEmpty();
        }

        private SidebarPlacement place() {
            return SidebarLayout.computePlacement(SCREEN_HEIGHT, PADDING_TOP, PADDING_LEFT,
                    BORDER_WIDTH, TABS, LABEL_BODY, measurerFake);
        }
    }
}
