package cn.toside.music.mobile;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.KeyEvent;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import cn.toside.music.mobile.MainApplication;

public class CarKeyReceiver extends BroadcastReceiver {
    private static final String TAG = "CarKeyReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (action == null) return;

        Log.d(TAG, "Received broadcast action: " + action);

        switch (action) {
            case "com.leapmotor.command.multimedia": {
                String cmd = intent.getStringExtra("action");
                if (cmd == null) {
                    // 方向盘物理按键：ICU_MediaKey / ICU_MediaSwitch
                    handleWheelKeyEvent(intent);
                    return;
                }
                handleVoiceCommand(cmd);
                break;
            }
            case "com.leapmotor.command.music": {
                String cmd = intent.getStringExtra("action");
                if (cmd != null) {
                    handleVoiceCommand(cmd);
                }
                break;
            }
            case "com.leapmotor.customkey.music.pauseplay": {
                sendCarKeyEvent("playpause");
                break;
            }
            default:
                Log.d(TAG, "Unhandled action: " + action);
                break;
        }
    }

    /**
     * 处理方向盘物理按键事件 (ICU_MediaKey / ICU_MediaSwitch)
     * 参考 MultiMedia.apk CtrlReciver.dispatchWheelEvent 的逻辑：
     * - ICU_MediaKey=1 → playpause
     * - ICU_MediaSwitch=1 → preOne (上一首)
     * - ICU_MediaSwitch=2 → nextOne (下一首)
     */
    private void handleWheelKeyEvent(Intent intent) {
        int mediaKey = intent.getIntExtra("ICU_MediaKey", 0);
        int mediaSwitch = intent.getIntExtra("ICU_MediaSwitch", 0);

        Log.d(TAG, "Wheel key event - ICU_MediaKey: " + mediaKey + ", ICU_MediaSwitch: " + mediaSwitch);

        if (mediaKey == 1) {
            sendCarKeyEvent("playpause");
        }
        if (mediaSwitch == 1) {
            sendCarKeyEvent("preOne");
        } else if (mediaSwitch == 2) {
            sendCarKeyEvent("nextOne");
        }
    }

    /**
     * 处理语音/仪表盘命令
     * 参考 MultiMedia.apk CtrlReciver 的逻辑：
     * - preOne → 上一首
     * - nextOne → 下一首
     * - play → 播放
     * - pause → 暂停
     * - playpause → 切换播放/暂停
     * - stop → 停止
     */
    private void handleVoiceCommand(String cmd) {
        Log.d(TAG, "Voice command: " + cmd);
        sendCarKeyEvent(cmd);
    }

    /**
     * 发送按键事件到 JS 层，由 JS 层的 CarKeyHandler 处理播放控制
     */
    private void sendCarKeyEvent(String command) {
        MainApplication application = (MainApplication) MainApplication.getInstance();
        if (application == null) {
            Log.e(TAG, "MainApplication is null, cannot send event");
            return;
        }

        WritableMap params = Arguments.createMap();
        params.putString("command", command);

        try {
            application.getReactNativeHost()
                .getReactInstanceManager()
                .getCurrentReactContext()
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit("CarKeyEvent", params);
            Log.d(TAG, "Sent CarKeyEvent: " + command);
        } catch (Exception e) {
            Log.e(TAG, "Failed to send CarKeyEvent: " + e.getMessage());
        }
    }
}
