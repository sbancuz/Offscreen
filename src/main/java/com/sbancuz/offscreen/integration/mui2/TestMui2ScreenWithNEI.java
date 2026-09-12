package com.sbancuz.offscreen.integration.mui2;

import com.cleanroommc.modularui.ModularUI;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class TestMui2ScreenWithNEI extends ModularScreen {

    public TestMui2ScreenWithNEI() {
        super(ModularUI.ID, ctx -> {
            ModularPanel panel = ModularPanel.defaultPanel("offscreen_test_nei")
                .size(176, 166)
                .center();
            final TextFieldWidget testField = new TextFieldWidget().size(120, 18);
            testField.setText("type here");
            testField.pos(10, 80);
            testField.addTooltipLine(IKey.str("Type here"));
            panel.child(
                Flow.column()
                    .widthRel(1f)
                    .heightRel(1f)
                    .child(new TextWidget<>(IKey.str("Mui2 + NEI Test")))
                    .child(new TextWidget<>(IKey.str("Item panel, search bar, bookmarks render around this")))
                    .child(
                        new ButtonWidget<>().size(100, 20)
                            .overlay(IKey.str("Click Me"))
                            .onMousePressed(button -> {
                                System.out.println("Clicked!");
                                return true;
                            }))
                    .child(new TextWidget<>(IKey.str("Scroll items with NEI panel")))
                    .align(Alignment.CENTER));
            panel.child(testField);
            return panel;
        });
        var set = new UISettings();
        set.getRecipeViewerSettings()
            .enable();

        getContext().setSettings(set);
    }
}
