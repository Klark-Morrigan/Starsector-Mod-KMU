package kmu.maplayers.ownermap.owners.holders.base;

import com.fs.starfarer.api.Global;

import kmu.maplayers.ownermap.owners.SpotlitOwners;
import kmu.maplayers.ownermap.owners.SystemOwner;

import org.apache.log4j.Logger;

import java.util.Objects;
import java.util.Optional;

/**
 * The bloc a map is spotlighting, paired with the one holder every system it reaches draws as.
 *
 * <p>Here rather than in any one mechanic, because a layer can paint by several and each of them
 * rekeys the systems it gives the bloc. Opened once per batch or rebuild and handed to every
 * mechanic that rekeys, so the bloc's cells fuse through one value rather than through holders
 * resolved apart.
 *
 * <p>Off the pass's own palettes, so the spotlight paints the bloc in the colours its bands take.
 *
 * @param blocId the spotlighted bloc's ID
 * @param holder the holder every system the bloc reaches draws as
 */
public record BlocSpotlight(
    String blocId,
    SystemOwner holder) {

    private static final Logger LOG = Global.getLogger(BlocSpotlight.class);

    public BlocSpotlight {
        Objects.requireNonNull(blocId, "blocId");
        Objects.requireNonNull(holder, "holder");
    }

    /**
     * Opens the spotlight a pass paints the selected bloc under.
     *
     * <p>A selectable bloc's colour faction resolves. One gone mid-session leaves nothing to draw
     * the bloc in, so no spotlight opens and every system draws as its real holder. One policy for
     * every mechanic, so no cell of the bloc can leave the map while another beside it stays.
     *
     * @param pass           the pass whose palettes colour the bloc
     * @param selectedBlocId the spotlighted bloc's ID, or null off filter
     * @return the spotlight; empty off filter, or where the bloc's colour faction has gone
     */
    public static Optional<BlocSpotlight> openOver(HolderPass pass, String selectedBlocId) {

        if (selectedBlocId == null) {
            return Optional.empty();
        }

        var holder = SpotlitOwners.createSpotlitOwner(pass.palettes().readBlocPalette(selectedBlocId));
        if (holder == null) {

            LOG.warn("Spotlit bloc " + selectedBlocId
                + " has no colour faction left; every system draws as its real holder");

            return Optional.empty();
        }

        return Optional.of(new BlocSpotlight(selectedBlocId, holder));
    }

    /**
     * @param ownerId the bloc ID an owner carries
     * @return true when it is the spotlighted bloc
     */
    public boolean isSpotlitBloc(String ownerId) {
        return blocId.equals(ownerId);
    }
}
