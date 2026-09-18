package kmu.maplayers.base.geometry.ui.settings;

import kmu.desktop.ui.swing.RowFurniture;
import kmu.maplayers.base.geometry.settings.ViewerSettings;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JPanel;

/**
 * The order the viewer's controls are read in, and nothing else.
 *
 * <p>One class per section, because a section is what a reader folds, switches and looks for -
 * so it is what a change to the panel is a change to. Held as one list of rows, moving a knob
 * meant reading past the forty that were not moving, and every section's ranges, keys and
 * wiring sat in one namespace where a constant belonging to the coasts looked exactly like one
 * belonging to the cells.
 *
 * <p>Each section owns its own heading, its own remembered key and whether it switches, since
 * those are facts about the section rather than about where it sits. What is left here is the
 * one thing that is genuinely about the panel: what comes before what.
 *
 * <p>That order is a claim, not a convenience. What reaches everything comes first, the cells
 * next because the void is laid against them, and the two void constructions last and side by
 * side, behind a rule that says the subject has changed.
 */
public final class ViewerSettingsPanel {

    private static final int PANEL_PADDING = 8;

    private final GlobalGeometrySection globalGeometry;

    private final CellGeometrySection cellGeometry;

    private final CellAppearanceSection cellAppearance;

    private final VoidV3Section voidV3;

    private final VoidV4Section voidV4;

    /**
     * @param settings what every control here writes to
     * @param refreshes what they ask for once they have
     */
    public ViewerSettingsPanel(ViewerSettings settings, ViewerRefreshes refreshes) {

        this.globalGeometry = new GlobalGeometrySection(settings, refreshes);
        this.cellGeometry = new CellGeometrySection(settings, refreshes);
        this.cellAppearance = new CellAppearanceSection(settings, refreshes);
        this.voidV3 = new VoidV3Section(settings, refreshes);
        this.voidV4 = new VoidV4Section(settings, refreshes);
    }

    /**
     * Every control the viewer offers, in one column.
     *
     * <p>Built once, when the window is. Nothing here is rebuilt as settings change - a control
     * writes its setting and asks for whatever redraw that costs, and the panel itself never has
     * to know what changed.
     *
     * @return the column
     */
    public JPanel buildRows() {

        var controls = new JPanel();

        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        controls.setBorder(BorderFactory.createEmptyBorder(
            PANEL_PADDING,
            PANEL_PADDING,
            PANEL_PADDING,
            PANEL_PADDING));

        // First, because it is the only run here that reaches everything below it. A knob that
        // governs the sections is read before them or not at all: below the void stack, where
        // these sat loose, it is two screens past the thing it decides and a reader who has not
        // scrolled that far has no reason to think it exists.
        controls.add(globalGeometry.buildSection());

        controls.add(cellGeometry.buildSection());
        controls.add(cellAppearance.buildSection());

        // The rule that says the subject has changed. Everything above is about cells; a reader
        // hunting for a void knob was otherwise reading forty identical rows to find out where
        // one run ended and the next began.
        controls.add(RowFurniture.buildDivider());

        controls.add(voidV3.buildSection());
        controls.add(voidV4.buildSection());

        return controls;
    }
}
