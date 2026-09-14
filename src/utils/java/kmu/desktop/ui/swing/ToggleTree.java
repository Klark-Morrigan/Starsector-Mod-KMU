package kmu.desktop.ui.swing;

import kmu.desktop.ui.SavedValues;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
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
 *
 * <p><b>Which is exactly why folding reads the indentation and not the roll-ups.</b> A row folds
 * away the run of rows below it that are indented deeper - the outline rule - so what a fold
 * hides is what a reader sees underneath it. Folding by what a roll-up COVERS would hide rows
 * from elsewhere in the block and leave rows sitting under it on screen, because the two are
 * deliberately different sets: "every fill" covers a switch in each branch and owns none of
 * them.
 *
 * <p>So a row's tick and a row's fold answer to different things, and both are honest: the tick
 * says how the switches it names stand, and the fold says whether the rows drawn under it are
 * showing. A block this deep is unreadable at full height otherwise - two dozen rows of which a
 * reader wants three.
 *
 * <p><b>A folded row is not a row switched off.</b> Nothing about the map changes when one
 * folds; the switches underneath go on saying what they said. That is why the fold state is
 * remembered separately from every switch and never written through to the settings.
 *
 * <p>Whether each row is shown is worked out from every fold above it rather than toggled where
 * it was clicked, so a branch unfolded inside a folded parent stays hidden and comes back
 * unfolded when the parent opens.
 */
public final class ToggleTree {

    // Tighter than the padding a slider or a colour row takes. A block of a dozen switches
    // is read as a block, and spacing them like the knobs below would push the last of them
    // off the panel.
    private static final int ROW_PADDING = 2;

    // How far one level of nesting shifts a row, in pixels. Enough to read as a level at a
    // glance without pushing the deepest labels into the panel's scrollbar.
    private static final int INDENT_STEP = 14;

    // What a row's folded state is remembered under, appended to the row's own identity.
    private static final String FOLD_KEY_SUFFIX = "Unfolded";

    // A row at the outermost level, which nothing above can fold away.
    private static final int NO_PARENT = -1;

    // How a paired row divides its line: one row of two equal halves.
    private static final int SINGLE_ROW = 1;
    private static final int PAIRED_HALVES = 2;

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
    public sealed interface Row permits SwitchRow, RollUpRow, PairRow {

        /** @return how many levels in it sits, which is what decides what it folds away */
        int indent();

        /** @return what it is called */
        String title();

        /**
         * Where this row's own folded state is remembered.
         *
         * <p>Off the row's identity rather than its heading, for the reason a section's is: two
         * rows that came to be worded alike would share a key and fold each other.
         *
         * @return the key
         */
        String foldKey();

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
         * Two switches sharing one line, at the given depth.
         *
         * <p>A switch carries one bit and needs one line's height to say so, while a branch of
         * a dozen of them is read as a block - so the ones that pair naturally share a line and
         * the block stays short enough to hold in the eye.
         *
         * @param indent how many levels in they sit
         * @param left   the switch on the left
         * @param right  the switch on the right
         * @return the row
         */
        static Row ofSwitchPair(int indent, Switch left, Switch right) {
            return new PairRow(indent, ofSwitch(indent, left), ofSwitch(indent, right));
        }

        /**
         * Any two rows sharing one line, at the given depth.
         *
         * <p>Rows rather than switches, because the roll-ups pair for the same reason the
         * switches do and there is nothing about a line that cares which it was given. A pair
         * of roll-ups is how the flat block at the foot of a tree stops being six lines saying
         * one word each.
         *
         * @param indent how many levels in they sit, which both halves must agree with
         * @param left   the row on the left
         * @param right  the row on the right
         * @return the row holding both
         */
        static Row ofPair(int indent, Row left, Row right) {
            return new PairRow(indent, left, right);
        }

        /**
         * A roll-up at the given depth.
         *
         * @param indent how many levels in it sits
         * @param name   its identity, which is not its heading - what its folded state is
         *               remembered under, and nothing else: a roll-up stores no state of its own
         * @param title  what it is called
         * @param covers the keys of the switches it rolls up
         * @return the row
         */
        static Row ofRollUp(int indent, String name, String title, String... covers) {
            return new RollUpRow(indent, name, title, List.of(covers));
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

        @Override
        public String foldKey() {
            return toggle.key() + FOLD_KEY_SUFFIX;
        }
    }

    /**
     * A row that is two rows side by side.
     *
     * <p>One ROW, not two: it folds as one, it sits at one depth, and the pairing is a fact
     * about the layout rather than about what was paired. Each half is still whatever it was -
     * a switch saved under its own key, a roll-up reading its own set - so sharing a line
     * reaches nothing about what either means.
     *
     * <p>Folded under the LEFT half's key, since a row has one folded state and a pair has two
     * identities. Nothing sits under a pair today, so which half names the fold decides nothing
     * a reader can see; stated so that it stays decided if one ever gains a branch.
     *
     * <p><b>Both halves must sit at the pair's own depth, and neither may be a pair.</b> The
     * first because the two are one line and a line is at one depth - a half carrying a
     * different one is a statement the layout cannot honour, and silently ignoring it is how a
     * tree comes to be read as saying something it does not. The second because a line is two
     * halves wide and nesting would make it four, at which point the indentation stops meaning
     * anything.
     *
     * @param indent how many levels in they sit
     * @param left   the row on the left
     * @param right  the row on the right
     */
    public record PairRow(int indent, Row left, Row right) implements Row {

        public PairRow {
            if (left.indent() != indent || right.indent() != indent) {
                throw new IllegalArgumentException(
                    "a paired row and both its halves sit at one depth, not "
                        + indent + ", " + left.indent() + " and " + right.indent());
            }
            if (left instanceof PairRow || right instanceof PairRow) {
                throw new IllegalArgumentException("a paired row holds two halves, not four");
            }
        }

        @Override
        public String title() {
            return left.title();
        }

        @Override
        public String foldKey() {
            return left.foldKey();
        }
    }

    /**
     * A row that speaks for several switches at once.
     *
     * @param indent how many levels in it sits
     * @param name   its identity, used for nothing but remembering whether it is folded
     * @param title  what it is called
     * @param covers the keys of the switches it rolls up
     */
    public record RollUpRow(int indent, String name, String title, List<String> covers)
        implements Row {

        @Override
        public String foldKey() {
            return name + FOLD_KEY_SUFFIX;
        }
    }

    /**
     * Builds the block.
     *
     * @param onChange what to run once anything in it changes
     * @param rows     the rows, top to bottom
     * @return the block
     */
    public static JPanel buildToggleTree(Runnable onChange, Row... rows) {

        // Stacked rather than gridded, because a grid keeps a cell for a hidden row and a
        // folded branch would leave its gap behind on screen.
        var block = new JPanel();

        block.setLayout(new BoxLayout(block, BoxLayout.Y_AXIS));

        var saved = SavedValues.findSavedValues();

        // Every switch by key, so a roll-up can find the ones it covers without holding them
        // and so the redraw below can walk all of them once.
        var switches = new LinkedHashMap<String, JCheckBox>();
        var rollUps = new ArrayList<RollUp>();

        // Deferred, because a roll-up has to be able to redraw switches that are built after
        // it and switches have to be able to redraw roll-ups built after them.
        var redraw = new Runnable[1];

        var panels = new JPanel[rows.length];
        var folds = new JButton[rows.length];
        var isUnfolded = new boolean[rows.length];
        var refold = new Runnable[1];

        for (var index = 0; index < rows.length; index++) {

            var row = rows[index];
            var box = buildRowBox(row, rows, switches, rollUps, saved, redraw, onChange);

            isUnfolded[index] = !hasRowsUnder(rows, index)
                || saved.getBoolean(row.foldKey(), true);

            folds[index] = hasRowsUnder(rows, index)
                ? buildRowFold(row, index, isUnfolded, refold, saved)
                : null;

            panels[index] = layOutRow(box, row.indent(), folds[index]);

            block.add(panels[index]);
        }

        var parentOf = mapRowsToParents(rows);

        refold[0] = () -> {

            for (var index = 0; index < rows.length; index++) {

                panels[index].setVisible(isRowShown(index, parentOf, isUnfolded));

                if (folds[index] != null) {
                    FoldControls.showFoldState(folds[index], isUnfolded[index]);
                }
            }
            block.revalidate();
            block.repaint();
        };

        redraw[0] = () -> {
            for (var rollUp : rollUps) {
                rollUp.box().setState(readState(switches, rollUp.covers()));
            }
        };

        applyAll(switches, rows, saved);
        redraw[0].run();
        refold[0].run();

        return block;
    }

    // One row's control, wired to write through and redraw the roll-ups whenever it moves.
    //
    // A Component rather than a checkbox, because a paired row is two of them in a panel. What
    // the layout below needs is something to put in the row, and it has never needed to know
    // which kind it was handed.
    private static Component buildRowBox(
            Row row,
            Row[] rows,
            Map<String, JCheckBox> switches,
            List<RollUp> rollUps,
            SavedValues saved,
            Runnable[] redraw,
            Runnable onChange) {

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

            return box;
        }

        if (row instanceof PairRow pair) {

            var line = new JPanel(new GridLayout(SINGLE_ROW, PAIRED_HALVES));

            // Each half through this same method, so a half behaves exactly as it would on a
            // line of its own - which is the whole claim a pair makes.
            line.add(buildRowBox(pair.left(), rows, switches, rollUps, saved, redraw, onChange));
            line.add(buildRowBox(pair.right(), rows, switches, rollUps, saved, redraw, onChange));

            // Transparent, so the pair reads as two controls on a line rather than as a panel
            // laid over the block behind them.
            line.setOpaque(false);

            return line;
        }

        var toggle = ((SwitchRow) row).toggle();
        var box = new JCheckBox(
            toggle.title(), saved.getBoolean(toggle.key(), toggle.fallback()));

        switches.put(toggle.key(), box);

        box.addActionListener(event -> {

            applyAll(switches, rows, saved);
            redraw[0].run();
            onChange.run();
        });

        return box;
    }

    // The fold beside one branch row. It records the new state and asks for the whole block to
    // be laid out again rather than hiding the rows under it itself, because what a row shows
    // depends on every fold above it as well as on this one.
    private static JButton buildRowFold(
            Row row,
            int index,
            boolean[] isUnfolded,
            Runnable[] refold,
            SavedValues saved) {

        var fold = FoldControls.buildFoldButton(isUnfolded[index]);

        fold.addActionListener(event -> {

            isUnfolded[index] = !isUnfolded[index];

            saved.putBoolean(row.foldKey(), isUnfolded[index]);

            refold[0].run();
        });

        return fold;
    }

    // Whether anything is drawn beneath a row as belonging to it, which is what makes it a
    // branch. The next row alone decides it: rows are in reading order, so a deeper row further
    // down belongs to whatever branch sits directly above IT.
    private static boolean hasRowsUnder(Row[] rows, int index) {

        return index + 1 < rows.length && rows[index + 1].indent() > rows[index].indent();
    }

    // Which row each row is folded away by: the nearest row above it that is shallower.
    //
    // Always a branch, since the row directly under it is deeper by construction - so a chain
    // of these is exactly the set of folds that can hide a row.
    private static int[] mapRowsToParents(Row[] rows) {

        var parentOf = new int[rows.length];

        for (var index = 0; index < rows.length; index++) {

            parentOf[index] = NO_PARENT;

            for (var above = index - 1; above >= 0; above--) {

                if (rows[above].indent() < rows[index].indent()) {
                    parentOf[index] = above;
                    break;
                }
            }
        }
        return parentOf;
    }

    // A row shows only where every fold above it is open. Walked up the chain rather than read
    // off its own parent, so that folding a branch hides the whole subtree under it and not
    // merely its children.
    private static boolean isRowShown(int index, int[] parentOf, boolean[] isUnfolded) {

        for (var above = parentOf[index]; above != NO_PARENT; above = parentOf[above]) {

            if (!isUnfolded[above]) {
                return false;
            }
        }
        return true;
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

            applyRow(row, switches, saved);
        }
    }

    // One row told what it now stands at. A pair is walked through to its halves, so a switch
    // sharing a line is applied exactly as one on a line of its own; a roll-up holds no state
    // and there is nothing to apply.
    private static void applyRow(
            Row row,
            Map<String, JCheckBox> switches,
            SavedValues saved) {

        if (row instanceof SwitchRow switchRow) {
            applyOne(switchRow.toggle(), switches, saved);

        } else if (row instanceof PairRow pair) {

            applyRow(pair.left(), switches, saved);
            applyRow(pair.right(), switches, saved);
        }
    }

    // One switch told what it now stands at, and written down at the same moment. The two
    // together because a state applied without being saved is a knob that forgets on restart,
    // and one saved without being applied is a map drawn against a setting nobody holds.
    private static void applyOne(
            Switch toggle,
            Map<String, JCheckBox> switches,
            SavedValues saved) {

        var state = switches.get(toggle.key()).isSelected();

        toggle.apply().accept(state);
        saved.putBoolean(toggle.key(), state);
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

    // One row: its fold where it has one, its control, and the indentation that says how deep
    // it sits.
    //
    // The fold's width is left in front of a row that has none, so that every control in the
    // block lines up whatever else is beside it. Without it a leaf's label sits where a
    // branch's fold does, and the levels stop reading as levels.
    private static JPanel layOutRow(Component box, int indent, JButton fold) {

        var row = new JPanel(new BorderLayout()) {

            // Stacked rather than gridded, and a stack hands out whatever height is going - so
            // a row that did not cap itself would grow to fill the panel.
            //
            // Answered when asked rather than fixed while building, because the height a row
            // needs is not settled until it has been given a width: a name that wraps is two
            // lines at one width and three at another, and a cap taken at build time would hold
            // the row at whatever the first guess was and cut the rest off.
            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };

        var lead = new JPanel(new BorderLayout()) {

            // The gutter that keeps every row's label in line whether or not it has a fold. As
            // tall as whatever it stands beside, asked for at the same moment and for the same
            // reason as the cap above.
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(FoldControls.measureFoldWidth(), box.getPreferredSize().height);
            }
        };

        if (fold != null) {
            lead.add(fold, BorderLayout.CENTER);
        }

        row.add(lead, BorderLayout.WEST);
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
