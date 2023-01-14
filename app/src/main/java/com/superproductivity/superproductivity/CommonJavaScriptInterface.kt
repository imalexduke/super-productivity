package com.superproductivity.superproductivity

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.preference.PreferenceManager
import android.provider.Settings
import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.anggrayudi.storage.SimpleStorageHelper
import com.anggrayudi.storage.file.*
import com.superproductivity.superproductivity.FullscreenActivity.Companion.WINDOW_INTERFACE_PROPERTY
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import java.io.InputStream
import java.io.Writer
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.cert.CertPathValidatorException
import java.util.Locale
import java.util.concurrent.ThreadLocalRandom
import javax.net.ssl.SSLHandshakeException

abstract class CommonJavaScriptInterface(
    private val activity: FullscreenActivity,
) {

    /**
     * Instantiate the interface and set the context
     */
    open fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        // Additional callback for scoped storage permission management on Android 10+
        // Mandatory for Activity, but not for Fragment & ComponentActivity
        activity.storageHelper.storage.onActivityResult(requestCode, resultCode, data)
        callJavaScriptFunction(
            FN_PREFIX + "grantFilePermissionCallBack('" + requestCode + "')"
        )
    }

    @Suppress("unused")
    @JavascriptInterface
    fun showToast(toast: String) {
        Toast.makeText(activity, toast, Toast.LENGTH_SHORT).show()
    }

    @Suppress("unused")
    @JavascriptInterface
    fun updateTaskData(str: String) {
        Log.w("TW", "JavascriptInterface: updateTaskData")
        val intent = Intent(activity.applicationContext, TaskListWidget::class.java)
        intent.action = TaskListWidget.LIST_CHANGED
        intent.putExtra("taskJson", str)
        (activity.application as App).dataHolder.data = str
        activity.sendBroadcast(intent)
    }

    @Suppress("unused")
    @JavascriptInterface
    fun updatePermanentNotification(title: String, message: String, progress: Int) {
        Log.w("TW", "JavascriptInterface: updateNotificationWidget")
        // we need to use an explicit intent to make this work
        val intent = Intent(KeepAliveNotificationService.UPDATE_PERMANENT_NOTIFICATION)
        intent.putExtra("title", title)
        intent.putExtra("message", message)
        intent.putExtra("progress", progress)
        activity.sendBroadcast(intent)
    }

    @Suppress("unused")
    @JavascriptInterface
    open fun triggerGetGoogleToken() {
        // NOTE: empty here, and only filled for google build flavor
    }

    @Suppress("unused")
    @JavascriptInterface
    // LEGACY
    fun saveToDbNew(requestId: String, key: String, value: String) {
        (activity.application as App).keyValStore.set(key, value)
        callJavaScriptFunction(FN_PREFIX + "saveToDbCallback('" + requestId + "')")
    }

    @Suppress("unused")
    @JavascriptInterface
    // LEGACY
    fun loadFromDbNew(requestId: String, key: String) {
        val r = (activity.application as App).keyValStore.get(key, "")
        // NOTE: ' are important as otherwise the json messes up
        callJavaScriptFunction(FN_PREFIX + "loadFromDbCallback('" + requestId + "', '" + key + "', '" + r + "')")
    }

    @Suppress("unused")
    @JavascriptInterface
    fun removeFromDb(requestId: String, key: String) {
        (activity.application as App).keyValStore.set(key, null)
        callJavaScriptFunction(FN_PREFIX + "removeFromDbCallback('" + requestId + "')")
    }

    @Suppress("unused")
    @JavascriptInterface
    fun clearDb(requestId: String) {
        (activity.application as App).keyValStore.clearAll(activity)
        callJavaScriptFunction(FN_PREFIX + "clearDbCallback('" + requestId + "')")
    }


    // TODO: legacy remove in future version, but no the next release
    @Suppress("unused")
    @JavascriptInterface
    fun saveToDb(key: String, value: String) {
        (activity.application as App).keyValStore.set(key, value)
        callJavaScriptFunction("window.saveToDbCallback()")
    }

    // TODO: legacy remove in future version, but no the next release
    @Suppress("unused")
    @JavascriptInterface
    fun loadFromDb(key: String) {
        val r = (activity.application as App).keyValStore.get(key, "")
        // NOTE: ' are important as otherwise the json messes up
        callJavaScriptFunction("window.loadFromDbCallback('$key', '$r')")
    }


    @Suppress("unused")
    @JavascriptInterface
    fun showNotificationIfAppIsNotOpen(title: String, body: String) {
        if (!activity.isInForeground) {
            showNotification(title, body)
        }
    }

    @Suppress("unused")
    @JavascriptInterface
    fun showNotification(title: String, body: String) {
        Log.d("TW", "title $title")
        Log.d("TW", "body $body")

        val mBuilder: NotificationCompat.Builder = NotificationCompat.Builder(
            activity.applicationContext, "SUP_CHANNEL_ID"
        )
        mBuilder.build().flags = mBuilder.build().flags or Notification.FLAG_AUTO_CANCEL

        val ii = Intent(activity.applicationContext, FullscreenActivity::class.java)
        val pendingIntentFlags: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            0
        }
        val pendingIntent = PendingIntent.getActivity(activity, 0, ii, pendingIntentFlags)
        val bigText: NotificationCompat.BigTextStyle = NotificationCompat.BigTextStyle()
        bigText.setBigContentTitle(title)
        if (body.isNotEmpty() && body.trim() != "undefined") {
            bigText.bigText(body)
        }

        mBuilder.setContentIntent(pendingIntent)
        mBuilder.setSmallIcon(R.mipmap.ic_launcher)
        mBuilder.setLargeIcon(
            BitmapFactory.decodeResource(
                activity.resources, R.mipmap.ic_launcher
            )
        )
        mBuilder.setSmallIcon(R.drawable.ic_stat_sp)
        mBuilder.priority = Notification.PRIORITY_MAX
        mBuilder.setStyle(bigText)
        mBuilder.setAutoCancel(true)

        val mNotificationManager =
            activity.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "SUP_CHANNEL_ID"
            val channel = NotificationChannel(
                channelId, "Super Productivity", NotificationManager.IMPORTANCE_HIGH
            )
            mNotificationManager.createNotificationChannel(channel)
            mBuilder.setChannelId(channelId)
        }
        mNotificationManager.notify(0, mBuilder.build())
    }

    private fun readFully(inputStream: InputStream): ByteArray {
        val buffer = ByteArrayOutputStream()
        var nRead: Int
        val data = ByteArray(16384)
        nRead = inputStream.read(data, 0, data.size)
        while (nRead != -1) {
            buffer.write(data, 0, nRead)
            nRead = inputStream.read(data, 0, data.size)
        }
        return buffer.toByteArray()
    }

    @Suppress("unused")
    @JavascriptInterface
    fun makeHttpRequest(
        requestId: String,
        urlString: String,
        method: String,
        data: String,
        username: String,
        password: String,
        readResponse: String
    ) {
        Log.d("TW", "$requestId $urlString $method $data $username $readResponse")
        var status: Int
        var statusText: String
        var resultData = ""
        val headers = JSONObject()
        val doInput = readResponse.toBoolean()
        try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            if (username.isNotEmpty() && password.isNotEmpty()) {
                val auth = "$username:$password"
                val encodedAuth = Base64.encodeToString(auth.toByteArray(), Base64.NO_WRAP)
                connection.setRequestProperty(/* key = */ "Authorization", /* value = */
                    "Basic $encodedAuth"
                )
            }
            connection.requestMethod = method
            connection.setRequestProperty("Content-Type", "application/octet-stream")
            connection.doInput = doInput
            if (data.isNotEmpty()) {
                connection.doOutput = true
                val bytes = data.toByteArray()
                connection.setFixedLengthStreamingMode(bytes.size)
                val out = BufferedOutputStream(connection.outputStream)
                out.write(bytes)
                out.flush()
                out.close()
            }
            connection.headerFields.entries.forEach { entry ->
                val output = buildString {
                    entry.value.forEach {
                        append(",$it")
                    }
                }
                // https://datatracker.ietf.org/doc/html/rfc7230#section-3.2.2
                try {
                    headers.put(entry.key.lowercase(Locale.ROOT), output)
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }
            status = connection.responseCode
            statusText = connection.responseMessage
            val out: ByteArray
            if (status in 200..299 && doInput) {
                val inputStream = connection.inputStream
                out = readFully(inputStream)
                inputStream.close()
            } else {
                out = ByteArray(0)
            }
            connection.disconnect()
            resultData = String(out, StandardCharsets.UTF_8)
        } catch (e: MalformedURLException) {
            e.printStackTrace()
            status = -1
            statusText = "Malformed URL"
        } catch (e: SSLHandshakeException) {
            e.printStackTrace()
            var cause = e.cause
            while (cause != null && cause !is CertPathValidatorException) {
                cause = cause.cause
            }
            if (cause != null) {
                val validationException = cause as CertPathValidatorException
                val message = StringBuilder("Failed trust path:")
                validationException.certPath.certificates.forEach { certificate ->
                    message.append("\n")
                    message.append(certificate.toString())
                }
                Log.e("TW", message.toString())
            }
            status = -2
            statusText = "SSL Handshake Error"
        } catch (e: IOException) {
            e.printStackTrace()
            status = -3
            statusText = "Network Error"
        } catch (e: ClassCastException) {
            e.printStackTrace()
            status = -4
            statusText = "Unsupported Protocol"
        }
        val result = JSONObject()
        try {
            result.put("data", resultData)
            result.put("status", status)
            result.put("headers", headers)
            result.put("statusText", statusText)
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        Log.d("TW", "$requestId: $result")
        callJavaScriptFunction(FN_PREFIX + "makeHttpRequestCallback('" + requestId + "', " + result + ")")
    }

    @Suppress("unused")
    @JavascriptInterface
    fun getFileRev(filePath: String): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Scoped storage permission management for Android 10+
            val file = DocumentFileCompat.fromFullPath(activity, filePath, requiresWriteAccess = false)
            file?.lastModified().toString()
        } else {
            val file = File(filePath)
            file.lastModified().toString()
        }
    }

    @Suppress("unused")
    @JavascriptInterface
    fun readFile(filePath: String): String {
        val reader =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Scoped storage permission management for Android 10+
                // Get folder path
                val sp = activity.getPreferences(Context.MODE_PRIVATE)
                val folderPath = sp.getString("filesyncFolder", "") ?: ""
                // Build fullFilePath from folder path and filepath
                val fullFilePath = "$folderPath/filePath"
                Log.d("SuperProductivity", "readFile: trying to save to fullFilePath: " + fullFilePath)
                val file = DocumentFileCompat.fromFullPath(activity, fullFilePath, requiresWriteAccess=false)
                file?.openInputStream(activity)?.reader()
            } else {
                BufferedReader(FileReader(filePath))
            }
        val sb = StringBuilder()

        if (reader == null) {
            Log.d("SuperProductivity", "readFile warning: tried to open file, but file does not exist or we do not have permission! This may be normal if file does not exist yet, it will be created when some tasks will be added.")
        } else {
            try {
                val lines: List<String> = reader.readLines()
                val ls = System.getProperty("line.separator")
                for (line in lines) {
                    sb.append(line)
                    sb.append(ls)
                }
                sb.deleteCharAt(sb.length - 1)
            } catch (e: Exception) {
                Log.d("SuperProductivity", "readFile error: " + e.stackTraceToString())
            } finally {
                reader.close()
            }
        }
        return sb.toString()
    }

    @Suppress("unused")
    @JavascriptInterface
    fun writeFile(filePath: String, data: String) {
        val writer: Writer =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Scoped storage permission management for Android 10+
                Log.d("SuperProductivity", "writeFile: trying to save to filePath: " + filePath)
                // Get folder path
                val sp = activity.getPreferences(Context.MODE_PRIVATE)
                val folderPath = sp.getString("filesyncFolder", "") ?: ""
                // Build fullFilePath from folder path and filepath
                val fullFilePath = "$folderPath/filePath"
                Log.d("SuperProductivity", "writeFile: trying to save to fullFilePath: " + fullFilePath)
                // Open file with write access, using SimpleStorage helper wrapper DocumentFileCompat
                var file = DocumentFileCompat.fromFullPath(activity, fullFilePath, requiresWriteAccess=true, considerRawFile=true)
                if ((file == null) || (!file.exists())) {  // if file does not exist, we create it
                    Log.d("SuperProductivity", "writeFile: file does not exist, try to create it")
                    val folder = DocumentFileCompat.fromFullPath(activity, folderPath, requiresWriteAccess=true)
                    Log.d("SuperProductivity", "writeFile: do we have access to parentFolder? " + folder.toString())
                    file = folder!!.makeFile(activity, filePath, mode=CreateMode.REPLACE) // do NOT specify a mimeType, otherwise Android will force a file extension
                }
                Log.d("SuperProductivity", "writeFile: erase file content by recreating it")
                file = file?.recreateFile(activity)  // erase content first by recreating file. For some reason, DocumentFileCompat.fromFullPath(requiresWriteAccess=true) and openOutputStream(append=false) only open the file in append mode, so we need to recreate the file to truncate its content first
                Log.d("SuperProductivity", "writeFile: try to openOutputStream")
                file?.openOutputStream(activity, append=false)!!.writer()
            } else {
                BufferedWriter(FileWriter(filePath))
            }
        try {
            Log.d("SuperProductivity", "writeFile: try to write data into file: " + data)
            writer.write(data)
            Log.d("SuperProductivity", "writeFile: write apparently successful!")
        } catch (e: Exception) {
            Log.d("SuperProductivity", "writeFile error: " + e.stackTraceToString())
        } finally {
            writer.close()
        }
    }

    @Suppress("unused")
    @JavascriptInterface
    fun isGrantedFilePermission(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val grantedPaths = DocumentFileCompat.getAccessibleAbsolutePaths(activity)
        Log.d("SuperProductivity", "isGrantedFilePermission grantedPaths: " + grantedPaths.toString())
        grantedPaths.isNotEmpty()
    } else {
        val result = ContextCompat.checkSelfPermission(
            activity, Manifest.permission.READ_EXTERNAL_STORAGE
        )
        val result1 = ContextCompat.checkSelfPermission(
            activity, Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        result == PackageManager.PERMISSION_GRANTED && result1 == PackageManager.PERMISSION_GRANTED
    }

    @Suppress("unused")
    @JavascriptInterface
    fun grantFilePermission(requestId: String) {
        // For Android >= 10, use scoped storage via SimpleStorage library to get the permission to write files in a folder
        // Note that SimpleStorage takes care of all the gritty technical details, including whether the user must pick a root path BEFORE selecting the folder they want to store in, everything is explained to the user
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Log.d("SuperProductivity", "Before SimpleStorageHelper callback func def")
            // Register a callback with SimpleStorage when a folder is picked
            activity.storageHelper.onFolderSelected =
                { _, root -> // could also use simpleStorageHelper.onStorageAccessGranted()
                    Log.d("SuperProductivity", "Success Folder Pick! Now saving...")
                    // Get absolute path to folder
                    val fpath = root.getAbsolutePath(activity)
                    // Open preferences to save folder to path
                    val sp = activity.getPreferences(Context.MODE_PRIVATE)
                    sp.edit().putString("filesyncFolder", fpath).apply()
                }
            // Open folder picker via SimpleStorage, this will request the necessary scoped storage permission
            // Note that even though we get permissions, we need to only write DocumentFile files, not MediaStore files, because the latter are not meant to be reopened in the future so we can lose permission at anytime once they are written once, see: https://github.com/anggrayudi/SimpleStorage/issues/103
            Log.d("SuperProductivity", "Get Storage Access permission")
            activity.storageHelper.openFolderPicker(
                // We could also use simpleStorageHelper.requestStorageAccess()
                initialPath = FileFullPath(
                    activity,
                    StorageId.PRIMARY,
                    "SupProd"
                ), // SimpleStorage.externalStoragePath
                //expectedStorageType = StorageType.EXTERNAL,
                //expectedBasePath = "SupProd"
            )
        } else {
            ActivityCompat.requestPermissions(
                activity, arrayOf(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ), 1
            )
        }
    }

    protected fun callJavaScriptFunction(script: String) {
        activity.callJavaScriptFunction(script)
    }

    companion object {
        // TODO rename to WINDOW_PROPERTY
        const val FN_PREFIX: String = "window.$WINDOW_INTERFACE_PROPERTY."
    }
}
