package kmu.desktop.ui.swing;

import kmu.desktop.ui.RememberedValue;
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
 * <p>A dozen switches is more than a reader can hold in their head as a dozen switches. What
 * they actually want to say is usually coarser - "show me this branch", "take every name off",
 * "hide all of it" - so the coarse statements get controls of their own, and the switches remain
 * underneath for when the question really is that specific.
 *
 * <p><b>A roll-up is a view, not a setting.</b> Nothing is stored for one: it reads the switches
 * it covers and shows all, none, or some, and setting it sets every switch it covers. That is
 * what keeps the two directions in step without a rule about which one wins - there is only one
 * copy of the state, and the roll-ups are drawn from it after every change, wherever the change
 * came from.
 *
 * <p>Which is why the roll-ups need a third look. A control that covers six switches and can
 * only be drawn on or off has to lie about most of the ways they can stand, and a reader who
 * unticks one leaf and sees its parent stay ticked stops believing the parent. So a roll-up
 * with a mixed set under it is drawn as a filled square rather than a tick.
 *
 * <p>The roll-ups do not have to nest. A roll-up can cover switches that sit in different
 * branches - the same kind of thing seen in two places - so it names the switches it covers
 * rather than owning a subtree, and the indentation is layout rather than structure.
 *
 * <p><b>Which is exactly why folding reads the indentation and not the roll-ups.</b> A row folds
 * away the run of rows below it that are indented deeper - the outline rule - so what a fold
 * hides is what a reader sees underneath it. Folding by what a roll-up COVERS would hide rows
 * from elsewhere in the block and leave rows sitting under it on screen, because the two are
 * deliberately different sets.
 *
 * <p><b>A folded row is not a row switched off.</b> The switches underneath go on saying what
 * they said. That is why the fold state is remembered separately from every switch and never
 * handed to an owner.
 *
 * <p>Whether each row is shown is worked out from every fold above it rather than toggled where
 * it was clicked, so a branch unfolded inside a folded parent stays hidden and comes back
 * unfolded when the parent opens.
 */
public final class ToggleTree {

    // Tighter than the padding a slider or a colour row takes. A block of a dozen switches
    // is read as a block, and spacing them like the controls below would push the last of them
    // out of sight.
    private static final int ROW_PADDING = 2;

    // How far one level of nesting shifts a row, in pixels. Enough to read as a level at a
    // glance without pushing the deepest labels into the column's scrollbar.
    private static final int INDENT_STEP = 14;

    // What a row's folded state is remembered under, appended to the row's own identity.
    private static final String FOLD_KEY_SUFFIX = "Unfolded";

    // A row at the outermost level, which nothing above can fold away.
    private static final int NO_PARENT = -1;

    private ToggleTree() {
    }

    /**
     * One switch: a real setting, stored and restored.
     *
     * @param title what it is called
     * @param value what it remembers and tells
     */
    public record Switch(
        String title,
        RememberedValue<Boolean> value) {

        /** @return what it is remembered under, which is what a roll-up names it by */
        public String key() {
            return value.key();
        }
    }

    /**
     * One row of the block.
     *
     * <p>Sealed over the kinds rather than one shape with a field left null for whichever kind
     * does not use it: a row is a switch, a roll-up or a pair, and this is the statement of that.
     * The layout treats them alike, which is why they share a list.
     */
    public sealed interface Row
            permits SwitchRow, RollUpRow, PairRow {

        /** @return how many levels in it sits, which is what decides what it folds away */
        int indent();

        /** @return what it is called */
        String title();

        /**
         * Where this row's own folded state is remembered.
         *
         * <p>Off the row's identity rather than its heading: two rows that came to be worded
         * alike would otherwise share a key and fold each other.
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
         * <p>Rows rather than switches, because roll-ups pair for the same reason switches do
         * and there is nothing about a line that cares which it was given.
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
    public record SwitchRow(
        int indent,
        Switch toggle)
            implements Row {

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
     * a switch saved under its own key, a roll-up reading its own set.
     *
     * <p>Folded under the LEFT half's key, since a row has one folded state and a pair has two
     * identities.
     *
     * <p><b>Both halves must sit at the pair's own depth, and neither may be a pair.</b> The
     * first because a line is at one depth, and a half carrying a different one is a statement
     * the layout cannot honour. The second because a line is two halves wide and nesting would
     * make it four, at which point the indentation stops meaning anything.
     *
     * @param indent how many levels in they sit
     * @param left   the row on the left
     * @param right  the row on the right
     */
    public record PairRow(
        int indent,
        Row left,
        Row right)
            implements Row {

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
    public record RollUpRow(
        int indent,
        String name,
        String title,
        List<String> covers)
            implements Row {

        @Override
        public String foldKey() {
            return name + FOLD_KEY_SUFFIX;
        }
    }

    /**
     * Builds the block.
     *
     * @param onChange what to run once a click has changed anything in it
     * @param rows     the rows, top to bottom
     * @return the block
     */
    public static JPanel buildToggleTree(Runnable onChange, Row... rows) {
        return new TreeBuild(onChange, rows).buildBlock();
    }

    // Whether anything is drawn beneath a row as belonging to it, which is what makes it a
    // branch. The next row alone decides it: rows are in reading order, so a deeper row further
    // down belongs to whatever branch sits directly above IT.
    private static boolean hasRowsUnder(Row[] rows, int index) {

        return index + 1 < rows.length
            && rows[index + 1].indent() > rows[index].indent();
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

    // One row: its fold where it has one, its control, and the indentation that says how deep
    // it sits.
    //
    // The fold's width is left in front of a row that has none, so that every control in the
    // block lines up whatever else is beside it. Without it a leaf's label sits where a
    // branch's fold does, and the levels stop reading as levels.
    private static JPanel layOutRow(Component box, int indent, JButton fold) {

        var row = RowFurniture.buildCappedRow(new BorderLayout());
        var lead = new JPanel(new BorderLayout()) {

            // The gutter that keeps every row's label in line whether or not it has a fold. As
            // tall as whatever it stands beside, and asked for when the row is laid out rather
            // than while it is built, for the reason the cap on the row itself is.
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
            ROW_PADDING,
            indent * INDENT_STEP, ROW_PADDING,
            0));

        return row;
    }

    /** How a roll-up's switches stand between them. */
    private enum TriState {

        NONE,
        SOME,
        ALL
    }

    // One roll-up: the control, and which switches it speaks for.
    private record RollUp(
        TriStateBox box,
        List<String> covers) {
    }

    // One switch: the box drawn for it, and the value it remembers.
    private record SwitchBox(
        JCheckBox box,
        RememberedValue<Boolean> value) {
    }

    // One build of one block, holding what its rows need of each other while they are wired.
    //
    // An instance per build rather than statics passing arrays about, because the rows refer to
    // each other in both directions: a roll-up redraws switches built after it, and a switch
    // redraws roll-ups built after it. Held here, the wiring reads that state directly.
    private static final class TreeBuild {

        private final Runnable onChange;

        private final Row[] rows;

        private final SavedValues saved = SavedValues.findSavedValues();

        private final JPanel block = new JPanel();

        // Every switch by key, so a roll-up can find the ones it covers without holding them.
        private final Map<String, SwitchBox> switchesByKey = new LinkedHashMap<>();

        private final List<RollUp> rollUps = new ArrayList<>();

        private final JPanel[] panels;

        private final JButton[] folds;

        private final boolean[] isUnfolded;

        private final int[] parentOf;

        private TreeBuild(Runnable onChange, Row[] rows) {

            this.onChange = onChange;
            this.rows = rows;
            this.panels = new JPanel[rows.length];
            this.folds = new JButton[rows.length];
            this.isUnfolded = new boolean[rows.length];
            this.parentOf = mapRowsToParents(rows);
        }

        private JPanel buildBlock() {

            // Stacked rather than gridded, because a grid keeps a cell for a hidden row and a
            // folded branch would leave its gap behind on screen.
            block.setLayout(new BoxLayout(block, BoxLayout.Y_AXIS));

            for (var index = 0; index < rows.length; index++) {

                var row = rows[index];
                var box = buildRowBox(row);
                var isBranch = hasRowsUnder(rows, index);

                isUnfolded[index] = !isBranch || saved.getBoolean(row.foldKey(), true);
                folds[index] = isBranch ? buildRowFold(row, index) : null;
                panels[index] = layOutRow(box, row.indent(), folds[index]);

                block.add(panels[index]);
            }

            redrawRollUps();
            showFoldedRows();

            return block;
        }

        // One row's control, wired to write through and redraw the roll-ups whenever it moves.
        //
        // A Component rather than a checkbox, because a paired row is two of them in a panel.
        private Component buildRowBox(Row row) {

            if (row instanceof RollUpRow rollUp) {
                return buildRollUpBox(rollUp);
            }

            if (row instanceof PairRow pair) {

                var line = new JPanel(
                    new GridLayout(RowFurniture.SINGLE_ROW, RowFurniture.PAIRED_COLUMNS));

                // Each half through this same method, so a half behaves exactly as it would on a
                // line of its own - which is the whole claim a pair makes.
                line.add(buildRowBox(pair.left()));
                line.add(buildRowBox(pair.right()));

                // Transparent, so the pair reads as two controls on a line rather than as a
                // panel laid over the block behind them.
                line.setOpaque(false);

                return line;
            }

            return buildSwitchBox(((SwitchRow) row).toggle());
        }

        private Component buildRollUpBox(RollUpRow rollUp) {

            var box = new TriStateBox(rollUp.title());

            rollUps.add(new RollUp(box, rollUp.covers()));

            box.addActionListener(event -> {

                var isTurningOn = box.getState() != TriState.ALL;

                for (var key : rollUp.covers()) {
                    switchesByKey.get(key).box().setSelected(isTurningOn);
                }
                recordChange();
            });

            return box;
        }

        // Opened on what was remembered, with its owner told before anything draws.
        private Component buildSwitchBox(Switch toggle) {

            var box = new JCheckBox(toggle.title(), toggle.value().applyRemembered());

            switchesByKey.put(toggle.key(), new SwitchBox(box, toggle.value()));

            box.addActionListener(event -> recordChange());

            return box;
        }

        // Every switch's state told and written down at once, then the roll-ups redrawn.
        //
        // All of them rather than the one that moved, because a roll-up moves several at a time
        // and a reader cannot tell which - and doing it wholesale means the owners always hold
        // exactly what the boxes show, whichever control was touched.
        private void recordChange() {

            for (var entry : switchesByKey.values()) {
                entry.value().recordValue(entry.box().isSelected());
            }

            redrawRollUps();
            onChange.run();
        }

        private void redrawRollUps() {

            for (var rollUp : rollUps) {
                rollUp.box().setState(readState(rollUp.covers()));
            }
        }

        private TriState readState(List<String> covers) {

            var onCount = 0;

            for (var key : covers) {

                if (switchesByKey.get(key).box().isSelected()) {
                    onCount++;
                }
            }

            if (onCount == 0) {
                return TriState.NONE;
            }

            return onCount == covers.size()
                ? TriState.ALL
                : TriState.SOME;
        }

        // The fold beside one branch row. It records the new state and asks for the whole block
        // to be laid out again rather than hiding the rows under it itself, because what a row
        // shows depends on every fold above it as well as on this one.
        private JButton buildRowFold(Row row, int index) {

            var fold = FoldControls.buildFoldButton(isUnfolded[index]);

            fold.addActionListener(event -> {

                isUnfolded[index] = !isUnfolded[index];

                saved.putBoolean(row.foldKey(), isUnfolded[index]);
                showFoldedRows();
            });

            return fold;
        }

        private void showFoldedRows() {

            for (var index = 0; index < rows.length; index++) {

                panels[index].setVisible(isRowShown(index));

                if (folds[index] != null) {
                    FoldControls.showFoldState(folds[index], isUnfolded[index]);
                }
            }

            block.revalidate();
            block.repaint();
        }

        // A row shows only where every fold above it is open. Walked up the chain rather than
        // read off its own parent, so that folding a branch hides the whole subtree under it and
        // not merely its children.
        private boolean isRowShown(int index) {

            for (var above = parentOf[index]; above != NO_PARENT; above = parentOf[above]) {

                if (!isUnfolded[above]) {
                    return false;
                }
            }

            return true;
        }
    }

    // A checkbox with a third look for "some of them".
    //
    // Swing has no such control, and the usual workaround - leaving it ticked and hoping the
    // reader notices a tooltip - is the lie this exists to avoid. So the box paints its own
    // marker: the look-and-feel's own icon for the two settled states, so it sits beside the
    // ordinary checkboxes without looking foreign, and a filled square for the third.
    private static final class TriStateBox
            extends JCheckBox {

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
                    return settled == null
                        ? 0
                        : settled.getIconWidth();
                }

                @Override
                public int getIconHeight() {

                    var settled = UIManager.getIcon("CheckBox.icon");
                    return settled == null
                        ? 0
                        : settled.getIconHeight();
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
