package kmu.settings;

import kmlib.starsector.ui.sound.PointerArrivalVolumes;

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
     * The balance every case runs under: how loudly the panel's furniture, a control with one answer to
     * give, and one of many alike are reached. Three distinct numbers rather than the shipped levels,
     * deliberately - two of the three ship at the same value, so a composition handing them over in the
     * wrong order would agree with itself and pass, and a panel whose chrome answers at its list's level
     * is wrong only to the ear.
     *
     * <p>One value rather than three constants for the reason the record itself exists: a case asserting
     * on the whole scheme and this fixture setting it would otherwise each spell the same balance out,
     * and two spellings of one balance is the transposition all over again.
     */
    public static final PointerArrivalVolumes ARRIVAL_VOLUMES = new PointerArrivalVolumes(0.8f, 0.6f, 0.3f);

    // Every level at the bottom of its slider - what a player asking for a panel that is quiet under the
    // pointer sets, and the one balance that is stated as a role the look does not name.
    private static final PointerArrivalVolumes SILENT_ARRIVAL_VOLUMES =
        new PointerArrivalVolumes(0f, 0f, 0f);

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
        installed.setArrivalVolumes(ARRIVAL_VOLUMES);

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
     * that stays quiet under the pointer. Named for the intent rather than left to a case handing over a
     * balance of zeroes, since it is the whole balance being silenced that the look answers to and not
     * any one level of it.
     */
    public void silenceEveryArrival() {
        setArrivalVolumes(SILENT_ARRIVAL_VOLUMES);
    }

    /**
     * Retunes the balance, for a case about what a level change moves. Re-stubs rather than installing a
     * second mock, so a case can retune mid-case and rebuild the look from what it set.
     *
     * @param arrivalVolumes how loudly each kind of thing the pointer reaches is to sound
     */
    public void setArrivalVolumes(PointerArrivalVolumes arrivalVolumes) {

        settingsMock
            .when(KmuMapLayerSettings::getMapSidebarPanelChromeArrivalVolume)
            .thenReturn(arrivalVolumes.panelChromeVolume());
        settingsMock
            .when(KmuMapLayerSettings::getMapSidebarSingleOptionControlArrivalVolume)
            .thenReturn(arrivalVolumes.singleOptionControlVolume());
        settingsMock
            .when(KmuMapLayerSettings::getMapSidebarListedItemArrivalVolume)
            .thenReturn(arrivalVolumes.listedItemVolume());
    }

    @Override
    public void close() {
        settingsMock.close();
    }
}
