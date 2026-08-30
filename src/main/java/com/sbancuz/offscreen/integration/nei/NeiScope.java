package com.sbancuz.offscreen.integration.nei;

import java.lang.reflect.Field;

import net.minecraft.client.gui.inventory.GuiContainer;

import com.sbancuz.offscreen.scope.Scope;

import codechicken.nei.NEIController;
import codechicken.nei.guihook.GuiContainerManager;

public final class NeiScope implements Scope {

    private static final Field managerField;

    static {
        try {
            managerField = GuiContainer.class.getDeclaredField("manager");
            managerField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("NEI manager field not found", e);
        }
    }

    private GuiContainerManager savedManager;
    private final GuiContainer container;

    public NeiScope(GuiContainer container) {
        this.container = container;
    }

    @Override
    public void enter() {
        savedManager = NEIController.manager;

        GuiContainerManager mgr = getManager(container);
        if (mgr == null) {
            mgr = new GuiContainerManager(container);
            setManager(container, mgr);
            mgr.load();
        }

        NEIController.manager = mgr;
    }

    @Override
    public void restore() {
        NEIController.manager = savedManager;
        savedManager = null;
    }

    private static GuiContainerManager getManager(GuiContainer container) {
        try {
            return (GuiContainerManager) managerField.get(container);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to read NEI manager field", e);
        }
    }

    private static void setManager(GuiContainer container, GuiContainerManager mgr) {
        try {
            managerField.set(container, mgr);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to write NEI manager field", e);
        }
    }
}
