package kmu.maplayers.base.chrome.arrange;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;

import kmlib.starsector.ui.colour.StarsectorUiColour;
import kmlib.starsector.ui.coreui.CoreUiOverlayPanels;
import kmlib.starsector.ui.map.probes.ShownMapTab;

import kmu.maplayers.base.layer.LiveMapLayerArrangement;
import kmu.maplayers.base.layer.MapLayerRegistry;
import kmu.util.KmuStrings;

import org.lwjgl.input.Keyboard;

import java.util.List;

/**
 * The dialog the player arranges the layer bar in: a column of the registered layers, each with a box
 * saying whether its tab is on the bar and a pair of buttons that move it one place.
 *
 * <p><b>Built from the game's own widgets</b> - a panel from {@code SettingsAPI.createCustom} stood in
 * the core UI's own tree by {@link CoreUiOverlayPanels}, with vanilla labels, boxes and buttons inside
 * it. Nothing here is painted into the map's render pass, so the dialog reads as part of the game
 * rather than as something drawn over it, and its controls behave like the ones beside them.
 *
 * <p>Every published route to a custom dialog hangs off an interaction dialog, and the screens this is
 * opened from have none - raising one would close the screen whose bar is being arranged. The core UI
 * tree is what is left, which is a reach rather than an API and is why the dialog simply does not open
 * where that reach comes up empty.
 *
 * <p><b>Modality is supplied here, because the game does not supply it.</b> A panel added this way is
 * an ordinary child: nothing dims behind it and nothing stops the screen underneath being dispatched
 * to. So the dialog paints its own backdrop - a vanilla element sized to the screen, drawn by the
 * game's own background fill rather than by us - and claims the input its own widgets do not want.
 * What cannot be supplied is being recognised as a modal by anything reading the game's modal base, so
 * the map-side gates that stand down under one read {@link #isDialogRaised()} beside that reading.
 *
 * <p>Only mouse events inside the dialog's own box are left alone, and everything else is claimed.
 * That way round rather than "claim everything" because the order in which the game hands events to a
 * panel's widgets and to its plugin is the game's business: leaving the box's own events untouched is
 * correct whichever way round it is, while claiming them first would leave the dialog's buttons dead
 * on a build that dispatches to the plugin first.
 *
 * <p>Closes itself when the map goes off screen. The panel hangs from the core UI rather than from the
 * screen it was opened on, so nothing about leaving that screen takes it down - and a dialog left
 * standing over the campaign would be one the player cannot connect to anything they did.
 *
 * <p>One dialog for the process, like the hosts that open it. Two would be two arrangements of one bar
 * being edited at once, each recording over the other.
 */
public final class MapLayerArrangementDialog {

    /** The one dialog, opened by whichever screen's bar the player pressed the key on. */
    public static final MapLayerArrangementDialog INSTANCE = new MapLayerArrangementDialog();

    // How dark the backdrop stands the screen down to. Vanilla's own element fill rather than a quad of
    // our own, so it dims exactly as the game's panels dim.
    private static final float BACKDROP_ALPHA = 0.7f;

    // The dialog box's own fill, over the backdrop - opaque enough to read a column of text against.
    private static final float BOX_ALPHA = 0.95f;

    // The column's parts, left to right, in UI units. The box's width follows from them, so a wider
    // label column widens the dialog rather than pushing the move buttons off it.
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

    // The width the parts above add up to, plus the box's own margins.
    private static final float BOX_WIDTH =
        BOX_PAD * 2f
            + LABEL_WIDTH + CONTROL_GAP
            + SHOWN_BOX_WIDTH + CONTROL_GAP
            + MOVE_BUTTON_WIDTH + MOVE_BUTTON_GAP + MOVE_BUTTON_WIDTH;

    // What the player is arranging, or null while the dialog is down. Seeded when the dialog opens and
    // dropped when it closes, so a second visit reads the store again rather than the rows it left.
    private MapLayerArrangementEditor editor;

    // The screen-sized panel standing in the core UI, or null while the dialog is down. Also the
    // dialog's own "is it up" state, there being nothing else that can be up without it.
    private CustomPanelAPI dialogPanel;

    // Where the dialog's box is, for the input claim to leave its own events alone. Read from the
    // laid-out panel rather than recomputed, so the claim answers to the box on screen.
    private PositionAPI boxPlacement;

    // The two children the body is made of, held so a rebuild can take the previous ones off. The
    // panel offers no way to ask what it is holding, so what was added has to be remembered.
    private UIComponentAPI backdropElement;
    private UIComponentAPI boxPanel;

    private MapLayerArrangementDialog() {
    }

    /**
     * @return whether the dialog stands over the screen right now - which the map-side gates read
     *         beside the game's own modal reading, this dialog being invisible to that one
     */
    public boolean isDialogRaised() {
        return dialogPanel != null;
    }

    /**
     * Raises the dialog over whatever screen is showing a map.
     *
     * <p>Does nothing where it is already up, where no store is bound to write the arrangement to, and
     * where the core UI cannot be reached to stand a panel in - all three leaving the screen as it was
     * rather than half-opening.
     */
    public void openDialog() {

        if (isDialogRaised()) {
            return;
        }

        var arrangementSelection = LiveMapLayerArrangement.resolveArrangementSelection();
        if (arrangementSelection == null) {
            return;
        }

        var panel = Global.getSettings().createCustom(
            Global.getSettings().getScreenWidth(),
            Global.getSettings().getScreenHeight(),
            new DialogPanelPlugin());

        var placement = CoreUiOverlayPanels.attachOverlayPanel(panel);
        if (placement == null) {
            return;
        }
        placement.inTL(NO_PAD, NO_PAD);

        editor = new MapLayerArrangementEditor(arrangementSelection, MapLayerRegistry.getLayers());
        dialogPanel = panel;

        buildDialogBody();
    }

    /**
     * Takes the dialog off the screen and drops what it was holding. Safe to call with the dialog
     * already down, which is what lets every way it can end - the key, the button, the screen closing -
     * say the same thing.
     */
    public void closeDialog() {

        if (!isDialogRaised()) {
            return;
        }

        CoreUiOverlayPanels.detachOverlayPanel(dialogPanel);

        dialogPanel = null;
        boxPlacement = null;
        backdropElement = null;
        boxPanel = null;
        editor = null;
    }

    // The dialog's contents, rebuilt whole after every change rather than patched. The rows move, so
    // the thing a change alters is the column's order - and a column rebuilt from the editor cannot
    // disagree with it, which a set of widgets each nudged into a new place eventually would.
    private void buildDialogBody() {

        removeDialogBody();

        var backdrop = dialogPanel.createUIElement(
            Global.getSettings().getScreenWidth(),
            Global.getSettings().getScreenHeight(),
            false);
        backdrop.setBgAlpha(BACKDROP_ALPHA);
        dialogPanel.addUIElement(backdrop).inTL(NO_PAD, NO_PAD);
        backdropElement = backdrop;

        var rows = editor.getRows();
        var boxHeight = BOX_PAD * 2f + TITLE_HEIGHT + HINT_HEIGHT + FOOTER_HEIGHT
            + rows.size() * ROW_HEIGHT;

        var box = dialogPanel.createCustomPanel(BOX_WIDTH, boxHeight, null);
        buildDialogBox(box, rows, boxHeight);

        boxPlacement = dialogPanel.addComponent(box).inMid();
        boxPanel = box;
    }

    // The previous body, off the panel. Remembered rather than asked for: a panel does not publish what
    // it is holding, so a rebuild that did not track its own children would stack a second column over
    // the first.
    private void removeDialogBody() {

        if (backdropElement != null) {
            dialogPanel.removeComponent(backdropElement);
            backdropElement = null;
        }

        if (boxPanel != null) {
            dialogPanel.removeComponent(boxPanel);
            boxPanel = null;
        }
    }

    // The box's own contents: a title, a line saying what the controls do, one row per layer, and the
    // way out.
    private void buildDialogBox(CustomPanelAPI box, List<MapLayerArrangementRow> rows, float boxHeight) {

        var header = box.createUIElement(BOX_WIDTH - BOX_PAD * 2f, TITLE_HEIGHT + HINT_HEIGHT, false);
        header.setBgAlpha(BOX_ALPHA);
        header.addTitle(KmuStrings.get(KmuStrings.MAP_LAYER_ARRANGE_TITLE));
        header.addPara(KmuStrings.get(KmuStrings.MAP_LAYER_ARRANGE_HINT), NO_PAD);
        box.addUIElement(header).inTL(BOX_PAD, BOX_PAD);

        for (var rowIndex = 0; rowIndex < rows.size(); rowIndex++) {

            buildDialogRow(
                box,
                rows.get(rowIndex),
                BOX_PAD + TITLE_HEIGHT + HINT_HEIGHT + rowIndex * ROW_HEIGHT);
        }

        var footer = box.createUIElement(CLOSE_BUTTON_WIDTH, FOOTER_HEIGHT, false);
        footer.setActionListenerDelegate((buttonId, data) -> closeDialog());
        footer.addButton(
            KmuStrings.get(KmuStrings.DIALOG_CLOSE),
            KmuStrings.DIALOG_CLOSE,
            CLOSE_BUTTON_WIDTH,
            CONTROL_HEIGHT,
            NO_PAD);
        box.addUIElement(footer).inTL(BOX_WIDTH - BOX_PAD - CLOSE_BUTTON_WIDTH, boxHeight - FOOTER_HEIGHT);
    }

    // One layer's row: what its tab says, whether that tab is on the bar, and the two move buttons.
    //
    // The label sits in an element of its own rather than beside the controls, because a vanilla
    // element lays its contents out top to bottom - so a row is built as cells placed side by side
    // rather than as one element told to run horizontally.
    private void buildDialogRow(CustomPanelAPI box, MapLayerArrangementRow row, float rowTop) {

        var labelCell = box.createUIElement(LABEL_WIDTH, ROW_HEIGHT, false);
        labelCell.addPara(row.layerLabel(), NO_PAD);
        box.addUIElement(labelCell).inTL(BOX_PAD, rowTop);

        var controlsCell = box.createUIElement(
            BOX_WIDTH - BOX_PAD * 2f - LABEL_WIDTH - CONTROL_GAP,
            ROW_HEIGHT,
            false);
        controlsCell.setActionListenerDelegate((buttonId, data) -> handleRowAction(row.layerId(), buttonId));

        var shownBox = controlsCell.addAreaCheckbox(
            KmuStrings.get(KmuStrings.MAP_LAYER_ARRANGE_SHOWN),
            RowAction.TOGGLE_SHOWN,
            StarsectorUiColour.VANILLA_BUTTON_BG_DARK.resolve(),
            StarsectorUiColour.VANILLA_PLAYER_DARK.resolve(),
            StarsectorUiColour.VANILLA_PLAYER_BRIGHT.resolve(),
            SHOWN_BOX_WIDTH,
            CONTROL_HEIGHT,
            NO_PAD);
        shownBox.setChecked(!row.isHidden());
        shownBox.setEnabled(editor.canToggleRowHidden(row.layerId()));

        var upButton = addMoveButton(controlsCell, KmuStrings.MAP_LAYER_ARRANGE_MOVE_UP, RowAction.MOVE_UP);
        upButton.getPosition().rightOfMid(shownBox, CONTROL_GAP);
        upButton.setEnabled(editor.canMoveRowUp(row.layerId()));

        var downButton =
            addMoveButton(controlsCell, KmuStrings.MAP_LAYER_ARRANGE_MOVE_DOWN, RowAction.MOVE_DOWN);
        downButton.getPosition().rightOfMid(upButton, MOVE_BUTTON_GAP);
        downButton.setEnabled(editor.canMoveRowDown(row.layerId()));

        box.addUIElement(controlsCell).inTL(BOX_PAD + LABEL_WIDTH + CONTROL_GAP, rowTop);
    }

    // One of the pair that moves a row, at the row's control size. Named apart because the two differ
    // only in their word and their action, and a second copy of the five-argument call would be a second
    // place for the size to drift.
    private static ButtonAPI addMoveButton(TooltipMakerAPI controlsCell, String labelKey, RowAction action) {

        return controlsCell.addButton(
            KmuStrings.get(labelKey),
            action,
            MOVE_BUTTON_WIDTH,
            CONTROL_HEIGHT,
            NO_PAD);
    }

    // What a press on one of a row's controls does. The row is addressed by layer id rather than by
    // position, the position having moved by the time a second press arrives.
    private void handleRowAction(String layerId, Object buttonId) {

        if (!(buttonId instanceof RowAction action)) {
            return;
        }

        switch (action) {
            case MOVE_UP -> editor.moveRowUp(layerId);
            case MOVE_DOWN -> editor.moveRowDown(layerId);
            case TOGGLE_SHOWN -> editor.toggleRowHidden(layerId);
        }
        buildDialogBody();
    }

    // Whether this event belongs to the dialog's own widgets, which is the one thing the claim leaves
    // alone. Answered off the box the layout placed rather than off the numbers it was laid out from,
    // so a resized window moves the claim with the box.
    private boolean isEventInsideDialogBox(InputEventAPI event) {

        return boxPlacement != null
            && event.isMouseEvent()
            && boxPlacement.containsEvent(event);
    }

    // What a press on a row's controls means. An enum rather than a string, so a button carrying
    // something else - another mod's, or a stale one - is not mistaken for one of these.
    private enum RowAction {
        MOVE_UP,
        MOVE_DOWN,
        TOGGLE_SHOWN
    }

    // The dialog's own frame hooks: the claim that makes it modal, and the guard that takes it down
    // with the screen it was opened on.
    private final class DialogPanelPlugin implements CustomUIPanelPlugin {

        @Override
        public void positionChanged(PositionAPI position) {
        }

        @Override
        public void renderBelow(float alphaMult) {
        }

        @Override
        public void render(float alphaMult) {
        }

        @Override
        public void advance(float amount) {

            // The panel hangs from the core UI, which outlives the screen the dialog was opened on -
            // so leaving that screen has to be noticed rather than waited for.
            if (!isMapShowing()) {
                closeDialog();
            }
        }

        @Override
        public void processInput(List<InputEventAPI> events) {

            for (var event : events) {

                // Asked first because a consumed event's own accessors throw, and every test below
                // reads one.
                if (event.isConsumed()) {
                    continue;
                }

                if (event.isKeyDownEvent() && event.getEventValue() == Keyboard.KEY_ESCAPE) {
                    event.consume();
                    closeDialog();
                    return;
                }

                if (!isEventInsideDialogBox(event)) {
                    event.consume();
                }
            }
        }

        @Override
        public void buttonPressed(Object buttonId) {
        }

        // Whether a map is still on screen to have a bar to arrange. The reach raises where the widget
        // tree cannot be walked at all, and that reads as no map: a dialog nobody can place should come
        // down rather than stay up over a screen it can no longer see.
        private boolean isMapShowing() {

            try {
                return ShownMapTab.resolveShownMapTab() != null;

            } catch (RuntimeException cannotReachMap) {
                return false;
            }
        }
    }
}
