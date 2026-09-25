package kmu.maplayers.politicalmap.claims;

import kmlib.starsector.systems.SectorPassIndex;
import kmlib.starsector.systems.claims.ClaimReader;
import kmlib.starsector.systems.claims.ClaimReaderSource;

import kmu.maplayers.base.visibility.colonies.ColonyKnowledge;
import kmu.maplayers.ownermap.holding.HolderPass;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins that a claim reader is opened over the pass's own walk and the pass's own colony rule
 * together, never one of them beside some other reading's.
 */
final class PassClaimReadersTest {

    @Nested
    class OpenClaimReaderOver {

        @Test
        void openClaimReaderOverHandsTheSourceThePassesOwnWalkAndColonyRule() {

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
