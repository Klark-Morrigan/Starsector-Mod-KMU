package kmu.maplayers.base.chrome.arrange;

import kmu.maplayers.base.layer.ArrangedLayers;
import kmu.maplayers.base.layer.MapLayer;
import kmu.maplayers.base.layer.MapLayerArrangement;
import kmu.maplayers.base.layer.MapLayerArrangementSelection;

import java.util.ArrayList;
import java.util.List;

/**
 * What the arranging dialog is actually doing: the rows the player is looking at, the three things
 * they can do to one, and the rules about when they cannot.
 *
 * <p>Apart from the dialog that draws it because none of this is about widgets. Which row may move up,
 * what hiding the last visible tab would leave, and what an arrangement is worth writing are questions
 * with answers, and answers reachable only by clicking a button in a running game are answers nobody
 * can check.
 *
 * <p><b>Every change is recorded as it is made.</b> There is no draft to commit and no cancel: the bar
 * behind the dialog is what the player is arranging, so it moves as they arrange it and they judge the
 * result by looking at it. A draft would also have to answer what a dialog left open across a screen
 * change does with the edits it is holding, which is a question this way of working never asks.
 *
 * <p>Seeded once, from the arrangement and roster it was built with, and thereafter its own source of
 * truth. Re-reading the store between clicks would mean reading back what this just wrote, and
 * re-reading the roster would let a mod registering a layer mid-dialog shuffle the row under the
 * player's pointer.
 *
 * <p>Hidden layers are rows like any other. The dialog is the only way a hidden tab comes back, so a
 * list showing only what is on the bar would be a control the player cannot undo.
 */
public final class MapLayerArrangementEditor {

    // How many tabs a bar has left when it cannot afford to lose one. A bar showing this many cannot
    // hide the last, the dialog that would put it back being reached from the bar.
    private static final int LAST_VISIBLE_TAB_COUNT = 1;

    // Nothing in the row list answers to this id.
    private static final int NO_ROW = -1;

    // Which way a move goes, as the offset from a row to the neighbour it swaps with. Named because a
    // bare sign at a call site says nothing about which end of the column it means.
    private static final int ONE_PLACE_UP = -1;
    private static final int ONE_PLACE_DOWN = 1;

    // Where the arrangement is recorded as the player makes it. Read once at construction and written
    // on every change, which is the whole of this editor's contact with anything outside itself.
    private final MapLayerArrangementSelection arrangementSelection;

    // The rows as the player has them now, in bar order. Mutable because arranging is what this is
    // for; nothing outside sees the list itself.
    private final List<MapLayerArrangementRow> rows;

    /**
     * @param arrangementSelection where the arrangement is read from at construction and recorded to
     *                             on every change
     * @param rosterLayers         every registered layer, in registration order - the roster the stored
     *                             arrangement is laid over
     */
    public MapLayerArrangementEditor(
            MapLayerArrangementSelection arrangementSelection,
            List<MapLayer> rosterLayers) {

        this.arrangementSelection = arrangementSelection;
        this.rows = buildRows(arrangementSelection.readArrangement(), rosterLayers);
    }

    /**
     * @return the rows in bar order, hidden tabs included
     */
    public List<MapLayerArrangementRow> getRows() {
        return List.copyOf(rows);
    }

    /**
     * @param layerId the row's layer ID
     * @return whether this row has anywhere above it to go
     */
    public boolean canMoveRowUp(String layerId) {
        return canMoveRowBy(indexOfRow(layerId), ONE_PLACE_UP);
    }

    /**
     * @param layerId the row's layer ID
     * @return whether this row has anywhere below it to go
     */
    public boolean canMoveRowDown(String layerId) {
        return canMoveRowBy(indexOfRow(layerId), ONE_PLACE_DOWN);
    }

    /**
     * Whether this row's tab can be taken off the bar or put back on it.
     *
     * <p>Only ever refuses in one direction. Putting a tab back is always allowed; taking the last one
     * off would leave a bar with no tabs, and the dialog that would undo it is opened from that bar.
     *
     * @param layerId the row's layer ID
     * @return whether the toggle on this row does anything
     */
    public boolean canToggleRowHidden(String layerId) {

        var rowIndex = indexOfRow(layerId);

        return rowIndex != NO_ROW && canToggleRowAt(rowIndex);
    }

    /**
     * Swaps this row with the one above it and records the arrangement. A row with nothing above it is
     * left where it is, which is the same answer {@link #canMoveRowUp} gives the button that raised it.
     *
     * @param layerId the row's layer ID
     */
    public void moveRowUp(String layerId) {
        moveRow(layerId, ONE_PLACE_UP);
    }

    /**
     * Swaps this row with the one below it and records the arrangement.
     *
     * @param layerId the row's layer ID
     */
    public void moveRowDown(String layerId) {
        moveRow(layerId, ONE_PLACE_DOWN);
    }

    /**
     * Takes this row's tab off the bar or puts it back, and records the arrangement. A refused toggle
     * changes nothing and records nothing.
     *
     * @param layerId the row's layer ID
     */
    public void toggleRowHidden(String layerId) {

        var rowIndex = indexOfRow(layerId);

        if (rowIndex == NO_ROW || !canToggleRowAt(rowIndex)) {
            return;
        }
        rows.set(rowIndex, rows.get(rowIndex).toggleHidden());

        recordArrangement();
    }

    /**
     * Applies what a press on one of a row's controls means.
     *
     * <p>The mapping lives here rather than in the dialog that receives the press, so it can be read
     * without a running game: a press arrives through a vanilla panel's delegate, and a case wired to
     * the wrong one of these is silent - the button works, it simply does the other thing.
     *
     * @param layerId the row's layer ID
     * @param action  what was pressed
     */
    void applyRowAction(String layerId, ArrangementRowAction action) {

        switch (action) {
            case MOVE_UP -> moveRowUp(layerId);
            case MOVE_DOWN -> moveRowDown(layerId);
            case TOGGLE_SHOWN -> toggleRowHidden(layerId);
        }
    }

    // The seed: every registered layer in the player's order, each row carrying the label its tab reads
    // and whether that tab is currently off the bar. Hidden layers are ordered with the rest, the stored
    // order being about where a tab stands rather than about whether it is standing.
    private static List<MapLayerArrangementRow> buildRows(
            MapLayerArrangement arrangement,
            List<MapLayer> rosterLayers) {

        var rows = new ArrayList<MapLayerArrangementRow>(rosterLayers.size());

        for (var layer : ArrangedLayers.arrangeAllLayers(arrangement, rosterLayers)) {

            rows.add(new MapLayerArrangementRow(
                layer.getId(),
                layer.resolveTabLabelText(),
                arrangement.isLayerHidden(layer.getId())));
        }
        return rows;
    }

    // One row swapped with its neighbour, or nothing at all where there is no such neighbour. Both
    // directions in one rule because a move is a swap either way, and two rules would be two places for
    // the range check to be wrong.
    private void moveRow(String layerId, int offset) {

        var rowIndex = indexOfRow(layerId);

        if (!canMoveRowBy(rowIndex, offset)) {
            return;
        }
        var targetIndex = rowIndex + offset;

        var movedRow = rows.set(targetIndex, rows.get(rowIndex));
        rows.set(rowIndex, movedRow);

        recordArrangement();
    }

    // The move rule itself, over a row its caller has already found. Shared by the question and the act
    // for the reason the toggle's is: a button disabled by one rule and a press refused by another are
    // two statements of one bound, and they drift.
    private boolean canMoveRowBy(int rowIndex, int offset) {

        if (rowIndex == NO_ROW) {
            return false;
        }
        var targetIndex = rowIndex + offset;

        return targetIndex >= 0 && targetIndex < rows.size();
    }

    // Where this layer's row stands, or NO_ROW for an ID the dialog is not showing - which a caller
    // reaches by holding a button from a dialog whose rows have since been rebuilt.
    private int indexOfRow(String layerId) {

        for (var rowIndex = 0; rowIndex < rows.size(); rowIndex++) {

            if (rows.get(rowIndex).layerId().equals(layerId)) {
                return rowIndex;
            }
        }
        return NO_ROW;
    }

    // The toggle rule itself, over a row its caller has already found. Shared by the question and the
    // act so the two cannot part company, and so neither pays for a second lookup of the same row.
    //
    // Refuses in one direction only: putting a tab back is always allowed, and taking the last one off
    // would leave a bar with no way to the dialog that would undo it.
    private boolean canToggleRowAt(int rowIndex) {

        return rows.get(rowIndex).isHidden()
            || countVisibleRows() > LAST_VISIBLE_TAB_COUNT;
    }

    // How many tabs the bar would show as the rows stand, which is what the last-tab guard counts.
    private int countVisibleRows() {

        return (int) rows.stream()
            .filter(row -> !row.isHidden())
            .count();
    }

    // The rows as an arrangement: every ID in the order they now stand, and the hidden ones named
    // again. Every ID is placed rather than only the ones the player moved, because after a visit to
    // this dialog every row is where the player left it - an ID omitted as "never arranged" would be
    // appended in registration order on the next read and jump out of the place they just saw it in.
    private void recordArrangement() {

        var orderedLayerIds = rows.stream()
            .map(MapLayerArrangementRow::layerId)
            .toList();

        var hiddenLayerIds = rows.stream()
            .filter(MapLayerArrangementRow::isHidden)
            .map(MapLayerArrangementRow::layerId)
            .toList();

        arrangementSelection.recordArrangement(
            new MapLayerArrangement(orderedLayerIds, hiddenLayerIds));
    }
}
