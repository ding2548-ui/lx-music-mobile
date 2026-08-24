import { NativeModules, NativeEventEmitter, Platform } from 'react-native'
import TrackPlayer, { State, Event } from 'react-native-track-player'
import { playNext, playPrev } from '@/core/player/player'
import settingState from '@/store/setting/state'

const { BluetoothMusicModule } = NativeModules

let isInitialized = false

/**
 * 蓝牙播放状态：
 *  - PLAYING = 1 播放中
 *  - PAUSED  = 0 已暂停
 *  - DISCONNECTED = 2 已断开
 */
export const BLUETOOTH_PLAY_STATE = {
  PLAYING: 1,
  PAUSED: 0,
  DISCONNECTED: 2,
}

/**
 * 初始化蓝牙音乐协议桥接：
 *  - 监听车机通过蓝牙下发的控制指令（ctrl=next/prev），转发到播放器
 *  - 监听 TrackPlayer 播放状态变化，回传给车机蓝牙面板（com.leapmotor.bluetoothmusic.ctrl 等）
 */
export const initBluetoothMusic = async (): Promise<void> => {
  if (isInitialized || Platform.OS !== 'android' || !BluetoothMusicModule) {
    return
  }

  try {
    const eventEmitter = new NativeEventEmitter(BluetoothMusicModule)

    // 车机 -> App：蓝牙下发的控制指令
    eventEmitter.addListener('BluetoothMusicEvent', (event: { ctrl?: string; connection?: boolean; carPlaying?: boolean }) => {
      if (!settingState.setting['player.isEnableBluetoothMusic']) return
      void handleBluetoothEvent(event)
    })

    // App 播放状态变化 -> 回传车机蓝牙面板
    TrackPlayer.addEventListener(Event.PlaybackState, (data) => {
      if (!settingState.setting['player.isEnableBluetoothMusic']) return
      const playState = data.state === State.Playing
        ? BLUETOOTH_PLAY_STATE.PLAYING
        : BLUETOOTH_PLAY_STATE.PAUSED
      BluetoothMusicModule.sendBluetoothState(playState)
    })

    // 切歌 -> 回传当前为播放状态
    TrackPlayer.addEventListener(Event.PlaybackTrackChanged, () => {
      if (!settingState.setting['player.isEnableBluetoothMusic']) return
      BluetoothMusicModule.sendBluetoothState(BLUETOOTH_PLAY_STATE.PLAYING)
    })

    isInitialized = true
    console.log('Bluetooth music module initialized')
  } catch (error) {
    console.error('Failed to init bluetooth music module:', error)
  }
}

/**
 * 处理车机下发的蓝牙事件
 */
const handleBluetoothEvent = async (event: { ctrl?: string; connection?: boolean; carPlaying?: boolean }): Promise<void> => {
  try {
    if (event.ctrl === 'next') {
      await playNext()
    } else if (event.ctrl === 'prev') {
      await playPrev()
    }
    // connection / carPlaying 用于 UI 状态展示，本次仅做日志，可按需扩展
  } catch (error) {
    console.error('Failed to handle bluetooth event:', error)
  }
}

/**
 * 主动回传播放状态给车机蓝牙面板
 */
export const sendBluetoothState = (playState: number): void => {
  if (Platform.OS === 'android' && BluetoothMusicModule) {
    BluetoothMusicModule.sendBluetoothState(playState)
  }
}
