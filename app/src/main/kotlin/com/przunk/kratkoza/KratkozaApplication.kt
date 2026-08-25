package com.przunk.kratkoza

import android.app.Application
import com.przunk.kratkoza.di.AppContainer

/**
 * Owns the app-wide object graph.
 *
 * technical_requirements.md section 8 asks for manual dependency injection, so the graph is a plain
 * container built here and handed to the navigation graph, which builds each screen's ViewModel
 * from it. Passing it down explicitly rather than through a CompositionLocal keeps every screen's
 * dependencies visible at the call site.
 */
class KratkozaApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
