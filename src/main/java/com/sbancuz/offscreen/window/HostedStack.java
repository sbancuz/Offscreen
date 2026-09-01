package com.sbancuz.offscreen.window;

import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import com.sbancuz.offscreen.Offscreen;
import com.sbancuz.offscreen.api.HostUI;
import com.sbancuz.offscreen.api.UIRegistry;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import javax.annotation.Nullable;

public class HostedStack extends ObjectArrayList<HostedScreen<?>> {

    public @Nullable  HostedScreen<?> findEntry(final GuiScreen screen) {
        for (HostedScreen<?> hostedScreen : this) {
            if (hostedScreen.getGuiScreen() == screen) return hostedScreen;
        }
        return null;
    }

    public void popTo(final HostedScreen<?> target) {
        while (top() != target && size() > 1) {
            pop().dispose();
        }
    }

    public void runScoped(final Consumer<HostedScreen<?>> action) {
        final HostedScreen<?> screen = top();
        final GuiScreen savedScreen = Minecraft.getMinecraft().currentScreen;
        boolean poisoned = false;
        screen.scope()
            .enter();

        try {
            action.accept(screen);
        } catch (final Throwable t) {
            poisoned = true;
            Offscreen.LOG.error("[secondscreen] scoped action failed", t);
        } finally {
            if (!poisoned) {
                try {
                    handleScreenChange(savedScreen);
                } catch (final Throwable t) {
                    Offscreen.LOG.error("[secondscreen] screen-change handling failed", t);
                }
            }
            screen.scope()
                .restore();
        }
    }

    private void handleScreenChange(final GuiScreen prevTop) {
        final Minecraft mc = Minecraft.getMinecraft();
        final GuiScreen now = mc.currentScreen;
        if (now == prevTop) return;

        final HostedScreen<?> match = findEntry(now);
        if (match != null) {
            popTo(match);
            match.requestResize();
            return;
        }

        if (now == null) {
            if (size() > 1) {
                final HostedScreen<?> t = pop();
                Offscreen.LOG.info("[secondscreen] pop {} (close-to-null, depth {})", t, size());
                t.dispose();
                top().requestResize();
            }
            mc.currentScreen = top().getGuiScreen();
            return;
        }

        pushStolen(now);
    }

    public void pushStolen(final GuiScreen stolen) {
        final HostUI ui = UIRegistry.resolve(stolen);
        if (ui == null) {
            Offscreen.LOG.warn("[secondscreen] no UI factory for stolen screen: {}", stolen.getClass()
                .getSimpleName());
            return;
        }
        final HostedScreen<?> entry = new HostedScreen<>(ui);
        entry.requestResize();
        push(entry);
        Offscreen.LOG.info(
            "[secondscreen] STEAL {} (adapter: {}, depth {})",
            stolen.getClass()
                .getSimpleName(),
            ui.getClass()
                .getSimpleName(),
            size());
    }

}
