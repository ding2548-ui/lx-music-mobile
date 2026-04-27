package cn.toside.music.mobile;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

public class CarKeyReceiver extends BroadcastReceiver {
    private static final String TAG = "CarKeyReceiver";

    private static final String ACTION_MULTIMEDIA = "com.leapmotor.command.multimedia";
    private static final String ACTION_MUSIC = "com.leapmotor.command.music";
    private static final String ACTION_WHEEL = "com.leapmotor.customkey.music.pauseplay";
    private static final String ACTION_HMI = "car.hmi.music.BROADCAST";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (action == null) return;

        Log.d(TAG, "Received broadcast action: " + action);

        switch (action) {
            case ACTION_MULTIMEDIA: {
                String cmd = intent.getStringExtra("action");
                if (cmd == null) {
                    handleWheelKeyEvent(context, intent);
                    return;
                }
                handleCommand(context, cmd);
                break;
            }
            case ACTION_MUSIC: {
                String cmd = intent.getStringExtra("action");
                if (cmd != null) {
                    handleCommand(context, cmd);
                }
                break;
            }
            case ACTION_WHEEL: {
                handleWheelKeyEvent(context, intent);
                break;
            }
            case ACTION_HMI: {
                String cmd = intent.getStringExtra("action");
                if (cmd == null) {
                    cmd = intent.getStringExtra("type");
                }
                if (cmd != null) {
                    handleCommand(context, cmd);
                }
                break;
            }
            default:
                Log.d(TAG, "Unhandled action: " + action);
                break;
        }
    }

    private void handleWheelKeyEvent(Context context, Intent intent) {
        int mediaKey = intent.getIntExtra("ICU_MediaKey", 0);
        int mediaSwitch = intent.getIntExtra("ICU_MediaSwitch", 0);

        Log.d(TAG, "Wheel key - ICU_MediaKey: " + mediaKey + ", ICU_MediaSwitch: " + mediaSwitch);

        if (mediaKey == 1) {
            sendCarKeyEvent(context, "playpause");
        }
        if (mediaSwitch == 1) {
            sendCarKeyEvent(context, "preOne");
        } else if (mediaSwitch == 2) {
            sendCarKeyEvent(context, "nextOne");
        }
    }

    private void handleCommand(Context context, String cmd) {
        Log.d(TAG, "Command: " + cmd);
        sendCarKeyEvent(context, cmd);
    }

    private void sendCarKeyEvent(Context context, String command) {
        try {
            MainApplication application = (MainApplication) context.getApplicationContext();
            ReactContext reactContext = application.getReactNativeHost()
                .getReactInstanceManager()
                .getCurrentReactContext();

            if (reactContext == null) {
                Log.w(TAG, "ReactContext is null, cannot send CarKeyEvent");
                return;
            }

            WritableMap params = Arguments.createMap();
            params.putString("command", command);

            reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit("CarKeyEvent", params);
            Log.d(TAG, "Sent CarKeyEvent: " + command);
        } catch (Exception e) {
            Log.e(TAG, "Failed to send CarKeyEvent: " + e.getMessage());
        }
    }
}
