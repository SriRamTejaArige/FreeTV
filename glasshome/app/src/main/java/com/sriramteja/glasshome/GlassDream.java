package com.sriramteja.glasshome;

import android.service.dreams.DreamService;

/** The screensaver: the theme's watch face (or the one chosen in the settings). Any button wakes the TV. */
public class GlassDream extends DreamService {

    private FaceView view;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(true);
        view = new FaceView(this, null);
        setContentView(view);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        view.start();
    }

    @Override
    public void onDreamingStopped() {
        view.stop();
        super.onDreamingStopped();
    }
}
