package com.sbancuz.offscreen.integration.mui2;

import com.cleanroommc.modularui.ModularUI;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.widgets.TextWidget;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class TestMui2ScreenNoNEI extends ModularScreen {

    private final int[] updateCount;

    public TestMui2ScreenNoNEI() {
        this(new int[1]);
    }

    private TestMui2ScreenNoNEI(final int[] updateCount) {
        super(ModularUI.ID, ctx -> {
            ModularPanel panel = ModularPanel.defaultPanel("offscreen_test_nonei", 200, 150);
            var label = new TextWidget<>(IKey.str("Mui2 Only (No NEI)"));
            label.pos(10, 10);
            panel.child(label);
            var hint = new TextWidget<>(IKey.str("Recipe viewer disabled"));
            hint.pos(10, 25);
            panel.child(hint);
            // Live tick proof: the dynamic keys re-evaluate every draw, so these only
            // advance while Mui2UI.update() drives context.tick() + onUpdate().
            var tickProof = new TextWidget<>(IKey.dynamic(() -> "onUpdate ticks: " + updateCount[0]));
            tickProof.pos(10, 45);
            panel.child(tickProof);
            var ctxProof = new TextWidget<>(IKey.dynamic(() -> "context ticks: " + ctx.getTick()));
            ctxProof.pos(10, 60);
            panel.child(ctxProof);
            return panel;
        });
        this.updateCount = updateCount;
        var set = new UISettings();
        set.getRecipeViewerSettings()
            .disable();
        getContext().setSettings(set);
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        updateCount[0]++;
    }
}
