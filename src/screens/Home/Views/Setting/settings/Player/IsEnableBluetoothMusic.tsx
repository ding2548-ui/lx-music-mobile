import { updateSetting } from '@/core/common'
import { useI18n } from '@/lang'
import { createStyle } from '@/utils/tools'
import { memo } from 'react'
import { View } from 'react-native'
import { useSettingValue } from '@/store/setting/hook'

import CheckBoxItem from '../../components/CheckBoxItem'

export default memo(() => {
  const t = useI18n()
  const isEnableBluetoothMusic = useSettingValue('player.isEnableBluetoothMusic')
  const setEnableBluetoothMusic = (isEnableBluetoothMusic: boolean) => {
    updateSetting({ 'player.isEnableBluetoothMusic': isEnableBluetoothMusic })
  }

  return (
    <View style={styles.content}>
      <CheckBoxItem check={isEnableBluetoothMusic} onChange={setEnableBluetoothMusic} label={t('setting_play_enable_bluetooth_music')} />
    </View>
  )
})


const styles = createStyle({
  content: {
    marginTop: 5,
  },
})
