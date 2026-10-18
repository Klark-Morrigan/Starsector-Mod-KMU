package kmu.maplayers.base.sidebar.style;

import kmlib.starsector.ui.sound.PointerArrivalVolumes;
import kmlib.starsector.ui.sound.StarsectorUiSound;
import kmlib.starsector.ui.sound.UiSoundCue;
import kmlib.starsector.ui.sound.UiSoundScheme;

import kmu.settings.KmuMapSoundSettings;

/**
 * Composes how the sidebar sounds: the engine's own roles, at the levels the player set on the sound
 * settings tab.
 *
 * <p>A bundle of its own rather than a part of {@link SidebarStyles}, because the two halves of the panel
 * take it at different moments. The paint pass carries it inside the widget style it draws from. The
 * controller answering pointer events is handed one directly when it is built. Both compose through this one
 * factory, which is what keeps a panel from looking and sounding from two.
 */
public final class SidebarSounds {

    // Composes only; never instantiated.
    private SidebarSounds() {
    }

    /**
     * How the sidebar answers a press, the pointer arriving, and its list moving under the wheel: the
     * engine's own roles, the panel being drawn to sit among vanilla chrome, at the levels the player set
     * them to.
     *
     * <p>The roles are vanilla's and the balance is not, which is the whole of what this composes. What
     * the engine mixed its mouseover for is a screen carrying a handful of hit targets, and this panel
     * packs a column of them into a strip - so matching the chrome around it means matching it under one
     * pointer sweep, which the IDs alone cannot do at any volume they ship with.
     *
     * <p>Read here rather than watched, on the rule the colours of the look follow: a scheme is composed
     * whenever the panel's look is, which is often enough that a slider moved mid-session is heard
     * shortly after. A binding that pushed changes over would be a second mechanism for a value already
     * re-read.
     *
     * @return the scheme this sidebar's moments sound by
     */
    public static UiSoundScheme buildSoundScheme() {

        var arrivalVolumes = new PointerArrivalVolumes(
            KmuMapSoundSettings.getMapSidebarPanelChromeArrivalVolume(),
            KmuMapSoundSettings.getMapSidebarSingleOptionControlArrivalVolume(),
            KmuMapSoundSettings.getMapSidebarListedItemArrivalVolume());

        return new UiSoundScheme(
            // The press keeps the engine's own level, having no slider of its own: it is one act the
            // player asked for, so the case for quietening it - a sweep crossing many things at once -
            // never arises.
            UiSoundCue.createAtFullVolume(StarsectorUiSound.BUTTON_PRESSED),
            resolveArrivalSound(arrivalVolumes),
            arrivalVolumes,
            resolveListScrollCue());
    }

    // What the wheel makes as it moves the sidebar's list, or nothing at all once the player has pulled its
    // slider to the bottom - the library's own rule for a level composed from a slider, silence being
    // stated by naming no cue rather than by playing one at zero.
    //
    // A level of its own and not part of the arrival balance: the wheel is one act the player asked for,
    // sounding once however far the list travels, where the arrival levels are set against how many things
    // one sweep of the pointer crosses.
    private static UiSoundCue resolveListScrollCue() {

        return UiSoundCue.createIfAudible(
            StarsectorUiSound.LIST_SCROLLED,
            KmuMapSoundSettings.getMapSidebarListScrollVolume());
    }

    // Whether the pointer sounds at all, which is the balance's own answer: a player who has pulled every
    // level down to nothing has asked for a panel that is quiet under the pointer, and a look states that
    // by naming no role rather than by playing one at zero. Named per moment and not per kind because one
    // role covers every arrival - silencing one kind alone is a balance the scheme has no way to hold.
    private static StarsectorUiSound resolveArrivalSound(PointerArrivalVolumes arrivalVolumes) {

        return isEveryArrivalSilenced(arrivalVolumes)
            ? null
            : StarsectorUiSound.BUTTON_MOUSEOVER;
    }

    // Silence read off the levels rather than off a switch of its own, so the sliders are the only place
    // the panel's volume is stated. Compared against the floor rather than for equality with it: the
    // levels arrive from a stored double narrowed to a float, and a slider parked at its own minimum has
    // no business turning on how that landed.
    private static boolean isEveryArrivalSilenced(PointerArrivalVolumes arrivalVolumes) {

        return arrivalVolumes.panelChromeVolume() <= UiSoundCue.SILENT_VOLUME
            && arrivalVolumes.singleOptionControlVolume() <= UiSoundCue.SILENT_VOLUME
            && arrivalVolumes.listedItemVolume() <= UiSoundCue.SILENT_VOLUME;
    }
}
