package com.pricingos.common;

import java.time.LocalDate;
import java.util.List;

public interface IBundlePromotionService {

    String createBundlePromotion(String name, List<String> bundleSkuIds,
                                 double discountPct, LocalDate startDate, LocalDate endDate);

    double getBestBundleDiscount(List<String> cartSkuIds, double cartTotal);

    List<String> getActiveBundlePromotions();
}
