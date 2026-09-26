package io.github.silentanr

import android.app.Application
import io.github.silentanr.data.AnrRepository
import io.github.silentanr.notify.AnrNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SilentAnrApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        AnrNotifier.createChannels(this)
        appScope.launch(Dispatchers.IO) { AnrRepository.get(this@SilentAnrApp).applyRetention() }
    }
}
