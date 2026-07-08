package kmu.starsector.nexerelin;

import java.util.List;

/**
 * A supplier of the current alliance set as plain {@link AllianceRecord}s, inverting
 * the Nexerelin static call behind an interface so the grouping pipeline depends on
 * this port rather than on {@code exerelin.*}. The live implementation
 * ({@link NexAllianceSource}) is the only class that imports a Nexerelin type; a test
 * can stand in a fake source of hand-built records to exercise the factory with no
 * game running.
 */
public interface AllianceSource {

    /**
     * The alliances in play right now, each already flattened to plain data. Empty
     * when there are no alliances, or when Nexerelin's alliance manager has not been
     * created yet (very early in a session).
     *
     * @return the current alliance records, never null
     */
    List<AllianceRecord> readAlliances();
}
