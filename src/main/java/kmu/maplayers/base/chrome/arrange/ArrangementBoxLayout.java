package kmu.maplayers.base.chrome.arrange;

/**
 * Every measurement the arranging dialog's box is built from, and the positions derived from them.
 *
 * <p>Apart from the widgets because arithmetic is checkable and widget calls are not. Where an
 * element sits and how tall the box stands for a given number of rows are questions with answers; asking
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

    // No pad of this layout's own above what an element is given. That argument is the vertical space
    // over an element's contents; the horizontal inset it adds regardless is stated separately below.
    static final float NO_PAD = 0f;

    // How far in from its own left edge a vanilla element draws whatever it holds. The engine gives the
    // first thing added to an element an x-align offset of five and lays every later one flush with it
    // (com.fs.starfarer.ui.impl.StandardTooltipV2Expandable.addCustom), so an element placed at the
    // box's pad draws its contents that much further in again. Not ours to choose, only to account for: an
    // edge measured without this term comes out narrower than one measured with it, which is the whole
    // of why the box used to stand closer to the buttons than to the words.
    static final float VANILLA_ELEMENT_CONTENT_INSET = 5f;

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
    // width where the element is built: the box is as wide as its parts, so the parts are what is stated
    // and the width is what follows.
    static final float CONTROLS_WIDTH =
        SHOWN_BOX_WIDTH + CONTROL_GAP + MOVE_BUTTON_WIDTH + MOVE_BUTTON_GAP + MOVE_BUTTON_WIDTH;

    static final float BOX_WIDTH =
        BOX_PAD * 2f + LABEL_WIDTH + CONTROL_GAP + CONTROLS_WIDTH;

    // The hint's own two lines, and the element holding them plus the gap above them - the box being
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
     * @return how wide the head's element is - the box less the pad at either side of it
     */
    static float resolveHeaderWidth() {

        return BOX_WIDTH - BOX_PAD * 2f;
    }

    /**
     * @return how tall the head's element is, holding both the title and the line under it
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
     * Where an element reading from the box's left edge is placed - the head, and each row's name.
     *
     * <p>The pad is taken off rather than handed over, because what the player sees is not the
     * element's own edge but what it draws inside itself, a further
     * {@link #VANILLA_ELEMENT_CONTENT_INSET} in.
     *
     * @return how far in from the box's left edge the element itself goes
     */
    static float resolveLeadingElementLeft() {

        return BOX_PAD - VANILLA_ELEMENT_CONTENT_INSET;
    }

    /**
     * Where an element reading from the box's right edge is placed - a row's controls, and the way out.
     *
     * <p>Measured back from the box's far edge rather than accumulated rightward from what stands to
     * its left, so both edges of the box are reached by the one arithmetic and cannot drift apart as a
     * control's width moves.
     *
     * @param contentsWidth how wide what the element holds is
     * @return how far in from the box's left edge the element itself goes
     */
    static float resolveTrailingElementLeft(float contentsWidth) {

        return BOX_WIDTH - BOX_PAD - contentsWidth - VANILLA_ELEMENT_CONTENT_INSET;
    }

    /**
     * @return how far the controls element is in from the box's left edge, so the pair of buttons ends a
     *         pad in from the box's right edge - the same pad the row's name begins at
     */
    static float resolveControlsElementLeft() {

        return resolveTrailingElementLeft(CONTROLS_WIDTH);
    }

    /**
     * @return how far the foot's element is in from the box's left edge, the way out standing in the
     *         bottom right corner where the game puts its own dialogs' buttons
     */
    static float resolveFooterLeft() {

        return resolveTrailingElementLeft(APPLY_BUTTON_WIDTH);
    }

    /**
     * @param boxHeight how tall this box stands, which turns on how many rows it carries
     * @return how far the foot's top is below the box's own top
     */
    static float resolveFooterTop(float boxHeight) {

        return boxHeight - FOOTER_HEIGHT;
    }
}
