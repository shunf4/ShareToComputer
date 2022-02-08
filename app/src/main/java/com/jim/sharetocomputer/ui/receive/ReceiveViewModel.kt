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

package com.jim.sharetocomputer.ui.receive

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Context.CLIPBOARD_SERVICE
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getSystemService
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.ViewModel
import com.jim.sharetocomputer.*
import com.jim.sharetocomputer.coroutines.TestableDispatchers
import com.jim.sharetocomputer.ext.*
import com.jim.sharetocomputer.ext.getPrimaryUrl
import com.jim.sharetocomputer.ext.getServerBaseUrls
import com.jim.sharetocomputer.logging.MyLog
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

@AllOpen
class ReceiveViewModel(
    val context: Context,
    val navigation: ReceiveNavigation
) : ViewModel() {

    private val devicePort = WebUploadService.port
    private val combinedIpAndPort = MediatorLiveData<Pair<String, Int>>().apply {
        value = Pair("unknown", 8080)
        addSource((context.applicationContext as Application).primaryIp) {
            value = Pair(it, WebUploadService.port.value!!)
        }
        addSource(WebUploadService.port) {
            value = Pair((context.applicationContext as Application).primaryIp.value!!, it)
        }
    }
    private val primaryUrl = Transformations.map(combinedIpAndPort) {
        getUrl(it.first, it.second)
    }.apply {
        observeForever {  }
    }
    private val serverBaseUrls = Transformations.map(combinedIpAndPort) {
        getServerBaseUrlsFromIpPort(it.first, it.second)
    }.apply {
        observeForever {  }
    }
    private val isAbleToReceiveData = MediatorLiveData<Boolean>().apply {
        addSource(WebServerService.isRunning) {
            this.value = !it
        }
    }

    fun scanQrCode() {
        MyLog.i("Select QrCode")
        GlobalScope.launch(TestableDispatchers.Default) {
            navigation.openScanQrCode()?.let { url ->
                MyLog.i("Start download service to download from: $url")
                ContextCompat.startForegroundService(
                    context, DownloadService.createIntent(context, url)
                )
            }
        }
    }

    fun receiveFromComputer() {
        MyLog.i("Select start web")
        navigation.startWebUploadService()
    }

    fun stopWeb() {
        navigation.stopWebUploadService()
    }

    private fun showToast(@StringRes id: Int, duration: Int = Toast.LENGTH_LONG) =
        GlobalScope.launch(TestableDispatchers.Main) {
            Toast.makeText(context, id, duration).show()
        }


    fun isAbleToReceive() = isAbleToReceiveData

    fun isSharing(): MutableLiveData<Boolean> {
        return WebUploadService.isRunning
    }
    fun primaryUrl() = primaryUrl
    fun serverBaseUrls() = serverBaseUrls
    fun devicePort() = devicePort

    fun copyPrimaryUrl() {
        (context.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(
            ClipData.newPlainText("shareToComputerUrl", primaryUrl().value!!)
        )
    }

}
