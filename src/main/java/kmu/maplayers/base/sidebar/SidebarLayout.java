package kmu.maplayers.base.sidebar;

import kmlib.math.geometry.Rectangle;
import kmlib.starsector.ui.font.LineWidthMeasurer;
import kmlib.starsector.ui.layout.RowStack;
import kmlib.starsector.ui.widgets.RadioRow;
import kmlib.starsector.ui.widgets.VanillaTab;
import kmlib.starsector.ui.widgets.VanillaTabContent;
import kmlib.starsector.ui.widgets.VanillaTabStrip;
import kmlib.text.KmlibStrings;

import java.util.ArrayList;
import java.util.List;

/**
 * Lays out the sidebar: a text-snapped tab row inside an outer border, placed by an explicit padding
 * from the screen's top-left, over a control body the active tab fills with whatever controls it
 * carries. The sector map exposes no panel seam, so the sidebar is drawn in raw GL and hit-tested by
 * hand; this computes every rectangle once as a pure function of its inputs so the renderer and the
 * input listener share one placement and draw exactly what the player clicks.
 *
 * <p>The body is generic: it stacks whatever {@link SidebarControlSpec}s the caller hands it (empty
 * for a tab with no body), so the political map is just one tab's controls and another tab plugs in
 * without changing this. UI coordinates throughout (origin bottom-left, y grows up): the box's top
 * edge sits {@code paddingTop} below the screen top and its left edge {@code paddingLeft} in from
 * the left, the border frames the whole footprint, the tab row caps the content, and the body hangs
 * beneath. Text snapping runs through the injected {@link LineWidthMeasurer}, so the layout depends
 * on a width measurement rather than a concrete font and stays a pure computation.
 */
public final class SidebarLayout {
    // The tab row's height, shared by every tab. The font sizes below are measured (here) and drawn
    // (by the renderer) at one value each, so a snapped tab width matches the text painted into it.
    static final float TAB_HEIGHT = 24f;
    static final double TAB_FONT_SIZE = 15d;
    static final double BODY_FONT_SIZE = 13d;

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
    // padding sizing a toggle button past its label.
    static final float CHECKBOX_LABEL_GAP = 6f;
    static final float RADIO_SEGMENT_PADDING = 12f;
    static final float TRAILING_LABEL_GAP = 6f;
    static final float TOGGLE_TEXT_PADDING = 16f;

    private SidebarLayout() {
    }

    /**
     * Lays the sidebar out for the current screen height, padding, border, tabs, and body controls.
     * The box is pinned by its top-left ({@code paddingLeft} in, {@code paddingTop} down); screen
     * width plays no part, since the box grows rightward and downward from that corner rather than
     * anchoring to the far edges. An empty {@code bodyControls} leaves the tab row with no body.
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
        var boxTopY = screenHeight - paddingTop;
        // Content is inset by the border on every edge, so the tab row and body clear the stroke.
        var contentX = paddingLeft + (float) borderWidth;
        var contentTopY = boxTopY - borderWidth;

        var tabs = VanillaTabStrip.layoutTabs(contentX, contentTopY, TAB_HEIGHT, TAB_TEXT_PADDING,
                MIN_TAB_WIDTH, TAB_FONT_SIZE, tabContents, measurer);
        var tabRowWidth = measureTabRowWidth(tabs, contentX);
        var tabRowBottomY = contentTopY - TAB_HEIGHT;

        var bodyLayout = bodyControls.isEmpty()
                ? new BodyLayout(new Rectangle(contentX, tabRowBottomY, 0f, 0f), List.of())
                : computeBodyLayout(contentX, tabRowBottomY, bodyControls, measurer);

        // The box is as wide as the wider of the tab row and the body, and as tall as the tab row
        // plus the body, all wrapped by the border.
        var contentWidth = Math.max(tabRowWidth, bodyLayout.body().width());
        var contentHeight = TAB_HEIGHT + bodyLayout.body().height();
        var boxWidth = contentWidth + 2f * borderWidth;
        var boxHeight = contentHeight + 2f * borderWidth;
        var box = new Rectangle(paddingLeft, boxTopY - boxHeight, boxWidth, boxHeight);
        return new SidebarPlacement(box, bodyLayout.body(), tabs, bodyLayout.controls());
    }

    // The tab row spans from the content's left edge to the right edge of the last tab; an empty row
    // is zero wide.
    private static float measureTabRowWidth(List<VanillaTab> tabs, float contentX) {
        if (tabs.isEmpty()) {
            return 0f;
        }
        var last = tabs.get(tabs.size() - 1).bounds();
        return last.x() + last.width() - contentX;
    }

    // Stacks the control rows inside the body inset (via the shared row-stacker), snapping each row
    // to its control's width and splitting a radio row into its option segments, then sizes the body
    // to enclose the widest row (a trailing label counts, so the backdrop covers it) plus the inset.
    private static BodyLayout computeBodyLayout(float contentX, float bodyTopY,
            List<SidebarControlSpec> specs, LineWidthMeasurer measurer) {
        var rowWidths = new ArrayList<Float>(specs.size());
        var contentWidth = 0f;
        for (var spec : specs) {
            var rowWidth = measureRowWidth(spec, measurer);
            rowWidths.add(rowWidth);
            contentWidth = Math.max(contentWidth, rowWidth + measureTrailingWidth(spec, measurer));
        }
        var rows = RowStack.layoutRows(contentX + BODY_PADDING, bodyTopY - BODY_PADDING,
                CONTROL_ROW_HEIGHT, ROW_GAP, rowWidths);

        var controls = new ArrayList<SidebarControl>(specs.size());
        for (var index = 0; index < specs.size(); index++) {
            var spec = specs.get(index);
            var row = rows.get(index);
            var segments = spec.kind() == SidebarControlKind.RADIO
                    ? RadioRow.splitIntoSegments(row, spec.labels().size())
                    : List.<Rectangle>of();
            controls.add(new SidebarControl(spec, row, segments));
        }

        var rowCount = specs.size();
        var bodyWidth = contentWidth + 2f * BODY_PADDING;
        var bodyHeight = 2f * BODY_PADDING + rowCount * CONTROL_ROW_HEIGHT
                + (rowCount - 1) * ROW_GAP;
        var body = new Rectangle(contentX, bodyTopY - bodyHeight, bodyWidth, bodyHeight);
        return new BodyLayout(body, List.copyOf(controls));
    }

    // The width of a control's clickable row, snapped to its label(s): a checkbox is its tick box
    // plus a gap plus its label; a radio is its equal segments; a toggle is its label plus padding.
    private static float measureRowWidth(SidebarControlSpec spec, LineWidthMeasurer measurer) {
        return switch (spec.kind()) {
            case CHECKBOX -> CONTROL_ROW_HEIGHT + CHECKBOX_LABEL_GAP
                    + measureWidth(measurer, spec.labels().get(0));
            case RADIO -> spec.labels().size() * measureRadioSegmentWidth(spec, measurer);
            case TOGGLE -> measureWidth(measurer, spec.labels().get(0)) + TOGGLE_TEXT_PADDING;
        };
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

    // The body's own footprint and the controls within it, returned together so the caller can size
    // the box to the body and hand the controls straight to the placement.
    private record BodyLayout(Rectangle body, List<SidebarControl> controls) {
    }
}
