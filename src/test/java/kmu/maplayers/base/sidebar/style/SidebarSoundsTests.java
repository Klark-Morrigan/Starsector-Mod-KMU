package kmu.maplayers.base.sidebar.style;

import kmlib.starsector.ui.sound.PointerArrivalTarget;
import kmlib.starsector.ui.sound.PointerArrivalVolumes;
import kmlib.starsector.ui.sound.StarsectorUiSound;
import kmlib.starsector.ui.sound.UiSoundCue;

import kmu.settings.SidebarSettingsMock;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link SidebarSounds}: that the panel's sound scheme names the engine's own roles at the levels the
 * player set them to. KMLib names the kinds and takes the numbers; which numbers those are is this mod's
 * answer, so they are pinned here.
 */
final class SidebarSoundsTests {

    // The one level left standing in the case about a balance silenced everywhere but one kind. Any
    // audible level does; it is named so the case's expectation and the level it set are one value.
    private static final float LAST_AUDIBLE_VOLUME = 0.25f;

    private SidebarSettingsMock sidebarSettingsMock;

    @BeforeEach
    void mockSoundSettings() {
        sidebarSettingsMock = SidebarSettingsMock.install();
    }

    @AfterEach
    void closeSoundSettings() {
        sidebarSettingsMock.close();
    }

    @Nested
    class BuildSoundScheme {

        @Test
        void reachesEachKindOfThingAtItsOwnSettingsLevel() {
            // The three levels are one balance, and what makes it right is the ratio between them - so
            // what is pinned is that each kind resolves the level set for it and not another's. Two of
            // the three ship at the same number, which is exactly why the stubbed levels differ: a pair
            // handed over crossed would compile, paint identically, and be wrong only to the ear. The
            // numbers are the fixture's own, spelt out rather than read back off it, so a case that
            // resolved every kind through one accessor could not agree with itself.
            var soundScheme = SidebarSounds.buildSoundScheme();

            assertThat(soundScheme.resolvePointerArrivalCueFor(PointerArrivalTarget.PANEL_CHROME))
                .isEqualTo(new UiSoundCue(StarsectorUiSound.BUTTON_MOUSEOVER, 0.8f));

            assertThat(soundScheme.resolvePointerArrivalCueFor(
                    PointerArrivalTarget.SINGLE_OPTION_CONTROL))
                .isEqualTo(new UiSoundCue(StarsectorUiSound.BUTTON_MOUSEOVER, 0.6f));

            assertThat(soundScheme.resolvePointerArrivalCueFor(PointerArrivalTarget.LISTED_ITEM))
                .isEqualTo(new UiSoundCue(StarsectorUiSound.BUTTON_MOUSEOVER, 0.3f));
        }

        @Test
        void confirmsAPressAtTheEnginesOwnLevel() {
            // The one moment with no slider behind it: a press is a single act the player asked for, so
            // the case for quietening it never arises and it keeps vanilla's balance whatever the
            // arrival levels are set to.
            assertThat(SidebarSounds.buildSoundScheme().pressCue())
                .isEqualTo(new UiSoundCue(StarsectorUiSound.BUTTON_PRESSED, 1f));
        }

        @Test
        void scrollsItsListAtItsOwnSettingsLevel() {
            // The wheel's level is its own slider and not part of the arrival balance: the player turned
            // the wheel once however far the list travelled, where the arrival levels answer to how many
            // things one sweep of the pointer crosses. Its number differs from all three of those, so a
            // composition reaching into the balance for it records a level nobody set for the wheel.
            assertThat(SidebarSounds.buildSoundScheme().listScrollCue())
                .isEqualTo(new UiSoundCue(StarsectorUiSound.LIST_SCROLLED, 0.4f));
        }

        @Test
        void namesNoScrollCueAtAllWhenItsLevelIsSilenced() {
            // Silence stated by naming no cue rather than by playing one at nothing, the rule the arrivals
            // beside it answer to - and read off the slider alone, this moment having one level of its own
            // rather than a balance to weigh.
            sidebarSettingsMock.setListScrollVolume(0f);

            assertThat(SidebarSounds.buildSoundScheme().listScrollCue())
                .isNull();
        }

        @Test
        void namesNoArrivalRoleAtAllWhenEveryLevelIsSilenced() {
            // A player who has pulled the whole balance down has asked for a panel that is quiet under
            // the pointer, and a look states that by naming no role - a cue at zero is still a sound
            // played, which reads as wiring that half worked rather than as a panel deliberately quiet.
            sidebarSettingsMock.silenceEveryArrival();

            assertThat(SidebarSounds
                    .buildSoundScheme()
                    .resolvePointerArrivalCueFor(PointerArrivalTarget.PANEL_CHROME))
                .isNull();
        }

        @Test
        void stillSoundsWhileAnyOneLevelIsAudible() {
            // The other side of the rule above, and the reason silence is read off the whole balance:
            // one role covers every arrival, so a look silenced because one slider reached the bottom
            // would take the two still set with it.
            sidebarSettingsMock.setArrivalVolumes(new PointerArrivalVolumes(0f, 0f, LAST_AUDIBLE_VOLUME));

            assertThat(SidebarSounds
                    .buildSoundScheme()
                    .resolvePointerArrivalCueFor(PointerArrivalTarget.LISTED_ITEM))
                .isEqualTo(new UiSoundCue(StarsectorUiSound.BUTTON_MOUSEOVER, LAST_AUDIBLE_VOLUME));
        }
    }
}
