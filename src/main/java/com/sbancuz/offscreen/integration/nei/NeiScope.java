package com.sbancuz.offscreen.integration.nei;

import com.sbancuz.offscreen.api.ActionScope;

import codechicken.nei.NEIController;
import codechicken.nei.guihook.GuiContainerManager;

public final class NeiScope implements ActionScope {

    private boolean saved = false;
    private GuiContainerManager manager;

    @Override
    public void save() {
        manager = NEIController.manager;
        saved = true;
    }

    @Override
    public void restore() {
        if (!saved) return;

        NEIController.manager = manager;
    }
}
