package kmu.maplayers.base.chrome.arrange;

import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;

import kmlib.math.geometry.Rectangle;
import kmlib.starsector.ui.buttons.VanillaActionIds;
import kmlib.starsector.ui.colour.StarsectorUiColour;
import kmlib.starsector.ui.render.gl.UiElementPaint;
import kmlib.starsector.ui.render.gl.UiFill;
import kmlib.starsector.ui.screen.VanillaScreen;

import kmu.util.KmuStrings;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * The box the arranging dialog is made of: a title, one row per layer, the way out, and the rule around
 * them - with the dim over the screen and the box's own surface painted beneath, since the game publishes
 * a rectangle component that strokes and none that fills.
 *
 * <p>Apart from the dialog because the two change for different reasons. What the dialog is - when it
 * stands up, what it claims, when it comes down - is a question about a screen; how wide the label
 * column is and which cell sits beside which is a question about a layout. Together they were one class
 * carrying a dozen measurements and a lifecycle, and neither half could be read without the other.
 *
 * <p>Built whole in the constructor rather than assembled by a caller, so a body that exists is a body
 * that is on screen. A change to the arrangement makes a new one and takes the old one off; nothing
 * here is edited in place, because the rows move and a set of widgets each nudged into a new position
 * would eventually disagree with the order they were drawn from.
 *
 * <p>Every cell is a vanilla element placed by hand rather than one element told to run across, because
 * a vanilla element lays its contents out top to bottom. A row is therefore cells side by side, and the
 * surface they all stand on is one painted rectangle rather than a fill per cell, which would leave the
 * gaps between cells showing the map through.
 */
final class MapLayerArrangementDialogBody {

    // How dark the backdrop stands the screen down to.
    private static final float BACKDROP_ALPHA = 0.7f;

    // The box's own fill over that backdrop - opaque enough to read a column of text against.
    private static final float BOX_ALPHA = 0.95f;

    // How thick the box's frame is stroked, in UI units. One pixel, matching the rule the game draws
    // around its own panels.
    private static final float FRAME_THICKNESS = 1f;

    // A row's parts, left to right, in UI units.
    private static final float LABEL_WIDTH = 200f;
    private static final float SHOWN_BOX_WIDTH = 80f;

    // Wide enough for the longer of the two words rather than for a glyph, both buttons taking the one
    // width so the pair reads as a pair rather than as two controls that happen to sit together.
    private static final float MOVE_BUTTON_WIDTH = 54f;
    private static final float CONTROL_HEIGHT = 20f;
    private static final float CONTROL_GAP = 8f;
    private static final float MOVE_BUTTON_GAP = 4f;

    private static final float ROW_HEIGHT = 28f;
    private static final float BOX_PAD = 12f;
    private static final float TITLE_HEIGHT = 28f;
    private static final float HINT_HEIGHT = 40f;
    private static final float FOOTER_HEIGHT = 34f;
    private static final float CLOSE_BUTTON_WIDTH = 90f;

    // A vanilla element's own text padding, which is what a label added to one is inset by.
    private static final float NO_PAD = 0f;

    // What a row's controls occupy beside its label. Named rather than subtracted back out of the box
    // width where the cell is built: the box is as wide as its parts, so the parts are what is stated
    // and the width is what follows - two expressions for the one measurement would have to be kept
    // agreeing by hand.
    private static final float CONTROLS_WIDTH =
        SHOWN_BOX_WIDTH + CONTROL_GAP + MOVE_BUTTON_WIDTH + MOVE_BUTTON_GAP + MOVE_BUTTON_WIDTH;

    private static final float BOX_WIDTH =
        BOX_PAD * 2f + LABEL_WIDTH + CONTROL_GAP + CONTROLS_WIDTH;

    // What each row may do, and the labels its tab reads. Held for the length of the build alone - a
    // change makes a new body rather than re-reading through this one.
    private final MapLayerArrangementEditor editor;

    // Where a press on a row's controls goes, by layer id rather than by position: the position has
    // moved by the time a second press arrives.
    private final BiConsumer<String, ArrangementRowAction> onRowAction;

    private final Runnable onClosePressed;

    // The child this added to the dialog's panel, held so it can be taken off again. The panel publishes
    // no way to ask what it is holding, so what was added has to be remembered.
    private final UIComponentAPI boxPanel;

    private final PositionAPI boxPlacement;

    /**
     * Builds the body and stands it in {@code dialogPanel}.
     *
     * @param dialogPanel   the screen-sized panel the dialog stands in
     * @param editor        the rows to draw and what each of them may do
     * @param onRowAction   where a press on a row's controls goes, as the row's layer id and what was
     *                      pressed
     * @param onClosePressed what a press on the way out does
     */
    MapLayerArrangementDialogBody(
            CustomPanelAPI dialogPanel,
            MapLayerArrangementEditor editor,
            BiConsumer<String, ArrangementRowAction> onRowAction,
            Runnable onClosePressed) {

        this.editor = editor;
        this.onRowAction = onRowAction;
        this.onClosePressed = onClosePressed;

        var rows = editor.getRows();
        var boxHeight = resolveBoxHeight(rows.size());
        var box = dialogPanel.createCustomPanel(BOX_WIDTH, boxHeight, null);
        fillBox(box, rows, boxHeight);

        this.boxPlacement = dialogPanel.addComponent(box).inMid();
        this.boxPanel = box;
    }

    /**
     * @return where the layout put the box, for a caller hit-testing against what is on screen rather
     *         than against the numbers it was laid out from - so a resized window moves both together
     */
    PositionAPI getBoxPlacement() {
        return boxPlacement;
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
            new Rectangle(
                boxPlacement.getX(),
                boxPlacement.getY(),
                boxPlacement.getWidth(),
                boxPlacement.getHeight()),
            new UiElementPaint(
                StarsectorUiColour.BLACK.resolve(),
                BOX_ALPHA * alphaMult));
    }

    // How tall the box stands for this many rows: its fixed furniture plus the column.
    private static float resolveBoxHeight(int rowCount) {

        return BOX_PAD * 2f + TITLE_HEIGHT + HINT_HEIGHT + FOOTER_HEIGHT + rowCount * ROW_HEIGHT;
    }

    // The rule around the box, as the game's own rectangle component rather than as anything drawn: a
    // stroked rect of the given thickness on all four edges. It is added last so it rules over the cells
    // rather than under them, children being drawn in the order they were added.
    private static void addFrame(CustomPanelAPI box, float boxHeight) {

        var frameCell = box.createUIElement(BOX_WIDTH, boxHeight, false);
        var frame = frameCell.createRect(
            StarsectorUiColour.VANILLA_PLAYER_BASE.resolve(),
            FRAME_THICKNESS);

        frameCell.addCustomDoNotSetPosition(frame)
            .getPosition()
            .inTL(NO_PAD, NO_PAD)
            .setSize(BOX_WIDTH, boxHeight);

        box.addUIElement(frameCell).inTL(NO_PAD, NO_PAD);
    }

    // One of the pair that moves a row, at the row's control size. Named apart because the two differ
    // only in their word and their action, and a second copy of the five-argument call would be a
    // second place for the size to drift.
    private static ButtonAPI addMoveButton(
            TooltipMakerAPI controlsCell,
            String labelKey,
            ArrangementRowAction action) {

        return controlsCell.addButton(
            KmuStrings.get(labelKey),
            action,
            MOVE_BUTTON_WIDTH,
            CONTROL_HEIGHT,
            NO_PAD);
    }

    // The box's contents: its own fill, a title, a line saying what the controls do, one row per layer,
    // and the way out.
    private void fillBox(CustomPanelAPI box, List<MapLayerArrangementRow> rows, float boxHeight) {

        // The box's own fill is not a widget: the game publishes a rectangle that strokes and none that
        // fills, so the surface these cells stand on is painted in renderFills and only the rule around
        // it is composed here.
        var header = box.createUIElement(BOX_WIDTH - BOX_PAD * 2f, TITLE_HEIGHT + HINT_HEIGHT, false);
        header.addTitle(KmuStrings.get(KmuStrings.MAP_LAYER_ARRANGE_TITLE));
        header.addPara(KmuStrings.get(KmuStrings.MAP_LAYER_ARRANGE_HINT), NO_PAD);
        box.addUIElement(header).inTL(BOX_PAD, BOX_PAD);

        for (var rowIndex = 0; rowIndex < rows.size(); rowIndex++) {

            addRow(
                box,
                rows.get(rowIndex),
                BOX_PAD + TITLE_HEIGHT + HINT_HEIGHT + rowIndex * ROW_HEIGHT);
        }
        addCloseButton(box, boxHeight);
        addFrame(box, boxHeight);
    }

    // One layer's row: what its tab says, whether that tab is on the bar, and the two move buttons.
    private void addRow(CustomPanelAPI box, MapLayerArrangementRow row, float rowTop) {

        var labelCell = box.createUIElement(LABEL_WIDTH, ROW_HEIGHT, false);
        labelCell.addPara(row.layerLabel(), NO_PAD);
        box.addUIElement(labelCell).inTL(BOX_PAD, rowTop);

        var controlsCell = box.createUIElement(CONTROLS_WIDTH, ROW_HEIGHT, false);
        controlsCell.setActionListenerDelegate(
            (buttonId, data) -> reportRowAction(row.layerId(), buttonId, data));

        var shownBox = controlsCell.addAreaCheckbox(
            KmuStrings.get(KmuStrings.MAP_LAYER_ARRANGE_SHOWN),
            ArrangementRowAction.TOGGLE_SHOWN,
            StarsectorUiColour.VANILLA_BUTTON_BG_DARK.resolve(),
            StarsectorUiColour.VANILLA_PLAYER_DARK.resolve(),
            StarsectorUiColour.VANILLA_PLAYER_BRIGHT.resolve(),
            SHOWN_BOX_WIDTH,
            CONTROL_HEIGHT,
            NO_PAD);
        shownBox.setChecked(!row.isHidden());
        shownBox.setEnabled(editor.canToggleRowHidden(row.layerId()));

        var upButton = addMoveButton(
            controlsCell,
            KmuStrings.MAP_LAYER_ARRANGE_MOVE_UP,
            ArrangementRowAction.MOVE_UP);
        upButton.getPosition().rightOfMid(shownBox, CONTROL_GAP);
        upButton.setEnabled(editor.canMoveRowUp(row.layerId()));

        var downButton = addMoveButton(
            controlsCell,
            KmuStrings.MAP_LAYER_ARRANGE_MOVE_DOWN,
            ArrangementRowAction.MOVE_DOWN);
        downButton.getPosition().rightOfMid(upButton, MOVE_BUTTON_GAP);
        downButton.setEnabled(editor.canMoveRowDown(row.layerId()));

        box.addUIElement(controlsCell).inTL(BOX_PAD + LABEL_WIDTH + CONTROL_GAP, rowTop);
    }

    // The way out, in the box's bottom right corner where the game puts its own dialogs' buttons.
    private void addCloseButton(CustomPanelAPI box, float boxHeight) {

        var footer = box.createUIElement(CLOSE_BUTTON_WIDTH, FOOTER_HEIGHT, false);
        footer.setActionListenerDelegate((buttonId, data) -> onClosePressed.run());
        footer.addButton(
            KmuStrings.get(KmuStrings.DIALOG_CLOSE),
            KmuStrings.DIALOG_CLOSE,
            CLOSE_BUTTON_WIDTH,
            CONTROL_HEIGHT,
            NO_PAD);

        box.addUIElement(footer)
            .inTL(BOX_WIDTH - BOX_PAD - CLOSE_BUTTON_WIDTH, boxHeight - FOOTER_HEIGHT);
    }

    // A press on a row's controls, passed on once it is one of ours. A press carrying anything else -
    // another mod's, or one left over from a column already rebuilt - is dropped here rather than
    // reaching the editor as a guess.
    //
    // Both objects the delegate is handed are searched, because which of them carries the id is the
    // engine's business and differs by widget: the panel passes the button and expects the id to be read
    // off it, while other surfaces hand the id over directly. Reading one position alone is why this did
    // nothing at all - the press arrived, the test failed, and there was nothing to report it.
    private void reportRowAction(String layerId, Object firstArgument, Object secondArgument) {

        var action = VanillaActionIds.resolveActionId(
            firstArgument,
            secondArgument,
            ArrangementRowAction.class);

        if (action != null) {
            onRowAction.accept(layerId, action);
        }
    }
}
