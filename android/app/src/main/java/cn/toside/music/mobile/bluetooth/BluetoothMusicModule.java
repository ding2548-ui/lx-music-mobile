package cn.toside.music.mobile.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

/**
 * 车机蓝牙音乐协议模块（蓝牙）
 *
 * 协议逆向自零跑 com.leapmotor.mymusic 蓝牙音乐模块（car-music-bt-extract 技能）：
 *  - 接收（车机 -> App）：com.leapmotor.bluetoothmusic.ctrl（ctrl=next/prev）、
 *      com.leapmotor.bluetooth.BROADCAST（连接状态）、com.leapmotor.bluetoothmusic.state.change（state: 4/7=播放）
 *  - 发送（App -> 车机）：com.leapmotor.bluetoothmusic.ctrl（ctrl=state/next/prev）、
 *      com.leapmotor.command.mediastate（state）
 *  - playState 语义：1=播放中，0=已暂停，2=已断开；A2DP_SINK = 0xb
 */
public class BluetoothMusicModule extends ReactContextBaseJavaModule {
    private static final String TAG = "BluetoothMusicModule";
    private final ReactApplicationContext reactContext;

    // 接收（车机 -> App）
    private static final String ACTION_CTRL = "com.leapmotor.bluetoothmusic.ctrl";
    private static final String ACTION_BT_BROADCAST = "com.leapmotor.bluetooth.BROADCAST";
    private static final String ACTION_STATE_CHANGE = "com.leapmotor.bluetoothmusic.state.change";
    // 发送（App -> 车机）
    private static final String ACTION_SEND_CTRL = "com.leapmotor.bluetoothmusic.ctrl";
    private static final String ACTION_SEND_MEDIASTATE = "com.leapmotor.command.mediastate";
    // A2DP_SINK
    private static final int A2DP_SINK = 0xb;

    public static final int PLAY_STATE_PLAYING = 1;
    public static final int PLAY_STATE_PAUSED = 0;
    public static final int PLAY_STATE_DISCONNECTED = 2;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            String action = intent.getAction();
            if (action == null) return;
            switch (action) {
                case ACTION_CTRL: {
                    String ctrl = intent.getStringExtra("ctrl");
                    if (ctrl == null) return;
                    WritableMap params = Arguments.createMap();
                    params.putString("ctrl", ctrl);
                    sendEvent("BluetoothMusicEvent", params);
                    Log.i(TAG, "recv ctrl: " + ctrl);
                    break;
                }
                case ACTION_BT_BROADCAST: {
                    boolean connected = isBluetoothA2dpConnected();
                    WritableMap params = Arguments.createMap();
                    params.putBoolean("connection", connected);
                    sendEvent("BluetoothMusicEvent", params);
                    Log.i(TAG, "bluetooth connection: " + connected);
                    break;
                }
                case ACTION_STATE_CHANGE: {
                    int state = intent.getIntExtra("state", -1);
                    boolean playing = (state == 4 || state == 7);
                    WritableMap params = Arguments.createMap();
                    params.putBoolean("carPlaying", playing);
                    sendEvent("BluetoothMusicEvent", params);
                    Log.i(TAG, "car state.change: " + state);
                    break;
                }
                default:
                    break;
            }
        }
    };

    public BluetoothMusicModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
    }

    @Override
    public String getName() {
        return "BluetoothMusicModule";
    }

    @Override
    public void initialize() {
        super.initialize();
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_CTRL);
        filter.addAction(ACTION_BT_BROADCAST);
        filter.addAction(ACTION_STATE_CHANGE);
        try {
            this.reactContext.registerReceiver(receiver, filter);
            Log.i(TAG, "BluetoothMusicModule initialized, receiver registered");
        } catch (Exception e) {
            Log.e(TAG, "registerReceiver failed: " + e.getMessage());
        }
    }

    @Override
    public void onCatalystInstanceDestroy() {
        super.onCatalystInstanceDestroy();
        try {
            this.reactContext.unregisterReceiver(receiver);
        } catch (Exception e) {
            Log.w(TAG, "unregisterReceiver failed: " + e.getMessage());
        }
    }

    /**
     * 向车机回传播放状态（App -> 车机）
     * @param playState 1=播放中，0=已暂停，2=已断开
     */
    @ReactMethod
    public void sendBluetoothState(int playState) {
        try {
            Intent ctrlIntent = new Intent(ACTION_SEND_CTRL);
            ctrlIntent.putExtra("ctrl", "state");
            ctrlIntent.putExtra("state", playState);
            this.reactContext.sendBroadcast(ctrlIntent);

            Intent mediaStateIntent = new Intent(ACTION_SEND_MEDIASTATE);
            mediaStateIntent.putExtra("state", playState);
            this.reactContext.sendBroadcast(mediaStateIntent);

            Log.i(TAG, "sendBluetoothState: " + playState);
        } catch (Exception e) {
            Log.e(TAG, "sendBluetoothState failed: " + e.getMessage());
        }
    }

    /**
     * 向车机发送控制指令（next/prev）
     */
    @ReactMethod
    public void sendControl(String cmd) {
        try {
            Intent ctrlIntent = new Intent(ACTION_SEND_CTRL);
            ctrlIntent.putExtra("ctrl", cmd);
            this.reactContext.sendBroadcast(ctrlIntent);
            Log.i(TAG, "sendControl: " + cmd);
        } catch (Exception e) {
            Log.e(TAG, "sendControl failed: " + e.getMessage());
        }
    }

    private boolean isBluetoothA2dpConnected() {
        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter == null) return false;
            int state = adapter.getProfileConnectionState(A2DP_SINK);
            return state == BluetoothProfile.STATE_CONNECTED;
        } catch (Exception e) {
            Log.w(TAG, "getProfileConnectionState failed: " + e.getMessage());
            return false;
        }
    }

    private void sendEvent(String eventName, WritableMap params) {
        try {
            if (this.reactContext.hasActiveReactInstance()) {
                this.reactContext
                        .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                        .emit(eventName, params);
            }
        } catch (Exception e) {
            Log.e(TAG, "sendEvent failed: " + e.getMessage());
        }
    }
}
