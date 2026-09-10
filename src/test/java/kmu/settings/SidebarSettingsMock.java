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

    /**
     * How loudly the panel's list sounds as the wheel moves it. A fourth number, distinct from every level
     * in the balance above, deliberately: the wheel is not one of the kinds the pointer reaches, so a
     * composition that answered it from one of those would agree with itself and pass.
     */
    public static final float LIST_SCROLL_VOLUME = 0.4f;

    // Every level at the bottom of its slider - what a player asking for a panel that is quiet under the
    // pointer sets, and the one balance that is stated as a role the look does not name.
    private static final PointerArrivalVolumes SILENT_ARRIVAL_VOLUMES =
        new PointerArrivalVolumes(0f, 0f, 0f);

    // Two mocks because the look is composed from two readers: the panel's own chrome, and the
    // levels its moments sound at. They are installed and taken down together, a case about the look
    // needing both whichever half it is about.
    private final MockedStatic<KmuMapSidebarSettings> sidebarSettingsMock;

    private final MockedStatic<KmuMapSoundSettings> soundSettingsMock;

    private SidebarSettingsMock(
            MockedStatic<KmuMapSidebarSettings> sidebarSettingsMock,
            MockedStatic<KmuMapSoundSettings> soundSettingsMock) {

        this.sidebarSettingsMock = sidebarSettingsMock;
        this.soundSettingsMock = soundSettingsMock;
    }

    /**
     * Installs both. Close the result to take the static mocks back down - a leaked one fails the
     * next case in the class to touch the same type.
     *
     * @return the installed choices, to be closed when the case is done with them
     */
    public static SidebarSettingsMock install() {

        var installed = new SidebarSettingsMock(
            mockStatic(KmuMapSidebarSettings.class),
            mockStatic(KmuMapSoundSettings.class));

        installed.sidebarSettingsMock
            .when(KmuMapSidebarSettings::getMapSidebarChevronColour)
            .thenReturn(CHEVRON_COLOUR);

        installed.selectColourScheme(COLOUR_SCHEME);
        installed.setArrivalVolumes(ARRIVAL_VOLUMES);
        installed.setListScrollVolume(LIST_SCROLL_VOLUME);

        return installed;
    }

    /**
     * Opens the dev hatch that stands the bar's opener whatever the roster holds, for a case about what
     * that hatch overrides. Named rather than left to a case stubbing the reader itself, since the whole
     * of what it does is bypass a rule stated elsewhere.
     *
     * <p>Not set at install: an unstubbed switch answers false, which is the state the row ships in and
     * the one every case about the count needs.
     */
    public void openTheArrangementOpenerHatch() {
        sidebarSettingsMock
            .when(KmuMapSidebarSettings::isMapLayerArrangementOpenerAlwaysShown)
            .thenReturn(true);
    }

    /**
     * Points the panel at another colour scheme, for a case about what a scheme change moves. Re-stubs
     * rather than installing a second mock, so the choice can be changed mid-case and the look rebuilt
     * from it.
     *
     * @param choice the scheme the panel is to be coloured from
     */
    public void selectColourScheme(SidebarColourSchemeChoice choice) {
        sidebarSettingsMock
            .when(KmuMapSidebarSettings::getMapSidebarColourScheme)
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

        soundSettingsMock
            .when(KmuMapSoundSettings::getMapSidebarPanelChromeArrivalVolume)
            .thenReturn(arrivalVolumes.panelChromeVolume());
        soundSettingsMock
            .when(KmuMapSoundSettings::getMapSidebarSingleOptionControlArrivalVolume)
            .thenReturn(arrivalVolumes.singleOptionControlVolume());
        soundSettingsMock
            .when(KmuMapSoundSettings::getMapSidebarListedItemArrivalVolume)
            .thenReturn(arrivalVolumes.listedItemVolume());
    }

    /**
     * Sets how loudly the panel's list sounds as the wheel moves it, for a case about a player who has
     * pulled that one slider somewhere of its own. Apart from the arrival balance because the wheel is not
     * one of the kinds the pointer reaches - it answers a movement rather than a thing got to.
     *
     * @param listScrollVolume how loudly the wheel is to sound, zero for a list that scrolls silently
     */
    public void setListScrollVolume(float listScrollVolume) {

        soundSettingsMock
            .when(KmuMapSoundSettings::getMapSidebarListScrollVolume)
            .thenReturn(listScrollVolume);
    }

    @Override
    public void close() {

        soundSettingsMock.close();
        sidebarSettingsMock.close();
    }
}
