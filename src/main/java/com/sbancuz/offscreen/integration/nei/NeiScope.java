package com.sbancuz.offscreen.integration.nei;

import com.sbancuz.offscreen.scope.Scope;

public final class NeiScope implements Scope {

    private Object savedManager;

    @Override
    public void enter() {
        savedManager = codechicken.nei.NEIController.manager;
    }

    @Override
    public void restore() {
        codechicken.nei.NEIController.manager =
            (codechicken.nei.guihook.GuiContainerManager) savedManager;
        savedManager = null;
    }
}
