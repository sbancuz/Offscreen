package com.sbancuz.offscreen.window.input;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(fluent = true)
@EqualsAndHashCode
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class KeyEvent {

    int keyCode;
    char character;
    boolean pressed;
    int sdlKeyCode;
    int sdlScanCode;
    int sdlMod;

    public KeyEvent set(final int keyCode, final char character, final boolean pressed) {
        this.keyCode = keyCode;
        this.character = character;
        this.pressed = pressed;
        return this;
    }

    public KeyEvent setSdl(final int sdlKeyCode, final int sdlScanCode, final short sdlMods) {
        this.sdlKeyCode = sdlKeyCode;
        this.sdlScanCode = sdlScanCode;
        this.sdlMod = sdlMods;
        return this;
    }
}
