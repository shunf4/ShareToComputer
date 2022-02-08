/*
    This file is part of Share To Computer  Copyright (C) 2019  Jimmy <https://github.com/jimmod/ShareToComputer>.

    Share To Computer is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Share To Computer is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with Share To Computer.  If not, see <https://www.gnu.org/licenses/>.
*/
package com.jim.sharetocomputer

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import com.jim.sharetocomputer.ext.prioritizedIps
import com.jim.sharetocomputer.logging.KoinLogger
import com.jim.sharetocomputer.logging.MyLog
import com.jim.sharetocomputer.logging.MyUncaughtExceptionHandler
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.take
import org.koin.android.ext.koin.androidContext
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import java.net.Inet4Address
import java.net.NetworkInterface


open class Application : android.app.Application() {

    val primaryIp = MediatorLiveData<String>().apply {
        value = "unknown"
        observeForever {  }
    }

    fun refreshPrimaryIp() {
        primaryIp.value = prioritizedIps().firstOrNull() ?: "not available"
        MyLog.i("refreshed IP: ${primaryIp.value}")
    }

    override fun onCreate() {
        super.onCreate()

        Thread.setDefaultUncaughtExceptionHandler(
            MyUncaughtExceptionHandler(Thread.getDefaultUncaughtExceptionHandler())
        )

        MyLog.setupLogging(this)
        MyLog.i("Application is starting")
        MyLog.i("*QR Code version: $QR_CODE_VERSION")

        startKoin {
            KoinApplication.logger = KoinLogger()
            androidContext(this@Application)
            modules(applicationModule)
        }
    }

    companion object {
        const val EMAIL_ADDRESS = "sharetocomputer@gmail.com"
        const val QR_CODE_VERSION = 2
        const val CHANNEL_ID = "DEFAULT_CHANNEL"
    }

}
