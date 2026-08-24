package cn.toside.music.mobile;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import com.facebook.react.ReactApplication;
import com.facebook.react.ReactInstanceManager;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

/**
 * 车机方向盘按键接收器（方控）
 * 处理车机发送的多媒体控制按键事件，解析后发送到 JS 层控制音乐播放。
 *
 * 通道（主 + 备用）与报文格式来自车机方控协议（car-fangkong-inject 技能，经零跑/酷我车机版实战验证）：
 *  - 主通道 car.meter.music.BROADCAST / 备用 car.hmi.music.BROADCAST：
 *      并行广播，报文载体为 Intent extra "receiver" = byte[]；
 *      len = (b[0]&0xff)*0x64 + (b[1]&0xff)，载荷从 offset 2 起为 UTF-8 JSON；
 *      报文形如 {"data":{"action":"nextOne","type":-1},"type":"music"}（真实 data.type == -1）。
 *  - com.leapmotor.command.multimedia / com.leapmotor.command.music：extra "type" = play/pause/nextOne/preOne/playpause
 *  - com.leapmotor.customkey.music.pauseplay：pauseplay
 *  - com.leapmotor.ICU2MMICtrl：备用，取 type/action/cmd extra
 */
public class CarKeyReceiver extends BroadcastReceiver {
    private static final String TAG = "CarKeyReceiver";

    // 广播 Action
    public static final String ACTION_METER = "car.meter.music.BROADCAST";
    public static final String ACTION_HMI = "car.hmi.music.BROADCAST";
    public static final String ACTION_MULTIMEDIA = "com.leapmotor.command.multimedia";
    public static final String ACTION_MUSIC = "com.leapmotor.command.music";
    public static final String ACTION_CUSTOMKEY_PLAYPAUSE = "com.leapmotor.customkey.music.pauseplay";
    public static final String ACTION_ICU2MMIC = "com.leapmotor.ICU2MMICtrl";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (action == null) return;
        Log.i(TAG, "Received action: " + action);

        switch (action) {
            case ACTION_METER:
            case ACTION_HMI:
                handleMeterBroadcast(context, intent);
                break;
            case ACTION_MULTIMEDIA:
            case ACTION_MUSIC:
                handleCarKey(context, intent.getStringExtra("type"));
                break;
            case ACTION_CUSTOMKEY_PLAYPAUSE:
                handleCarKey(context, "playpause");
                break;
            case ACTION_ICU2MMIC:
                String cmd = intent.getStringExtra("type");
                if (cmd == null) cmd = intent.getStringExtra("action");
                if (cmd == null) cmd = intent.getStringExtra("cmd");
                handleCarKey(context, cmd);
                break;
            default:
                break;
        }
    }

    /**
     * 解析 car.meter/hmi.music.BROADCAST 的 byte[] 报文，提取 data.action 转发到 JS。
     */
    private void handleMeterBroadcast(Context context, Intent intent) {
        byte[] payload = intent.getByteArrayExtra("receiver");
        if (payload == null || payload.length < 2) {
            Log.w(TAG, "meter broadcast: empty receiver payload");
            return;
        }
        int len = (payload[0] & 0xff) * 0x64 + (payload[1] & 0xff);
        if (len <= 0 || 2 + len > payload.length) {
            Log.w(TAG, "meter broadcast: invalid length=" + len);
            return;
        }
        String json;
        try {
            json = new String(payload, 2, len, StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.e(TAG, "meter broadcast: decode failed " + e.getMessage());
            return;
        }
        try {
            JSONObject root = new JSONObject(json);
            // 外层 type 必须为 "music" 才处理（真实报文 data.type == -1，过滤交给 mapCmd）
            if (!"music".equals(root.optString("type", ""))) return;
            JSONObject data = root.optJSONObject("data");
            if (data == null) return;
            String cmd = data.optString("action", "");
            Log.i(TAG, "meter action: " + cmd);
            handleCarKey(context, cmd);
        } catch (JSONException e) {
            Log.e(TAG, "meter broadcast: json parse failed " + e.getMessage());
        }
    }

    /**
     * 根据按键类型映射到 JS 事件
     */
    private void handleCarKey(Context context, String keyType) {
        if (keyType == null) return;

        String jsEvent = null;
        if ("play".equals(keyType) || "playpause".equals(keyType) || "pauseplay".equals(keyType)) {
            jsEvent = "carPlayPause";
        } else if ("pause".equals(keyType)) {
            jsEvent = "carPause";
        } else if ("nextOne".equals(keyType)) {
            jsEvent = "carNext";
        } else if ("preOne".equals(keyType)) {
            jsEvent = "carPrev";
        } else if ("stop".equals(keyType)) {
            jsEvent = "carStop";
        }

        if (jsEvent != null) {
            sendEventToJS(context, jsEvent, keyType);
        }
    }

    /**
     * 发送事件到 React Native JS 层
     */
    private void sendEventToJS(Context context, String eventName, String keyType) {
        try {
            ReactApplication reactApplication = (ReactApplication) context.getApplicationContext();
            ReactInstanceManager reactInstanceManager = reactApplication.getReactNativeHost().getReactInstanceManager();

            if (reactInstanceManager == null) {
                Log.w(TAG, "ReactInstanceManager is null");
                return;
            }

            ReactContext reactContext = reactInstanceManager.getCurrentReactContext();
            if (reactContext == null) {
                Log.w(TAG, "ReactContext is null");
                return;
            }

            if (reactContext.hasActiveReactInstance()) {
                DeviceEventManagerModule.RCTDeviceEventEmitter eventEmitter = reactContext
                        .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class);

                WritableMap params = Arguments.createMap();
                params.putString("keyType", keyType);
                params.putString("event", eventName);

                eventEmitter.emit("CarKeyEvent", params);
                Log.i(TAG, "Event sent to JS: " + eventName + ", keyType: " + keyType);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error sending event to JS: " + e.getMessage());
        }
    }
}
