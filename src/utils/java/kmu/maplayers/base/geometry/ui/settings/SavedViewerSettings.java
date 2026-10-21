package kmu.maplayers.base.geometry.ui.settings;

import kmu.desktop.ui.SavedValues;
import kmu.maplayers.base.geometry.settings.ViewerSettings;

import java.nio.file.Path;

/**
 * The settings the viewer is actually running, read without opening a window.
 *
 * <p>For anything measuring the map in order to explain what is ON SCREEN - a probe, a report,
 * a dump. A reading taken at the shipped defaults describes a map nobody is looking at: the
 * window remembers every knob between runs, and the one that matters most here is the bound
 * the cells' arcs are flattened onto. Run at 24 rather than the shipped 48 the sagitta
 * quadruples, and with it the tolerance the void is welded at and the smallest piece that
 * survives - so pockets present in one reading are absent from the other, and a fault chased
 * at the defaults is a fault chased on the wrong map.
 *
 * <p>So it is here rather than restated per probe. Written out each time it is fifteen lines
 * against one call to the defaults, and the cheaper of the two wins by default - which is how
 * a drawer full of probes comes to measure a map nobody asked about.
 *
 * <p>What the mod ships is a different question, answered by the defaults whatever the window
 * was last set to. This answers what the window shows.
 */
public final class SavedViewerSettings {

    // Under the user's home rather than in the checkout, so a knob survives a clean, a branch
    // switch and a fresh clone.
    private static final Path SAVED_VALUES_FILE = Path.of(
        System.getProperty("user.home"), ".kmu", "sector-geometry-viewer.json");

    private SavedViewerSettings() {
    }

    /**
     * Where the window remembers its knobs.
     *
     * <p>Said once, here, because the window that writes the file and anything reading it back
     * have to name the same one - and a second spelling of the path is a reader that quietly
     * finds nothing and reports the defaults as though they were the settings.
     *
     * @return the file the knobs are remembered in
     */
    public static Path savedValuesFile() {
        return SAVED_VALUES_FILE;
    }

    /**
     * Reads the knobs the window was last left at.
     *
     * @return the settings, or the shipped defaults where the window has never been opened
     */
    public static ViewerSettings readSavedSettings() {
        return readSavedSettingsIn(SAVED_VALUES_FILE);
    }

    /**
     * Reads the knobs a saved-values file holds, as the window would open on them.
     *
     * <p>Installs the file as the saved values, which is where every knob reads from. Each knob
     * is read under the same key and fallback its row opens on, and nothing is redrawn: there is
     * no window to redraw.
     *
     * @param file the saved values, such as a copy kept from another session
     * @return the settings, or the defaults for every knob the file does not hold
     */
    public static ViewerSettings readSavedSettingsIn(Path file) {

        SavedValues.rememberIn(file);

        var settings = new ViewerSettings();

        for (var knob : ViewerSettingsPanel.listEveryKnob()) {
            knob.applySavedTo(settings);
        }

        return settings;
    }
}
