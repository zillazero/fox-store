package com.looker.droidify

import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import androidx.appcompat.app.AppCompatDelegate
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.asImage
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.intercept.Interceptor
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.ImageResult
import coil3.request.SuccessResult
import coil3.request.crossfade
import com.looker.droidify.content.ProductPreferences
import com.looker.droidify.database.Database
import com.looker.droidify.datastore.SettingsRepository
import com.looker.droidify.datastore.get
import com.looker.droidify.index.RepositoryUpdater
import com.looker.droidify.installer.InstallManager
import com.looker.droidify.network.Downloader
import com.looker.droidify.receivers.InstalledAppReceiver
import com.looker.droidify.service.SyncService
import com.looker.droidify.utility.common.cache.Cache
import com.looker.droidify.utility.common.extension.getDrawableCompat
import com.looker.droidify.utility.common.extension.getInstalledPackagesCompat
import com.looker.droidify.utility.extension.toInstalledItem
import com.looker.droidify.work.CleanUpWorker
import com.looker.droidify.work.AutoUpdateWorker
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.INFINITE

@HiltAndroidApp
class Droidify : Application(), SingletonImageLoader.Factory, Configuration.Provider {

    private val parentJob = SupervisorJob()
    private val appScope = CoroutineScope(Dispatchers.Default + parentJob)

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var installer: InstallManager

    @Inject
    lateinit var downloader: Downloader

    @Inject
    lateinit var httpClient: OkHttpClient

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        com.looker.droidify.fox.FoxCrash.install(this)

        val databaseUpdated = Database.init(this)
        ProductPreferences.init(this, appScope)
        RepositoryUpdater.init(appScope, downloader)
        listenApplications()
        checkLanguage()
        updatePreference()
        com.looker.droidify.filmverleih.PrivateRepo.init(this)
        com.looker.droidify.filmverleih.ThemeSync.start(this, appScope, settingsRepository)
        appScope.launch { installer() }
        if (databaseUpdated) {
            appScope.launch {
                SyncService.forceSyncAll(applicationContext)
            }
        }
    }

    private fun listenApplications() {
        val installedItems = packageManager
            .getInstalledPackagesCompat()
            ?.map { it.toInstalledItem() }
        if (installedItems != null) {
            Database.InstalledAdapter.putAll(installedItems)
        }
        appScope.launch {
            registerReceiver(
                InstalledAppReceiver(packageManager),
                IntentFilter().apply {
                    addAction(Intent.ACTION_PACKAGE_ADDED)
                    addAction(Intent.ACTION_PACKAGE_REMOVED)
                    addDataScheme("package")
                },
            )
        }
    }

    private fun checkLanguage() {
        appScope.launch {
            val lastSetLanguage = settingsRepository.getInitial().language
            val systemSetLanguage = AppCompatDelegate.getApplicationLocales().toLanguageTags()
            if (systemSetLanguage != lastSetLanguage && lastSetLanguage != "system") {
                settingsRepository.setLanguage(systemSetLanguage)
            }
        }
    }

    private fun updatePreference() {
        appScope.launch {
            launch {
                settingsRepository.get { unstableUpdate }.drop(1).collect {
                    SyncService.forceSyncAll(applicationContext)
                }
            }
            launch {
                settingsRepository.get { autoSync }.collectIndexed { index, syncMode ->
                    // Don't update sync job on initial collect
                    SyncService.Job.schedule(applicationContext, index > 0, syncMode)
                }
            }
            launch {
                // FOX Store: Auto-Update-Worker (auch für Store-Selbst-Update)
                settingsRepository.data
                    .map { listOf(it.autoUpdate || it.selfUpdate, it.autoUpdateIntervalHours, it.autoUpdateWifiOnly, it.autoUpdateCharging, it.autoUpdateNightOnly) }
                    .distinctUntilChanged()
                    .collect { (enabled, hours, wifi, charging, night) ->
                        AutoUpdateWorker.schedule(
                            applicationContext, enabled as Boolean, hours as Int, wifi as Boolean, charging as Boolean,
                            night as Boolean,
                        )
                    }
            }
            launch {
                settingsRepository.get { cleanUpInterval }.collect {
                    if (it == INFINITE) {
                        CleanUpWorker.removeAllSchedules(applicationContext)
                    } else {
                        CleanUpWorker.scheduleCleanup(applicationContext, it)
                    }
                }
            }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    private fun isLowMemoryDevice(): Boolean {
        val am = getSystemService(android.app.ActivityManager::class.java) ?: return false
        val info = android.app.ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        return am.isLowRamDevice || info.totalMem < 3L * 1024 * 1024 * 1024 || am.memoryClass <= 192
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Bei Speicherdruck Bild-Cache freigeben (wichtig auf 2-GB-TV-Sticks)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            runCatching { coil3.SingletonImageLoader.get(this).memoryCache?.clear() }
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val memoryCache = MemoryCache.Builder()
            // FOX Store: auf Low-RAM-Sticks (z. B. waipu.tv 4K Stick, ~2 GB) kleinerer Bild-Cache
            .maxSizePercent(context, if (isLowMemoryDevice()) 0.10 else 0.25)
            .build()

        val diskCache = DiskCache.Builder()
            .directory(Cache.getImagesDir(this))
            // FOX Store 1.4.0: feste Obergrenze je Geräteprofil (Stick 48 MB, Shield 192 MB)
            .maxSizeBytes(if (isLowMemoryDevice()) 48L * 1024 * 1024 else 192L * 1024 * 1024)
            .build()

        return ImageLoader.Builder(this)
            .memoryCache(memoryCache)
            .diskCache(diskCache)
            .error(getDrawableCompat(R.drawable.ic_cannot_load).asImage())
            .crossfade(
                when (com.looker.droidify.fox.FoxDevice.motion(this)) {
                    com.looker.droidify.fox.FoxDevice.Motion.AUS -> 0
                    com.looker.droidify.fox.FoxDevice.Motion.REDUZIERT -> 120
                    com.looker.droidify.fox.FoxDevice.Motion.NORMAL -> 250
                },
            )
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { httpClient }))
                add(FallbackIconInterceptor())
            }
            .build()
    }
}

private class FallbackIconInterceptor : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val request = chain.request
        val result = chain.proceed()

        if (result is SuccessResult) return result

        val fallbackIconUrl = request.newBuilder()
            .data((request.data as String).replaceAfterLast('/', "icon.png"))
            .build()
        return chain.withRequest(fallbackIconUrl).proceed()
    }
}
