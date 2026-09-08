package kmu.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.UIManager;

/**
 * A block of related toggles with roll-ups over them.
 *
 * <p>Six switches over the void is more than a reader can hold in their head as six switches.
 * What they actually want to say is usually coarser - "show me the inland void", "take every
 * name off", "hide all of it" - so the coarse statements get controls of their own, and the six
 * remain underneath for when the question really is that specific.
 *
 * <p><b>A roll-up is a view, not a setting.</b> Nothing is stored for one: it reads the switches
 * it covers and shows all, none, or some, and setting it sets every switch it covers. That is
 * what keeps the two directions in step without a rule about which one wins - there is only one
 * copy of the state, and the roll-ups are drawn from it after every change, wherever the change
 * came from.
 *
 * <p>Which is why the roll-ups need a third look. A control that covers six switches and can
 * only be drawn on or off has to lie about five of the eight ways they can stand, and a reader
 * who unticks one leaf and sees its parent stay ticked stops believing the parent. So a roll-up
 * with a mixed set under it is drawn as a filled square rather than a tick.
 *
 * <p>The roll-ups do not have to nest. "Pocket borders" covers the bridges and the coastline,
 * which sit in different branches: the two are the same kind of thing seen in two places, and a
 * reader comparing walls against fills wants them together whatever else they belong to. So a
 * roll-up names the switches it covers rather than owning a subtree, and the indentation is
 * layout rather than structure.
 */
public final class ToggleTree {

    // Tighter than the padding a slider or a colour row takes. A block of a dozen switches
    // is read as a block, and spacing them like the knobs below would push the last of them
    // off the panel.
    private static final int ROW_PADDING = 2;

    // How far one level of nesting shifts a row, in pixels. Enough to read as a level at a
    // glance without pushing the deepest labels into the panel's scrollbar.
    private static final int INDENT_STEP = 14;

    // The rows are laid one per line, so the grid is one column of however many rows there
    // are - named because a bare 1 in a GridLayout says nothing about which axis it fixes.
    private static final int SINGLE_COLUMN = 1;

    private ToggleTree() {
    }

    /**
     * One switch: a real setting, stored and restored.
     *
     * @param key      what to remember it under
     * @param title    what it is called
     * @param fallback the state to start in when nothing was remembered
     * @param apply    records the new state
     */
    public record Switch(
        String key,
        String title,
        boolean fallback,
        Consumer<Boolean> apply) {
    }

    /**
     * One row of the block.
     *
     * <p>Sealed over the two kinds rather than one shape with a field left null for whichever
     * kind does not use it: a row is a switch or a roll-up, never both and never neither, and
     * this is the statement of that. The layout treats them alike, which is why they share a
     * list; nothing else about them is alike, and a reader taking a row apart is made to say
     * which one it has.
     */
    public sealed interface Row permits SwitchRow, RollUpRow {

        /** @return how many levels in it sits, for layout only */
        int indent();

        /** @return what it is called */
        String title();

        /**
         * A switch at the given depth.
         *
         * @param indent how many levels in it sits
         * @param toggle the switch
         * @return the row
         */
        static Row ofSwitch(int indent, Switch toggle) {
            return new SwitchRow(indent, toggle);
        }

        /**
         * A roll-up at the given depth.
         *
         * @param indent how many levels in it sits
         * @param title  what it is called
         * @param covers the keys of the switches it rolls up
         * @return the row
         */
        static Row ofRollUp(int indent, String title, String... covers) {
            return new RollUpRow(indent, title, List.of(covers));
        }
    }

    /**
     * A row that is one switch.
     *
     * @param indent how many levels in it sits
     * @param toggle the switch it stands for
     */
    public record SwitchRow(int indent, Switch toggle) implements Row {

        @Override
        public String title() {
            return toggle.title();
        }
    }

    /**
     * A row that speaks for several switches at once.
     *
     * @param indent how many levels in it sits
     * @param title  what it is called
     * @param covers the keys of the switches it rolls up
     */
    public record RollUpRow(int indent, String title, List<String> covers) implements Row {
    }

    /**
     * Builds the block.
     *
     * @param onChange what to run once anything in it changes
     * @param rows     the rows, top to bottom
     * @return the block
     */
    public static JPanel buildToggleTree(Runnable onChange, Row... rows) {

        var block = new JPanel(new GridLayout(rows.length, SINGLE_COLUMN));
        var saved = SavedValues.findSavedValues();

        // Every switch by key, so a roll-up can find the ones it covers without holding them
        // and so the redraw below can walk all of them once.
        var switches = new LinkedHashMap<String, JCheckBox>();
        var rollUps = new ArrayList<RollUp>();

        // Deferred, because a roll-up has to be able to redraw switches that are built after
        // it and switches have to be able to redraw roll-ups built after them.
        var redraw = new Runnable[1];

        for (var row : rows) {

            if (row instanceof RollUpRow rollUp) {

                var box = new TriStateBox(rollUp.title());

                rollUps.add(new RollUp(box, rollUp.covers()));

                box.addActionListener(event -> {

                    var turningOn = box.getState() != TriState.ALL;

                    for (var key : rollUp.covers()) {
                        switches.get(key).setSelected(turningOn);
                    }
                    applyAll(switches, rows, saved);
                    redraw[0].run();
                    onChange.run();
                });

                block.add(layOutRow(box, row.indent()));

            } else if (row instanceof SwitchRow switchRow) {

                var toggle = switchRow.toggle();
                var box = new JCheckBox(
                    toggle.title(), saved.getBoolean(toggle.key(), toggle.fallback()));

                switches.put(toggle.key(), box);

                box.addActionListener(event -> {

                    applyAll(switches, rows, saved);
                    redraw[0].run();
                    onChange.run();
                });

                block.add(layOutRow(box, row.indent()));
            }
        }

        redraw[0] = () -> {
            for (var rollUp : rollUps) {
                rollUp.box().setState(readState(switches, rollUp.covers()));
            }
        };

        applyAll(switches, rows, saved);
        redraw[0].run();

        return block;
    }

    // One roll-up: the control, and which switches it speaks for.
    private record RollUp(TriStateBox box, List<String> covers) {
    }

    // Writes every switch's state through to the settings and to the saved values at once.
    //
    // All of them on every change rather than the one that moved, because a roll-up moves
    // several at a time and a reader cannot tell which - and doing it wholesale means the
    // settings always hold exactly what the boxes show, whichever control was touched.
    private static void applyAll(
            Map<String, JCheckBox> switches,
            Row[] rows,
            SavedValues saved) {

        for (var row : rows) {

            if (row instanceof SwitchRow switchRow) {

                var toggle = switchRow.toggle();
                var state = switches.get(toggle.key()).isSelected();

                toggle.apply().accept(state);
                saved.putBoolean(toggle.key(), state);
            }
        }
    }

    private static TriState readState(Map<String, JCheckBox> switches, List<String> covers) {

        var on = 0;

        for (var key : covers) {

            if (switches.get(key).isSelected()) {
                on++;
            }
        }

        if (on == 0) {
            return TriState.NONE;
        }
        return on == covers.size() ? TriState.ALL : TriState.SOME;
    }

    private static JPanel layOutRow(Component box, int indent) {

        var row = new JPanel(new BorderLayout());

        row.add(box, BorderLayout.CENTER);
        row.setBorder(BorderFactory.createEmptyBorder(
            ROW_PADDING, indent * INDENT_STEP, ROW_PADDING, 0));

        return row;
    }

    /** How a roll-up's switches stand between them. */
    private enum TriState {
        NONE, SOME, ALL
    }

    // A checkbox with a third look for "some of them".
    //
    // Swing has no such control, and the usual workaround - leaving it ticked and hoping the
    // reader notices a tooltip - is the lie this exists to avoid. So the box paints its own
    // marker: the look-and-feel's own icon for the two settled states, so it sits beside the
    // ordinary checkboxes without looking foreign, and a filled square for the third.
    private static final class TriStateBox extends JCheckBox {

        // How far inside the box the partial marker is drawn. Enough that it reads as a
        // filled square within the frame rather than as a box that has been shaded in.
        private static final int MARK_INSET = 4;

        private TriState state = TriState.NONE;

        private TriStateBox(String title) {

            super(title);

            setIcon(new Icon() {

                @Override
                public void paintIcon(Component host, Graphics g, int x, int y) {

                    var settled = UIManager.getIcon("CheckBox.icon");

                    if (settled != null) {
                        settled.paintIcon(host, g, x, y);
                    }

                    if (state != TriState.SOME) {
                        return;
                    }

                    g.setColor(host.getForeground());
                    g.fillRect(
                        x + MARK_INSET,
                        y + MARK_INSET,
                        getIconWidth() - 2 * MARK_INSET,
                        getIconHeight() - 2 * MARK_INSET);
                }

                @Override
                public int getIconWidth() {

                    var settled = UIManager.getIcon("CheckBox.icon");
                    return settled == null ? 0 : settled.getIconWidth();
                }

                @Override
                public int getIconHeight() {

                    var settled = UIManager.getIcon("CheckBox.icon");
                    return settled == null ? 0 : settled.getIconHeight();
                }
            });
        }

        private TriState getState() {
            return state;
        }

        // The tick is the ALL state and nothing else: a partly-set roll-up shows its own
        // marker over an unticked box, so the tick keeps meaning "all of them".
        private void setState(TriState showing) {

            state = showing;

            setSelected(showing == TriState.ALL);
            repaint();
        }
    }
}
