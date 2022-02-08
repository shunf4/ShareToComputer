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

package com.jim.sharetocomputer.ui.send

import android.app.Instrumentation
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.MediaStore
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import com.google.gson.Gson
import com.google.zxing.BarcodeFormat
import com.jim.sharetocomputer.*
import com.jim.sharetocomputer.coroutines.TestableDispatchers
import com.jim.sharetocomputer.ext.*
import com.jim.sharetocomputer.ext.convertDpToPx
import com.jim.sharetocomputer.ext.getPrimaryUrl
import com.jim.sharetocomputer.ext.getServerBaseUrls
import com.jim.sharetocomputer.gateway.ActivityHelper
import com.jim.sharetocomputer.logging.MyLog
import com.jim.sharetocomputer.ui.main.MainViewModel
import com.journeyapps.barcodescanner.BarcodeEncoder
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

@AllOpen
class SendViewModel(context: Context, val activityHelper: ActivityHelper) :
    MainViewModel(context) {

    private val devicePort = WebServerService.port
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
        observeForever { updateWebServerUi() }
    }
    private val serverBaseUrls = Transformations.map(combinedIpAndPort) {
        getServerBaseUrlsFromIpPort(it.first, it.second)
    }.apply {
        observeForever {  }
    }
    private val isAbleToShareData = MediatorLiveData<Boolean>().apply {
        addSource(WebUploadService.isRunning) {
            this.value = !it
        }
    }
    private var qrCode = MutableLiveData<Drawable>()
    private var qrCodeBitmap: Bitmap? = null
    lateinit var activity: FragmentActivity

    fun selectFile() {
        MyLog.i("Select File")
        GlobalScope.launch(TestableDispatchers.Default) {
            val intent =
                Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }
            activityHelper.startActivityForResult(activity, intent)?.let { result ->
                handleSelectFileResult(result)
            }
        }
    }

    fun selectMedia() {
        MyLog.i("Select Media")
        GlobalScope.launch(TestableDispatchers.Default) {
            val intent =
                Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
                    type = "*/*"
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }
            activityHelper.startActivityForResult(activity, intent)?.let { result ->
                handleSelectFileResult(result)
            }
        }
    }

    fun isAbleToShare(): LiveData<Boolean> = isAbleToShareData

    private fun handleSelectFileResult(result: Instrumentation.ActivityResult) {
        MyLog.i("*Result: ${result.resultCode}|${result.resultData?.extras?.keySet()}")
        if (result.resultCode == AppCompatActivity.RESULT_OK) {
            result.resultData.data?.run {
                startWebService(ShareRequest.ShareRequestSingleFile(this))
            }
            result.resultData.clipData?.run {
                if (this.itemCount == 1) {
                    startWebService(ShareRequest.ShareRequestSingleFile(this.getItemAt(0).uri))
                } else {
                    val uris = mutableListOf<Uri>()
                    for (i in 0 until this.itemCount) {
                        uris.add(this.getItemAt(i).uri)
                    }
                    startWebService(ShareRequest.ShareRequestMultipleFile(uris))
                }
            }
            updateWebServerUi()
        }
    }

    private fun updateWebServerUi() {
        GlobalScope.launch(TestableDispatchers.Main) {
            qrCodeBitmap?.recycle()
            qrCodeBitmap = generateQrCode()
            qrCode.value = BitmapDrawable(context.resources, qrCodeBitmap)
        }
    }

    private fun generateQrCode(): Bitmap {
        val barcodeEncoder = BarcodeEncoder()
        val barcodeContent = primaryUrl.value
        return barcodeEncoder.encodeBitmap(
            barcodeContent,
            BarcodeFormat.QR_CODE,
            context.convertDpToPx(200F).toInt(), context.convertDpToPx(200F).toInt()
        )
    }

    fun stopShare() {
        stopWebService()
    }


    private fun stopWebService() {
        val intent = WebServerService.createIntent(context, null)
        context.stopService(intent)
    }

    fun isSharing(): MutableLiveData<Boolean> {
        return WebServerService.isRunning
    }

    fun primaryUrl() = primaryUrl
    fun serverBaseUrls() = serverBaseUrls
    fun devicePort() = devicePort
    fun qrCode() = qrCode

    fun copyPrimaryUrl() {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(
            ClipData.newPlainText("shareToComputerUrl", primaryUrl().value!!)
        )
    }
}
