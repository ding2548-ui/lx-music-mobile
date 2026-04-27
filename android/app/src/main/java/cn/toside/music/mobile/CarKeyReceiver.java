package cn.toside.music.mobile;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

public class CarKeyReceiver extends BroadcastReceiver {
    private static final String TAG = "CarKeyReceiver";

    // 车机广播 Action — 与 MultiMedia.apk CtrlReciver 完全对齐
    private static final String ACTION_HMI = "car.hmi.music.BROADCAST";
    private static final String ACTION_MULTIMEDIA = "com.leapmotor.command.multimedia";
    private static final String ACTION_MUSIC = "com.leapmotor.command.music";
    private static final String ACTION_WHEEL = "com.leapmotor.customkey.music.pauseplay";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (action == null) return;

        Log.d(TAG, "Received broadcast action: " + action);

        switch (action) {
            case ACTION_HMI:
                // car.hmi.music.BROADCAST — 二进制 JSON 协议（仪表盘/中控屏）
                handleHmiBroadcast(context, intent);
                break;

            case ACTION_MULTIMEDIA:
                // com.leapmotor.command.multimedia — 语音命令（带 action 字符串）
                // 也可能包含方向盘按键（ICU_MediaKey/ICU_MediaSwitch）
                handleMultimediaCommand(context, intent);
                break;

            case ACTION_MUSIC:
                // com.leapmotor.command.music — 音乐命令
                handleMusicCommand(context, intent);
                break;

            case ACTION_WHEEL:
                // com.leapmotor.customkey.music.pauseplay — 方向盘专用通道
                handleWheelKeyEvent(context, intent);
                break;

            default:
                Log.d(TAG, "Unhandled action: " + action);
                break;
        }
    }

    /**
     * 处理 car.hmi.music.BROADCAST（二进制 JSON 协议）
     * 
     * 参考 CtrlReciver.dispatchMeterEvent：
     * 1. getByteArrayExtra("receiver") 获取 byte[]
     * 2. byte[0]*256 + byte[1] = JSON 长度
     * 3. byte[2...] = UTF-8 JSON 字符串
     * 4. JSON: {"type":"music", "data":{"type":0~5, "action":"preOne/nextOne/playpause"}}
     * 
     * data.type 含义：
     *   0 = 在线音乐（preOne→playPre, nextOne→playNext, playpause→playOrPause）
     *   1 = U盘音乐
     *   2 = 蓝牙音乐
     *   3 = 喜马拉雅
     *   4 = FM
     *   5 = 方向盘按键（action 转 ICU_MediaKey/ICU_MediaSwitch 再 dispatchWheelEvent）
     */
    private void handleHmiBroadcast(Context context, Intent intent) {
        try {
            byte[] receiverData = intent.getByteArrayExtra("receiver");
            if (receiverData == null || receiverData.length < 2) {
                Log.d(TAG, "HMI broadcast: no receiver byte data, trying string extras");
                // 降级：尝试读字符串格式的 action
                String cmd = intent.getStringExtra("action");
                if (cmd == null) cmd = intent.getStringExtra("type");
                if (cmd != null) {
                    handleCommand(context, cmd);
                }
                return;
            }

            int jsonLen = ((receiverData[0] & 0xFF) * 256) + (receiverData[1] & 0xFF);
            if (jsonLen <= 0 || receiverData.length < jsonLen + 2) {
                Log.w(TAG, "HMI broadcast: invalid JSON length: " + jsonLen);
                return;
            }

            byte[] jsonBytes = new byte[jsonLen];
            System.arraycopy(receiverData, 2, jsonBytes, 0, jsonLen);
            String jsonStr = new String(jsonBytes, StandardCharsets.UTF_8);
            Log.d(TAG, "HMI broadcast JSON: " + jsonStr);

            JSONObject root = new JSONObject(jsonStr);
            String type = root.optString("type", "");

            if (!"music".equalsIgnoreCase(type)) {
                Log.d(TAG, "HMI broadcast: type is not music: " + type);
                return;
            }

            JSONObject data = root.getJSONObject("data");
            int dataType = data.optInt("type", -1);
            String dataAction = data.optString("action", "");

            Log.d(TAG, "HMI broadcast: dataType=" + dataType + ", action=" + dataAction);

            // dataType=5: 方向盘按键（CtrlReciver 会将 action 映射回 ICU_MediaKey/ICU_MediaSwitch）
            // 其他 dataType (0~4): 直接使用 action 字符串
            if (dataType == 5) {
                // 方向盘按键映射（参考 CtrlReciver.dispatchMeterEvent）:
                // preOne → ICU_MediaSwitch=1
                // nextOne → ICU_MediaSwitch=2
                // playpause → ICU_MediaKey=1
                Intent wheelIntent = new Intent();
                switch (dataAction) {
                    case "preOne":
                        wheelIntent.putExtra("ICU_MediaSwitch", 1);
                        break;
                    case "nextOne":
                        wheelIntent.putExtra("ICU_MediaSwitch", 2);
                        break;
                    case "playpause":
                        wheelIntent.putExtra("ICU_MediaKey", 1);
                        break;
                    default:
                        // 未知方向盘按键，直接当命令发
                        handleCommand(context, dataAction);
                        return;
                }
                handleWheelKeyEvent(context, wheelIntent);
            } else {
                // 在线音乐/U盘/蓝牙/喜马拉雅/FM — 直接使用 action 命令
                handleCommand(context, dataAction);
            }
        } catch (Exception e) {
            Log.e(TAG, "HMI broadcast parse error: " + e.getMessage());
            // 降级：尝试读字符串
            String cmd = intent.getStringExtra("action");
            if (cmd != null) {
                handleCommand(context, cmd);
            }
        }
    }

    /**
     * 处理 com.leapmotor.command.multimedia
     * 
     * 参考 CtrlReciver.onReceive：
     * - 优先读 getStringExtra("action") → 语音命令 (preOne/nextOne/play/pause/playpause/stop)
     * - 如果 action 为 null，尝试读 ICU_MediaKey/ICU_MediaSwitch → 方向盘按键
     */
    private void handleMultimediaCommand(Context context, Intent intent) {
        String cmd = intent.getStringExtra("action");
        if (cmd != null && !cmd.isEmpty()) {
            handleCommand(context, cmd);
            return;
        }
        // 降级：尝试方向盘按键
        handleWheelKeyEvent(context, intent);
    }

    /**
     * 处理 com.leapmotor.command.music
     * 
     * 参考 CtrlReciver: 读 getStringExtra("action") → 语音命令
     */
    private void handleMusicCommand(Context context, Intent intent) {
        String cmd = intent.getStringExtra("action");
        if (cmd != null && !cmd.isEmpty()) {
            handleCommand(context, cmd);
        }
    }

    /**
     * 处理方向盘物理按键事件
     * 
     * 参考 CtrlReciver.dispatchWheelEvent：
     * - ICU_MediaKey=1 → playpause（播放/暂停切换）
     * - ICU_MediaSwitch=1 → preOne（上一首）
     * - ICU_MediaSwitch=2 → nextOne（下一首）
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
     * 
     * 参考 CtrlReciver.ctrlOnlineMusicByVoice：
     * - preOne → 上一首
     * - nextOne → 下一首
     * - play → 播放
     * - pause → 暂停
     * - playpause → 播放/暂停切换
     * - stop → 停止
     */
    private void handleCommand(Context context, String cmd) {
        if (cmd == null || cmd.isEmpty()) return;
        Log.d(TAG, "Command: " + cmd);
        sendCarKeyEvent(context, cmd);
    }

    /**
     * 发送按键事件到 JS 层
     * JS 层 carKeyHandler.ts 监听 "CarKeyEvent" 并调用对应的播放控制函数
     */
    private void sendCarKeyEvent(Context context, String command) {
        try {
            MainApplication application = (MainApplication) context.getApplicationContext();
            if (application == null) {
                Log.e(TAG, "MainApplication is null");
                return;
            }

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
