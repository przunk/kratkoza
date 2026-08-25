package com.przunk.kratkoza

import android.app.Application
import com.przunk.kratkoza.di.AppContainer

/**
 * Owns the app-wide object graph.
 *
 * technical_requirements.md section 8 asks for manual dependency injection, so the graph is a plain
 * container built here and read by the composition through
 * [com.przunk.kratkoza.di.LocalAppContainer].
 */
class KratkozaApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
