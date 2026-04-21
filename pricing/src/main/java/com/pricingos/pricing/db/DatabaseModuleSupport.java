package com.pricingos.pricing.db;

import com.jackfruit.scm.database.adapter.PackagingAdapter;
import com.jackfruit.scm.database.adapter.PricingAdapter;
import com.jackfruit.scm.database.facade.SupplyChainDatabaseFacade;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

public final class DatabaseModuleSupport {

    private DatabaseModuleSupport() {
    }

    public static <T> T withFacade(Function<SupplyChainDatabaseFacade, T> work) {
        Objects.requireNonNull(work, "work cannot be null");
        try (SupplyChainDatabaseFacade facade = new SupplyChainDatabaseFacade()) {
            return work.apply(facade);
        }
    }

    public static void useFacade(Consumer<SupplyChainDatabaseFacade> work) {
        Objects.requireNonNull(work, "work cannot be null");
        withFacade(facade -> {
            work.accept(facade);
            return null;
        });
    }

    public static <T> T withPricingAdapter(Function<PricingAdapter, T> work) {
        Objects.requireNonNull(work, "work cannot be null");
        return withFacade(facade -> work.apply(new PricingAdapter(facade)));
    }

    public static void usePricingAdapter(Consumer<PricingAdapter> work) {
        Objects.requireNonNull(work, "work cannot be null");
        useFacade(facade -> work.accept(new PricingAdapter(facade)));
    }

    public static <T> T withPackagingAdapter(Function<PackagingAdapter, T> work) {
        Objects.requireNonNull(work, "work cannot be null");
        return withFacade(facade -> work.apply(new PackagingAdapter(facade)));
    }

    public static void usePackagingAdapter(Consumer<PackagingAdapter> work) {
        Objects.requireNonNull(work, "work cannot be null");
        useFacade(facade -> work.accept(new PackagingAdapter(facade)));
    }
}
