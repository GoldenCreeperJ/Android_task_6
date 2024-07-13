package com.example.sourcecode

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.widget.ImageView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

@RequiresApi(Build.VERSION_CODES.O)
object Glide:LifecycleEventObserver {

    class RequestManager(private val activity: Activity) {
        private val requestBuilderSet = mutableSetOf<RequestBuilder>()

        fun load(bitmap: Bitmap): RequestBuilder =
            RequestBuilder(bitmap, activity)

        fun load(drawable: Drawable): RequestBuilder =
            RequestBuilder(drawable, activity)

        fun load(uri: Uri): RequestBuilder =
            RequestBuilder(uri, activity)

        fun load(file: File): RequestBuilder =
            RequestBuilder(file, activity)

        fun load(resourceId: Int): RequestBuilder =
            RequestBuilder(resourceId, activity)

        fun load(url: String): RequestBuilder {
            val requestBuilder = RequestBuilder(url, activity)
            requestBuilderSet.add(requestBuilder)
            return requestBuilder
        }

        fun stop() {
            for (i in requestBuilderSet) {
                i.stop()
            }
        }

        fun restart() {
            for (i in requestBuilderSet) {
                i.restart()
            }
        }
    }

    class RequestBuilder(private val resource: Any, private val activity: Activity) {
        private lateinit var imageView: ImageView
        private var isVisible = true

        fun stop() {
            isVisible = false
        }

        fun restart() {
            isVisible = true
            downloadOnline(File(activity.externalCacheDir, resource.toString().replace('/', '*')))
        }

        fun into(imgView: ImageView) {
            imageView = imgView
            when (resource) {
                is Bitmap -> imageView.setImageBitmap(resource)
                is Drawable -> imageView.setImageDrawable(resource)
                is Uri -> imageView.setImageURI(resource)
                is Int -> imageView.setImageResource(resource)
                is File -> imageView.setImageURI(Uri.fromFile(resource))
                is String -> {
                    val imgFile =
                        File(activity.externalCacheDir, resource.replace('/', '*'))
                    if (imgFile.exists()) {
                        imageView.setImageURI(Uri.fromFile(imgFile))
                    } else {
                        downloadOnline(imgFile)
                    }
                }

                else -> Toast.makeText(activity.applicationContext, "错误图源", Toast.LENGTH_SHORT)
                    .show()
            }
        }

        private fun downloadOnline(imgFile: File) {
            if (internetCheck()) {
                CoroutineScope(Dispatchers.IO).launch {
                    val connection =
                        withContext(Dispatchers.IO) {
                            URL(resource as String).openConnection()
                        } as HttpURLConnection
                    connection.apply {
                        requestMethod = "GET"
                        connectTimeout = 80000
                        readTimeout = 80000
                    }
                    if (isVisible) {
                        withContext(Dispatchers.IO) {
                            imgFile.createNewFile()
                        }
                        connection.inputStream.use { inputStream ->
                            val imgCode = BitmapFactory.decodeStream(inputStream)
                            if (imgCode != null) {
                                FileOutputStream(imgFile).use { fileOutputStream ->
                                    imgCode.compress(
                                        Bitmap.CompressFormat.JPEG,
                                        100,
                                        fileOutputStream
                                    )
                                }
                            }
                        }
                        connection.disconnect()
                        launch(Dispatchers.Main) {
                            imageView.setImageURI(Uri.fromFile(imgFile))
                        }
                    }
                }
            }
        }

        private fun internetCheckCore(block: () -> Boolean): Boolean {
            return if (ContextCompat.checkSelfPermission(
                    activity.applicationContext,
                    Manifest.permission.INTERNET
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                block()
            } else {
                val connectivityManager =
                    activity.getSystemService(AppCompatActivity.CONNECTIVITY_SERVICE) as ConnectivityManager
                if (connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)!!
                        .hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                ) {
                    true
                } else {
                    Toast.makeText(activity.applicationContext, "网络连接失败", Toast.LENGTH_SHORT)
                        .show()
                    false
                }
            }
        }

        private fun internetCheck(): Boolean {
            return internetCheckCore {
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(Manifest.permission.INTERNET),
                    1
                )
                internetCheckCore {
                    Toast.makeText(activity.applicationContext, "网络连接失败", Toast.LENGTH_SHORT)
                        .show()
                    return@internetCheckCore false
                }
            }
        }
    }

    private val requestManagerMap = mutableMapOf<LifecycleOwner, RequestManager>()

    fun with(lifecycleOwner: LifecycleOwner): RequestManager {
        if (!requestManagerMap.contains(lifecycleOwner)) {
            requestManagerMap[lifecycleOwner] = RequestManager(lifecycleOwner as Activity)
            lifecycleOwner.lifecycle.addObserver(this)
        }
        return requestManagerMap[lifecycleOwner]!!
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_STOP -> requestManagerMap[source]!!.stop()
            Lifecycle.Event.ON_START -> requestManagerMap[source]!!.restart()
            else -> {}
        }
    }
}