package kmu.maplayers.base.geometry.ui.settings;

import kmu.maplayers.base.geometry.settings.ViewerSettings;

import javax.swing.JPanel;

/**
 * One run of controls under one heading, and what every run of them builds with.
 *
 * <p>A base rather than five copies of the same fields and constructor. What a section is made
 * of does not vary - it writes settings, and it builds its rows through the shared shapes bound
 * to the refresh each knob costs.
 *
 * <p>Building the section is what a section is FOR, so it is the one thing declared abstract
 * here: the column asks each for a finished section and knows nothing else about any of them.
 * Its heading, its remembered keys and whether it switches stay with the section, since those
 * are facts about it rather than about where it sits.
 */
abstract class PanelSection {

    protected final ViewerSettings settings;

    /** Rows that build the partition again, and everything downstream of it. */
    protected final SettingRows rebuildRows;

    /** Rows that trace v3's coasts again. */
    protected final SettingRows coastRows;

    /** Rows that read v4's void again. */
    protected final SettingRows voidV4Rows;

    /** Rows that trace the unclipped partition again. */
    protected final SettingRows unboundedRows;

    /** Rows that only draw the map again. */
    protected final SettingRows repaintRows;

    protected PanelSection(ViewerSettings settings, ViewerRefreshes refreshes) {

        this.settings = settings;
        this.rebuildRows = SettingRows.createSettlingRows(settings, refreshes::rebuildGeometry);
        this.coastRows = SettingRows.createSettlingRows(settings, refreshes::refreshCoastlines);
        this.voidV4Rows = SettingRows.createSettlingRows(settings, refreshes::refreshVoidV4);
        this.unboundedRows = SettingRows.createSettlingRows(
            settings,
            refreshes::refreshUnboundedCells);

        this.repaintRows = SettingRows.createRepaintingRows(settings, refreshes::repaintMap);
    }

    /**
     * @return the section, ready to put in the column
     */
    abstract JPanel buildSection();
}
