package com.sbancuz.offscreen.mixins;

import net.minecraft.client.gui.inventory.GuiContainer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GuiContainer.class)
public interface GuiContainerAccessor {

    @Accessor("xSize")
    int getXSize();

    @Accessor("ySize")
    int getYSize();

    @Accessor("guiLeft")
    void setGuiLeft(final int l);

    @Accessor("guiTop")
    void setGuiTop(final int t);
}
