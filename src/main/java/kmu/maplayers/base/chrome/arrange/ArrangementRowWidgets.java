package kmu.maplayers.base.chrome.arrange;

import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

import kmlib.starsector.ui.buttons.VanillaActionIds;
import kmlib.starsector.ui.colour.StarsectorUiColour;

import kmu.util.KmuStrings;

import java.util.function.BiConsumer;

/**
 * One layer's row in the arranging dialog: its name, the box saying whether its tab is on the bar,
 * and the pair that moves it.
 *
 * <p>Apart from the box that holds the column because the two answer different questions. What a row
 * is made of and what each of its controls may do is one thing; how many rows there are, what stands
 * above and below them, and what the surface under them is painted with is another.
 *
 * <p><b>A row shows its state rather than saying it.</b> The box carries no word - the line over the
 * column says what it does once, rather than once per row - and a layer whose tab is off the bar has
 * its name drawn muted, so a column answers "what have I taken off" at a glance.
 *
 * <p>Every part of a row is a vanilla element placed by hand rather than one element told to run
 * across, because an element lays its contents out top to bottom. A row is therefore elements side
 * by side.
 *
 * <p>Holds what a row may do for the length of one build. A change makes a new body and a new set of
 * rows rather than editing these in place.
 */
final class ArrangementRowWidgets {

    // What the shown box says, which is nothing. The line over the column already says what the box
    // does, so a word inside it would be that sentence repeated once per row.
    private static final String NO_LABEL = "";

    // What each row may do, asked afresh per row as it is built.
    private final MapLayerArrangementEditor editor;

    // Where a press on a row's controls goes, by layer ID rather than by position: the position has
    // moved by the time a second press arrives.
    private final BiConsumer<String, ArrangementRowAction> onRowAction;

    /**
     * @param editor      what each row may do - which way it can move, and whether its box answers
     * @param onRowAction where a press goes, as the row's layer ID and what was pressed
     */
    ArrangementRowWidgets(
            MapLayerArrangementEditor editor,
            BiConsumer<String, ArrangementRowAction> onRowAction) {

        this.editor = editor;
        this.onRowAction = onRowAction;
    }

    /**
     * Writes a row's name into {@code labelElement}, in the shade its state calls for.
     *
     * <p>The shade is read off the row's own state rather than off what the editor will allow to
     * change: the last row still on the bar cannot be taken off it, and it is nonetheless on it.
     *
     * @param labelElement the element the name is drawn in
     * @param row          the layer whose name is drawn
     */
    static void addRowLabel(TooltipMakerAPI labelElement, MapLayerArrangementRow row) {

        var shade = row.isHidden()
            ? StarsectorUiColour.VANILLA_GRAY
            : StarsectorUiColour.VANILLA_TEXT;

        labelElement.addPara(row.layerLabel(), shade.resolve(), ArrangementBoxLayout.NO_PAD);
    }

    /**
     * Adds a row's shown box to {@code controlsElement}, square and wordless.
     *
     * <p>Its state is the caller's to set: whether the box is ticked and whether it may be pressed
     * are both answered from the arrangement, which this does not hold.
     *
     * @param controlsElement the element the box is drawn in
     * @return the box, so the caller can state it and place the pair beside it
     */
    static ButtonAPI addShownBox(TooltipMakerAPI controlsElement) {

        return controlsElement.addAreaCheckbox(
            NO_LABEL,
            ArrangementRowAction.TOGGLE_SHOWN,
            StarsectorUiColour.VANILLA_BUTTON_BG_DARK.resolve(),
            StarsectorUiColour.VANILLA_PLAYER_DARK.resolve(),
            StarsectorUiColour.VANILLA_PLAYER_BRIGHT.resolve(),
            ArrangementBoxLayout.SHOWN_BOX_WIDTH,
            ArrangementBoxLayout.CONTROL_HEIGHT,
            ArrangementBoxLayout.NO_PAD);
    }

    /**
     * Stands one row's two elements in {@code box}: its name on the left, its three controls on the
     * right.
     *
     * @param box     the box panel the row's elements are added to
     * @param row     the layer this row stands for
     * @param rowTop  how far the row's top is below the box's own top
     */
    void addRowTo(CustomPanelAPI box, MapLayerArrangementRow row, float rowTop) {

        var labelElement = box.createUIElement(
            ArrangementBoxLayout.LABEL_WIDTH,
            ArrangementBoxLayout.ROW_HEIGHT,
            false);

        addRowLabel(labelElement, row);
        box.addUIElement(labelElement).inTL(ArrangementBoxLayout.resolveLeadingElementLeft(), rowTop);

        var controlsElement = box.createUIElement(
            ArrangementBoxLayout.resolveElementWidth(ArrangementBoxLayout.CONTROLS_WIDTH),
            ArrangementBoxLayout.ROW_HEIGHT,
            false);

        controlsElement.setActionListenerDelegate(
            (buttonId, data) -> reportRowAction(row.layerId(), buttonId, data));

        addRowControls(controlsElement, row);

        box.addUIElement(controlsElement)
            .inTL(ArrangementBoxLayout.resolveControlsElementLeft(), rowTop);
    }

    // One of the pair that moves a row, at the row's control size. Named apart because the two differ
    // only in their word and their action, and a second copy of the five-argument call would be a
    // second place for the size to drift.
    private static ButtonAPI addMoveButton(
            TooltipMakerAPI controlsElement,
            String labelKey,
            ArrangementRowAction action) {

        return controlsElement.addButton(
            KmuStrings.get(labelKey),
            action,
            ArrangementBoxLayout.MOVE_BUTTON_WIDTH,
            ArrangementBoxLayout.CONTROL_HEIGHT,
            ArrangementBoxLayout.NO_PAD);
    }

    // The three controls, left to right, each placed against the one before it and each told whether
    // it answers. A button at the end of its travel is disabled rather than absent, so a row keeps its
    // shape as it moves through the column.
    private void addRowControls(TooltipMakerAPI controlsElement, MapLayerArrangementRow row) {

        var shownBox = addShownBox(controlsElement);
        shownBox.setChecked(!row.isHidden());
        shownBox.setEnabled(editor.canToggleRowHidden(row.layerId()));

        var upButton = addMoveButton(
            controlsElement,
            KmuStrings.MAP_LAYER_ARRANGE_MOVE_UP,
            ArrangementRowAction.MOVE_UP);
        upButton.getPosition().rightOfMid(shownBox, ArrangementBoxLayout.CONTROL_GAP);
        upButton.setEnabled(editor.canMoveRowUp(row.layerId()));

        var downButton = addMoveButton(
            controlsElement,
            KmuStrings.MAP_LAYER_ARRANGE_MOVE_DOWN,
            ArrangementRowAction.MOVE_DOWN);
        downButton.getPosition().rightOfMid(upButton, ArrangementBoxLayout.MOVE_BUTTON_GAP);
        downButton.setEnabled(editor.canMoveRowDown(row.layerId()));
    }

    // A press on a row's controls, passed on once it is one of ours. A press carrying anything else -
    // another mod's, or one left over from a column already rebuilt - is dropped here rather than
    // reaching the editor as a guess.
    //
    // Both objects the delegate is handed are searched, because which of them carries the ID is the
    // engine's business and differs by widget: the panel passes the button and expects the ID to be
    // read off it, while other surfaces hand the ID over directly.
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
