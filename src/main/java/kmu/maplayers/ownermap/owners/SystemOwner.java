package kmu.maplayers.ownermap.owners;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.factions.FactionPalette;
import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.base.geometry.CellGrouping;
import kmu.maplayers.ownermap.holding.HolderGrouping;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The faction holding a star system on an owner map, paired with the two
 * palette colours its cell can draw in.
 *
 * <p>The render-ready output of the holder pipeline: a layer's own resolver settles who holds
 * each system and this pairs that answer with its palette, so the render layer consumes a plain
 * holder and never reaches into the economy or touches {@code FactionAPI}. The two colours are the
 * faction's own authored UI shades - {@link #primaryColour()} its bright colour,
 * {@link #secondaryColour()} its dark colour - and the player points each map element (fill, outer border, inner seam)
 * at one of them through the "Faction ... colour" settings. Naming them by palette
 * slot rather than by element keeps the record neutral about which element uses
 * which, since that pairing is the player's choice. Retaining the ID beside the
 * colours keeps the holder available for per-holder styling (dimming independent-held
 * space, for one) and any other per-holder behaviour, decided off the same holding the
 * fill was.
 */
public record SystemOwner(
    String factionId,
    Color primaryColour,
    Color secondaryColour) {

    /**
     * Builds the render-ready holder for one bloc: its ID paired with the two palette colours its
     * cells may draw in.
     *
     * <p>The colour faction is the grouping's, not the bloc: a faction bloc is its own colour
     * faction and a group's bloc takes its leading member's - so resolving it here keeps the
     * bloc ID, which for a group is not a faction ID, out of the {@code FactionAPI} lookup.
     * The two slots are that faction's own authored UI shades, the bright colour as primary and
     * the dark as secondary, each specified in the {@code .faction} file; which map element uses
     * which is the player's choice, made downstream in the render layer.
     *
     * <p>Here rather than beside any one resolver because nothing in it is a mechanic: every
     * layer that paints by an owner turns a bloc into a holder this way, and the filter's own
     * presence resolver reuses it under a synthetic key.
     *
     * @param sector   the sector whose {@code FactionAPI} palette is read
     * @param grouping the grouping that names the bloc's colour faction
     * @param blocId   the bloc to colour, carried on the returned holder as its ID
     * @return the render-ready holder, or null when the colour faction does not resolve, which
     *         drops the system as unowned
     */
    public static SystemOwner resolveForBloc(
            SectorAPI sector,
            HolderGrouping grouping,
            String blocId) {

        var faction = sector.getFaction(grouping.resolveColourFactionId(blocId));
        if (faction == null) {
            return null;
        }
        return new SystemOwner(blocId, faction.getBrightUIColor(), faction.getDarkUIColor());
    }

    /**
     * Maps each owned system to its holding faction's ID - the per-system holder the
     * owner-map geometry clusters by. Adapts the faction-holder map to the opaque
     * {@code Map<SystemKey, String>} the agnostic geometry ({@code CellShaper},
     * {@code SystemClusters}, {@code SystemClusterBorders}, {@code ClusterBorderTrace}) fuses on, so
     * the holder side supplies "who owns this" while the geometry stays ignorant of factions.
     *
     * @param ownerBySystemKey the holder per owned system
     * @return each system's faction ID, in the map's iteration order
     */
    public static Map<SystemKey, String> mapFactionIdBySystemKey(
            Map<SystemKey, SystemOwner> ownerBySystemKey) {

        var keyBySystemKey = new LinkedHashMap<SystemKey, String>();
        for (var entry : ownerBySystemKey.entrySet()) {
            keyBySystemKey.put(entry.getKey(), entry.getValue().factionId());
        }
        return keyBySystemKey;
    }

    /**
     * The drawn cells' grouping under a holder map: which system each cell draws as, paired
     * with each owned system's faction id. The one place the two halves are joined into a
     * {@link CellGrouping}, so every pass that groups the drawn cells groups them the same way
     * rather than each re-composing the pair.
     *
     * <p>Lives here rather than on {@code CellGrouping} so the geometry layer stays ignorant of
     * factions: this is the holder side supplying "who owns this cell", exactly as
     * {@link #mapFactionIdBySystemKey} does for the key half.
     *
     * @param systemKeyByCellKey the system each cell draws as, from the geometry cache
     * @param ownerBySystemKey   the holder per owned system
     * @return the cells grouped by the faction ID of the system each draws as
     */
    public static CellGrouping mapCellGrouping(
            Map<SystemKey, SystemKey> systemKeyByCellKey,
            Map<SystemKey, SystemOwner> ownerBySystemKey) {
        return new CellGrouping(systemKeyByCellKey, mapFactionIdBySystemKey(ownerBySystemKey));
    }

    /**
     * This holder's two shades as the pair every palette rule is stated over, so a caller that
     * needs the pair asks for it rather than re-pairing the two accessors at each site. The
     * slots keep their meaning exactly - bright into primary, dark into secondary.
     *
     * @return the holder's own palette
     */
    public FactionPalette resolvePalette() {
        return new FactionPalette(primaryColour, secondaryColour);
    }
}
