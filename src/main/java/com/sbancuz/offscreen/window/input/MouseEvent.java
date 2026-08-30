package com.sbancuz.offscreen.window.input;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(fluent = true)
@EqualsAndHashCode
@ToString
@AllArgsConstructor
public final class MouseEvent {

    private int mouseX;
    private int mouseY;
    private int pressedButton;
    private int releasedButton;
}
