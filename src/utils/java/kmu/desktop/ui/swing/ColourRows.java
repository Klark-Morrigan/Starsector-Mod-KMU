package kmu.desktop.ui.swing;

import kmu.desktop.ui.SavedValues;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JComponent;
import javax.swing.JPanel;

/**
 * The viewer's colour knobs: a swatch you click to open a chooser, one or two to a row.
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
    // only has to be big enough to see and to hit - and there are fifteen of these rows
    // competing with the sliders for the panel, which at full height push the sliders off it.
    private static final int SWATCH_HEIGHT = 11;

    // Tighter than the padding a labelled row takes, for the same reason a swatch is shorter
    // than one: there are fifteen of these competing with the sliders for the panel.
    private static final int SWATCH_ROW_PADDING = 1;

    private ColourRows() {
    }

    /**
     * One colour a reader can choose: where it starts, and where the choice goes.
     *
     * <p>Paired because a fallback with no home is a colour nothing reads, and a setter with no
     * fallback is a swatch that cannot be reset. Passed apart, the two of them were four of a
     * method's seven parameters, in an order only the compiler was checking - and both halves
     * of a PAIR of colours have the same two types, so swapping a fill's setter for an edge's
     * would have compiled cleanly and painted the wrong thing.
     *
     * @param fallback the colour to start at and to reset to
     * @param apply    records the new colour
     */
    public record Choice(
        Color fallback,
        Consumer<Color> apply) {
    }

    /**
     * One colour on its own line, remembered, with a reset.
     *
     * <p>Separate from the pair because some things are one colour. Handing a pair to
     * something with only one, and pointing both halves at the same field, means whichever
     * swatch is touched last silently wins and the other reads as broken.
     *
     * @param key      what to remember it under
     * @param title    what the colour is for
     * @param fallback the colour to start at and to reset to
     * @param apply    records the new colour
     * @param onChange what to run once it changes
     * @return the row
     */
    public static JPanel buildColour(
            String key,
            String title,
            Color fallback,
            Consumer<Color> apply,
            Runnable onChange) {

        var swatch = buildSwatch(key, title, fallback, apply, onChange);

        return layOutSwatchRow(
            title,
            swatch,
            () -> {

                resetSwatch(swatch, key, fallback, apply);
                onChange.run();
            });
    }

    /**
     * A fill colour and an outline colour on one line, each remembering its choice, with a
     * reset for the pair.
     *
     * <p>Together rather than as two rows because they are one decision. A layer is drawn as
     * a translucent body under an opaque edge, and choosing those apart - on separate rows,
     * with the panel scrolled between them - is how a fill ends up with an outline nothing
     * like it. Side by side they are compared while being picked.
     *
     * @param key      what to remember them under; each takes its own suffix
     * @param title    what the pair is for
     * @param fill     the body colour
     * @param edge     the outline colour
     * @param onChange what to run once either changes
     * @return the row
     */
    public static JPanel buildColourPair(
            String key,
            String title,
            Choice fill,
            Choice edge,
            Runnable onChange) {

        var fillSwatch = buildSwatch(
            key + " fill",
            title + " fill",
            fill.fallback(),
            fill.apply(),
            onChange);

        var edgeSwatch = buildSwatch(
            key + " outline",
            title + " outline",
            edge.fallback(),
            edge.apply(),
            onChange);

        var swatches = new JPanel(
            new GridLayout(ControlRows.SINGLE_ROW, ControlRows.PAIRED_COLUMNS));

        swatches.add(fillSwatch);
        swatches.add(edgeSwatch);

        return layOutSwatchRow(
            title,
            swatches,
            () -> {

                resetSwatch(fillSwatch, key + " fill", fill.fallback(), fill.apply());
                resetSwatch(edgeSwatch, key + " outline", edge.fallback(), edge.apply());

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
        trailing.add(ControlRows.buildResetButton(reset), BorderLayout.EAST);

        var row = new JPanel(new BorderLayout());

        row.add(ControlRows.buildWrappingLabel(title), BorderLayout.CENTER);
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
     * every remembered control does it - a swatch showing one colour while the map is drawn in
     * another is a control whose first use appears to change the wrong thing.
     *
     * <p>A cancelled chooser leaves everything alone rather than recording a null, which is what
     * makes changing your mind free.
     *
     * @param key      what to remember it under
     * @param title    what the colour is for, which also names the chooser
     * @param fallback the colour to start at
     * @param apply    records the new colour
     * @param onChange what to run once it changes
     * @return the swatch
     */
    private static JButton buildSwatch(
            String key,
            String title,
            Color fallback,
            Consumer<Color> apply,
            Runnable onChange) {

        var saved = new Color(
            SavedValues.findSavedValues().getInt(key, fallback.getRGB()),
            true);

        var swatch = new JButton();

        swatch.setPreferredSize(new Dimension(SWATCH_WIDTH, SWATCH_HEIGHT));
        swatch.setBackground(saved);
        swatch.setOpaque(true);
        swatch.setToolTipText(title);

        apply.accept(saved);

        swatch.addActionListener(event -> {

            var chosen = JColorChooser.showDialog(swatch, title, swatch.getBackground());

            if (chosen == null) {
                return;
            }

            swatch.setBackground(chosen);
            apply.accept(chosen);

            SavedValues.findSavedValues().putInt(key, chosen.getRGB());

            onChange.run();
        });
        return swatch;
    }

    /**
     * One swatch put back to its default: the button, the owner and the saved value together.
     *
     * <p>All three because a pair's reset has to do it twice, and a reset that moved two swatches
     * but wrote down one would come back half-changed on the next run.
     *
     * @param swatch   the swatch to repaint
     * @param key      what it is remembered under
     * @param fallback the colour to go back to
     * @param apply    records the colour
     */
    private static void resetSwatch(
            JButton swatch,
            String key,
            Color fallback,
            Consumer<Color> apply) {

        swatch.setBackground(fallback);
        apply.accept(fallback);

        SavedValues.findSavedValues().putInt(key, fallback.getRGB());
    }
}
