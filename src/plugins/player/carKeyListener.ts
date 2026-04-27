import { NativeModules, NativeEventEmitter, Platform } from 'react-native'
import TrackPlayer, { Event as TPEvent } from 'react-native-track-player'
import { play, pause, playNext, playPrev } from '@/core/player/player'

const { CarKeyReceiver: CarKeyReceiverModule } = NativeModules

let isInitialized = false

/**
 * 初始化车机按键事件监听
 * 在应用启动时调用
 */
export const initCarKeyListener = async (): Promise<void> => {
  if (isInitialized || Platform.OS !== 'android' || !CarKeyReceiverModule) {
    return
  }

  try {
    const eventEmitter = new NativeEventEmitter(CarKeyReceiverModule)

    // 监听车机按键事件
    eventEmitter.addListener('CarKeyEvent', (event: { keyType: string; event: string }) => {
      console.log('CarKeyEvent received:', event)
      handleCarKeyEvent(event.keyType)
    })

    isInitialized = true
    console.log('Car key listener initialized')
  } catch (error) {
    console.error('Failed to init car key listener:', error)
  }
}

/**
 * 处理车机按键事件
 * 映射到 react-native-track-player 远程控制
 */
const handleCarKeyEvent = async (keyType: string): Promise<void> => {
  try {
    switch (keyType) {
      case 'play':
      case 'playpause':
        // 切换播放/暂停
        await TrackPlayer.play()
        break
      case 'pause':
        await TrackPlayer.pause()
        break
      case 'nextOne':
        await TrackPlayer.skipToNext()
        break
      case 'preOne':
        await TrackPlayer.skipToPrevious()
        break
      default:
        console.log('Unknown car key type:', keyType)
        break
    }
  } catch (error) {
    console.error('Failed to handle car key event:', error)
  }
}

/**
 * 注册 react-native-track-player 远程控制服务
 * 这个函数在应用启动时被调用
 */
export const registerCarControlService = async (): Promise<void> => {
  // 注册 react-native-track-player 远程控制事件
   TrackPlayer.addEventListener(TPEvent.RemotePlay, () => {
    console.log('Remote play event received')
    void play()
  })

  TrackPlayer.addEventListener(TPEvent.RemotePause, () => {
    console.log('Remote pause event received')
    void pause()
  })

  TrackPlayer.addEventListener(TPEvent.RemoteNext, () => {
    console.log('Remote next event received')
    void playNext()
  })

  TrackPlayer.addEventListener(TPEvent.RemotePrevious, () => {
    console.log('Remote previous event received')
    void playPrev()
  })

  TrackPlayer.addEventListener(TPEvent.RemoteStop, () => {
    console.log('Remote stop event received')
    void TrackPlayer.reset()
  })

  // 初始化车机按键监听
  await initCarKeyListener()
}