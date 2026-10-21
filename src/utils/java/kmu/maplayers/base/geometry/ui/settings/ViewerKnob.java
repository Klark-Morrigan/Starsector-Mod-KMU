package kmu.maplayers.base.geometry.ui.settings;

import kmu.maplayers.base.geometry.settings.ViewerSettings;

/**
 * One setting the viewer's panel offers: what it is remembered under, where it starts, and which
 * field of the settings it writes.
 *
 * <p>Declared once per setting and read twice - by the row that edits it and by
 * {@link SavedViewerSettings}, which applies every remembered value without building a window.
 * Two readers with their own keys or defaults would describe two different maps.
 */
interface ViewerKnob {

    /**
     * Writes what the installed saved values remember for this setting, or its fallback, into
     * the settings.
     *
     * @param settings what to write into
     */
    void applySavedTo(ViewerSettings settings);
}
