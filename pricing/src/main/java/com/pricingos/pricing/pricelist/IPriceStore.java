package com.pricingos.pricing.pricelist;

import java.util.Date;
import java.util.List;
import java.util.Optional;

public interface IPriceStore {

    Optional<PriceRecord> findActive(String skuId, String region, String channel);

    List<PriceRecord> findBySku(String skuId);

    List<PriceRecord> findAllActive();

    void save(PriceRecord record);

    void markActiveAsSuperseded(String skuId, String region, String channel, Date effectiveTo);

    void clear();
}
