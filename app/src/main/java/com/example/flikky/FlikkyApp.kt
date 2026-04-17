package com.example.flikky

import android.app.Application
import com.example.flikky.di.ServiceLocator

class FlikkyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}
