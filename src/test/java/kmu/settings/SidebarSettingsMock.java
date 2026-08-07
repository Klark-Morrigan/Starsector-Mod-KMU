package kmu.settings;

import org.mockito.MockedStatic;

import static org.mockito.Mockito.mockStatic;

/**
 * Stands in for the two colour choices the sidebar's look is composed from: the panel's colour scheme
 * and the collapse handle's chevron colour. Both are the player's rather than the engine's, so a look
 * built without them resolves a null choice and fails before any case reaches its own assertion - which
 * is why they are installed together rather than per case.
 *
 * <p>Held here rather than repeated in each suite because the pair is what *any* case touching the
 * sidebar's look needs, whichever half of it that case is about. Three suites spelling the same setup
 * out is three places for the shipped defaults to be described differently, and a case reading one
 * suite's stubbed scheme while asserting against another's would be sound only against its own file.
 * The sibling {@link kmu.starsector.StarsectorUiColoursMock} does the same for the engine palette; a
 * suite about the look installs both.
 *
 * <p>The stubbed values are the choices the file ships with, so a case says nothing and describes the
 * look a fresh player is given. A case about a *different* choice names it through
 * {@link #selectColourScheme}, which is what keeps "this case varies the scheme" visible in the case
 * rather than buried in its setup.
 */
public final class SidebarSettingsMock implements AutoCloseable {

    /**
     * The scheme every case runs under unless it says otherwise: the fixed UI palette the sidebar ships
     * on.
     */
    public static final SidebarColourSchemeChoice COLOUR_SCHEME = SidebarColourSchemeChoice.UI_PALETTE;

    /**
     * The chevron choice every case runs under. The panel-accent one rather than the shipped gold,
     * deliberately: it is the choice that draws the handle from the panel's own accents, so a look whose
     * accents went astray shows up in the notch shades as well as in the controls.
     */
    public static final NotchChevronColourChoice CHEVRON_COLOUR = NotchChevronColourChoice.PANEL_ACCENT;

    private final MockedStatic<KmuMapLayerSettings> settingsMock;

    private SidebarSettingsMock(MockedStatic<KmuMapLayerSettings> settingsMock) {
        this.settingsMock = settingsMock;
    }

    /**
     * Installs the pair. Close the result to take the static mock back down - a leaked one fails the
     * next case in the class to touch the same type.
     *
     * @return the installed choices, to be closed when the case is done with them
     */
    public static SidebarSettingsMock install() {

        var settingsMock = mockStatic(KmuMapLayerSettings.class);
        var installed = new SidebarSettingsMock(settingsMock);

        settingsMock
            .when(KmuMapLayerSettings::getMapSidebarChevronColour)
            .thenReturn(CHEVRON_COLOUR);
            
        installed.selectColourScheme(COLOUR_SCHEME);

        return installed;
    }

    /**
     * Points the panel at another colour scheme, for a case about what a scheme change moves. Re-stubs
     * rather than installing a second mock, so the choice can be changed mid-case and the look rebuilt
     * from it.
     *
     * @param choice the scheme the panel is to be coloured from
     */
    public void selectColourScheme(SidebarColourSchemeChoice choice) {
        settingsMock
            .when(KmuMapLayerSettings::getMapSidebarColourScheme)
            .thenReturn(choice);
    }

    @Override
    public void close() {
        settingsMock.close();
    }
}
