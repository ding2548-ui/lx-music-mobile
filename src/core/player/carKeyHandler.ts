import { DeviceEventEmitter } from 'react-native'
import { play, pause, togglePlay, playNext, playPrev, stop } from '@/core/player/player'
import playerState from '@/store/player/state'

const CAR_KEY_EVENT = 'CarKeyEvent'

let isListening = false

const handleCarKeyEvent = (params: { command: string }) => {
  const command = params.command
  console.log(`[CarKeyHandler] Received command: ${command}`)

  switch (command) {
    case 'preOne':
      void playPrev()
      break
    case 'nextOne':
      void playNext()
      break
    case 'play':
      play()
      break
    case 'pause':
      void pause()
      break
    case 'playpause':
      togglePlay()
      break
    case 'stop':
      void stop()
      break
    default:
      console.log(`[CarKeyHandler] Unknown command: ${command}`)
      break
  }
}

export const initCarKeyHandler = () => {
  if (isListening) return
  isListening = true
  DeviceEventEmitter.addListener(CAR_KEY_EVENT, handleCarKeyEvent)
  console.log('[CarKeyHandler] Initialized')
}

export const removeCarKeyHandler = () => {
  if (!isListening) return
  isListening = false
  DeviceEventEmitter.removeListener(CAR_KEY_EVENT, handleCarKeyEvent)
  console.log('[CarKeyHandler] Removed')
}
