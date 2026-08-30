package kmu.maplayers.politicalmap.base.tooltip;

import kmu.maplayers.base.visibility.colonies.ColonyDiscoveryLookup;
import kmu.maplayers.base.visibility.colonies.ColonyKindLookup;
import kmu.maplayers.base.visibility.colonies.OpenlyKnownColonyLookup;

import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The box's walk of a system, posed as a case needs it: one colony the player has yet to find, or
 * one the box has something to remark on.
 *
 * <p>Shared by the two row resolvers' suites because they pose the same two systems. Each holds a
 * carrier the other has never heard of - a claim breakdown against a dominance weight - but neither
 * carries what a reading answers, which is exactly why one reading serves both.
 */
final class SystemColonyReadingFixture {

    /**
     * What a remarked line runs on into. Stated verbatim rather than composed, the suites taking
     * this being about which line carries a remark rather than about how one reads.
     */
    static final String LAST_SEEN = "last seen 34 days ago (c206.05.12)";

    private SystemColonyReadingFixture() {
    }

    /**
     * A walk of a system holding one colony whose entity the player has yet to discover, which is
     * the half of a line's account neither a score nor a weight can supply.
     *
     * @param colonyId the colony's market id, as the account listing it carries
     * @return a reading answering that colony undiscovered and every other discovered
     */
    static SystemColonyReading buildReadingWithUndiscovered(String colonyId) {
        return new SystemColonyReading(
            ColonyKindLookup.NONE,
            new ColonyDiscoveryLookup(Set.of(colonyId)),
            OpenlyKnownColonyLookup.NONE,
            ColonyObservationNotes.NONE);
    }

    /**
     * A walk of a system holding one concealed colony the sector openly points at, which is the
     * other half of a line's account no carrier of either box holds.
     *
     * @param colonyId the colony's market id, as the account listing it carries
     * @return a reading answering that colony openly known and every other a secret
     */
    static SystemColonyReading buildReadingWithOpenlyKnown(String colonyId) {
        return new SystemColonyReading(
            ColonyKindLookup.NONE,
            ColonyDiscoveryLookup.NONE,
            new OpenlyKnownColonyLookup(Set.of(colonyId)),
            ColonyObservationNotes.NONE);
    }

    /**
     * A walk remarking on exactly one colony. The notes are mocked because what makes a remark due
     * is the notes' own question, pinned by {@link ColonyObservationNotesTest}; what the suites
     * taking this are about is which line carries the answer.
     *
     * @param colonyId the colony's market id, as the account listing it carries
     * @return a reading remarking on that colony and on nothing else
     */
    static SystemColonyReading buildReadingRemarkingOn(String colonyId) {

        var notesMock = mock(ColonyObservationNotes.class);

        when(notesMock.resolveLastSeenNote(colonyId))
            .thenReturn(Optional.of(LAST_SEEN));

        // The one thing the notes do to a line is left to run for real, since which line the remark
        // lands on is what the suites taking this assert. Every other colony answers the unstubbed
        // empty, so nothing else is touched.
        when(notesMock.remarkOnColony(any(), any()))
            .thenCallRealMethod();

        return new SystemColonyReading(
            ColonyKindLookup.NONE,
            ColonyDiscoveryLookup.NONE,
            OpenlyKnownColonyLookup.NONE,
            notesMock);
    }
}
