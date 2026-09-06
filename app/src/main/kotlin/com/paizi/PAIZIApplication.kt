package com.paizi

import android.app.Application
import com.paizi.di.DIModule

class PAIZIApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DIModule.initialize(this)
    }
}
