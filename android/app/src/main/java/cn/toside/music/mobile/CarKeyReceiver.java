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

/**
 * 车机方向盘按键接收器
 * 处理车机发送的多媒体控制按键事件，发送到 JS 层控制音乐播放
 */
public class CarKeyReceiver extends BroadcastReceiver {
    private static final String TAG = "CarKeyReceiver";
    
    // 广播 Action
    public static final String ACTION_MULTIMEDIA = "com.leapmotor.command.multimedia";
    public static final String ACTION_MUSIC = "com.leapmotor.command.music";
    
    // 播放控制命令
    public static final String KEY_PLAY = "play";
    public static final String KEY_PAUSE = "pause";
    public static final String KEY_NEXT = "nextOne";
    public static final String KEY_PREV = "preOne";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        
        String action = intent.getAction();
        Log.i(TAG, "Received action: " + action);
        
        if (ACTION_MULTIMEDIA.equals(action)) {
            String type = intent.getStringExtra("type");
            Log.i(TAG, "Multimedia key: " + type);
            handleCarKey(context, type);
        } else if (ACTION_MUSIC.equals(action)) {
            String type = intent.getStringExtra("type");
            Log.i(TAG, "Music key: " + type);
            handleCarKey(context, type);
        }
    }

    /**
     * 处理车机按键事件，发送到 JS 层
     */
    private void handleCarKey(Context context, String keyType) {
        if (keyType == null) return;
        
        String jsEvent = null;
        
        // 根据按键类型映射到 JS 事件
        if (KEY_PLAY.equals(keyType) || "playpause".equals(keyType)) {
            jsEvent = "carPlayPause";
        } else if (KEY_PAUSE.equals(keyType)) {
            jsEvent = "carPause";
        } else if (KEY_NEXT.equals(keyType)) {
            jsEvent = "carNext";
        } else if (KEY_PREV.equals(keyType)) {
            jsEvent = "carPrev";
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
            
            WritableMap params = Arguments.createMap();
            params.putString("event", eventName);
            params.putString("keyType", keyType);
            
            reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit("CarKeyEvent", params);
                
            Log.i(TAG, "Sent event to JS: " + eventName + " (keyType: " + keyType + ")");
        } catch (Exception e) {
            Log.e(TAG, "Failed to send event to JS: " + e.getMessage());
        }
    }
}
