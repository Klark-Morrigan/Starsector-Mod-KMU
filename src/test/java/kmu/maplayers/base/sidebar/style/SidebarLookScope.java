package kmu.maplayers.base.sidebar.style;

import kmlib.starsector.ui.font.FontAtlas;
import kmlib.starsector.ui.font.StarsectorFont;
import kmlib.testfixtures.starsector.ui.font.FaceLineHeightReaderFake;
import kmlib.testfixtures.starsector.ui.font.installed.LazyFontLineHeightReaderMock;

import kmu.maplayers.base.faces.ProbedText;
import kmu.maplayers.base.faces.SettledFaces;
import kmu.settings.SidebarSettingsMock;
import kmu.starsector.StarsectorUiColoursMock;

import org.mockito.MockedStatic;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Stands in for every live read {@link SidebarStyles} composes a look from, for the length of one case:
 * the engine's UI colours, the player's sidebar settings, the line heights of the installed atlases the
 * tab faces are drawn at, and the faces the running sector settled. All four are static reads a test JVM
 * cannot answer, and a look reads every one of them whichever field a case then asserts on, so a suite
 * composing a look opens them together.
 *
 * <p>One scope rather than four stand-ins per suite, so the suites composing a look cannot drift into
 * describing the live game differently, and closing one stand-in cannot be forgotten while the others go.
 * The settled faces answer every face as the one asked for unless a case says otherwise: a sector whose
 * names every atlas draws.
 */
public final class SidebarLookScope implements AutoCloseable {

    private final StarsectorUiColoursMock uiColoursMock;
    private final SidebarSettingsMock sidebarSettingsMock;
    private final LazyFontLineHeightReaderMock lineHeightsMock;
    private final MockedStatic<SettledFaces> facesSourceMock;
    private final SettledFaces liveFacesMock;

    private SidebarLookScope(FaceLineHeightReaderFake lineHeightsFake) {

        this.uiColoursMock = StarsectorUiColoursMock.install();
        this.sidebarSettingsMock = SidebarSettingsMock.install();
        this.lineHeightsMock = LazyFontLineHeightReaderMock.install(lineHeightsFake);
        this.liveFacesMock = mock(SettledFaces.class);
        this.facesSourceMock = mockStatic(SettledFaces.class);

        when(liveFacesMock.settleFace(any(), any()))
            .thenAnswer(invocation -> invocation.getArgument(0));
        facesSourceMock.when(SettledFaces::resolveFacesForLiveSector)
            .thenReturn(liveFacesMock);
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
     * Has the running sector settle one face, asked against one set of kinds, on another - for a case
     * pinning which kinds a face is held to, the settled face answering only for exactly those.
     *
     * @param requestedFont the face a text asks for
     * @param probes        the kinds it is asked against
     * @param settledFace   what the sector settles it on
     */
    public void answerSettledFace(StarsectorFont requestedFont, Set<ProbedText> probes, FontAtlas settledFace) {

        when(liveFacesMock.settleFace(requestedFont, probes))
            .thenReturn(settledFace);
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
            facesSourceMock.close();

        } finally {

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
}
