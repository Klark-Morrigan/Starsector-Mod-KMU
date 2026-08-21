package kmu.starsector.listeners;

import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;

import java.util.ArrayList;
import java.util.List;

/**
 * A listener manager that records what was registered with it rather than registering anything.
 *
 * <p>What every installer suite asserts against, since an installer's whole observable behaviour is
 * the calls it makes here: which classes it cleared first, what it added, and under which flag.
 * Recording all three is what lets a suite tell the two registration shapes apart - a has-check that
 * skips, and a remove-then-add that always registers.
 *
 * <p>Shared rather than restated per suite so "what an install looks like from the manager's side"
 * has one answer. A suite that built its own would be free to record only the half it happened to
 * assert on, and the next case written against it would have nothing to check.
 */
public final class RecordingListenerManager implements ListenerManagerAPI {

    private final boolean hasExistingListener;
    private final List<Object> addedListeners = new ArrayList<>();

    // Parallel to addedListeners: the engine's second addListener parameter means transient (true
    // keeps the listener out of the save), so the recording mirrors that vocabulary. One install can
    // add several listeners, so these are lists, not scalars.
    private final List<Boolean> addedTransientFlags = new ArrayList<>();
    private final List<Class<?>> removedListenerClasses = new ArrayList<>();

    /**
     * @param hasExistingListener what the presence check answers, whichever class is asked about -
     *                            the state a reloaded save puts a persistent registration in
     */
    public RecordingListenerManager(boolean hasExistingListener) {
        this.hasExistingListener = hasExistingListener;
    }

    /** Every listener registered, in registration order. */
    public List<Object> getAddedListeners() {
        return addedListeners;
    }

    /** The flag each registration was made under, positionally matching the listeners above. */
    public List<Boolean> getAddedTransientFlags() {
        return addedTransientFlags;
    }

    /** Every class cleared before a registration, in the order they were cleared. */
    public List<Class<?>> getRemovedListenerClasses() {
        return removedListenerClasses;
    }

    @Override
    public void addListener(Object listener) {
        addedListeners.add(listener);
        addedTransientFlags.add(false);
    }

    @Override
    public void addListener(Object listener, boolean isTransient) {
        addedListeners.add(listener);
        addedTransientFlags.add(isTransient);
    }

    @Override
    public void removeListener(Object listener) {
    }

    @Override
    public void removeListenerOfClass(Class<?> listenerClass) {
        removedListenerClasses.add(listenerClass);
    }

    @Override
    public boolean hasListener(Object listener) {
        return false;
    }

    @Override
    public boolean hasListenerOfClass(Class<?> listenerClass) {
        return hasExistingListener;
    }

    @Override
    public <T> List<T> getListeners(Class<T> listenerClass) {
        return List.of();
    }
}
