package ua.lujek.monetizekit.sample

import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Process
import android.os.SystemClock

internal object DebugPrivacyProfileRestart {
    fun restart(activity: Activity) {
        val restartIntent = Intent(activity, DebugPrivacyProfileRestartReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            activity,
            7,
            restartIntent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        activity.getSystemService(AlarmManager::class.java).set(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + restartDelayMillis,
            pendingIntent,
        )
        activity.finishAndRemoveTask()
        Process.killProcess(Process.myPid())
    }

    private const val restartDelayMillis = 300L
}

class DebugPrivacyProfileRestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
            ?: return
        context.startActivity(launchIntent)
    }
}
