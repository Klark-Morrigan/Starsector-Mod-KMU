package kmu.ui.context;

import java.util.Optional;

public interface KmuMarketUiContextResolver {
    Optional<KmuMarketUiContext> findCurrentMarketContext();
}
