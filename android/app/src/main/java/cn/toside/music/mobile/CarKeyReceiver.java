package cn.toside.music.mobile;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class CarKeyReceiver extends BroadcastReceiver {
    private static final String TAG = "CarKeyReceiver";
    public static final String ACTION_MULTIMEDIA = "com.leapmotor.command.multimedia";
    public static final String ACTION_MUSIC = "com.leapmotor.command.music";
    public static final String KEY_PLAY = "play";
    public static final String KEY_PAUSE = "pause";
    public static final String KEY_NEXT = "nextOne";
    public static final String KEY_PREV = "preOne";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.i(TAG, "Received action: " + action);
        
        if (ACTION_MULTIMEDIA.equals(action)) {
            String type = intent.getStringExtra("type");
            Log.i(TAG, "Multimedia key: " + type);
        } else if (ACTION_MUSIC.equals(action)) {
            String type = intent.getStringExtra("type");
            Log.i(TAG, "Music key: " + type);
        }
    }
}