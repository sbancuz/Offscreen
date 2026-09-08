package com.sbancuz.offscreen.integration.vanilla;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;

import com.sbancuz.offscreen.Offscreen;
import com.sbancuz.offscreen.api.HostUI;
import com.sbancuz.offscreen.mixins.GuiContainerAccessor;
import com.sbancuz.offscreen.mixins.GuiScreenAccessor;
import com.sbancuz.offscreen.scope.Scope;
import com.sbancuz.offscreen.scope.ScopePipeline;
import com.sbancuz.offscreen.scope.ScreenScope;
import com.sbancuz.offscreen.window.input.KeyEvent;

import me.eigenraven.lwjgl3ify.api.InputEvents;

public class VanillaUI implements HostUI {

    private final GuiScreen screen;
    private final Scope scope;

    public VanillaUI(GuiScreen screen) {
        this.screen = screen;
        this.scope = ScopePipeline.builder()
            .always(new ScreenScope(this::getGuiScreen))
            .build();
    }

    @Override
    public Scope scope() {
        return scope;
    }

    @Override
    public void dispose() {

    }

    public void update() {
        if (screen.width <= 0) return;
        screen.updateScreen();
    }

    @Override
    public GuiScreen getGuiScreen() {
        return screen;
    }

    @Override
    public void onResize(final int width, final int height) {
        if (screen instanceof GuiContainer container) {
            final GuiContainerAccessor accessor = (GuiContainerAccessor) container;
            accessor.setGuiTop((height - accessor.getYSize()) / 2);
            accessor.setGuiLeft((width - accessor.getXSize()) / 2);
        }
    }

    @Override
    public void draw(Minecraft mc, final int mouseX, final int mouseY, float partialTicks, long now) {
        screen.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public void onMouseClicked(int mouseX, int mouseY, int button) {
        if (!(screen instanceof GuiScreenAccessor acc)) return;
        try {
            acc.invokeMouseClicked(mouseX, mouseY, button);
        } catch (final Throwable t) {
            Offscreen.LOG.trace("[secondscreen] mouseClicked failed", t);
        }
    }

    @Override
    public void onMouseReleased(int mouseX, int mouseY, int button) {
        if (!(screen instanceof GuiScreenAccessor acc)) return;
        acc.invokeMouseMovedOrUp(mouseX, mouseY, button);
    }

    @Override
    public void onTextInput(String text) {
        InputEvents.injectTextEvent(new InputEvents.TextEvent(text));
    }

    @Override
    public void onKeyTyped(char typedChar, int keyCode) {
        if (!(screen instanceof GuiScreenAccessor acc)) return;
        try {
            acc.invokeKeyTyped(typedChar, keyCode);
        } catch (final Throwable t) {
            Offscreen.LOG.trace("[secondscreen] keyTyped failed", t);
        }
    }

    @Override
    public void onKeyReleased(char typedChar, int keyCode) {
        // Vanilla screens generally ignore key releases (pre-wrapper MCHostUI did)
    }

    @Override
    public void onKeyPressed(KeyEvent key) {
        // Override HostUI default that injects via lwjgl3ify – vanilla must NOT double-inject.
        // Pre-wrapper vanilla only invoked keyTyped directly (or NEI via wrapper) without inject.
        if (key.pressed()) onKeyTyped(key.character(), key.keyCode());
        else onKeyReleased(key.character(), key.keyCode());
    }
}
