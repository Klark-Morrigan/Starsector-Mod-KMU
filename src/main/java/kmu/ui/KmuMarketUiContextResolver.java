package kmu.ui;

import java.util.Optional;

public interface KmuMarketUiContextResolver {
    Optional<KmuMarketUiContext> findCurrentMarketContext();
}
