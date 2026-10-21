package kmu.starsector;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorAPI;

import java.awt.Color;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Sectors and the factions they look up by ID, for every suite whose subject reads a faction's names,
 * crest or shades off a sector.
 *
 * <p>One home for them because what "a faction the sector knows" stubs is setup rather than subject
 * wherever it appears. Written out per suite, the copies drift: one stubs a crest another leaves null,
 * and each goes on agreeing with itself while the suites disagree.
 *
 * <p>A faction is stubbed onto a sector rather than handed back with one, so a suite needing two of them
 * states each in a line against the same sector - and a faction never stubbed at all is exactly the
 * unknown-ID case. Each stub hands the faction back, so a suite that cares about one more read stubs it
 * on the same mock rather than standing a second one up the sector does not return.
 *
 * <p>Every faction is built before the sector is told about it: stubbing one mock inside another stub's
 * argument leaves Mockito mid-stubbing and fails the whole fixture.
 */
public final class SectorFactionFixtures {

    /**
     * The ID of the faction {@link #buildSectorKnowingHegemony()} knows.
     */
    public static final String HEGEMONY = "hegemony";

    /**
     * The crest the Hegemony of {@link #buildSectorKnowingHegemony()} is authored with.
     */
    public static final String HEGEMONY_CREST = "graphics/hegemony_crest.png";

    /**
     * The long display title the Hegemony of {@link #buildSectorKnowingHegemony()} is authored with.
     */
    public static final String HEGEMONY_LONG_NAME = "The Hegemony";

    // Fixtures only; never instantiated.
    private SectorFactionFixtures() {
    }

    /**
     * Builds a sector that knows nothing, for the case where a read has only an ID to go on.
     *
     * @return the sector, with no faction stubbed onto it
     */
    public static SectorAPI buildEmptySector() {
        return mock(SectorAPI.class);
    }

    /**
     * Builds a sector knowing one faction, the Hegemony, by its long title and its crest - the faction
     * a suite names when what it is about is how a known faction presents.
     *
     * @return the sector, with {@link #HEGEMONY} stubbed onto it
     */
    public static SectorAPI buildSectorKnowingHegemony() {

        var sectorMock = buildEmptySector();

        stubFaction(sectorMock, HEGEMONY, HEGEMONY_LONG_NAME, HEGEMONY_CREST);

        return sectorMock;
    }

    /**
     * Builds a sector holding one faction with its two authored shades under {@code factionId}, and
     * nothing under any other ID, so a case can tell a lookup that went through the ID it meant from one
     * that fell back.
     *
     * @param factionId    the ID the faction answers to
     * @param brightColour the faction's bright colour
     * @param darkColour   the faction's dark colour
     * @return the sector mock
     */
    public static SectorAPI buildSectorShadingFaction(
            String factionId,
            Color brightColour,
            Color darkColour) {

        var sectorMock = buildEmptySector();

        stubShadedFaction(sectorMock, factionId, brightColour, darkColour);

        return sectorMock;
    }

    /**
     * Stubs how one faction already on {@code sectorMock} stands toward another, for a suite whose
     * case turns on a disposition rather than on a name.
     *
     * <p>Layered onto the faction the sector already answers with rather than standing a second one
     * up, so a faction's name, its crest and its standings are the one faction the subject reads - two
     * mocks under one ID would leave whichever was stubbed last answering all three.
     *
     * <p>A faction never asked about answers no standing at all, which is the indifferent case every
     * suite gets without saying anything.
     *
     * @param sectorMock     the sector the faction was stubbed onto
     * @param factionId      the faction whose disposition is being stated
     * @param otherFactionId the faction it is disposed toward
     * @param standing       how it stands toward them
     */
    public static void stubDispositionToward(
            SectorAPI sectorMock,
            String factionId,
            String otherFactionId,
            RepLevel standing) {

        when(sectorMock.getFaction(factionId).getRelationshipLevel(otherFactionId))
            .thenReturn(standing);
    }

    /**
     * Stubs one faction onto {@code sectorMock} by what a tooltip line naming it reads - its long
     * display title and its crest.
     *
     * @param sectorMock      the sector the faction is stubbed onto
     * @param factionId       the ID the faction answers to
     * @param longName        the faction's long display title
     * @param crestSpritePath the faction's crest path; null or blank stands for a faction with none
     *                        authored
     * @return the stubbed faction
     */
    public static FactionAPI stubFaction(
            SectorAPI sectorMock,
            String factionId,
            String longName,
            String crestSpritePath) {

        var factionMock = mock(FactionAPI.class);

        when(factionMock.getDisplayNameLong())
            .thenReturn(longName);
        when(factionMock.getCrest())
            .thenReturn(crestSpritePath);

        return registerFaction(sectorMock, factionId, factionMock);
    }

    /**
     * Stubs one faction onto {@code sectorMock} that answers nothing past being known, for a case
     * about a read the other stubs do not cover.
     *
     * @param sectorMock the sector the faction is stubbed onto
     * @param factionId  the ID the faction answers to
     * @return the stubbed faction
     */
    public static FactionAPI stubKnownFaction(SectorAPI sectorMock, String factionId) {
        return registerFaction(sectorMock, factionId, mock(FactionAPI.class));
    }

    /**
     * Stubs one faction onto {@code sectorMock} by its short display name alone.
     *
     * @param sectorMock the sector the faction is stubbed onto
     * @param factionId  the ID the faction answers to
     * @param shortName  the faction's short display name
     * @return the stubbed faction
     */
    public static FactionAPI stubNamedFaction(
            SectorAPI sectorMock,
            String factionId,
            String shortName) {

        var factionMock = mock(FactionAPI.class);

        when(factionMock.getDisplayName())
            .thenReturn(shortName);

        return registerFaction(sectorMock, factionId, factionMock);
    }

    /**
     * Stubs one faction onto {@code sectorMock} by both its names.
     *
     * @param sectorMock the sector the faction is stubbed onto
     * @param factionId  the ID the faction answers to
     * @param shortName  the faction's short display name
     * @param longName   the faction's long display title
     * @return the stubbed faction
     */
    public static FactionAPI stubNamedFaction(
            SectorAPI sectorMock,
            String factionId,
            String shortName,
            String longName) {

        var factionMock = mock(FactionAPI.class);

        when(factionMock.getDisplayName())
            .thenReturn(shortName);
        when(factionMock.getDisplayNameLong())
            .thenReturn(longName);

        return registerFaction(sectorMock, factionId, factionMock);
    }

    /**
     * Stubs one faction onto {@code sectorMock} by its two authored UI shades - the bright colour and
     * the dark one. The two arrive stated rather than one derived from the other, so a case asserting
     * on the dark one names a literal instead of computing the expectation it is about to check.
     *
     * @param sectorMock   the sector the faction is stubbed onto
     * @param factionId    the ID the faction answers to
     * @param brightColour the faction's bright colour
     * @param darkColour   the faction's dark colour
     * @return the stubbed faction
     */
    public static FactionAPI stubShadedFaction(
            SectorAPI sectorMock,
            String factionId,
            Color brightColour,
            Color darkColour) {

        var factionMock = mock(FactionAPI.class);

        when(factionMock.getBrightUIColor())
            .thenReturn(brightColour);
        when(factionMock.getDarkUIColor())
            .thenReturn(darkColour);

        return registerFaction(sectorMock, factionId, factionMock);
    }

    private static FactionAPI registerFaction(
            SectorAPI sectorMock,
            String factionId,
            FactionAPI factionMock) {

        when(sectorMock.getFaction(factionId))
            .thenReturn(factionMock);

        return factionMock;
    }
}
