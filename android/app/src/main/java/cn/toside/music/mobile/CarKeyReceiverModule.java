package cn.toside.music.mobile;

import android.content.Intent;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.content.Context;
import android.util.Log;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import javax.annotation.Nullable;

/**
 * 车机按键 Native Module
 * 允许 JS 层通过 NativeEventEmitter 监听车机按键事件
 */
public class CarKeyReceiverModule extends ReactContextBaseJavaModule {
    private static final String TAG = "CarKeyReceiverModule";
    private static final String MODULE_NAME = "CarKeyReceiver";
    
    private ReactApplicationContext reactContext;
    private BroadcastReceiver broadcastReceiver;
    
    public CarKeyReceiverModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
        registerBroadcastReceiver();
    }
    
    @Override
    public String getName() {
        return MODULE_NAME;
    }
    
    /**
     * 注册广播接收器
     */
    private void registerBroadcastReceiver() {
        if (broadcastReceiver != null) return;
        
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null) return;
                
                String action = intent.getAction();
                Log.i(TAG, "Received action: " + action);
                
                String keyType = intent.getStringExtra("type");
                if (keyType != null) {
                    Log.i(TAG, "Key type: " + keyType);
                    sendEvent(keyType);
                }
            }
        };
        
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.leapmotor.command.multimedia");
        filter.addAction("com.leapmotor.command.music");
        
        try {
            reactContext.registerReceiver(broadcastReceiver, filter);
            Log.i(TAG, "BroadcastReceiver registered successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to register BroadcastReceiver: " + e.getMessage());
        }
    }
    
    /**
     * 发送事件到 JS 层
     */
    private void sendEvent(String keyType) {
        if (reactContext == null || !reactContext.hasActiveCatalystInstance()) {
            return;
        }
        
        WritableMap params = Arguments.createMap();
        params.putString("keyType", keyType);
        params.putString("event", getEventName(keyType));
        
        reactContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
            .emit("CarKeyEvent", params);
            
        Log.i(TAG, "Event sent to JS: " + keyType);
    }
    
    /**
     * 根据按键类型获取事件名称
     */
    private String getEventName(String keyType) {
        if (keyType == null) return "unknown";
        
        switch (keyType) {
            case "play":
            case "playpause":
                return "carPlayPause";
            case "pause":
                return "carPause";
            case "nextOne":
                return "carNext";
            case "preOne":
                return "carPrev";
            default:
                return "unknown";
        }
    }
    
    @ReactMethod
    public void addListener(String eventName) {
        // Required for NativeEventEmitter
    }
    
    @ReactMethod
    public void removeListeners(Integer count) {
        // Required for NativeEventEmitter
    }
    
    @Override
    public void invalidate() {
        super.invalidate();
        if (broadcastReceiver != null) {
            try {
                reactContext.unregisterReceiver(broadcastReceiver);
                Log.i(TAG, "BroadcastReceiver unregistered");
            } catch (Exception e) {
                Log.e(TAG, "Failed to unregister BroadcastReceiver: " + e.getMessage());
            }
            broadcastReceiver = null;
        }
    }
}
