package com.example.web;

import com.example.lib.PriceSource;

/**
 * Depends on a type that exists only on the classpath. Loading this module
 * without the classpath entry configured fails, which is the point: it makes
 * a missing or wrong classpath setting visible immediately.
 */
public final class Cart {

    private final /*@ spec_public @*/ PriceSource prices;

    /*@ public normal_behavior
      @   requires source != null;
      @   ensures prices == source;
      @   assignable prices;
      @*/
    public Cart(PriceSource source) {
        this.prices = source;
    }

    /*@ public normal_behavior
      @   requires itemId >= 0;
      @   ensures \result >= 0;
      @   assignable \nothing;
      @*/
    public /*@ pure @*/ int priceOf(int itemId) {
        return prices.priceOf(itemId);
    }
}
