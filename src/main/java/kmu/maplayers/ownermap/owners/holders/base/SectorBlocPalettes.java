package kmu.maplayers.ownermap.owners.holders.base;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.colour.ColourPair;
import kmlib.starsector.factions.FactionColours;
import kmlib.starsector.factions.SectorFactions;

import kmu.maplayers.ownermap.owners.SystemOwner;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The live binding of {@link BlocPaletteReader}: a bloc's two shades read off the faction it
 * paints in.
 *
 * <p>A bloc is not always a faction - a group is named by a synthetic ID no
 * {@code FactionAPI} answers to - so the grouping names the colour faction first and the sector is
 * asked for that. The two shades are the faction's own authored UI pair, so whatever is drawn for a
 * bloc draws it in the same colours its fills and borders already give it.
 *
 * <p>Holds the sector and grouping the pass sampled rather than reading either live, so everything
 * one pass colours for a bloc is coloured off the one snapshot the fills were.
 *
 * <p>Remembers each bloc's owner on first ask, for as long as the pass that holds it. Every
 * system's holder, every cell's band and the spotlight each ask per bloc, so a pass asks of a
 * handful of blocs many times over. Not safe for concurrent use.
 */
public final class SectorBlocPalettes implements BlocPaletteReader {

    // Each bloc's owner as first resolved, a bloc whose colour faction has gone held as empty so its
    // absence is remembered too. The owner rather than the bare shades, since it carries them and
    // every system that bloc holds can then share the one value.
    private final Map<String, Optional<SystemOwner>> ownerByBlocId = new HashMap<>();

    private final SectorAPI sector;
    private final HolderGrouping grouping;

    /**
     * @param sector   the sector the colour faction is read from
     * @param grouping the grouping that names each bloc's colour faction, sampled once by the
     *                 pass so everything drawn for a bloc resolves its colours identically
     */
    public SectorBlocPalettes(SectorAPI sector, HolderGrouping grouping) {
        this.sector = sector;
        this.grouping = grouping;
    }

    @Override
    public ColourPair readBlocPalette(String blocId) {

        var owner = resolveOwnerOf(blocId);
        return owner == null
            ? null
            : owner.palette();
    }

    /**
     * The render-ready owner for one bloc: its ID paired with the two shades its cells may draw in.
     *
     * <p>The colour faction is the grouping's, not the bloc: a faction bloc is its own colour
     * faction and a group's bloc takes its leading member's - so resolving it here keeps the bloc
     * ID, which for a group is not a faction ID, out of the {@code FactionAPI} lookup. Which map
     * element uses which slot is the player's choice, made downstream in the render stages.
     *
     * <p>Every holder source turns a bloc into an owner this way, and the filter's own presence
     * resolver reuses its shades under a synthetic key.
     *
     * @param blocId the bloc to colour, carried on the returned owner as its ID
     * @return the render-ready owner, the same value on every ask, or null when the colour faction
     *         does not resolve, which drops the system as unowned
     */
    public SystemOwner resolveOwnerOf(String blocId) {

        return ownerByBlocId
            .computeIfAbsent(blocId, this::findOwnerOf)
            .orElse(null);
    }

    // One bloc's owner read off the sector, for the memo above to remember. A faction gone from the
    // sector yields none: painting it a stand-in colour would put colour on the map for something
    // the map cannot name.
    private Optional<SystemOwner> findOwnerOf(String blocId) {

        var colourFaction = SectorFactions.findFaction(sector, grouping.resolveColourFactionId(blocId));

        return Optional
            .ofNullable(FactionColours.findPalette(colourFaction))
            .map(palette -> new SystemOwner(blocId, palette));
    }
}
