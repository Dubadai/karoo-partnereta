package com.daviddokakis.partnereta

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Debug builds only: send the start alert for a person now, with their real current data, to see
 * the Karoo's own alert at the desk (the real rule needs your ride recording and theirs starting).
 *
 *   adb shell am broadcast -a com.daviddokakis.partnereta.DEBUG_ALERT -p com.daviddokakis.partnereta --ei slot 1
 */
class DebugAlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        StartAlert.debugFire.tryEmit(intent.getIntExtra("slot", 1))
    }
}
