package kmu.maplayers.base.faces;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.factions.FactionNames;
import kmlib.starsector.markets.SectorMarkets;
import kmlib.starsector.strings.StarsectorStrings;
import kmlib.starsector.systems.SectorStarSystems;
import kmlib.starsector.ui.font.FontAtlas;
import kmlib.starsector.ui.font.SettledFaceMemo;
import kmlib.starsector.ui.font.StarsectorFont;
import kmlib.starsector.ui.font.installed.InstalledFaces;

import kmu.KmuMod;
import kmu.maplayers.base.machinery.InstalledMachinery;
import kmu.maplayers.base.machinery.SectorMapMachinery;
import kmu.maplayers.base.machinery.SectorMapMachineryIndex;
import kmu.util.KmuStringKeys;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The face each KMU text draws in on one sector: KMLib's {@link SettledFaceMemo} held as one of the
 * sector's installed machinery, reading the sector's names and KMU's own strings as its kinds of text.
 *
 * <p>Held per sector because the names are the sector's, and released with the sector's machinery. Every
 * name the sector holds is read, discovered or not: a name the player has yet to see is one a face will
 * draw later in the same sector.
 *
 * <p>Each text keeps the face it asks for where there is no sector to read - the detached machinery,
 * standing in when no game is loaded - since there is nothing to hold the face to.
 */
public final class SettledFaces implements InstalledMachinery {

    private final SettledFaceMemo<ProbedText> faceMemo;

    SettledFaces(SettledFaceMemo<ProbedText> faceMemo) {
        this.faceMemo = faceMemo;
    }

    /**
     * @param machinery the sector's machinery
     * @return the faces settled on that sector, settling none until asked
     */
    public static SettledFaces resolveFacesIn(SectorMapMachinery machinery) {
        return machinery.resolveMachinery(
            SettledFaces.class,
            () -> createForSector(machinery.resolveSector()));
    }

    /**
     * For a surface vanilla drives without naming a sector.
     *
     * @return the faces settled on the sector the game is running, or the unsettled ones where none is
     */
    public static SettledFaces resolveFacesForLiveSector() {
        return resolveFacesIn(SectorMapMachineryIndex.resolveMachineryForLiveSector());
    }

    /**
     * The face a text draws in on this sector.
     *
     * @param requestedFont the face the text asks for
     * @param probes        the kinds of text it is made of, held by the caller as a constant
     * @return the face the text settles on
     */
    public FontAtlas settleFace(StarsectorFont requestedFont, Set<ProbedText> probes) {
        return faceMemo.settleFace(requestedFont, probes);
    }

    @Override
    public void disposeMachinery() {
        faceMemo.discardFaces();
    }

    // The faces of one sector, settled against the running game's install; of no sector, unsettled.
    static SettledFaces createForSector(SectorAPI sector) {
        return new SettledFaces(sector == null
            ? SettledFaceMemo.createUnsettled()
            : InstalledFaces.createFaceMemo(probe -> readTexts(sector, probe)));
    }

    // One kind of text off the sector and the running build's strings.
    static List<String> readTexts(SectorAPI sector, ProbedText probe) {
        return switch (probe) {
            case FACTION_NAMES -> FactionNames.listEveryName(sector);
            case PLACE_NAMES -> readPlaceNames(sector);
            case MOD_STRINGS -> StarsectorStrings.listCategoryStrings(KmuMod.MOD_ID, KmuStringKeys.CATEGORY);
        };
    }

    // Star systems and colonies both: the box titles a system and lists its colonies, and the sidebar rows
    // name either.
    private static List<String> readPlaceNames(SectorAPI sector) {

        var names = new ArrayList<>(SectorStarSystems.listSystemNames(sector));

        names.addAll(SectorMarkets.listMarketNames(sector));

        return names;
    }
}
