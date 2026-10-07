package com.watchsos.util

import android.util.Log
import com.watchsos.BuildConfig

object EventLog {
    fun event(event: String) { if (BuildConfig.DEBUG) Log.i("WatchSOS", event) }
}
