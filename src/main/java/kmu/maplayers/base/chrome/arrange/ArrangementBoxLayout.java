package kmu.maplayers.base.chrome.arrange;

/**
 * Every measurement the arranging dialog's box is built from, and the positions derived from them.
 *
 * <p>Apart from the widgets because arithmetic is checkable and widget calls are not. Where a cell
 * sits and how tall the box stands for a given number of rows are questions with answers; asking
 * them of a class that also needs a running game to build a panel means they can only be answered by
 * opening the dialog and looking.
 *
 * <p>One home for all of it rather than a set per class. The box is as wide as its parts, so the
 * parts are what is stated and the width is what follows - two classes each holding half the sum
 * would have to be kept agreeing by hand, and the widths are exactly what moves when a control's
 * shape changes.
 *
 * <p>Coordinates are the box's own, measured from its top-left corner, which is how a vanilla panel
 * places what it holds.
 */
final class ArrangementBoxLayout {

    // No pad of this layout's own above what a cell is given. A vanilla element still insets what it
    // holds by a text padding of its own, which asking for none here does not remove.
    static final float NO_PAD = 0f;

    // A row's parts, left to right, in UI units.
    static final float LABEL_WIDTH = 200f;

    // Wide enough for the longer of the two words rather than for a glyph, both buttons taking the one
    // width so the pair reads as a pair rather than as two controls that happen to sit together.
    static final float MOVE_BUTTON_WIDTH = 54f;
    static final float CONTROL_HEIGHT = 20f;
    static final float CONTROL_GAP = 8f;
    static final float MOVE_BUTTON_GAP = 4f;

    // Square at the row's control height, the box carrying no word to be wide enough for. Stated after
    // the height it is taken from rather than up with the other widths, a constant being unable to
    // read one declared below it.
    static final float SHOWN_BOX_WIDTH = CONTROL_HEIGHT;

    static final float ROW_HEIGHT = 28f;
    static final float BOX_PAD = 12f;

    // Tall enough for the larger face the box is headed in, which stands above what an element's
    // default title size would have needed.
    static final float TITLE_HEIGHT = 34f;

    // What parts the heading from the line under it. Stated rather than left at no pad: added back to
    // back, the two read as one block and the box has nothing that looks like a head.
    static final float TITLE_GAP = 10f;

    static final float FOOTER_HEIGHT = 34f;
    static final float APPLY_BUTTON_WIDTH = 90f;

    // What a row's controls occupy beside its label. Named rather than subtracted back out of the box
    // width where the cell is built: the box is as wide as its parts, so the parts are what is stated
    // and the width is what follows.
    static final float CONTROLS_WIDTH =
        SHOWN_BOX_WIDTH + CONTROL_GAP + MOVE_BUTTON_WIDTH + MOVE_BUTTON_GAP + MOVE_BUTTON_WIDTH;

    static final float BOX_WIDTH =
        BOX_PAD * 2f + LABEL_WIDTH + CONTROL_GAP + CONTROLS_WIDTH;

    // The hint's own two lines, and the cell holding them plus the gap drawn above them - the box being
    // as tall as its furniture, what the gap costs is carried here.
    private static final float HINT_TEXT_HEIGHT = 40f;
    private static final float HINT_HEIGHT = HINT_TEXT_HEIGHT + TITLE_GAP;

    private ArrangementBoxLayout() {
    }

    /**
     * @param rowCount how many layers the column shows
     * @return how tall the box stands: its fixed furniture plus the column
     */
    static float resolveBoxHeight(int rowCount) {

        return BOX_PAD * 2f + resolveHeaderHeight() + FOOTER_HEIGHT + rowCount * ROW_HEIGHT;
    }

    /**
     * @return how wide the head's cell is - the box less the pad at either side of it
     */
    static float resolveHeaderWidth() {

        return BOX_WIDTH - BOX_PAD * 2f;
    }

    /**
     * @return how tall the head's cell is, holding both the title and the line under it
     */
    static float resolveHeaderHeight() {

        return TITLE_HEIGHT + HINT_HEIGHT;
    }

    /**
     * @param rowIndex where this row stands in the column, from zero
     * @return how far the row's top is below the box's own top - the column starting where the head
     *         ends, so a row butts against the one above it
     */
    static float resolveRowTop(int rowIndex) {

        return BOX_PAD + resolveHeaderHeight() + rowIndex * ROW_HEIGHT;
    }

    /**
     * @return how far the controls cell's left edge is in from the box's, which is the label column
     *         and the channel between the two
     */
    static float resolveControlsCellLeft() {

        return BOX_PAD + LABEL_WIDTH + CONTROL_GAP;
    }

    /**
     * @return how far the foot's cell is in from the box's left edge, the way out standing in the
     *         bottom right corner where the game puts its own dialogs' buttons
     */
    static float resolveFooterLeft() {

        return BOX_WIDTH - BOX_PAD - APPLY_BUTTON_WIDTH;
    }

    /**
     * @param boxHeight how tall this box stands, which turns on how many rows it carries
     * @return how far the foot's top is below the box's own top
     */
    static float resolveFooterTop(float boxHeight) {

        return boxHeight - FOOTER_HEIGHT;
    }
}
