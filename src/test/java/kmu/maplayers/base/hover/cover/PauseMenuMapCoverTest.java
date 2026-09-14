package kmu.maplayers.base.hover.cover;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.SectorAPI;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Pins the one reading of this cover's own, which the delegation above it does not reach: what the
 * campaign is asked, and what is answered on the frames it cannot be asked at all.
 *
 * <p>That the answer then becomes the cover's is {@link FlagMapCoverTest}'s, so nothing here hands a
 * supplier in. This cover's whole substance is the two ways the campaign can be absent, and both of
 * them are reached before a menu state exists to read.
 */
final class PauseMenuMapCoverTest {

    @Nested
    class ReadLiveMenuState {

        @Test
        void readLiveMenuStateAnswersCoveredWhileTheMenuIsUp() {

            // Stubbed before the static stubbing is opened, never inside its argument: Mockito reads
            // a mock arranged mid-statement as an unfinished stubbing of the outer one.
            var sectorMock = stubSectorShowingMenu(true);

            try (var globalMock = mockStatic(Global.class)) {

                globalMock.when(Global::getSector).thenReturn(sectorMock);

                assertThat(PauseMenuMapCover.readLiveMenuState())
                    .isTrue();
            }
        }

        @Test
        void readLiveMenuStateAnswersUncoveredWhileNoMenuIsUp() {

            var sectorMock = stubSectorShowingMenu(false);

            try (var globalMock = mockStatic(Global.class)) {

                globalMock.when(Global::getSector).thenReturn(sectorMock);

                assertThat(PauseMenuMapCover.readLiveMenuState())
                    .isFalse();
            }
        }

        @Test
        void readLiveMenuStateFailsOpenWithNoSector() {
            // Fails open per the role's rule: what cannot be established is not covering. There is
            // no map to hover in this state either, so the open answer costs nothing.
            try (var globalMock = mockStatic(Global.class)) {

                globalMock.when(Global::getSector)
                    .thenReturn(null);

                assertThat(PauseMenuMapCover.readLiveMenuState())
                    .isFalse();
            }
        }

        @Test
        void readLiveMenuStateFailsOpenWithNoCampaignUi() {
            // The second way the campaign can be absent, and the one an unguarded read would throw
            // on rather than answer: a sector stood up before its UI is.
            var sectorMock = mock(SectorAPI.class);
            when(sectorMock.getCampaignUI()).thenReturn(null);

            try (var globalMock = mockStatic(Global.class)) {

                globalMock.when(Global::getSector).thenReturn(sectorMock);

                assertThat(PauseMenuMapCover.readLiveMenuState())
                    .isFalse();
            }
        }

        private SectorAPI stubSectorShowingMenu(boolean isShowingMenu) {

            var campaignUiMock = mock(CampaignUIAPI.class);
            when(campaignUiMock.isShowingMenu()).thenReturn(isShowingMenu);

            var sectorMock = mock(SectorAPI.class);
            when(sectorMock.getCampaignUI()).thenReturn(campaignUiMock);

            return sectorMock;
        }
    }
}
