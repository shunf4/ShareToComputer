/*
 *     This file is part of Share To Computer  Copyright (C) 2019  Jimmy <https://github.com/jimmod/ShareToComputer>.
 *
 *     Share To Computer is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Share To Computer is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Share To Computer.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

//TODO check Android 10 compatibility (NetworkInfo is deprecated)
@file:Suppress("DEPRECATION")

package com.jim.sharetocomputer

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.*
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.MutableLiveData
import com.jim.sharetocomputer.coroutines.TestableDispatchers
import com.jim.sharetocomputer.ext.getPrimaryUrl
import com.jim.sharetocomputer.ext.getServerBaseUrls
import com.jim.sharetocomputer.logging.MyLog
import com.jim.sharetocomputer.ui.main.MainActivity
import com.jim.sharetocomputer.webserver.WebServerReceive
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.koin.dsl.koinApplication
import java.io.IOException
import java.net.Inet4Address
import java.net.ServerSocket

class WebUploadService : Service() {

    private var webServer: WebServerReceive? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var wlanStatusReceiver: BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()
        isRunning.value = true

        val refreshIpAndRestart = {
            GlobalScope.launch(TestableDispatchers.Main) {
                (application as Application).refreshPrimaryIp()
                onStartCommand(null, 0, 0)
            }
        }

        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onLosing(network: Network, maxMsToLive: Int) {
                refreshIpAndRestart()
            }

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                refreshIpAndRestart()
            }
        }

        (getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager).registerNetworkCallback(
            NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
                .build(),
            networkCallback
        )

        this.networkCallback = networkCallback

        val wlanStatusReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Handler().postDelayed({ refreshIpAndRestart() }, 1000)
            }
        }
        registerReceiver(wlanStatusReceiver, IntentFilter().apply {
            addAction("android.net.wifi.WIFI_AP_STATE_CHANGED")
            addAction(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION)
        })
        this.wlanStatusReceiver = wlanStatusReceiver
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MyLog.i("onStartCommand")
        startForeground(NOTIFICATION_ID, createNotification())
        webServer?.stop()
        val port = findFreePort()
        WebUploadService.port.value = port
        webServer = WebServerReceive(this, port)
        webServer!!.start()
        startForeground(NOTIFICATION_ID, createNotification())

        return START_STICKY
    }

    private fun findFreePort(): Int {
        for (port in 8080..8100) {
            var socket: ServerSocket? = null
            try {
                socket = ServerSocket(port)
            } catch (ex: IOException) {
                continue
            } finally {
                try {
                    socket?.close()
                } catch (ex: IOException) {
                }
            }
            MyLog.i("free port: $port")
            return port
        }
        MyLog.e("no free port found")
        throw IOException("no free port found")
    }

    private fun createNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.channel_name)
            val descriptionText = getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(Application.CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            // Register the channel with the system
            val notificationManager = NotificationManagerCompat.from(this)
            notificationManager.createNotificationChannel(channel)
        }
        val stopIntent = ActionActivity.stopReceivingIntent(this)
        val stopPendingIntent = TaskStackBuilder.create(this).run {
            addNextIntentWithParentStack(stopIntent)
            getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        val builder = NotificationCompat.Builder(this, Application.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(
                getString(
                    R.string.notification_server_text,
                    this.getPrimaryUrl(WebServerService.port.value!!)
                )
            )
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText(getString(
                    R.string.notification_server_text,
                    this.getServerBaseUrls(WebServerService.port.value!!)
                ))
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(TaskStackBuilder.create(this).run {
                addNextIntentWithParentStack(Intent(this@WebUploadService, MainActivity::class.java).apply {
                    action = "com.jim.sharetocomputer.VIEW_SERVE"
                    putExtra("viewTabPos", 1)
                })
                getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            })
            .addAction(
                R.mipmap.ic_launcher, getString(R.string.stop_receiving),
                stopPendingIntent
            )
        return builder.build()
    }

    override fun onDestroy() {
        MyLog.i("onDestroy")
        networkCallback?.let {
            (getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager).unregisterNetworkCallback(
                it
            )
        }
        wlanStatusReceiver?.let {
            unregisterReceiver(wlanStatusReceiver)
        }
        webServer?.stop()
        isRunning.value = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    companion object {
        private const val NOTIFICATION_ID = 1946

        var isRunning = MutableLiveData<Boolean>().apply { value = false }
        var port = MutableLiveData<Int>().apply { value = 8080 }

        fun createIntent(context: Context): Intent {
            return Intent(context, WebUploadService::class.java)
        }
    }
}

