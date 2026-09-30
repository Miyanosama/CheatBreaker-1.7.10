package com.cheatbreaker.client.module;

import com.cheatbreaker.client.config.Setting;

/** A module that can be toggled by one physical keyboard key. */
public interface ToggleKeybindModule {
    Setting getToggleKeybind();

    void toggleFromKey(int key);
}
