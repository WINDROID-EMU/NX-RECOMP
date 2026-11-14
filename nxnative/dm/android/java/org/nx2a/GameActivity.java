package org.nx2a;

import android.app.NativeActivity;
import android.content.Intent;
import java.nio.charset.StandardCharsets;

/** Optional Java text entry and storage management; the ordinary NativeActivity APK remains code-less. */
public class GameActivity extends NativeActivity {
    private final GameKeyboard keyboard = new GameKeyboard(this);
    private final GameStorage storage = new GameStorage(this);

    private static String text(byte[] value) {
        return value == null ? "" : new String(value, StandardCharsets.UTF_8);
    }

    // Guest callers wait off the UI thread. Ordinary UTF-8, not JNI modified UTF-8.
    public byte[] readKeyboard(byte[] title, byte[] subtitle, byte[] guide, byte[] initial, int min, int max) {
        return keyboard.read(text(title), text(subtitle), text(guide), text(initial), min, max);
    }

    public void ensureRomfs() {
        storage.ensureRomfs();
    }

    @Override
    protected void onResume() {
        super.onResume();
        keyboard.pause(false);
        storage.onResume();
    }

    @Override
    protected void onPause() {
        keyboard.pause(true);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        keyboard.pause(true);
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        storage.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        storage.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
}
