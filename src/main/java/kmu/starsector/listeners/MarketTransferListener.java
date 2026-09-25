package kmu.starsector.listeners;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

/**
 * Told when a colony changes hands, which vanilla fires no listener for.
 *
 * <p>Registered on a sector's listener manager like any vanilla listener, and reached the same way:
 * whatever reports transfers asks the manager for every listener of this type and tells each one.
 * That keeps the reporting side free to be another mod's adapter while every listener stays free of
 * that mod's types - a listener here is written once, whatever supplies the event, and an install
 * without a supplier simply never calls it.
 */
public interface MarketTransferListener {

    /**
     * Reports a colony that has just changed hands.
     *
     * @param market    the colony, already under its new holder
     * @param oldHolder the faction it was taken from; null where none held it
     * @param newHolder the faction that holds it now; null where none does
     * @param isCapture whether it was taken by force rather than handed over
     */
    void reportMarketTransferred(
        MarketAPI market,
        FactionAPI oldHolder,
        FactionAPI newHolder,
        boolean isCapture);
}
