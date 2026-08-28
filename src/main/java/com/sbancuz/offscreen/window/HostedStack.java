package com.sbancuz.offscreen.window;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

public class HostedStack extends ObjectArrayList<HostedScreen<?>> {

    // private HostedEntry findEntry(final GuiScreen s) {
    // if (s == null) return null;
    // for (final HostedEntry e : hostedStack) {
    // if (e.wrapper == s) return e;
    // }
    // return null;
    // }

    /** Pops entries until {@code target} is on top. Never pops the target or the base entry. */
    private void popTo(final HostedScreen<?> target) {
        while (top() != target && size() > 1) {
            pop().dispose();
        }
    }

}
