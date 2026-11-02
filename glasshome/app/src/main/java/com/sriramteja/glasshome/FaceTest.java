package com.sriramteja.glasshome;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;

/**
 * Shows a watch face full screen right now, to try it (the phone app opens this with "face" = the one to
 * show; without it, the screensaver's own). Any button closes it.
 */
public class FaceTest extends Activity {

    private FaceView view;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        view = new FaceView(this, getIntent().getStringExtra("face"));
        setContentView(view);
    }

    @Override
    protected void onResume() {
        super.onResume();
        view.post(view::start);
    }

    @Override
    protected void onPause() {
        view.stop();
        super.onPause();
        finish();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        finish();
        return true;
    }
}
