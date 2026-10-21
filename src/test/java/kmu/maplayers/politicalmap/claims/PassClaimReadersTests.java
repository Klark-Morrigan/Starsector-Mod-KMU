package kmu.maplayers.politicalmap.claims;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.systems.SectorPassIndex;
import kmlib.starsector.systems.claims.ClaimBreakdownReader;
import kmlib.starsector.systems.claims.ClaimBreakdownReaderSource;
import kmlib.starsector.systems.claims.ClaimReader;
import kmlib.starsector.systems.claims.ClaimReaderSource;

import kmu.maplayers.base.visibility.colonies.ColonyKnowledge;
import kmu.maplayers.base.visibility.colonies.ColonyVisibility;
import kmu.maplayers.ownermap.owners.holders.base.HolderPass;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static kmu.maplayers.base.visibility.colonies.ColonyVisibilityFixtures.UNDER_THE_REVEAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins that a claim reader is opened over the pass's own walk and the pass's own colony rule
 * together, never one of them beside some other reading's.
 */
final class PassClaimReadersTests {

    @Nested
    class OpenBreakdownReaderOver {

        @Test
        void handsTheSourceThePassesOwnWalkAndColonyRule() {

            var passMock = mock(HolderPass.class);
            var colonyKnowledgeMock = mock(ColonyKnowledge.class);
            var sectorIndexMock = mock(SectorPassIndex.class);
            var claimReaderSourceMock = mock(ClaimBreakdownReaderSource.class);
            var claimReaderMock = mock(ClaimBreakdownReader.class);

            when(passMock.colonyKnowledge())
                .thenReturn(colonyKnowledgeMock);
            when(passMock.sectorIndex())
                .thenReturn(sectorIndexMock);
            when(claimReaderSourceMock.openReaderOver(colonyKnowledgeMock, sectorIndexMock))
                .thenReturn(claimReaderMock);

            assertThat(PassClaimReaders.openBreakdownReaderOver(passMock, claimReaderSourceMock))
                .isSameAs(claimReaderMock);
        }

        @Test
        void handsTheSourceTheWalkAndTheColonyRuleItIsHanded() {
            // A breakdown reports the player's knowledge as a flag on each market rather than by
            // leaving the market out, so a reader opened under any rule but the one handed would
            // surface only as a name a box should have withheld. And a reader opened with no walk
            // reads the running game's sector rather than the one handed.
            var sectorIndex = new SectorPassIndex(mock(SectorAPI.class));
            var openedRules = new ArrayList<ColonyVisibility>();
            var openedIndexes = new ArrayList<SectorPassIndex>();

            PassClaimReaders.openBreakdownReaderOver(
                sectorIndex,
                UNDER_THE_REVEAL,
                (knownColonyReader, openedIndex) -> {
                    openedRules.add(((ColonyKnowledge) knownColonyReader).rule());
                    openedIndexes.add(openedIndex);
                    return mock(ClaimBreakdownReader.class);
                });

            assertThat(openedRules)
                .containsExactly(UNDER_THE_REVEAL);
            assertThat(openedIndexes)
                .containsExactly(sectorIndex);
        }
    }

    @Nested
    class OpenClaimReaderOver {

        @Test
        void handsTheSourceThePassesOwnWalkAndColonyRule() {

            var passMock = mock(HolderPass.class);
            var colonyKnowledgeMock = mock(ColonyKnowledge.class);
            var sectorIndexMock = mock(SectorPassIndex.class);
            var claimReaderSourceMock = mock(ClaimReaderSource.class);
            var claimReaderMock = mock(ClaimReader.class);

            when(passMock.colonyKnowledge())
                .thenReturn(colonyKnowledgeMock);
            when(passMock.sectorIndex())
                .thenReturn(sectorIndexMock);
            when(claimReaderSourceMock.openReaderOver(colonyKnowledgeMock, sectorIndexMock))
                .thenReturn(claimReaderMock);

            assertThat(PassClaimReaders.openClaimReaderOver(passMock, claimReaderSourceMock))
                .isSameAs(claimReaderMock);
        }
    }
}
