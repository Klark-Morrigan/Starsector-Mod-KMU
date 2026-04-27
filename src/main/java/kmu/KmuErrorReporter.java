package kmu;

@FunctionalInterface
public interface KmuErrorReporter {
    void report(String message, RuntimeException cause);

    static KmuErrorReporter noop() {
        return (message, cause) -> {
        };
    }
}
