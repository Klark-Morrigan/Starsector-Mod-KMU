package kmu.maplayers.base.geometry.ui.settings;

import kmu.maplayers.base.geometry.settings.ViewerSettings;

import javax.swing.JPanel;

/**
 * One run of controls under one heading, and the three things every run of them needs.
 *
 * <p>A base rather than five copies of the same two fields and the same constructor. What a
 * section is made of does not vary - it writes settings, it asks for redraws, and it builds its
 * rows through the shared shapes - and stated per class that was sixty lines saying so five
 * times, in which a section that quietly held something else would have looked the same.
 *
 * <p>Building the section is what a section is FOR, so it is the one thing declared abstract
 * here: the column asks each for a finished section and knows nothing else about any of them.
 * Its heading, its remembered key and whether it switches stay with the section, since those
 * are facts about it rather than about where it sits.
 */
abstract class PanelSection {

    protected final ViewerSettings settings;

    protected final ViewerRefreshes refreshes;

    protected final SettingRows rows;

    protected PanelSection(ViewerSettings settings, ViewerRefreshes refreshes) {

        this.settings = settings;
        this.refreshes = refreshes;
        this.rows = new SettingRows(refreshes);
    }

    /**
     * @return the section, ready to put in the column
     */
    abstract JPanel buildSection();
}
