package com.pricingos.common;

public enum CustomerTier{
    STANDARD(0.0),
    SILVER(0.05),
    GOLD(0.10),
    PLATINUM(0.15);

    //final variable to ensure its not overriden
    private final double discountRate;

    //constructor
    CustomerTier(double discountRate){  
        this.discountRate = discountRate;
    }
    public double getDiscountRate(){
        return discountRate;
    }
}