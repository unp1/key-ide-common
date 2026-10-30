package com.example.core;

/**
 * A minimal account with a non-negative balance.
 */
public final class Account {

    private /*@ spec_public @*/ int balance;

    //@ public invariant balance >= 0;

    /*@ public normal_behavior
      @   ensures balance == 0;
      @   assignable balance;
      @*/
    public Account() {
        balance = 0;
    }

    /*@ public normal_behavior
      @   requires amount > 0;
      @   requires balance + amount <= Integer.MAX_VALUE;
      @   ensures balance == \old(balance) + amount;
      @   assignable balance;
      @ also
      @ public normal_behavior
      @   requires amount > 0;
      @   requires balance + amount <= Integer.MAX_VALUE;
      @   ensures balance == \old(balance) + amount;
      @   assignable balance;
      @*/
    public void deposit(int amount) {
        balance += amount;
    }

    /*@ public normal_behavior
      @   requires 0 < amount && amount <= balance;
      @   ensures balance == \old(balance) - amount;
      @   assignable balance;
      @*/
    public void withdraw(int amount) {
        balance -= amount;
    }

    /*@ public normal_behavior
      @   ensures \result == balance;
      @   assignable \nothing;
      @*/
    public /*@ pure @*/ int getBalance() {
        return balance;
    }
}
