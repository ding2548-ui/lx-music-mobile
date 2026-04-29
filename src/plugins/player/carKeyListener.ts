import { NativeModules, NativeEventEmitter, Platform } from 'react-native'
import { play, pause, playNext, playPrev } from '@/core/player/player'

const { CarKeyReceiver } = NativeModules

let isInitialized = false
let pendingKeyHandler: ((keyType: string) => void) | null = null

/**
 * 初始化车机按键事件监听
 * 对照 MultiMedia CtrlReciver + KGMusicBrowserService 的方向盘控制实现
 * 
 * 修复对照差异：
 * 1. 使用 CarKeyReceiver（NativeModule）作为 NativeEventEmitter 源，而非 DeviceEventManagerModule
 *    CarKeyReceiverModule 已添加 addListener/removeListeners 方法支持
 * 2. 使用应用自身的 play/pause/playNext/playPrev 函数（而非 TrackPlayer 直接调用）
 * 3. 支持三种按键来源：
 *    - ICU_MediaKey/ICU_MediaSwitch（方向盘按键，对照 dispatchWheelEvent）
 *    - type 字段（语音/launcher 控制，对照 dispathVoiceCtrl）
 */
export const initCarKeyListener = async(): Promise<void> => {
  if (isInitialized || Platform.OS !== 'android') {
    return
  }

  try {
    // 使用 CarKeyReceiver NativeModule 作为事件源
    // CarKeyReceiverModule 已添加 addListener/removeListeners 支持 NativeEventEmitter
    const eventEmitter = new NativeEventEmitter(CarKeyReceiver)

    // 监听车机按键事件
    eventEmitter.addListener('CarKeyEvent', (event: { keyType: string }) => {
      console.log('CarKeyEvent received:', event)
      handleCarKeyEvent(event.keyType)
    })

    // 发送待处理的按键事件（如果 app 启动时有 Intent extras）
    if (pendingKeyHandler) {
      pendingKeyHandler = null
    }

    isInitialized = true
    console.log('Car key listener initialized (CarKeyReceiver NativeEventEmitter)')

    // 通知 MainActivity 发送待处理的按键事件
    // 当静态 CarKeyReceiver 在 ReactContext 不活跃时启动了 MainActivity，
    // 按键数据保存在 MainActivity.pendingCarKeyType，ReactContext 准备好后再发送
    void sendPendingEvent()
  } catch (error) {
    console.error('Failed to init car key listener:', error)
  }
}

/**
 * 处理车机按键事件
 * 对照 MultiMedia CtrlReciver 的按键映射
 * 
 * 按键映射（对照 CtrlReciver.ctrlOnlineMusicByVoice + dispatchWheelEvent）：
 * - play/playpause → 播放或暂停
 * - pause → 暂停
 * - nextOne → 下一曲
 * - preOne → 上一曲
 * 
 * 关键修复：使用应用自身的 play/pause/playNext/playPrev 函数
 * 这些函数会正确更新应用状态（播放列表、UI、歌词等）
 * 而不是直接调用 TrackPlayer（TrackPlayer 只控制音频引擎，不更新应用状态）
 */
const handleCarKeyEvent = (keyType: string): void => {
  console.log('Handling car key type:', keyType)

  switch (keyType) {
    case 'play':
    case 'playpause':
      // 对照 CtrlReciver.playOrPause() — 播放/暂停切换
      void play()
      break
    case 'pause':
      // 对照 CtrlReciver.pause() — 暂停
      void pause()
      break
    case 'nextOne':
      // 对照 CtrlReciver.playNext() — 下一曲
      void playNext()
      break
    case 'preOne':
      // 对照 CtrlReciver.playPre() — 上一曲
      void playPrev()
      break
    default:
      console.log('Unknown car key type:', keyType)
      break
  }
}

/**
 * 注册 TrackPlayer 远程控制服务（MediaSession 标准按键）
 * 这些事件由 Android 系统的 MediaSession 机制触发（耳机按钮、方向盘标准按键等）
 * 对照 MultiMedia KGMusicBrowserService 的 MediaSessionCompat 配置
 */
export const registerCarControlService = async(): Promise<void> => {
  // TrackPlayer RemotePlay/Pause/Next/Previous 事件由 MediaSession 触发
  // 对照 MultiMedia PlaySessionCallback.onPlay/onPause/onSkipToNext/onSkipToPrevious
  // 注意：这些已在 service.ts 的 registerPlaybackService 中注册
  // 不需要重复注册，避免事件冲突

  // 初始化车机按键监听
  await initCarKeyListener()
}

/**
 * 保存待处理的按键回调（供外部模块在 ReactContext 准备好前注册）
 */
export const setPendingKeyHandler = (handler: (keyType: string) => void): void => {
  if (isInitialized) {
    // 已经初始化，直接使用正常流程
    return
  }
  pendingKeyHandler = handler
}

/**
 * 发送待处理的按键事件
 * 当静态 CarKeyReceiver 在 ReactContext 不活跃时启动了 MainActivity，
 * 按键数据保存在 MainActivity.pendingCarKeyType
 * ReactContext 准备好后，通过此函数通知 MainActivity 发送
 */
const sendPendingEvent = async(): Promise<void> => {
  // Android MainActivity.sendPendingCarKeyEvent() 会在 ReactContext 准备好后
  // 调用 CarKeyReceiverModule.sendEventStatic() 发送待处理的按键事件
  // JS 层通过 NativeEventEmitter 的 CarKeyEvent 监听器接收
  // 这里不需要额外操作，事件会自动到达监听器
  console.log('CarKeyListener ready, pending events will be delivered via NativeEventEmitter')
}