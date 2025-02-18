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
package com.jim.sharetocomputer.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.databinding.DataBindingUtil
import androidx.navigation.NavController
import androidx.navigation.Navigation
import com.jim.sharetocomputer.R
import com.jim.sharetocomputer.ShareRequest
import com.jim.sharetocomputer.databinding.ActivityMainBinding
import com.jim.sharetocomputer.logging.MyLog

class MainActivity : AppCompatActivity() {

    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MyLog.i("onCreate")
        DataBindingUtil.setContentView<ActivityMainBinding>(
            this,
            R.layout.activity_main
        )

        var request: ShareRequest? = null
        var viewTabPos: Int? = null

        if (intent.action == "com.jim.sharetocomputer.VIEW_SERVE") {
            viewTabPos = intent.getIntExtra("viewTabPos", -1)
        } else {
            request = intent.generateShareRequest()
        }

        val bundle = if (viewTabPos != null) {
            MainFragment.createViewTabBundle(viewTabPos)
        } else if (request != null) {
            MainFragment.createBundle(request)
        } else {
            Bundle()
        }

        navController = Navigation.findNavController(this, R.id.main_nav_fragment)
        navController.setGraph(R.navigation.nav_main, bundle)
    }

    override fun onDestroy() {
        MyLog.i("onDestroy")
        super.onDestroy()
    }

    private fun Intent.generateShareRequest(): ShareRequest? {
        if (action == Intent.ACTION_SEND && type?.startsWith("text") == true) {
            return ShareRequest.ShareRequestText(getStringExtra(Intent.EXTRA_TEXT) ?: "")
        } else if (action == Intent.ACTION_SEND) {
            getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { uri ->
                return ShareRequest.ShareRequestSingleFile(uri)
            }
        } else if (action == Intent.ACTION_SEND_MULTIPLE) {
            getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let { uris ->
                return ShareRequest.ShareRequestMultipleFile(uris)
            }
        } else {
            MyLog.w("Unknown action: $action|$type")
        }
        return null
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
}
