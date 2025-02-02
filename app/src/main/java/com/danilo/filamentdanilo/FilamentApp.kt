package com.danilo.filamentdanilo

import android.app.Application
import com.danilo.filamentdanilo.di.appModule
import org.koin.core.context.startKoin

class FilamentApp : Application() {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            modules(appModule)
        }
    }
}
