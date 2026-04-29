package cn.toside.music.mobile;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * 车机按键静态广播接收器
 * 对照 MultiMedia StaticReciver + CtrlReciver 实现
 * 
 * 监听三个关键广播（priority=1000，与 MultiMedia 一致）：
 * - com.leapmotor.command.multimedia: type(string) + state(int) → 语音/launcher 控制
 * - com.leapmotor.command.music: type(string) → 语音搜索音乐（也可能含播放控制）
 * - com.leapmotor.customkey.music.pauseplay: ICU_MediaKey(int) + ICU_MediaSwitch(int) → 方向盘按键
 * 
 * 当 ReactContext 不活跃时：启动 MainActivity 并传递按键数据
 * 当 ReactContext 活跃时：直接通过 CarKeyReceiverModule 转发到 JS 层
 */
public class CarKeyReceiver extends BroadcastReceiver {
    private static final String TAG = "CarKeyReceiver";

    // 广播 Action（对照 MultiMedia CtrlReciver IntentFilter）
    private static final String ACTION_MULTIMEDIA = "com.leapmotor.command.multimedia";
    private static final String ACTION_MUSIC = "com.leapmotor.command.music";
    private static final String ACTION_CUSTOMKEY = "com.leapmotor.customkey.music.pauseplay";

    // type 字段指令（对照 MultiMedia CtrlReciver.dispathVoiceCtrl / ctrlOnlineMusicByVoice）
    private static final String TYPE_PLAY = "play";
    private static final String TYPE_PAUSE = "pause";
    private static final String TYPE_PLAYPAUSE = "playpause";
    private static final String TYPE_NEXT = "nextOne";
    private static final String TYPE_PREV = "preOne";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;

        String action = intent.getAction();
        if (action == null) return;

        Log.i(TAG, "StaticReceiver onReceive: " + action);

        String keyType = null;

        if (ACTION_CUSTOMKEY.equals(action)) {
            // 🎯 方向盘按键：ICU_MediaKey + ICU_MediaSwitch（对照 CtrlReciver.dispatchWheelEvent）
            keyType = parseWheelKeyEvent(intent);
        } else if (ACTION_MULTIMEDIA.equals(action) || ACTION_MUSIC.equals(action)) {
            // 语音/launcher 控制：读取 type 字段
            // 对照 CtrlReciver.dispathVoiceCtrl — 只处理播放控制类指令
            String type = intent.getStringExtra("type");
            if (type != null && isPlaybackControlType(type)) {
                keyType = type;
                Log.i(TAG, "Voice/Launcher playback control: type=" + type);
            } else if (type != null) {
                Log.i(TAG, "Voice search command (not playback control): type=" + type);
            }
        }

        if (keyType != null) {
            Log.i(TAG, "Parsed key type: " + keyType);

            // 方案1：尝试直接转发到 ReactModule（如果 ReactContext 活跃）
            boolean sentToJs = CarKeyReceiverModule.sendEventStatic(keyType);

            // 方案2：如果 JS 层不可达，启动/唤醒 MainActivity 并传递按键数据
            if (!sentToJs) {
                Log.i(TAG, "ReactContext not active, launching MainActivity with keyType=" + keyType);
                Intent launchIntent = new Intent(context, MainActivity.class);
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                launchIntent.putExtra("carKeyType", keyType);
                context.startActivity(launchIntent);
            }
        }
    }

    /**
     * 解析方向盘按键事件
     * 对照 MultiMedia CtrlReciver.dispatchWheelEvent
     * ICU_MediaKey: 1 → playpause（播放/暂停切换）
     * ICU_MediaSwitch: 1 → preOne（上一曲），2 → nextOne（下一曲）
     */
    private String parseWheelKeyEvent(Intent intent) {
        int mediaKey = intent.getIntExtra("ICU_MediaKey", 0);
        int mediaSwitch = intent.getIntExtra("ICU_MediaSwitch", 0);

        Log.i(TAG, "Wheel event: ICU_MediaKey=" + mediaKey + ", ICU_MediaSwitch=" + mediaSwitch);

        // 对照 dispatchWheelEvent: if-nez v1, :cond_0 + if-nez p1, :cond_0 → return
        if (mediaKey == 0 && mediaSwitch == 0) return null;

        // 对照 dispatchWheelEvent 的逻辑顺序：
        // ICU_MediaKey=1 → "playpause"（优先判断，对照 :cond_2）
        if (mediaKey == 1) {
            return TYPE_PLAYPAUSE;
        }

        // ICU_MediaSwitch=1 → "preOne"（对照 :cond_4）
        if (mediaSwitch == 1) {
            return TYPE_PREV;
        }

        // ICU_MediaSwitch=2 → "nextOne"（对照 :cond_3）
        if (mediaSwitch == 2) {
            return TYPE_NEXT;
        }

        Log.w(TAG, "Unknown ICU values: MediaKey=" + mediaKey + ", MediaSwitch=" + mediaSwitch);
        return null;
    }

    /**
     * 判断 type 是否为播放控制指令
     * 对照 CtrlReciver.ctrlOnlineMusicByVoice — 只处理 play/pause/playpause/nextOne/preOne
     * 其他 type（如语音搜索）不转发到播放控制
     */
    private boolean isPlaybackControlType(String type) {
        return TYPE_PLAY.equals(type)
            || TYPE_PAUSE.equals(type)
            || TYPE_PLAYPAUSE.equals(type)
            || TYPE_NEXT.equals(type)
            || TYPE_PREV.equals(type);
    }
}