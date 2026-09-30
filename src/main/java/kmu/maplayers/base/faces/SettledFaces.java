package kmu.maplayers.base.faces;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.ui.font.FaceResolver;
import kmlib.starsector.ui.font.FontAtlas;
import kmlib.starsector.ui.font.StarsectorFont;
import kmlib.starsector.ui.font.installed.InstalledFaces;

import kmu.maplayers.base.machinery.InstalledMachinery;
import kmu.maplayers.base.machinery.SectorMapMachinery;
import kmu.maplayers.base.machinery.SectorMapMachineryIndex;

import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * The face each KMU text draws in on one sector: the face it asks for where the installed atlas holds
 * everything that text may say, and otherwise the first face down its fallback walk that does.
 *
 * <p>A text names the face it asks for and the kinds of text it is made of; the answer is settled on first
 * asking and held for the sector's life. Held rather than resolved per paint, because settling reads every
 * glyph of every faction and place name the sector holds, and held per sector because those names are the
 * sector's. Two texts asking for the same face against the same kinds share one answer, which is what
 * keeps a row measured in one face from being painted in another.
 *
 * <p>Each text keeps the face it asks for where there is no sector to read - the detached machinery,
 * standing in when no game is loaded - since there is nothing to hold the face to.
 */
public final class SettledFaces implements InstalledMachinery {

    private static final Logger LOG = Global.getLogger(SettledFaces.class);

    private final Map<ProbedText, List<String>> textsByProbe = new EnumMap<>(ProbedText.class);
    private final Map<FaceRequest, FontAtlas> faceByRequest = new HashMap<>();

    private final SectorAPI sector;
    private final Supplier<FaceResolver> faceResolverSource;
    private final BiFunction<SectorAPI, ProbedText, List<String>> probeTextReader;

    private FaceResolver faceResolver;

    /**
     * @param sector             the sector whose names the faces are held to; null keeps every text in the
     *                           face it asks for
     * @param faceResolverSource builds the resolver the faces are settled through, on first settling
     * @param probeTextReader    reads a kind of text off the sector
     */
    SettledFaces(
            SectorAPI sector,
            Supplier<FaceResolver> faceResolverSource,
            BiFunction<SectorAPI, ProbedText, List<String>> probeTextReader) {

        this.sector = sector;
        this.faceResolverSource = faceResolverSource;
        this.probeTextReader = probeTextReader;
    }

    /**
     * @param machinery the sector's machinery
     * @return the faces settled on that sector, settling none until asked
     */
    public static SettledFaces resolveFacesIn(SectorMapMachinery machinery) {

        return machinery.resolveMachinery(
            SettledFaces.class,
            () -> new SettledFaces(
                machinery.resolveSector(),
                InstalledFaces::createFaceResolver,
                ProbeTexts::readTexts));
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
     * @param probes        the kinds of text it is made of
     * @return the face the text settles on
     */
    public FontAtlas settleFace(StarsectorFont requestedFont, Set<ProbedText> probes) {

        if (sector == null) {
            return requestedFont;
        }
        return faceByRequest.computeIfAbsent(
            new FaceRequest(requestedFont, Set.copyOf(probes)),
            this::resolveFace);
    }

    @Override
    public void disposeMachinery() {

        faceByRequest.clear();
        textsByProbe.clear();
    }

    // A face moved off the one its text asked for says so, once per sector: which atlas a text lands on is
    // the first thing a report of it drawing wrongly is read against.
    private FontAtlas resolveFace(FaceRequest request) {

        if (faceResolver == null) {
            faceResolver = faceResolverSource.get();
        }
        var texts = new ArrayList<String>();

        for (var probe : request.probes()) {
            texts.addAll(textsByProbe.computeIfAbsent(probe, kind -> probeTextReader.apply(sector, kind)));
        }

        var settledFace = faceResolver.resolveFont(request.requestedFont(), texts);

        if (!settledFace.equals(request.requestedFont())) {

            LOG.info("Text asking for " + request.requestedFont().getBasename() + " against " + request.probes()
                + " draws in " + settledFace.resolvePath() + " on this sector");
        }
        return settledFace;
    }

    // What a face is settled for: the face asked for and the kinds of text held to it.
    private record FaceRequest(
        StarsectorFont requestedFont,
        Set<ProbedText> probes) {
    }
}
