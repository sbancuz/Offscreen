package com.sbancuz.offscreen.scope;

public interface Scope {

    Scope NOP = new Scope() {
        @Override public void enter() {}
        @Override public void restore() {}
    };

    void enter();

    void restore();
}
