package kmu.maplayers.base.render;

import com.fs.starfarer.campaign.CampaignTerrain;

/**
 * The map layers' terrain entity for Starscape mode: it resolves its spec and its plugin from the
 * Starscape terrain type it is constructed with, while reporting the one terrain type the sector map
 * draws in Starscape mode.
 *
 * <p>The map widget's Starscape exception is a raw string compare against the literal
 * {@code "slipstream"}, and it takes that string from the entity rather than from the plugin, so
 * the whitelisted identity has to live here. It can be reported permanently because nothing inside
 * {@link CampaignTerrain} consults the getter: the type handed to the constructor is kept in a
 * private final field, and both the spec lookup and the plugin instantiation read that field. The
 * entity is thus a map-layer Starscape terrain to the game's own resolution, and a slipstream only
 * to the map's draw filter.
 *
 * <p>Reporting a slipstream type is safe against slipstream behaviour, which never matches on the
 * string: it finds its terrain by plugin type or through the location's own slipstream registry
 * entry, and this entity appears in neither. The residual exposure is third-party code that
 * compares a terrain type to a string, an idiom that exists in the wild for asteroid belts and
 * rings.
 *
 * <p>One class serves every Starscape surface. Reporting the whitelisted type is the entirety of
 * what this entity does, and it is already told at construction which type ID to resolve its spec
 * and plugin from, so the surfaces differ by the ID they are built with rather than by class. A
 * second shell restating the trick would be the same rationale written twice, of which one goes
 * stale at the first thing learned about the widget's filter.
 *
 * <p>It stays a shell with no logic of its own. Its supertype chain reaches obfuscated core classes
 * carrying members whose names are not legal Java identifiers, so a verifying JVM refuses to load
 * it anywhere but in the running game; every rule worth stating therefore lives in
 * {@link SectorMapLayerStarscapeTerrainPlugin} instead, which loads anywhere.
 */
public class SectorMapLayerStarscapeTerrain extends CampaignTerrain {

    // The one terrain type the map widget draws while the Starscape filter is on.
    private static final String WHITELISTED_MAP_TYPE = "slipstream";

    /**
     * @param type the registered terrain type to resolve the spec and plugin from - the map layers'
     *             Starscape type, not the type this entity goes on to report
     */
    public SectorMapLayerStarscapeTerrain(String type) {
        // No plugin params: the plugin is a map drawer that reads system positions itself, so there
        // is nothing to hand it at construction.
        super(type, null);
    }

    @Override
    public String getType() {
        return WHITELISTED_MAP_TYPE;
    }
}
