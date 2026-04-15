package com.pricingos.common;

import java.util.List;

public interface ISkuCatalogService {

    boolean isSkuActive(String skuId);

    List<String> getAllActiveSkuIds();
}
