package com.example.lib;

/**
 * A dependency that the project has specifications for but no sources of its own.
 * Declared as an interface so the classpath entry contains no method bodies:
 * KeY strips bodies from classpath classes and warns when it finds one.
 */
public interface PriceSource {

    /*@ public normal_behavior
      @   requires itemId >= 0;
      @   ensures \result >= 0;
      @   assignable \nothing;
      @*/
    /*@ pure @*/ int priceOf(int itemId);
}
