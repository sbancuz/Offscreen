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

    public TestMui2ScreenNoNEI() {
        super(ModularUI.ID, ctx -> {
            ModularPanel panel = ModularPanel.defaultPanel("offscreen_test_nonei", 200, 150);
            var label = new TextWidget<>(IKey.str("Mui2 Only (No NEI)"));
            label.pos(10, 10);
            panel.child(label);
            var hint = new TextWidget<>(IKey.str("Recipe viewer disabled"));
            hint.pos(10, 25);
            panel.child(hint);
            return panel;
        });
        var set = new UISettings();
        set.getRecipeViewerSettings()
            .disable();
        getContext().setSettings(set);
    }
}
