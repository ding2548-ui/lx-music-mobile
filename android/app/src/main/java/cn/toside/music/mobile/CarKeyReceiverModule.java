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
 * 车机按键 Native Module（对照 MultiMedia CtrlReciver + KGMusicBrowserService）
 * 
 * 支持三种广播数据格式：
 * - com.leapmotor.command.multimedia: type(string) + state(int) → 语音/launcher 播放控制
 * - com.leapmotor.command.music: type(string) → 语音搜索（仅处理播放控制类 type）
 * - com.leapmotor.customkey.music.pauseplay: ICU_MediaKey(int) + ICU_MediaSwitch(int) → 方向盘按键
 * 
 * 关键改进（对照 MultiMedia 差异修复）：
 * 1. 使用 Application context 注册 BroadcastReceiver（非 ReactContext），确保 ReactContext 销毁后仍可接收广播
 * 2. 添加静态 sendEvent 方法，供静态 CarKeyReceiver 直接调用
 * 3. 正确映射方向盘按键：com.leapmotor.customkey.music.pauseplay → dispatchWheelEvent（之前误用 command.music）
 */
public class CarKeyReceiverModule extends ReactContextBaseJavaModule {
    private static final String TAG = "CarKeyReceiverModule";
    private static final String MODULE_NAME = "CarKeyReceiver";

    // 广播 Action（对照 MultiMedia CtrlReciver IntentFilter）
    private static final String ACTION_MULTIMEDIA = "com.leapmotor.command.multimedia";
    private static final String ACTION_MUSIC = "com.leapmotor.command.music";
    private static final String ACTION_CUSTOMKEY = "com.leapmotor.customkey.music.pauseplay";

    // type 字段指令
    private static final String TYPE_PLAY = "play";
    private static final String TYPE_PAUSE = "pause";
    private static final String TYPE_PLAYPAUSE = "playpause";
    private static final String TYPE_NEXT = "nextOne";
    private static final String TYPE_PREV = "preOne";

    // 静态引用 ReactContext，供静态 CarKeyReceiver 调用
    private static ReactApplicationContext staticReactContext = null;
    private static BroadcastReceiver dynamicReceiver = null;
    private static boolean isReceiverRegistered = false;

    private ReactApplicationContext reactContext;

    public CarKeyReceiverModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
        staticReactContext = reactContext;
        registerBroadcastReceiver();
    }

    @Override
    public String getName() {
        return MODULE_NAME;
    }

    /**
     * 注册动态广播接收器，使用 Application context（非 ReactContext）
     * 对照 MultiMedia KGMusicBrowserService.onCreate() 中 registerReceiver
     * 
     * 关键差异修复：MultiMedia 在 MediaBrowserService 中注册 receiver（Service 生命周期独立于 Activity）
     * React Native 没有 MediaBrowserService，但 Application context 同样独立于 Activity 生命周期
     * 所以用 getApplicationContext() 注册 receiver，确保 ReactContext 销毁后仍能接收广播
     */
    private void registerBroadcastReceiver() {
        if (isReceiverRegistered) return;

        dynamicReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null) return;

                String action = intent.getAction();
                Log.i(TAG, "DynamicReceiver onReceive: " + action);

                if (action == null) return;

                String keyType = null;

                if (ACTION_CUSTOMKEY.equals(action)) {
                    // 🎯 方向盘按键：ICU_MediaKey + ICU_MediaSwitch
                    keyType = parseWheelKeyEvent(intent);
                } else if (ACTION_MULTIMEDIA.equals(action) || ACTION_MUSIC.equals(action)) {
                    // 语音/launcher 控制：读取 type 字段（仅处理播放控制类）
                    String type = intent.getStringExtra("type");
                    if (type != null && isPlaybackControlType(type)) {
                        keyType = type;
                        Log.i(TAG, "Voice/Launcher playback control: type=" + type);
                    }
                }

                if (keyType != null) {
                    Log.i(TAG, "Parsed key type: " + keyType);
                    sendEvent(keyType);
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_MULTIMEDIA);
        filter.addAction(ACTION_MUSIC);
        filter.addAction(ACTION_CUSTOMKEY);
        filter.setPriority(1000); // 对照 MultiMedia CtrlReciver priority=1000

        try {
            // 关键修复：使用 Application context 注册，而非 ReactContext
            // ReactContext 可能被销毁导致 receiver 自动解除注册
            // Application context 在整个 App 进程生命周期内保持有效
            reactContext.getApplicationContext().registerReceiver(dynamicReceiver, filter);
            isReceiverRegistered = true;
            Log.i(TAG, "DynamicReceiver registered with Application context (priority=1000)");
        } catch (Exception e) {
            Log.e(TAG, "Failed to register DynamicReceiver: " + e.getMessage());
        }
    }

    /**
     * 解析方向盘按键事件
     * 对照 MultiMedia CtrlReciver.dispatchWheelEvent 实现
     * ICU_MediaKey: 1 → playpause（播放/暂停切换）
     * ICU_MediaSwitch: 1 → preOne（上一曲），2 → nextOne（下一曲）
     */
    private String parseWheelKeyEvent(Intent intent) {
        int mediaKey = intent.getIntExtra("ICU_MediaKey", 0);
        int mediaSwitch = intent.getIntExtra("ICU_MediaSwitch", 0);

        Log.i(TAG, "Wheel event: ICU_MediaKey=" + mediaKey + ", ICU_MediaSwitch=" + mediaSwitch);

        if (mediaKey == 0 && mediaSwitch == 0) return null;

        // 对照 dispatchWheelEvent：ICU_MediaKey=1 → "playpause"（优先判断）
        if (mediaKey == 1) {
            return TYPE_PLAYPAUSE;
        }

        // ICU_MediaSwitch=1 → "preOne"，2 → "nextOne"
        if (mediaSwitch == 1) {
            return TYPE_PREV;
        }

        if (mediaSwitch == 2) {
            return TYPE_NEXT;
        }

        Log.w(TAG, "Unknown ICU values: MediaKey=" + mediaKey + ", MediaSwitch=" + mediaSwitch);
        return null;
    }

    /**
     * 判断 type 是否为播放控制指令
     */
    private boolean isPlaybackControlType(String type) {
        return TYPE_PLAY.equals(type)
            || TYPE_PAUSE.equals(type)
            || TYPE_PLAYPAUSE.equals(type)
            || TYPE_NEXT.equals(type)
            || TYPE_PREV.equals(type);
    }

    /**
     * 发送事件到 JS 层
     */
    private void sendEvent(String keyType) {
        if (reactContext == null || !reactContext.hasActiveCatalystInstance()) {
            Log.w(TAG, "ReactContext not active, cannot send event via instance");
            return;
        }

        WritableMap params = Arguments.createMap();
        params.putString("keyType", keyType);

        reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                    .emit("CarKeyEvent", params);
        Log.i(TAG, "Event sent to JS: CarKeyEvent, keyType=" + keyType);
    }

    /**
     * 静态方法：供静态 CarKeyReceiver 调用
     * 当 ReactContext 活跃时直接发送事件到 JS 层
     * 返回 true 表示事件已成功发送到 JS
     */
    public static boolean sendEventStatic(String keyType) {
        if (staticReactContext != null && staticReactContext.hasActiveCatalystInstance()) {
            WritableMap params = Arguments.createMap();
            params.putString("keyType", keyType);

            staticReactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                              .emit("CarKeyEvent", params);
            Log.i(TAG, "Static sendEvent: CarKeyEvent, keyType=" + keyType);
            return true;
        }

        Log.w(TAG, "Static sendEvent: ReactContext not active for keyType=" + keyType);
        return false;
    }

    /**
     * React Native NativeEventEmitter 需要的方法
     */
    @ReactMethod
    public void addListener(String eventName) {
        // Required for NativeEventEmitter
    }

    @ReactMethod
    public void removeListeners(Integer count) {
        // Required for NativeEventEmitter
    }
}