package com.gtarc.chariot.utils;

public class Pair {
    private long key;
    private Object value;

    public Pair() {}

    public Pair(long key, Object value) {
        this.key = key;
        this.value = value;
    }

    public long getKey() {
        return key;
    }

    public void setKey(long key) {
        this.key = key;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }
}
