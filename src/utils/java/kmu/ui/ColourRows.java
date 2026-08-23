package kmu.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JLabel;
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

    private static final int SWATCH_ROW_PADDING = 1;

    // A swatch row carries a label and one or two swatches side by side, so the grid it is
    // laid in is a single row of however many there are.
    private static final int SINGLE_ROW = 1;

    private static final int SWATCH_ROW_COLUMNS = 2;

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
        var trailing = new JPanel(new BorderLayout());

        trailing.add(swatch, BorderLayout.CENTER);
        trailing.add(ControlRows.buildResetButton(
            () -> {

                resetSwatch(swatch, key, fallback, apply);
                onChange.run();

            }),
            BorderLayout.EAST);

        var row = new JPanel(new BorderLayout());

        row.add(new JLabel(title), BorderLayout.CENTER);
        row.add(trailing, BorderLayout.EAST);
        row.setBorder(BorderFactory.createEmptyBorder(
            SWATCH_ROW_PADDING,
            0,
            SWATCH_ROW_PADDING,
            0));

        return row;
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

        var swatches = new JPanel(new GridLayout(SINGLE_ROW, SWATCH_ROW_COLUMNS));

        swatches.add(fillSwatch);
        swatches.add(edgeSwatch);

        var trailing = new JPanel(new BorderLayout());

        trailing.add(swatches, BorderLayout.CENTER);
        trailing.add(ControlRows.buildResetButton(
            () -> {

                resetSwatch(fillSwatch, key + " fill", fill.fallback(), fill.apply());
                resetSwatch(edgeSwatch, key + " outline", edge.fallback(), edge.apply());

                onChange.run();

            }),
            BorderLayout.EAST);

        var row = new JPanel(new BorderLayout());

        row.add(new JLabel(title), BorderLayout.CENTER);
        row.add(trailing, BorderLayout.EAST);
        row.setBorder(BorderFactory.createEmptyBorder(
            SWATCH_ROW_PADDING,
            0,
            SWATCH_ROW_PADDING,
            0));

        return row;
    }

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
