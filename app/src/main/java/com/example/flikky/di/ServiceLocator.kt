package com.example.flikky.di

import android.content.Context

object ServiceLocator {
    private lateinit var appContext: Context

    fun init(app: Context) {
        appContext = app.applicationContext
    }

    fun context(): Context = appContext
}
