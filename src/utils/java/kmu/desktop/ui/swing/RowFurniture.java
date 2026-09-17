package kmu.desktop.ui.swing;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.LayoutManager;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JSlider;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.UIManager;

/**
 * What every row in the panel is made of, and the shapes a row is laid out in.
 *
 * <p>Apart from {@link ControlRows} because the two answer different questions about the same
 * row. That one is about where a value is KEPT - read on build, written on change, put back on
 * demand - and this is about what a row LOOKS like. A panel gains controls far more often than
 * it gains row shapes, so the file a new knob is written into is not the file the padding and
 * the column widths live in, and neither one has to be read to work on the other.
 *
 * <p>Shared rather than laid out where each control is declared, because that is what makes a
 * column of rows line up. A row that sets its own padding is a row that reads as an unrelated
 * control, and twenty of those read as a list rather than as one panel.
 */
public final class RowFurniture {

    private static final int ROW_PADDING = 4;

    // Wider than a row's own padding, so the rule reads as a break between subjects
    // rather than as one more gap in an evenly spaced column.
    private static final int DIVIDER_PADDING = 8;

    // How a line is divided when things share it: one row of however many columns. Shared with
    // the rest of the package rather than restated per class, since three classes laying out
    // rows all mean the same two numbers by it.
    static final int SINGLE_ROW = 1;

    static final int PAIRED_COLUMNS = 2;

    // The gutter between two rows sharing a line. Enough that the left one's track stops short
    // of the right one's name rather than running into it - with no gap the two read as one
    // wide control whose parts are unrelated.
    private static final int PAIR_GUTTER = 8;

    // The size every small square button in the panel takes. Square enough to read as a marker
    // rather than as a button with a caption, and short enough that a column of them beside
    // checkboxes does not set the row height.
    private static final int MARK_BUTTON_WIDTH = 22;

    private static final int MARK_BUTTON_HEIGHT = 18;

    // ASCII only: the viewer runs wherever the JDK's default font does, and a glyph that
    // renders as a box on one machine makes the control unreadable rather than merely plain.
    private static final String RESET_LABEL = "R";
    private static final String RESET_TOOLTIP = "Reset to default";

    private RowFurniture() {
    }

    /**
     * A rule across the panel, breaking one run of knobs off from the next.
     *
     * <p>The panel is one long column and every row in it looks like every other, so a reader
     * looking for a knob has only its label to go on. A rule says where one subject ends, which
     * turns the search into "the void half, near the top" rather than a scan of forty rows.
     *
     * @return the rule, as a row
     */
    public static JPanel buildDivider() {

        // Capped, because a separator takes whatever height it is offered and the column offers
        // everything left over - without one the rule becomes a gap the height of the window.
        var row = buildCappedRow(new BorderLayout());

        row.add(new JSeparator(SwingConstants.HORIZONTAL), BorderLayout.CENTER);
        row.setBorder(BorderFactory.createEmptyBorder(DIVIDER_PADDING, 0, DIVIDER_PADDING, 0));

        return row;
    }

    /**
     * What a row with a track in it is made of.
     *
     * <p>One value because both shapes below take exactly these four and must go on taking the
     * same four: the shape a row is laid out in is chosen by whatever paired it, and a shape
     * that quietly grew a part the other did not have would make that choice mean something
     * else. It is also what lets a caller build the contents once and lay them out twice,
     * rather than repeat four arguments per shape.
     *
     * @param title    what the control is called
     * @param valueBox the box showing its value, beside the reset
     * @param reset    what to do when the reset is pressed
     * @param track    the slider spanning the full width beneath
     */
    public record RowParts(
        String title,
        JTextField valueBox,
        Runnable reset,
        JSlider track) {
    }

    /**
     * One control's row: its name on the left, its value and reset on the right, and the track
     * below.
     *
     * <p>The name WRAPS rather than being cut off. A label is the only part of a row that has
     * anything to say, and the column is narrow enough that several of these are already too
     * long for one line of it - so the name takes the lines it needs and the row grows, which
     * is the one arrangement where nothing has to be shortened to fit and nothing is lost to
     * the edge.
     *
     * @param row what the row holds
     * @return the row
     */
    public static JPanel layOutLabelledRow(RowParts row) {

        var heading = new JPanel(new BorderLayout());

        heading.add(buildWrappingLabel(row.title()), BorderLayout.CENTER);

        // Held to the top of the side rather than filling it, so the value and the reset stay
        // level with the name's FIRST line instead of drifting to the middle of a name that has
        // grown to three.
        var side = new JPanel(new BorderLayout());

        side.add(buildValueAndReset(row), BorderLayout.NORTH);
        heading.add(side, BorderLayout.EAST);

        return layOutHeadedRow(heading, row.track());
    }

    /**
     * The same row with its name on a line of its own, above the value and the reset.
     *
     * <p>For a row that has half a column rather than a whole one. Beside the value box a name
     * is left about a quarter of the width, which wraps a long one to four lines; over it the
     * name has the whole width and settles in two. Measured on the smoothing knobs, the two
     * shapes come to 104px and 76px in half a column - and the other way round at full width,
     * where a name fits beside the value and the extra line is wasted.
     *
     * <p>So the shape follows the width a row is given, and the only thing that knows which is
     * whatever decided to pair it.
     *
     * @param row what the row holds
     * @return the row
     */
    public static JPanel layOutStackedRow(RowParts row) {

        var heading = new JPanel(new BorderLayout());

        heading.add(buildWrappingLabel(row.title()), BorderLayout.NORTH);
        heading.add(buildValueAndReset(row), BorderLayout.CENTER);

        return layOutHeadedRow(heading, row.track());
    }

    /**
     * Two rows sharing one line.
     *
     * <p>A column of full-width rows spends its height on knobs that do not need it, and the
     * ones that belong together - a radius and the segments it is drawn with, a height and the
     * angle it is judged by - say more side by side than stacked.
     *
     * <p>Takes finished rows rather than building them, so anything the panel can already lay
     * out can be paired without this knowing what it is. Each half keeps its own name, value and
     * reset: the pairing is layout and nothing else, and a shared reset would put two separate
     * knobs back at once.
     *
     * @param left  the row on the left
     * @param right the row on the right
     * @return the line holding both
     */
    public static JPanel layOutPairedRow(JPanel left, JPanel right) {

        var pair = buildCappedRow(new GridLayout(SINGLE_ROW, PAIRED_COLUMNS, PAIR_GUTTER, 0));

        pair.add(left);
        pair.add(right);

        return pair;
    }

    /**
     * The small square button that puts one control back to its default.
     *
     * <p>Every remembered control carries one, in the same place and at the same size, so a
     * reader who has moved something and wants it back does not have to remember what it was.
     * That is the whole reason the defaults are worth naming: a knob nobody can undo is a knob
     * nobody experiments with.
     *
     * @param reset what to do when it is pressed, which is the control's own business
     * @return the button
     */
    public static JButton buildResetButton(Runnable reset) {

        var button = buildMarkButton(RESET_LABEL, RESET_TOOLTIP);

        button.addActionListener(event -> reset.run());

        return button;
    }

    /**
     * A small square button carrying one character.
     *
     * <p>Two things in the panel are this: the reset beside every remembered control, and the
     * fold beside every heading and branch. They mean entirely different things - one is a
     * setting put back, the other is rows going off screen - but they are the same object to
     * look at, and a reader picks them apart by the mark and by where they sit. Built in two
     * places they were free to drift apart in size, in padding, and in whether their marks were
     * legible at all, at which point the panel has two kinds of small button for no reason a
     * reader could name.
     *
     * <p>ASCII only, for the reason the reset's own label is: the viewer runs wherever the JDK's
     * default font does, and a glyph that renders as a box makes the control unreadable rather
     * than merely plain.
     *
     * @param mark    the one character it shows
     * @param tooltip what it says it does
     * @return the button, with nothing wired to it
     */
    public static JButton buildMarkButton(String mark, String tooltip) {

        var button = new JButton(mark);

        button.setToolTipText(tooltip);
        button.setMargin(new Insets(0, 0, 0, 0));
        button.setPreferredSize(new Dimension(MARK_BUTTON_WIDTH, MARK_BUTTON_HEIGHT));

        return button;
    }

    /**
     * @return how wide one of these is, so that a row without one can leave the same gap and
     *         keep its label in line with the rows that have one
     */
    public static int measureMarkWidth() {
        return MARK_BUTTON_WIDTH;
    }

    /**
     * A row that refuses to grow past the height it needs.
     *
     * <p>Every row in a stacked column wants this. A stack hands out whatever height is going,
     * so a row that does not refuse the surplus takes the height of the window and pushes the
     * rows under it off the bottom.
     *
     * <p><b>Answered when asked rather than fixed while building.</b> A cap taken while
     * building freezes a row at a height worked out before it had a width - which is wrong for
     * any row whose name wraps, since how many lines that takes is not settled until there is a
     * width to wrap against. It is also what asks for font metrics during construction, and a
     * machine whose fontconfig cannot be read cannot supply those: the same call, made at build
     * time, is what fails on a runner with no fonts configured.
     *
     * @param layout how the row arranges what is put in it
     * @return the row, empty
     */
    public static JPanel buildCappedRow(LayoutManager layout) {

        return new JPanel(layout) {

            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
    }

    /**
     * A label that wraps instead of running off the end of its column.
     *
     * <p>A {@link javax.swing.JLabel} truncates, which loses the end of the name - and it is the
     * end that carries the units, so a cut label reads as a different knob rather than as a
     * shortened one. A text area laid out to look like a label wraps to whatever width it is
     * given, which is what lets a narrow column hold a long name without anything being reworded
     * to fit.
     *
     * <p>Not editable, not focusable and not painted: everything that makes a text area a text
     * area is turned off, and what is left is a label that wraps.
     *
     * @param text what it says
     * @return the label
     */
    public static JTextArea buildWrappingLabel(String text) {

        var label = new JTextArea(text);

        label.setLineWrap(true);
        label.setWrapStyleWord(true);
        label.setEditable(false);
        label.setFocusable(false);
        label.setOpaque(false);
        label.setBorder(null);

        // Taken from the look a real label has rather than left at a text area's, which is a
        // different font and a different colour - the point is that this reads as a label.
        label.setFont(UIManager.getFont("Label.font"));
        label.setForeground(UIManager.getColor("Label.foreground"));

        return label;
    }

    /**
     * A control with a reset beside it, padded like every other row in the panel.
     *
     * <p>The plainest row there is, and the one most of the panel's controls want: the control
     * says what it is itself - a checkbox carries its own label, a row of them carries several -
     * so there is nothing to put above it.
     *
     * @param body  the control, or several of them already laid out
     * @param reset what to do when the reset is pressed
     * @return the row
     */
    static JPanel layOutControlRow(JComponent body, Runnable reset) {

        var row = new JPanel(new BorderLayout());

        row.add(body, BorderLayout.CENTER);
        row.add(buildResetButton(reset), BorderLayout.EAST);
        row.setBorder(BorderFactory.createEmptyBorder(ROW_PADDING, 0, ROW_PADDING, 0));

        return row;
    }

    /**
     * The same row with a name over it.
     *
     * <p>For the controls that cannot say what they are themselves. A dropdown shows whichever
     * option is picked and a radio row shows the options, and in both cases what a reader needs
     * to know first - what the choice is ABOUT - is nowhere on the control.
     *
     * <p>Above rather than beside, because the control below is already as wide as the column
     * and a name set beside it would take width from the thing it names.
     *
     * @param title what the control is called
     * @param body  the control
     * @param reset what to do when the reset is pressed
     * @return the row
     */
    static JPanel layOutNamedControlRow(String title, JComponent body, Runnable reset) {

        var row = layOutControlRow(body, reset);

        row.add(buildWrappingLabel(title), BorderLayout.NORTH);

        return row;
    }

    // The value box with its reset, which both row shapes carry and neither owns.
    private static JPanel buildValueAndReset(RowParts row) {

        var trailing = new JPanel(new BorderLayout());

        trailing.add(row.valueBox(), BorderLayout.CENTER);
        trailing.add(buildResetButton(row.reset()), BorderLayout.EAST);

        return trailing;
    }

    // A heading over a track, which is the half both row shapes share. A border layout rather
    // than a grid of two equal rows: a grid gives the heading whatever height the TRACK takes,
    // which is what cuts a wrapped name off at one line.
    private static JPanel layOutHeadedRow(JPanel heading, JSlider track) {

        var panel = new JPanel(new BorderLayout());

        panel.add(heading, BorderLayout.NORTH);
        panel.add(track, BorderLayout.CENTER);
        panel.setBorder(BorderFactory.createEmptyBorder(ROW_PADDING, 0, ROW_PADDING, 0));

        return panel;
    }
}
