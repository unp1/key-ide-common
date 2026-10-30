package com.example.core;

/**
 * Array helpers carrying loop invariants, so the fixture also covers a proof
 * obligation that needs more than symbolic execution of straight-line code.
 */
public final class ArrayUtils {

    private ArrayUtils() {
    }

    /*@ public normal_behavior
      @   ensures \result >= a && \result >= b;
      @   ensures \result == a || \result == b;
      @   assignable \nothing;
      @*/
    public static /*@ pure @*/ int max(int a, int b) {
        return a > b ? a : b;
    }

    /*@ public normal_behavior
      @   requires values != null && values.length > 0;
      @   ensures (\forall int i; 0 <= i && i < values.length; \result >= values[i]);
      @   ensures (\exists int i; 0 <= i && i < values.length; \result == values[i]);
      @   assignable \nothing;
      @*/
    public static /*@ pure @*/ int maximum(int[] values) {
        int result = values[0];

        /*@ loop_invariant 1 <= k && k <= values.length;
          @ loop_invariant (\forall int i; 0 <= i && i < k; result >= values[i]);
          @ loop_invariant (\exists int i; 0 <= i && i < k; result == values[i]);
          @ decreases values.length - k;
          @ assignable \nothing;
          @*/
        for (int k = 1; k < values.length; k++) {
            if (values[k] > result) {
                result = values[k];
            }
        }

        return result;
    }

    /*@ public normal_behavior
      @   requires values != null;
      @   ensures \result == (\exists int i; 0 <= i && i < values.length; values[i] == target);
      @   assignable \nothing;
      @*/
    public static /*@ pure @*/ boolean contains(int[] values, int target) {
       return indexOf(values, target) != -1;
    }

    /*@ public normal_behavior
      @   requires values != null;
      @   ensures (\exists int i; 0 <= i && i < values.length; values[i] == target) ?
      @         \result >= 0 && \result < values.length && values[\result] == target : \result == -1 ;
      @   assignable \nothing;
      @*/
    public static /*@ pure @*/ int indexOf(int[] values, int target) {
        /*@ loop_invariant 0 <= k && k <= values.length;
          @ loop_invariant (\forall int i; 0 <= i && i < k; values[i] != target);
          @ decreases values.length - k;
          @ assignable \nothing;
          @*/
        for (int k = 0; k < values.length; k++) {
            if (values[k] == target) {
                return k;
            }
        }
        return -1;
    }

    /*@ public normal_behavior
      @   requires low <= high;
      @   ensures \result >= low && \result <= high;
      @   assignable \nothing;
      @*/
    public static /*@ pure @*/ int clamp(int value, int low, int high) {
        return value < low ? low : (value > high ? high : value);
    }

    /*@ public normal_behavior
      @   ensures \result >= 0;
      @   assignable \nothing;
      @*/
    public static /*@ pure @*/ int clamp(int value) {
        return value < 0 ? 0 : value;
    }
}
