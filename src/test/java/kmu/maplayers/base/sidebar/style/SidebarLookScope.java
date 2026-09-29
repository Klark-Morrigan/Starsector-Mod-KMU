package kmu.maplayers.base.sidebar.style;

import kmlib.testfixtures.starsector.ui.font.FaceLineHeightReaderFake;
import kmlib.testfixtures.starsector.ui.font.installed.LazyFontLineHeightReaderMock;

import kmu.settings.SidebarSettingsMock;
import kmu.starsector.StarsectorUiColoursMock;

/**
 * Stands in for every live read {@link SidebarStyles} composes a look from, for the length of one case:
 * the engine's UI colours, the player's sidebar settings, and the line heights of the installed atlases
 * the tab faces are drawn at. All three are static reads a test JVM cannot answer, and a look reads every
 * one of them whichever field a case then asserts on, so a suite composing a look opens them together.
 *
 * <p>One scope rather than three stand-ins per suite, so the suites composing a look cannot drift into
 * describing the live game differently, and closing one stand-in cannot be forgotten while the others go.
 */
public final class SidebarLookScope implements AutoCloseable {

    private final StarsectorUiColoursMock uiColoursMock;
    private final SidebarSettingsMock sidebarSettingsMock;
    private final LazyFontLineHeightReaderMock lineHeightsMock;

    private SidebarLookScope(FaceLineHeightReaderFake lineHeightsFake) {
        this.uiColoursMock = StarsectorUiColoursMock.install();
        this.sidebarSettingsMock = SidebarSettingsMock.install();
        this.lineHeightsMock = LazyFontLineHeightReaderMock.install(lineHeightsFake);
    }

    /**
     * Opens a scope over a vanilla install's atlases.
     *
     * @return the open scope, to be closed when the case is done with it
     */
    public static SidebarLookScope openOnVanillaAtlases() {
        return new SidebarLookScope(FaceLineHeightReaderFake.createVanillaLineHeights());
    }

    /**
     * Opens a scope over an install whose atlases state {@code lineHeightsFake}'s line heights - one whose
     * tab faces are not vanilla's, for a case pinning that a size was read rather than written down.
     *
     * @param lineHeightsFake what the install is taken to hold
     * @return the open scope, to be closed when the case is done with it
     */
    public static SidebarLookScope openOnInstalledAtlases(FaceLineHeightReaderFake lineHeightsFake) {
        return new SidebarLookScope(lineHeightsFake);
    }

    /**
     * @return the player's sidebar settings, for a case about a choice other than the shipped one
     */
    public SidebarSettingsMock getSidebarSettingsMock() {
        return sidebarSettingsMock;
    }

    @Override
    public void close() {
        // Every one, whatever the first does: a stand-in left open would leak into whatever case ran next,
        // and the failure would land there rather than here.
        try {
            lineHeightsMock.close();

        } finally {

            try {
                sidebarSettingsMock.close();
            } finally {
                uiColoursMock.close();
            }
        }
    }
}
