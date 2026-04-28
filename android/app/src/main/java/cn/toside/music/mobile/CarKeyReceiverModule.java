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
 * 杞︽満鎸夐敭 Native Module
 * 瀵圭収闆惰窇 MultiMedia 鍘熺増 CtrlReciver 瀹炵幇锛屾敮鎸佷笁绉嶅箍鎾暟鎹牸寮忥細
 * - com.leapmotor.command.multimedia: type(string) 鈫?璇煶/ launcher 鎺у埗
 * - com.leapmotor.command.music: type(string) 鈫?璇煶鎼滅储闊充箰
 * - com.leapmotor.customkey.music.pauseplay: ICU_MediaKey(int) + ICU_MediaSwitch(int) 鈫?鏂瑰悜鐩樻寜閿?
 */
public class CarKeyReceiverModule extends ReactContextBaseJavaModule {
    private static final String TAG = "CarKeyReceiverModule";
    private static final String MODULE_NAME = "CarKeyReceiver";

    // 骞挎挱 Action锛堝鐓?MultiMedia CtrlReciver锛?
    private static final String ACTION_MULTIMEDIA = "com.leapmotor.command.multimedia";
    private static final String ACTION_MUSIC = "com.leapmotor.command.music";
    private static final String ACTION_CUSTOMKEY = "com.leapmotor.customkey.music.pauseplay";

    // type 瀛楁鍛戒护锛堣闊?launcher 鎺у埗锛?
    private static final String TYPE_PLAY = "play";
    private static final String TYPE_PAUSE = "pause";
    private static final String TYPE_PLAYPAUSE = "playpause";
    private static final String TYPE_NEXT = "nextOne";
    private static final String TYPE_PREV = "preOne";

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
     * 娉ㄥ唽骞挎挱鎺ユ敹鍣紝鐩戝惉涓変釜杞︽満鎺у埗骞挎挱
     * 瀵圭収 MultiMedia CtrlReciver 鐨?IntentFilter 娉ㄥ唽
     */
    private void registerBroadcastReceiver() {
        if (broadcastReceiver != null) return;

        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null) return;

                String action = intent.getAction();
                Log.i(TAG, "Received action: " + action);

                if (action == null) return;

                String keyType = null;

                if (ACTION_CUSTOMKEY.equals(action)) {
                    // 鏂瑰悜鐩樻寜閿細璇诲彇 ICU_MediaKey(int) + ICU_MediaSwitch(int)
                    keyType = parseWheelKeyEvent(intent);
                } else if (ACTION_MULTIMEDIA.equals(action) || ACTION_MUSIC.equals(action)) {
                    // 璇煶/launcher 鎺у埗锛氳鍙?type(string)
                    keyType = intent.getStringExtra("type");
                    if (keyType != null) {
                        Log.i(TAG, "Voice/Launcher key: " + keyType);
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
        filter.setPriority(1000);

        try {
            reactContext.registerReceiver(broadcastReceiver, filter);
            Log.i(TAG, "BroadcastReceiver registered for 3 actions (priority=1000)");
        } catch (Exception e) {
            Log.e(TAG, "Failed to register BroadcastReceiver: " + e.getMessage());
        }
    }

    /**
     * 瑙ｆ瀽鏂瑰悜鐩樻寜閿簨浠?
     * 瀵圭収 MultiMedia CtrlReciver.dispatchWheelEvent 瀹炵幇
     * ICU_MediaKey: 1 鈫?playpause锛堟挱鏀?鏆傚仠鍒囨崲锛?
     * ICU_MediaSwitch: 1 鈫?preOne锛堜笂涓€鏇诧級锛? 鈫?nextOne锛堜笅涓€鏇诧級
     */
    private String parseWheelKeyEvent(Intent intent) {
        int mediaKey = intent.getIntExtra("ICU_MediaKey", 0);
        int mediaSwitch = intent.getIntExtra("ICU_MediaSwitch", 0);

        Log.i(TAG, "Wheel event: ICU_MediaKey=" + mediaKey + ", ICU_MediaSwitch=" + mediaSwitch);

        // 濡傛灉閮戒负0锛屽拷鐣ワ紙瀵圭収 dispatchWheelEvent: if-nez v1, :cond_0 + if-nez p1, :cond_0 鈫?return锛?
        if (mediaKey == 0 && mediaSwitch == 0) return null;

        // 瀵圭収 dispatchWheelEvent 鐨勯€昏緫锛?
        // ICU_MediaKey=1 鈫?"playpause"
        if (mediaKey == 1) {
            return TYPE_PLAYPAUSE;
        }

        // ICU_MediaSwitch=1 鈫?"preOne"锛堜笂涓€鏇诧級
        if (mediaSwitch == 1) {
            return TYPE_PREV;
        }

        // ICU_MediaSwitch=2 鈫?"nextOne"锛堜笅涓€鏇诧級
        if (mediaSwitch == 2) {
            return TYPE_NEXT;
        }

        Log.w(TAG, "Unknown ICU values: MediaKey=" + mediaKey + ", MediaSwitch=" + mediaSwitch);
        return null;
    }

    /**
     * 鍙戦€佷簨浠跺埌 JS 灞?
     */
    private void sendEvent(String keyType) {
        if (reactContext == null || !reactContext.hasActiveCatalystInstance()) {
            Log.w(TAG, "ReactContext not active, cannot send event");
            return;
        }

        WritableMap params = Arguments.createMap();
        params.putString("keyType", keyType);

        reactContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
            .emit("CarKeyEvent", params);

        Log.i(TAG, "Event sent to JS: CarKeyEvent, keyType=" + keyType);
    }

    @Override
    public void onCatalystInstanceDestroy() {
        if (broadcastReceiver != null) {
            try {
                reactContext.unregisterReceiver(broadcastReceiver);
            } catch (Exception e) {
                Log.e(TAG, "Failed to unregister BroadcastReceiver: " + e.getMessage());
            }
            broadcastReceiver = null;
        }
    }
}