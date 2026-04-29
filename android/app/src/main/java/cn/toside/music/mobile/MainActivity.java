package cn.toside.music.mobile;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import com.reactnativenavigation.NavigationActivity;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableMap;

/**
 * 主 Activity，处理车机按键 Intent extras
 * 
 * 当静态 CarKeyReceiver 在 ReactContext 不活跃时收到广播，
 * 会启动此 Activity 并通过 Intent extras 传递按键类型。
 * Activity 在 onNewIntent/onCreate 中读取 extras 并转发到 JS 层。
 */
public class MainActivity extends NavigationActivity {
    private static final String TAG = "MainActivity";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handleCarKeyIntent(getIntent());
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleCarKeyIntent(intent);
    }

    /**
     * 处理从静态 CarKeyReceiver 传递的车机按键 Intent
     */
    private void handleCarKeyIntent(Intent intent) {
        if (intent == null) return;

        String carKeyType = intent.getStringExtra("carKeyType");
        if (carKeyType != null) {
            Log.i(TAG, "CarKeyEvent from Intent extras: " + carKeyType);

            // 尝试通过 CarKeyReceiverModule 静态方法发送到 JS
            boolean sent = CarKeyReceiverModule.sendEventStatic(carKeyType);
            if (!sent) {
                Log.w(TAG, "Could not send carKeyType to JS yet, will retry after ReactContext ready");
                // ReactContext 还没准备好，保留 keyType 以便后续发送
                pendingCarKeyType = carKeyType;
            }

            // 清除 extra，避免重复处理
            intent.removeExtra("carKeyType");
        }
    }

    // 待发送的按键类型（ReactContext 准备好后再发送）
    private static String pendingCarKeyType = null;

    /**
     * 当 ReactContext 准备好后，发送待处理的按键事件
     * 由 CarKeyReceiverModule 在初始化时调用
     */
    public static void sendPendingCarKeyEvent() {
        if (pendingCarKeyType != null) {
            boolean sent = CarKeyReceiverModule.sendEventStatic(pendingCarKeyType);
            if (sent) {
                Log.i(TAG, "Pending carKeyEvent sent: " + pendingCarKeyType);
                pendingCarKeyType = null;
            }
        }
    }
}