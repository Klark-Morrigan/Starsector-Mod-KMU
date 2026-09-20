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
 * <p><b>A test should NOT use this.</b> A test pins the geometry the mod ships, which is the
 * defaults and must stay the defaults however the window is set - otherwise the suite passes
 * or fails by what someone last dragged a slider to. Explaining the picture and pinning the
 * product are opposite jobs, and this one is the first.
 */
public final class SavedViewerSettings {

    // Under the user's home rather than in the checkout, so a knob survives a clean, a branch
    // switch and a fresh clone - which is what it did when the JDK kept it, and losing that
    // would be trading one silent forgetting for another.
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
     * <p>The rows are what remember, so the panel is built to read them - built headless and
     * thrown away, since only the settings it filled in are wanted. Nothing is refreshed: the
     * callbacks a row fires as it takes its remembered value have nowhere to go here, and
     * there is no map to redraw.
     *
     * @return the settings, or the shipped defaults where the window has never been opened
     */
    public static ViewerSettings readSavedSettings() {

        System.setProperty("java.awt.headless", "true");
        SavedValues.rememberIn(SAVED_VALUES_FILE);

        var settings = new ViewerSettings();

        new ViewerSettingsPanel(settings, new ViewerRefreshes() {

            @Override
            public void rebuildGeometry() {
            }

            @Override
            public void refreshCoastlines() {
            }

            @Override
            public void refreshVoidV4() {
            }

            @Override
            public void refreshUnboundedCells() {
            }

            @Override
            public void repaintMap() {
            }
        }).buildRows();

        return settings;
    }
}
