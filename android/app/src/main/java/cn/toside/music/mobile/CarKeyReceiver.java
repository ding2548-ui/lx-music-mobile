package cn.toside.music.mobile;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import cn.toside.music.mobile.MainApplication;

public class CarKeyReceiver extends BroadcastReceiver {
    private static final String TAG = "CarKeyReceiver";

    // 车机广播 Action
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
                // 语音/多媒体命令：带 action 字符串
                String cmd = intent.getStringExtra("action");
                if (cmd == null) {
                    // 方向盘物理按键也可能走这个通道
                    handleWheelKeyEvent(context, intent);
                    return;
                }
                handleCommand(context, cmd);
                break;
            }
            case ACTION_MUSIC: {
                // 音乐命令
                String cmd = intent.getStringExtra("action");
                if (cmd != null) {
                    handleCommand(context, cmd);
                }
                break;
            }
            case ACTION_WHEEL: {
                // 方向盘专用通道
                handleWheelKeyEvent(context, intent);
                break;
            }
            case ACTION_HMI: {
                // 中控屏/HMI 命令：也是通过 action 字符串
                String cmd = intent.getStringExtra("action");
                if (cmd != null) {
                    handleCommand(context, cmd);
                } else {
                    // 部分车型 HMI 可能用其他字段
                    cmd = intent.getStringExtra("type");
                    if (cmd != null) {
                        handleCommand(context, cmd);
                    }
                }
                break;
            }
            default:
                Log.d(TAG, "Unhandled action: " + action);
                break;
        }
    }

    /**
     * 处理方向盘物理按键事件
     * 参考 MultiMedia.apk CtrlReciver.dispatchWheelEvent：
     * - ICU_MediaKey=1 → playpause (播放/暂停切换)
     * - ICU_MediaSwitch=1 → preOne (上一首)
     * - ICU_MediaSwitch=2 → nextOne (下一首)
     */
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

    /**
     * 处理命令字符串
     * 参考 MultiMedia.apk CtrlReciver：
     * - preOne → 上一首
     * - nextOne → 下一首
     * - play → 播放
     * - pause → 暂停
     * - playpause → 播放/暂停切换
     * - stop → 停止
     */
    private void handleCommand(Context context, String cmd) {
        Log.d(TAG, "Command: " + cmd);
        sendCarKeyEvent(context, cmd);
    }

    /**
     * 发送按键事件到 JS 层
     * JS 层的 carKeyHandler.ts 监听 "CarKeyEvent" 并调用对应的播放控制函数
     * 
     * 使用 context.getApplicationContext() 获取 Application 实例，
     * 而非 MainApplication.getInstance()（该方法不存在）
     */
    private void sendCarKeyEvent(Context context, String command) {
        MainApplication application = (MainApplication) context.getApplicationContext();
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
