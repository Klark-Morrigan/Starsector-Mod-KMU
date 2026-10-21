package kmu.desktop.ui.swing;

import kmu.desktop.ui.RememberedValue;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JComponent;
import javax.swing.JPanel;

/**
 * Remembered colours: a swatch you click to open a chooser, one or two to a row.
 *
 * <p>Its own class because a swatch is the one control here that is mostly a button pretending
 * not to be one - it shows its value as its own background rather than as text beside it, which
 * takes a fixed size, a border and a hand-set foreground that none of the other controls need.
 *
 * <p>Built shorter than a labelled row, deliberately, which is the one thing about a swatch
 * that is not obvious from looking at one.
 */
public final class ColourRows {

    private static final int SWATCH_WIDTH = 40;

    // Half the height a labelled row takes. A swatch carries a colour and nothing else, so it
    // only has to be big enough to see and to hit - and a column holding many of them competes
    // with its sliders for height, which at full size pushes the sliders off it.
    private static final int SWATCH_HEIGHT = 11;

    // Tighter than the padding a labelled row takes, for the same reason a swatch is shorter
    // than one.
    private static final int SWATCH_ROW_PADDING = 1;

    private static final String FILL_SUFFIX = " fill";
    private static final String OUTLINE_SUFFIX = " outline";

    private ColourRows() {
    }

    /**
     * One colour on its own line, remembered, with a reset.
     *
     * <p>Separate from the pair because some things are one colour. Handing a pair to something
     * with only one, and pointing both halves at the same value, means whichever swatch is
     * touched last silently wins and the other reads as broken.
     *
     * @param title    what the colour is for
     * @param value    what it remembers and tells
     * @param onChange what to run once it changes
     * @return the row
     */
    public static JPanel buildColour(
            String title,
            RememberedValue<Color> value,
            Runnable onChange) {

        var swatch = buildSwatch(title, value, onChange);

        return layOutSwatchRow(
            title,
            swatch,
            () -> {

                resetSwatch(swatch, value);
                onChange.run();
            });
    }

    /**
     * A fill colour and an outline colour on one line, each remembering its choice, with a
     * reset for the pair.
     *
     * <p>Together rather than as two rows because they are one decision. A translucent body
     * under an opaque edge chosen apart - on separate rows, scrolled between - is how a fill ends
     * up with an outline nothing like it. Side by side they are compared while being picked.
     *
     * @param title    what the pair is for
     * @param fill     the body colour
     * @param edge     the outline colour
     * @param onChange what to run once either changes
     * @return the row
     */
    public static JPanel buildColourPair(
            String title,
            RememberedValue<Color> fill,
            RememberedValue<Color> edge,
            Runnable onChange) {

        var fillSwatch = buildSwatch(title + FILL_SUFFIX, fill, onChange);
        var edgeSwatch = buildSwatch(title + OUTLINE_SUFFIX, edge, onChange);

        var swatches = new JPanel(
            new GridLayout(RowFurniture.SINGLE_ROW, RowFurniture.PAIRED_COLUMNS));

        swatches.add(fillSwatch);
        swatches.add(edgeSwatch);

        return layOutSwatchRow(
            title,
            swatches,
            () -> {

                resetSwatch(fillSwatch, fill);
                resetSwatch(edgeSwatch, edge);

                onChange.run();
            });
    }

    /**
     * A swatch row: its name on the left, its swatches and reset on the right.
     *
     * <p>Beside the name rather than above it, which is where a dropdown's or a radio row's
     * name goes. A swatch is 40 pixels wide and does not want the column, so the two fit on one
     * line - and it is the whole reason a swatch row is half the height of a labelled one.
     *
     * @param title    what the colour or the pair is for
     * @param swatches the swatch, or two of them already laid out side by side
     * @param reset    what to do when the reset is pressed
     * @return the row
     */
    private static JPanel layOutSwatchRow(String title, JComponent swatches, Runnable reset) {

        var trailing = new JPanel(new BorderLayout());

        trailing.add(swatches, BorderLayout.CENTER);
        trailing.add(RowFurniture.buildResetButton(reset), BorderLayout.EAST);

        var row = new JPanel(new BorderLayout());

        row.add(RowFurniture.buildWrappingLabel(title), BorderLayout.CENTER);
        row.add(trailing, BorderLayout.EAST);
        row.setBorder(BorderFactory.createEmptyBorder(
            SWATCH_ROW_PADDING,
            0,
            SWATCH_ROW_PADDING,
            0));

        return row;
    }

    /**
     * The clickable colour itself: a button whose background IS its value.
     *
     * <p>Opened on what was remembered and its owner told before it is returned, for the reason
     * every remembered control does it - a swatch showing one colour while its owner holds
     * another is a control whose first use appears to change the wrong thing.
     *
     * <p>A cancelled chooser leaves everything alone rather than recording a null, which is what
     * makes changing your mind free.
     *
     * @param title    what the colour is for, which also names the chooser
     * @param value    what it remembers and tells
     * @param onChange what to run once it changes
     * @return the swatch
     */
    private static JButton buildSwatch(
            String title,
            RememberedValue<Color> value,
            Runnable onChange) {

        var swatch = new JButton();

        swatch.setPreferredSize(new Dimension(SWATCH_WIDTH, SWATCH_HEIGHT));
        swatch.setBackground(value.applyRemembered());
        swatch.setOpaque(true);
        swatch.setToolTipText(title);

        swatch.addActionListener(event -> {

            var chosen = JColorChooser.showDialog(swatch, title, swatch.getBackground());
            if (chosen == null) {
                return;
            }

            swatch.setBackground(chosen);

            value.recordValue(chosen);
            onChange.run();
        });
        return swatch;
    }

    // One swatch put back to its fallback: the button, the owner and the saved value together,
    // since a pair's reset that moved two swatches but wrote down one would come back
    // half-changed on the next run.
    private static void resetSwatch(JButton swatch, RememberedValue<Color> value) {

        swatch.setBackground(value.fallback());
        value.recordValue(value.fallback());
    }
}
