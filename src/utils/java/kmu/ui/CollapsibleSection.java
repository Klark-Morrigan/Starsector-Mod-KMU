package kmu.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * A run of controls under one heading, foldable away, with a switch over the whole of it.
 *
 * <p>A panel that grows past a screenful stops being a panel and becomes a scroll: the knob a
 * reader wants is somewhere below, and finding it means reading everything above it. Folding a
 * run away is what lets a reader keep the two or three subjects they are working on open and
 * the rest shut, which is the difference between a long panel and an unusable one.
 *
 * <p><b>The switch is a setting of its own, not a roll-up of what is inside.</b> A roll-up that
 * sets its children destroys them: switch it off and every child goes off with it, so switching
 * it back on cannot restore what was showing - it can only turn everything on. That is exactly
 * wrong for a heading a reader folds away meaning "not this, for now", and comes back to. So
 * the switch suppresses the whole run while every control inside keeps its own state, and
 * turning it back on shows precisely what was showing before.
 *
 * <p>The body is greyed while the switch is off, since a row that still looks live but changes
 * nothing is worse than one plainly out of use. Applied by walking the body, which assumes
 * nothing inside was independently disabled - true of a panel of settings, and the reason this
 * belongs to a section rather than to controls in general.
 *
 * <p>Both what the switch is set to and whether the run is folded are remembered, under keys
 * given rather than taken from the heading. A heading is copy and gets reworded; two sections
 * that came to be worded alike would then share a key and fold each other.
 */
public final class CollapsibleSection {

    private static final int HEADER_PADDING = 4;

    // How much larger a section heading is than the rows under it, in points. Two is enough
    // to read as a heading at a glance and small enough that a folded panel of them still
    // looks like one list rather than a stack of titles.
    private static final float HEADING_SIZE_INCREASE = 2f;

    // How far the body sits in from the heading, in pixels. Enough that the run reads as
    // belonging to the heading rather than merely following it.
    private static final int BODY_INDENT = 12;

    // ASCII only, for the reason the reset button is: the viewer runs wherever the JDK's
    // default font does, and a glyph that renders as a box makes the control unreadable
    // rather than merely plain.
    private static final String FOLDED_LABEL = "+";
    private static final String UNFOLDED_LABEL = "-";

    private static final String FOLD_TOOLTIP = "Fold this section away";

    private static final int FOLD_BUTTON_WIDTH = 22;
    private static final int FOLD_BUTTON_HEIGHT = 18;

    private CollapsibleSection() {
    }

    /**
     * Builds one section.
     *
     * @param keys     what its switch and its folded state are remembered under
     * @param heading  what the section is called, which is also the switch's label
     * @param fallback what the switch is before anyone has set it, and while nothing is
     *                 remembered; sections start unfolded whatever this is, since a panel
     *                 that opened with everything shut would hide what it is for
     * @param apply    records the switch's new state
     * @param onChange what to run once the switch changes
     * @param body     the controls the section holds, laid out by the caller
     * @return the section, heading and body together
     */
    public static JPanel buildSection(
            SectionKeys keys,
            String heading,
            boolean fallback,
            Consumer<Boolean> apply,
            Runnable onChange,
            JPanel body) {

        var saved = SavedValues.findSavedValues();
        var isOn = saved.getBoolean(keys.switchKey(), fallback);
        var isUnfolded = saved.getBoolean(keys.foldKey(), true);

        var master = new JCheckBox(heading, isOn);
        var fold = buildFoldButton(isUnfolded);

        body.setBorder(BorderFactory.createEmptyBorder(0, BODY_INDENT, 0, 0));
        body.setVisible(isUnfolded);
        applyEnabled(body, isOn);

        // Told the remembered state before anything is drawn, so the map opens matching the
        // panel. Without it a section switched off last time would come back drawn but
        // unticked, and the first click would appear to turn it OFF while turning it on.
        apply.accept(isOn);

        master.addActionListener(event -> {

            applyEnabled(body, master.isSelected());
            apply.accept(master.isSelected());

            SavedValues.findSavedValues().putBoolean(keys.switchKey(), master.isSelected());

            onChange.run();
        });

        fold.addActionListener(event -> {

            body.setVisible(!body.isVisible());
            fold.setText(body.isVisible() ? UNFOLDED_LABEL : FOLDED_LABEL);

            SavedValues.findSavedValues().putBoolean(keys.foldKey(), body.isVisible());
        });

        return layOutSection(fold, master, body);
    }

    /**
     * Builds one section that only folds, with no switch over it.
     *
     * <p>For a run of controls with nothing to suppress. A switch over a run of colours and
     * ranges would have to mean something - drawn in what, at which size, while it is off? -
     * and there is no answer, so it would be a control that looks like it does something and
     * does not. Folding on its own asks no such question: it is about the panel, not the map.
     *
     * @param sectionName what its folded state is remembered under, which is not its heading
     * @param heading     what the section is called
     * @param body        the controls it holds, laid out by the caller
     * @return the section, heading and body together
     */
    public static JPanel buildFoldingSection(String sectionName, String heading, JPanel body) {

        var foldKey = SectionKeys.forSection(sectionName).foldKey();
        var isUnfolded = SavedValues.findSavedValues().getBoolean(foldKey, true);
        var fold = buildFoldButton(isUnfolded);

        body.setBorder(BorderFactory.createEmptyBorder(0, BODY_INDENT, 0, 0));
        body.setVisible(isUnfolded);

        var label = new JLabel(heading);

        // Larger as well as bold. Every row label in this panel is already bold - that is the
        // platform's own default for a label, not something set here - so weight alone leaves
        // a heading looking like one more row of the run it is supposed to be heading. Size
        // is what is left to distinguish it.
        //
        // Derived from whatever font the label was given rather than named here, since a font
        // named in code is a font wrong on somebody's machine.
        label.setFont(label.getFont().deriveFont(
            Font.BOLD, label.getFont().getSize() + HEADING_SIZE_INCREASE));

        fold.addActionListener(event -> {

            body.setVisible(!body.isVisible());
            fold.setText(body.isVisible() ? UNFOLDED_LABEL : FOLDED_LABEL);

            SavedValues.findSavedValues().putBoolean(foldKey, body.isVisible());
        });

        return layOutSection(fold, label, body);
    }

    /**
     * Where a section remembers its two pieces of state.
     *
     * @param switchKey what the master switch is remembered under
     * @param foldKey   what the folded state is remembered under
     */
    public record SectionKeys(String switchKey, String foldKey) {

        /**
         * The two keys one section needs, derived from one name for it.
         *
         * @param sectionName the section's identity, which is not its heading
         * @return the keys
         */
        public static SectionKeys forSection(String sectionName) {
            return new SectionKeys(sectionName + "Shown", sectionName + "Unfolded");
        }
    }

    private static JPanel layOutSection(JButton fold, JComponent title, JPanel body) {

        var heading = new JPanel(new BorderLayout());

        heading.add(fold, BorderLayout.WEST);
        heading.add(title, BorderLayout.CENTER);
        heading.setBorder(BorderFactory.createEmptyBorder(HEADER_PADDING, 0, HEADER_PADDING, 0));

        // Capped, for the reason a divider is: the column's layout offers a row everything
        // left over, and an uncapped heading would take the height of the window.
        heading.setMaximumSize(new Dimension(Integer.MAX_VALUE, heading.getPreferredSize().height));

        var section = new JPanel();

        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.add(heading);
        section.add(body);

        return section;
    }

    private static JButton buildFoldButton(boolean isUnfolded) {

        var fold = new JButton(isUnfolded ? UNFOLDED_LABEL : FOLDED_LABEL);

        fold.setToolTipText(FOLD_TOOLTIP);
        fold.setMargin(new java.awt.Insets(0, 0, 0, 0));
        fold.setPreferredSize(new Dimension(FOLD_BUTTON_WIDTH, FOLD_BUTTON_HEIGHT));
        fold.setFocusable(false);

        return fold;
    }

    // Swing does not pass enablement down a container, so a disabled panel still draws its
    // children live. Walked rather than set on the body alone for that reason.
    private static void applyEnabled(Component component, boolean isEnabled) {

        component.setEnabled(isEnabled);

        if (component instanceof Container container) {

            for (var child : container.getComponents()) {
                applyEnabled(child, isEnabled);
            }
        }
    }
}
