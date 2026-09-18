package kmu.maplayers.base.chrome.arrange;

import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;

import kmlib.starsector.ui.colour.StarsectorUiColour;
import kmlib.starsector.ui.highlight.Highlight;
import kmlib.starsector.ui.highlight.HighlightedParagraph;
import kmlib.starsector.ui.layout.VanillaPositions;
import kmlib.starsector.ui.render.gl.UiElementPaint;
import kmlib.starsector.ui.render.gl.UiFill;
import kmlib.starsector.ui.screen.VanillaScreen;

import kmu.util.KmuStringKeys;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * The box the arranging dialog is made of: a title, a column of rows, the way out, and the rule
 * around them - with the dim over the screen and the box's own surface painted beneath, since the
 * game publishes a rectangle component that strokes and none that fills.
 *
 * <p>Apart from the dialog because the two change for different reasons. What the dialog is - when
 * it stands up, what it claims, when it comes down - is a question about a screen; what stands in
 * the box is a question about a layout. Together they were one class carrying a lifecycle and a
 * dozen measurements, and neither half could be read without the other.
 *
 * <p>What a row is made of is {@link ArrangementRowWidgets}'s and where everything sits is
 * {@link ArrangementBoxLayout}'s, so this is left with the furniture around the column and the two
 * areas nothing else paints.
 *
 * <p>Built whole in the constructor rather than assembled by a caller, so a body that exists is a
 * body that is on screen. A change to the arrangement makes a new one and takes the old one off;
 * nothing here is edited in place, because the rows move and a set of widgets each nudged into a new
 * position would eventually disagree with the order they were drawn from.
 *
 * <p>The surface every element stands on is one painted rectangle rather than a fill per element,
 * which would leave the gaps between them showing the map through.
 */
final class MapLayerArrangementDialogBody {

    // How dark the backdrop stands the screen down to.
    private static final float BACKDROP_ALPHA = 0.7f;

    // The box's own fill over that backdrop - opaque enough to read a column of text against.
    private static final float BOX_ALPHA = 0.95f;

    // How thick the box's frame is stroked, in UI units. One pixel, matching the rule the game draws
    // around its own panels.
    private static final float FRAME_THICKNESS = 1f;

    // The child this added to the dialog's panel, held so it can be taken off again. The panel publishes
    // no way to ask what it is holding, so what was added has to be remembered.
    private final UIComponentAPI boxPanel;

    private final PositionAPI boxPlacement;

    /**
     * Builds the body and stands it in {@code dialogPanel}.
     *
     * @param dialogPanel   the screen-sized panel the dialog stands in
     * @param editor        the rows to draw and what each of them may do
     * @param onRowAction   where a press on a row's controls goes, as the row's layer ID and what was
     *                      pressed
     * @param onClosePressed what a press on the way out does
     */
    MapLayerArrangementDialogBody(
            CustomPanelAPI dialogPanel,
            MapLayerArrangementEditor editor,
            BiConsumer<String, ArrangementRowAction> onRowAction,
            Runnable onClosePressed) {

        var rows = editor.getRows();
        var boxHeight = ArrangementBoxLayout.resolveBoxHeight(rows.size());
        var box = dialogPanel.createCustomPanel(ArrangementBoxLayout.BOX_WIDTH, boxHeight, null);

        // In the order they are drawn, children being drawn in the order they were added - which is why
        // the rule around the box is added last and rules over the widgets rather than under them.
        addHeader(box);
        addRows(box, rows, new ArrangementRowWidgets(editor, onRowAction));
        addFooter(box, rows.size(), onClosePressed);
        addFrame(box, boxHeight);

        this.boxPlacement = dialogPanel.addComponent(box).inMid();
        this.boxPanel = box;
    }

    /**
     * Takes this body back off the panel it was built into.
     *
     * @param dialogPanel the panel it was built into
     */
    void removeFromPanel(CustomPanelAPI dialogPanel) {

        dialogPanel.removeComponent(boxPanel);
    }

    /**
     * Paints what no widget paints: the dim over the whole screen, and the box's own fill under its
     * contents. The frame around that fill is a widget like everything else - only the two filled areas
     * are drawn, the game publishing a rectangle that strokes but none that fills.
     *
     * <p>Called from the dialog panel's own {@code renderBelow} hook, which is where the game draws the
     * interiors of its own custom panels. So this runs in the panel's coordinates, under every widget the
     * panel holds, and nothing of it reaches the map's render pass.
     *
     * @param alphaMult how far through its own fade the panel stands, which both fills honour so the
     *                  dialog arrives and leaves as one piece
     */
    void renderFills(float alphaMult) {

        UiFill.renderQuad(
            VanillaScreen.resolveScreenBox(),
            new UiElementPaint(
                StarsectorUiColour.BLACK.resolve(),
                BACKDROP_ALPHA * alphaMult));

        // Read off the placement the layout settled rather than the numbers it was laid out from, exactly
        // as the input claim is, so a resized window moves the fill with the box.
        UiFill.renderQuad(
            VanillaPositions.toRectangle(boxPlacement),
            new UiElementPaint(
                StarsectorUiColour.BLACK.resolve(),
                BOX_ALPHA * alphaMult));
    }

    /**
     * Writes the box's head into {@code header}: what the box is called, and the line saying what the
     * controls under it do.
     *
     * @param header the element the head is drawn in, sized for both
     */
    static void fillHeader(TooltipMakerAPI header) {

        // The face the game heads its own boxes with. An element's default title face is the size of
        // the body text under it, which leaves the two reading as one paragraph rather than as a head
        // and the line it heads.
        header.setTitleOrbitronLarge();
        header.addTitle(KmuStringKeys.get(KmuStringKeys.MAP_LAYER_ARRANGE_TITLE));

        composeHint().addTo(header, ArrangementBoxLayout.TITLE_GAP);
    }

    /**
     * Writes the way out into {@code footer}.
     *
     * <p>It says <b>Apply</b> rather than the shared close wording, because nothing in the box is held
     * back to be committed: every press is recorded as it is made, so the word names what the player is
     * leaving with. Under a key of its own rather than a reworded shared one, that one also closing the
     * condition picker, where there is nothing to apply.
     *
     * @param footer the element the button is drawn in
     */
    static void fillFooter(TooltipMakerAPI footer) {

        footer.addButton(
            KmuStringKeys.get(KmuStringKeys.MAP_LAYER_ARRANGE_APPLY),
            KmuStringKeys.MAP_LAYER_ARRANGE_APPLY,
            ArrangementBoxLayout.APPLY_BUTTON_WIDTH,
            ArrangementBoxLayout.CONTROL_HEIGHT,
            ArrangementBoxLayout.NO_PAD);
    }

    // The line under the heading, and the three words in it that name something on screen: the two
    // buttons a row carries and the box beside them, in the shade the game highlights with. A hint is
    // read once and skimmed thereafter, and what a skim should land on is the words naming a control.
    //
    // Each run is the very string its control is labelled from, so a reworded button carries its
    // highlight with it - a run that merely matched the sentence would fall silent on the rewording,
    // the substrate simply not drawing a run it cannot find.
    private static HighlightedParagraph composeHint() {

        var highlight = StarsectorUiColour.VANILLA_HIGHLIGHT_GOLD.resolve();

        return new HighlightedParagraph(
            KmuStringKeys.get(KmuStringKeys.MAP_LAYER_ARRANGE_HINT),
            Highlight.of(KmuStringKeys.get(KmuStringKeys.MAP_LAYER_ARRANGE_MOVE_UP), highlight),
            Highlight.of(KmuStringKeys.get(KmuStringKeys.MAP_LAYER_ARRANGE_MOVE_DOWN), highlight),
            Highlight.of(KmuStringKeys.get(KmuStringKeys.MAP_LAYER_ARRANGE_HINT_UNCHECK), highlight));
    }

    // The rule around the box, as the game's own rectangle component rather than as anything drawn: a
    // stroked rect of the given thickness on all four edges. It is added last so it rules over the other
    // widgets rather than under them, children being drawn in the order they were added.
    private static void addFrame(CustomPanelAPI box, float boxHeight) {

        var frameElement = box.createUIElement(ArrangementBoxLayout.BOX_WIDTH, boxHeight, false);
        var frame = frameElement.createRect(
            StarsectorUiColour.VANILLA_PLAYER_BASE.resolve(),
            FRAME_THICKNESS);

        frameElement.addCustomDoNotSetPosition(frame)
            .getPosition()
            .inTL(ArrangementBoxLayout.NO_PAD, ArrangementBoxLayout.NO_PAD)
            .setSize(ArrangementBoxLayout.BOX_WIDTH, boxHeight);

        box.addUIElement(frameElement)
            .inTL(ArrangementBoxLayout.NO_PAD, ArrangementBoxLayout.NO_PAD);
    }

    // The way out, in the box's bottom right corner where the game puts its own dialogs' buttons. The
    // element is as wide as the one button it holds, that button being the whole of the foot.
    private static void addFooter(CustomPanelAPI box, int rowCount, Runnable onClosePressed) {

        var footer = box.createUIElement(
            ArrangementBoxLayout.resolveElementWidth(ArrangementBoxLayout.APPLY_BUTTON_WIDTH),
            ArrangementBoxLayout.FOOTER_HEIGHT,
            false);

        footer.setActionListenerDelegate((buttonId, data) -> onClosePressed.run());
        fillFooter(footer);

        box.addUIElement(footer)
            .inTL(
                ArrangementBoxLayout.resolveFooterLeft(),
                ArrangementBoxLayout.resolveFooterTop(rowCount));
    }

    // The head's own element, standing so its title reads from the same left edge a row's name does.
    private static void addHeader(CustomPanelAPI box) {

        var header = box.createUIElement(
            ArrangementBoxLayout.resolveHeaderWidth(),
            ArrangementBoxLayout.resolveHeaderHeight(),
            false);

        fillHeader(header);

        box.addUIElement(header)
            .inTL(ArrangementBoxLayout.resolveLeadingElementLeft(), ArrangementBoxLayout.BOX_PAD);
    }

    // The column, one row per layer, each told where its own top stands. The rows are what the box is
    // sized around, so nothing here decides how far down they reach.
    private static void addRows(
            CustomPanelAPI box,
            List<MapLayerArrangementRow> rows,
            ArrangementRowWidgets rowWidgets) {

        for (var rowIndex = 0; rowIndex < rows.size(); rowIndex++) {

            rowWidgets.addRowTo(
                box,
                rows.get(rowIndex),
                ArrangementBoxLayout.resolveRowTop(rowIndex));
        }
    }
}
