package com.goofy.goofyaddons.keybinds;

/** Let key assignment receive the pressed key before global debug/stop shortcuts. */
public interface BindingCaptureScreen {
    boolean capturingTradingBinding();
}
