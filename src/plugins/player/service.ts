/* eslint-disable @typescript-eslint/no-misused-promises */
import TrackPlayer, { State as TPState, Event as TPEvent } from 'react-native-track-player'
import { isTempId, isEmpty } from './utils'
import { exitApp } from '@/core/common'
import { getCurrentTrackId } from './playList'
import { pause, play, playNext, playPrev } from '@/core/player/player'
//车机按键监听
import { initCarKeyListener } from './carKeyListener'

let isInitialized = false

const handleExitApp = async(reason: string) => {
  global.lx.isPlayedStop = false
  exitApp(reason)
}

const registerPlaybackService = async() => {
  if (isInitialized) return

  console.log('reg services...')

  // Remote player control events for headphones and steering wheel
  TrackPlayer.addEventListener(TPEvent.RemotePlay, () => {
    play()
  })

  TrackPlayer.addEventListener(TPEvent.RemotePause, () => {
    void pause()
  })

  TrackPlayer.addEventListener(TPEvent.RemoteNext, () => {
    void playNext()
  })

  TrackPlayer.addEventListener(TPEvent.RemotePrevious, () => {
    void playPrev()
  })

  TrackPlayer.addEventListener(TPEvent.RemoteStop, () => {
    void handleExitApp('Remote Stop')
  })

  // Remote player error
  TrackPlayer.addEventListener(TPEvent.PlaybackError, async(err: any) => {
    console.log('playback-error', err)
    global.app_event.error()
    global.app_event.playerError()
  })

  TrackPlayer.addEventListener(TPEvent.RemoteSeek, async({ position }) => {
    global.app_event.setProgress(position as number)
  })

  TrackPlayer.addEventListener(TPEvent.PlaybackState, async info => {
    if (global.lx.gettingUrlId || isTempId()) return

    switch (info.state) {
      case TPState.None:
        break
      case TPState.Ready:
      case TPState.Stopped:
      case TPState.Paused:
        global.app_event.playerPause()
        global.app_event.pause()
        break
      case TPState.Playing:
        global.app_event.playerPlaying()
        global.app_event.play()
        break
      case TPState.Buffering:
        global.app_event.pause()
        global.app_event.playerWaiting()
        break
      case TPState.Connecting:
        global.app_event.playerLoadstart()
        break
      default:
        break
    }
    if (global.lx.isPlayedStop) return handleExitApp('Timeout Exit')
  })

  // Track change
  TrackPlayer.addEventListener(TPEvent.PlaybackTrackChanged, async info => {
    global.lx.playerTrackId = await getCurrentTrackId()
    if (info.track == null) return
    if (global.lx.isPlayedStop) return handleExitApp('Timeout Exit')

    if (isEmpty()) {
      await TrackPlayer.pause()
      global.app_event.playerPause()
      global.app_event.pause()
      global.app_event.playerEnded()
      global.app_event.playerEnded()
      global.app_event.player Emmpty()
      global.app_event.playerEmmpty()
      return
    }
    // if (retryTrack) {
    //   if (retryTrack.musicId == retryGetUrlId) {
    //     if (++retryGetUrlNum > 1) {
    //       store.dispatch(playerAction.playNext(true))
    //       retryGetUrlId = null
    //       retryTrack = null
    //       return
    //     }
    //   } else {
    //     retryGetUrlId = retryTrack.musicId
    //     retryGetUrlNum = 0
    //   }
    //   store.dispatch(playerAction.refreshMusicUrl(global.lx.playerInfo.currentPlayMusicInfo, errorTime))
    // } else {
    //   store.dispatch(playerAction.playNext(true))
    // }
  })

  isInitialized = true

  //车机按键监听初始化
  await initCarKeyListener()
}

export default () => {
  if (global.lx.playerStatus.isRegisteredService) return
  console.log('handle registerPlaybackService...')
  TrackPlayer.registerPlaybackService(() => registerPlaybackService())
  global.lx.playerStatus.isRegisteredService = true
}