package com.ngg.instruments

import android.app.Application

class InstrumentsApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
