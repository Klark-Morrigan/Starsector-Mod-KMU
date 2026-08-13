package kmu.settings;

import org.mockito.MockedStatic;

import static org.mockito.Mockito.mockStatic;

/**
 * Stands in for the player's own settings the sidebar's look is composed from: the panel's colour
 * scheme, the collapse handle's chevron colour, and the levels its moments sound at. None of them are
 * the engine's, so a look built without them resolves a null choice or a silent balance and fails before
 * any case reaches its own assertion - which is why they are installed together rather than per case.
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

    /**
     * The level the panel's own furniture is reached at under every case. Three distinct numbers rather
     * than the shipped balance, deliberately: two of the three levels ship at the same value, so a
     * composition handing them over in the wrong order would agree with itself and pass - and a panel
     * whose chrome answers at its list's level is wrong only to the ear.
     */
    public static final float PANEL_CHROME_ARRIVAL_VOLUME = 0.8f;

    /** The level a control with one answer to give is reached at; distinct from the two beside it. */
    public static final float SINGLE_OPTION_CONTROL_ARRIVAL_VOLUME = 0.6f;

    /** The level one of many alike is reached at; distinct from the two above it. */
    public static final float LISTED_ITEM_ARRIVAL_VOLUME = 0.3f;

    // The bottom of every arrival slider - what a player asking for a panel that is quiet under the
    // pointer sets, and the one balance that is stated as a role the look does not name.
    private static final float SILENT_ARRIVAL_VOLUME = 0f;

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
        installed.setArrivalVolumes(
            PANEL_CHROME_ARRIVAL_VOLUME,
            SINGLE_OPTION_CONTROL_ARRIVAL_VOLUME,
            LISTED_ITEM_ARRIVAL_VOLUME);

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

    /**
     * Pulls every arrival level to the bottom, for the case about a player who has asked for a panel
     * that stays quiet under the pointer. Named for the intent rather than left to a case passing three
     * zeroes, since it is the whole balance being silenced that the look answers to and not any one of
     * them.
     */
    public void silenceEveryArrival() {
        setArrivalVolumes(
            SILENT_ARRIVAL_VOLUME,
            SILENT_ARRIVAL_VOLUME,
            SILENT_ARRIVAL_VOLUME);
    }

    /**
     * Retunes the whole balance, for a case about what a level change moves. The three together because
     * that is what they are - the levels are only meaningful against each other - and re-stubbed rather
     * than installed as a second mock, so a case can retune them mid-case and rebuild the look from what
     * it set.
     *
     * @param panelChromeVolume         the level the panel's own furniture is reached at
     * @param singleOptionControlVolume the level a control with one answer to give is reached at
     * @param listedItemVolume          the level one of many alike is reached at
     */
    public void setArrivalVolumes(
        float panelChromeVolume,
        float singleOptionControlVolume,
        float listedItemVolume) {

        settingsMock
            .when(KmuMapLayerSettings::getMapSidebarPanelChromeArrivalVolume)
            .thenReturn(panelChromeVolume);
        settingsMock
            .when(KmuMapLayerSettings::getMapSidebarSingleOptionControlArrivalVolume)
            .thenReturn(singleOptionControlVolume);
        settingsMock
            .when(KmuMapLayerSettings::getMapSidebarListedItemArrivalVolume)
            .thenReturn(listedItemVolume);
    }

    @Override
    public void close() {
        settingsMock.close();
    }
}
