package com.pricingos.pricing.contract;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ContractDao {

    private static final Map<String, Contract> CONTRACTS_BY_ID = new ConcurrentHashMap<>();

    private ContractDao() {
    }

    public static void save(Contract contract) {
        CONTRACTS_BY_ID.put(contract.getContractId(), copy(contract));
    }

    public static Contract get(String id) {
        Contract contract = CONTRACTS_BY_ID.get(id);
        return contract == null ? null : copy(contract);
    }

    public static List<Contract> findAll() {
        return CONTRACTS_BY_ID.values().stream()
                .map(ContractDao::copy)
                .toList();
    }

    private static Contract copy(Contract contract) {
        Contract copy = Contract.builder(contract.getContractId(), contract.getCustomerId())
                .startDate(contract.getStartDate())
                .endDate(contract.getEndDate())
                .skuPrices(contract.getSkuPricesSnapshot())
                .build();
        copy.restoreState(contract.getStatus());
        return copy;
    }
}
