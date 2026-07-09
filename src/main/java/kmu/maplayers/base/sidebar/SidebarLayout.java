package kmu.maplayers.base.sidebar;

import kmlib.math.geometry.Rectangle;
import kmlib.starsector.ui.font.LineWidthMeasurer;
import kmlib.starsector.ui.layout.RowStack;
import kmlib.starsector.ui.widgets.RadioAlignment;
import kmlib.starsector.ui.widgets.RadioRow;
import kmlib.starsector.ui.widgets.TabPanel;
import kmlib.starsector.ui.widgets.TabPanelBodySize;
import kmlib.starsector.ui.widgets.VanillaTabContent;
import kmlib.text.KmlibStrings;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns the active tab's controls into the sidebar's laid-out rectangles. The frame, tab row, and
 * body-region geometry is the reusable KMLib {@link TabPanel} (an outer border wrapping a
 * vanilla-styled tab strip over a framed body); this class supplies only what is KMU - which
 * controls the body carries and how each snaps to its label - so the political map is just one tab's
 * body and another tab plugs in without touching the frame math.
 *
 * <p>It measures how large the body must be, hands that size to the panel, then lays each control
 * into the body rectangle the panel frames. Computing it once as a pure function of its inputs lets
 * the renderer and the input listener share one placement, so what is drawn is exactly what the
 * player clicks. UI coordinates throughout (origin bottom-left, y grows up); text snapping runs
 * through the injected {@link LineWidthMeasurer}, so the layout depends on a width measurement
 * rather than a concrete font and stays a pure computation.
 */
public final class SidebarLayout {
    // The tab row's height, shared by every tab. The font sizes below are measured (here) and drawn
    // (by the renderer) at one value each, so a snapped tab width matches the text painted into it;
    // they are public so the renderer draws at the same size this measured, keeping one source.
    static final float TAB_HEIGHT = 24f;
    public static final double TAB_FONT_SIZE = 15d;
    public static final double BODY_FONT_SIZE = 13d;

    // Slack added to each measured tab label so text does not touch the tab edges, and a floor so a
    // very short label still gives a clickable box.
    static final float TAB_TEXT_PADDING = 16f;
    static final float MIN_TAB_WIDTH = 48f;

    // Body geometry: one fixed-height row per control, a gap between rows, and an inset framing the
    // controls off the body edge so they clear the outer border.
    static final float CONTROL_ROW_HEIGHT = 20f;
    static final float ROW_GAP = 4f;
    static final float BODY_PADDING = 8f;

    // Per-control slack: the gap between a checkbox's box and its label, the padding sizing each
    // radio segment past its option label, the gap before a control's trailing label, and the
    // padding sizing a toggle button past its label. The two label gaps are public so the renderer
    // places each label at the same offset this reserved for it, keeping one source of the spacing.
    public static final float CHECKBOX_LABEL_GAP = 6f;
    static final float RADIO_SEGMENT_PADDING = 12f;
    public static final float TRAILING_LABEL_GAP = 6f;
    static final float TOGGLE_TEXT_PADDING = 16f;

    private SidebarLayout() {
    }

    /**
     * Lays the sidebar out for the current screen height, padding, border, tabs, and body controls.
     * The panel frames the tab row and (when the active tab carries controls) a body sized to hold
     * them; this then places each control inside that framed body. An empty {@code bodyControls}
     * leaves the tab row with no body.
     *
     * @param screenHeight  the UI-coordinate screen height, giving the top edge to hang from
     * @param paddingTop    pixels from the screen top to the box's top edge
     * @param paddingLeft   pixels from the screen left to the box's left edge
     * @param borderWidth   the outer border thickness framing the footprint; 0 leaves no inset
     * @param tabContents   the tabs' labels and shortcuts, in registry order left to right
     * @param bodyControls  the active tab's body controls, top to bottom (empty for no body)
     * @param measurer      measures each label's rendered width for text snapping
     * @return the box, body, tabs, and laid-out body controls, all in UI coordinates
     */
    public static SidebarPlacement computePlacement(float screenHeight, int paddingTop,
            int paddingLeft, int borderWidth, List<VanillaTabContent> tabContents,
            List<SidebarControlSpec> bodyControls, LineWidthMeasurer measurer) {
        // Measure the body first so the panel can size the box around both the tab row and the body;
        // the measured row dimensions are reused to place each control once the body is framed.
        var body = measureBody(bodyControls, measurer);
        var placement = TabPanel.layout(screenHeight, paddingTop, paddingLeft, borderWidth,
                TAB_HEIGHT, TAB_TEXT_PADDING, MIN_TAB_WIDTH, TAB_FONT_SIZE, tabContents, body.size(),
                measurer);
        var controls = layoutControls(placement.body(), bodyControls, body.rowHeights(),
                body.rowWidths());
        return new SidebarPlacement(placement, controls);
    }

    // Measures each control row's width and height and the body footprint that holds them: the body
    // is as wide as the widest row (a trailing label counts, so the backdrop covers it) plus the
    // inset, and as tall as the stacked rows plus their gaps and the inset. Empty controls give the
    // absent body size, so a bodyless tab reserves nothing. A vertical radio stands one option-row
    // taller per segment, so the row heights vary and the body sums them rather than assuming one
    // height per control.
    private static BodyMeasurement measureBody(List<SidebarControlSpec> specs,
            LineWidthMeasurer measurer) {
        if (specs.isEmpty()) {
            return new BodyMeasurement(TabPanelBodySize.NONE, List.of(), List.of());
        }
        var rowWidths = new ArrayList<Float>(specs.size());
        var rowHeights = new ArrayList<Float>(specs.size());
        var contentWidth = 0f;
        var stackedHeight = 0f;
        for (var spec : specs) {
            var rowWidth = measureRowWidth(spec, measurer);
            rowWidths.add(rowWidth);
            var rowHeight = measureRowHeight(spec);
            rowHeights.add(rowHeight);
            stackedHeight += rowHeight;
            contentWidth = Math.max(contentWidth, rowWidth + measureTrailingWidth(spec, measurer));
        }
        var bodyWidth = contentWidth + 2f * BODY_PADDING;
        var bodyHeight = 2f * BODY_PADDING + stackedHeight + (specs.size() - 1) * ROW_GAP;
        return new BodyMeasurement(new TabPanelBodySize(bodyWidth, bodyHeight),
                List.copyOf(rowWidths), List.copyOf(rowHeights));
    }

    // Stacks each control row inside the framed body (via the shared row-stacker, starting from the
    // body's top-left plus the inset), snapping each row to its measured width and height and
    // splitting a radio row into its option segments so the hit rects are the drawn ones. The body
    // rectangle the panel framed carries the same top-left the measurement assumed, so the controls
    // land exactly under the tabs.
    private static List<SidebarControl> layoutControls(Rectangle body,
            List<SidebarControlSpec> specs, List<Float> rowHeights, List<Float> rowWidths) {
        if (specs.isEmpty()) {
            return List.of();
        }
        var bodyTopY = body.y() + body.height();
        var rows = RowStack.layoutRows(body.x() + BODY_PADDING, bodyTopY - BODY_PADDING,
                ROW_GAP, rowHeights, rowWidths);
        var controls = new ArrayList<SidebarControl>(specs.size());
        for (var index = 0; index < specs.size(); index++) {
            var spec = specs.get(index);
            var row = rows.get(index);
            var segments = spec.kind() == SidebarControlKind.RADIO
                    ? RadioRow.splitIntoSegments(row, spec.labels().size(), spec.alignment())
                    : List.<Rectangle>of();
            controls.add(new SidebarControl(spec, row, segments));
        }
        return List.copyOf(controls);
    }

    // The width of a control's clickable row, snapped to its label(s): a checkbox is its tick box
    // plus a gap plus its label; a horizontal radio is its equal segments side by side, a vertical
    // radio is one segment column wide; a toggle is its label plus padding.
    private static float measureRowWidth(SidebarControlSpec spec, LineWidthMeasurer measurer) {
        return switch (spec.kind()) {
            case CHECKBOX -> CONTROL_ROW_HEIGHT + CHECKBOX_LABEL_GAP
                    + measureWidth(measurer, spec.labels().get(0));
            case RADIO -> spec.alignment() == RadioAlignment.VERTICAL
                    ? measureRadioSegmentWidth(spec, measurer)
                    : spec.labels().size() * measureRadioSegmentWidth(spec, measurer);
            case TOGGLE -> measureWidth(measurer, spec.labels().get(0)) + TOGGLE_TEXT_PADDING;
        };
    }

    // The height of a control's row: one control-row tall for every control except a vertical radio,
    // which stacks its options and so stands one control-row tall per segment.
    private static float measureRowHeight(SidebarControlSpec spec) {
        if (spec.kind() == SidebarControlKind.RADIO
                && spec.alignment() == RadioAlignment.VERTICAL) {
            return spec.labels().size() * CONTROL_ROW_HEIGHT;
        }
        return CONTROL_ROW_HEIGHT;
    }

    // Segments are equal width, so all fit when each is sized to the widest option label plus
    // padding.
    private static float measureRadioSegmentWidth(SidebarControlSpec spec,
            LineWidthMeasurer measurer) {
        var widest = 0f;
        for (var label : spec.labels()) {
            widest = Math.max(widest, measureWidth(measurer, label));
        }
        return widest + RADIO_SEGMENT_PADDING;
    }

    // Extra footprint a trailing label adds past the control's own row, or none when it is blank.
    private static float measureTrailingWidth(SidebarControlSpec spec, LineWidthMeasurer measurer) {
        if (!KmlibStrings.hasText(spec.trailingLabel())) {
            return 0f;
        }
        return TRAILING_LABEL_GAP + measureWidth(measurer, spec.trailingLabel());
    }

    private static float measureWidth(LineWidthMeasurer measurer, String text) {
        return (float) measurer.measureLineWidth(text, BODY_FONT_SIZE);
    }

    // The body's measured footprint and the per-row dimensions behind it, returned together so the
    // size feeds the panel and the same row dimensions place the controls without measuring twice.
    private record BodyMeasurement(TabPanelBodySize size, List<Float> rowWidths,
            List<Float> rowHeights) {
    }
}
