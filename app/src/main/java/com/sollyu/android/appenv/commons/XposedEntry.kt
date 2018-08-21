/*
 * Copyright © 2017 Sollyu <https://www.sollyu.com/>
 *
 * Everyone is permitted to copy and distribute verbatim copies of this license document, but changing it is not allowed.
 *
 * This version of the GNU Lesser General Public License incorporates the terms and conditions of version 3 of the GNU General Public License, supplemented by the additional permissions listed below.
 */

package com.sollyu.android.appenv.commons

import android.content.res.Configuration
import android.content.res.Resources
import android.net.wifi.WifiInfo
import android.os.Build
import android.os.Environment
import android.telephony.TelephonyManager
import android.util.DisplayMetrics
import android.util.Log
import com.sollyu.android.appenv.BuildConfig
import com.sollyu.android.not.proguard.NotProguard
import de.robv.android.xposed.*
import de.robv.android.xposed.callbacks.XC_LoadPackage
import org.apache.commons.io.FileUtils
import org.json.JSONObject
import java.io.File
import java.util.*

/**
 * 作者：sollyu
 * 时间：2017/12/7
 * 说明：Xposed 加载类
 */
@NotProguard
class XposedEntry : IXposedHookLoadPackage {

    val TAG = "Xposed"

    /**
     * 当应用启动时
     */
    override fun handleLoadPackage(loadPackageParam: XC_LoadPackage.LoadPackageParam) {

        val ignoreApplication = arrayListOf("android", "de.robv.android.xposed.installer")
        if (ignoreApplication.contains(loadPackageParam.packageName)) {
            return
        }

        /* 设置状态 */
        if (loadPackageParam.packageName == BuildConfig.APPLICATION_ID) {
            XposedBridge.hookAllMethods(XposedHelpers.findClass("com.sollyu.android.appenv.commons.Application", loadPackageParam.classLoader), "isXposedWork", MethodHookValue(true))
            return
        }

        // 加载文件
        var xposedSettingsFile: File?
        do {
            // 检查内置存储
            xposedSettingsFile = File(Environment.getDataDirectory(), "data/" + BuildConfig.APPLICATION_ID + "/files/appenv.xposed.json")
            if (xposedSettingsFile.exists() && xposedSettingsFile.canRead())
                break

            // 检查sd卡存储
            xposedSettingsFile = File("/sdcard/Android/data/" + BuildConfig.APPLICATION_ID + "/files/appenv.xposed.json")
            if (xposedSettingsFile.exists() && xposedSettingsFile.canRead())
                break

            xposedSettingsFile = null
        } while (false)

        if (xposedSettingsFile == null) {
            Log.e(TAG, "${loadPackageParam.packageName} => not config file")
            return
        }

        /* 如果配置内容为空 */
        val xposedSettingsJsonContent = try {
            FileUtils.readFileToString(xposedSettingsFile, "UTF-8")
        } catch (e: Exception) {
            ""
        }

        if (xposedSettingsJsonContent.isEmpty()) {
            Log.d("Xposed", "${loadPackageParam.packageName} => xposedSettingsJsonContent.isEmpty")
            return
        }

        /* 其他包 */
        val xposedSettingsJson = JSONObject(xposedSettingsJsonContent)
        if (xposedSettingsJson.has(loadPackageParam.packageName)) {
            val xposedPackageJson = xposedSettingsJson.getJSONObject(loadPackageParam.packageName)
            val buildValueHashMap = HashMap<String, Any>()
            if (xposedPackageJson.has("android.os.Build.ro.product.manufacturer")) {
                val jsonValue = xposedPackageJson.getString("android.os.Build.ro.product.manufacturer")
                XposedHelpers.setStaticObjectField(Build::class.java, "MANUFACTURER", jsonValue)
                XposedHelpers.setStaticObjectField(Build::class.java, "PRODUCT"     , jsonValue)
                XposedHelpers.setStaticObjectField(Build::class.java, "BRAND"       , jsonValue)
                buildValueHashMap.put("ro.product.manufacturer", jsonValue)
                buildValueHashMap.put("ro.product.brand"       , jsonValue)
                buildValueHashMap.put("ro.product.name"        , jsonValue)
            }
            if (xposedPackageJson.has("android.os.Build.ro.product.model")) {
                val jsonValue = xposedPackageJson.getString("android.os.Build.ro.product.model")
                XposedHelpers.setStaticObjectField(Build::class.java, "MODEL" , jsonValue)
                XposedHelpers.setStaticObjectField(Build::class.java, "DEVICE", jsonValue)
                buildValueHashMap.put("ro.product.device", jsonValue)
                buildValueHashMap.put("ro.product.model" , jsonValue)
            }
            if (xposedPackageJson.has("android.os.Build.ro.serialno")) {
                XposedHelpers.setStaticObjectField(Build::class.java, "SERIAL", xposedPackageJson.getString("android.os.Build.ro.serialno"))
                buildValueHashMap.put("ro.serialno", xposedPackageJson.getString("android.os.Build.ro.serialno"))
            }
            if (xposedPackageJson.has("android.os.Build.VERSION.RELEASE")) {
                XposedHelpers.setStaticObjectField(Build.VERSION::class.java, "RELEASE", xposedPackageJson.getString("android.os.Build.VERSION.RELEASE"))
            }
            XposedBridge.hookAllMethods(XposedHelpers.findClass("android.os.SystemProperties", loadPackageParam.classLoader), "get", object : XC_MethodHook() {
                override fun afterHookedMethod(methodHookParam: MethodHookParam) {
                    if (buildValueHashMap.containsKey(methodHookParam.args[0].toString())) {
                        methodHookParam.result = buildValueHashMap[methodHookParam.args[0].toString()]
                    }
                }
            })

            if (xposedPackageJson.has("android.os.SystemProperties.android_id")) {
                XposedBridge.hookAllMethods(android.provider.Settings.System::class.java, "getString", object : XC_MethodHook() {
                    override fun afterHookedMethod(methodHookParam: MethodHookParam) {
                        if (methodHookParam.args.size > 1 && methodHookParam.args[1].toString() == "android_id") {
                            methodHookParam.result = xposedPackageJson.getString("android.os.SystemProperties.android_id")
                        }
                    }
                })
            }

            if (xposedPackageJson.has("android.telephony.TelephonyManager.getLine1Number")) {
                XposedBridge.hookAllMethods(TelephonyManager::class.java, "getLine1Number", MethodHookValue(xposedPackageJson.getString("android.telephony.TelephonyManager.getLine1Number")))
            }
            if (xposedPackageJson.has("android.telephony.TelephonyManager.getDeviceId")) {
                XposedBridge.hookAllMethods(TelephonyManager::class.java, "getDeviceId", MethodHookValue(xposedPackageJson.getString("android.telephony.TelephonyManager.getDeviceId")))
            }
            if (xposedPackageJson.has("android.telephony.TelephonyManager.getSubscriberId")) {
                XposedBridge.hookAllMethods(TelephonyManager::class.java, "getSubscriberId", MethodHookValue(xposedPackageJson.getString("android.telephony.TelephonyManager.getSubscriberId")))
            }
            if (xposedPackageJson.has("android.telephony.TelephonyManager.getSimOperator")) {
                XposedBridge.hookAllMethods(TelephonyManager::class.java, "getSimOperator", MethodHookValue(xposedPackageJson.getString("android.telephony.TelephonyManager.getSimOperator")))
            }
            if (xposedPackageJson.has("android.telephony.TelephonyManager.getSimCountryIso")) {
                XposedBridge.hookAllMethods(TelephonyManager::class.java, "getSimCountryIso", MethodHookValue(xposedPackageJson.getString("android.telephony.TelephonyManager.getSimCountryIso")))
            }
            if (xposedPackageJson.has("android.telephony.TelephonyManager.getSimOperatorName")) {
                XposedBridge.hookAllMethods(TelephonyManager::class.java, "getSimOperatorName", MethodHookValue(xposedPackageJson.getString("android.telephony.TelephonyManager.getSimOperatorName")))
            }
            if (xposedPackageJson.has("android.telephony.TelephonyManager.getSimSerialNumber")) {
                XposedBridge.hookAllMethods(TelephonyManager::class.java, "getSimSerialNumber", MethodHookValue(xposedPackageJson.getString("android.telephony.TelephonyManager.getSimSerialNumber")))
            }
            if (xposedPackageJson.has("android.telephony.TelephonyManager.getSimState")) {
                XposedBridge.hookAllMethods(TelephonyManager::class.java, "getSimState", MethodHookValue(xposedPackageJson.getInt("android.telephony.TelephonyManager.getSimState")))
            }

            if (xposedPackageJson.has("android.net.wifi.WifiInfo.getSSID")) {
                XposedBridge.hookAllMethods(WifiInfo::class.java, "getSSID", MethodHookValue(xposedPackageJson.getString("android.net.wifi.WifiInfo.getSSID")))
            }
            if (xposedPackageJson.has("android.net.wifi.WifiInfo.getBSSID")) {
                XposedBridge.hookAllMethods(WifiInfo::class.java, "getBSSID", MethodHookValue(xposedPackageJson.getString("android.net.wifi.WifiInfo.getBSSID")))
            }
            if (xposedPackageJson.has("android.net.wifi.WifiInfo.getMacAddress")) {
                XposedBridge.hookAllMethods(WifiInfo::class.java, "getMacAddress", MethodHookValue(xposedPackageJson.getString("android.net.wifi.WifiInfo.getMacAddress")))
            }

            if (xposedPackageJson.has("android.content.res.language") || xposedPackageJson.has("android.content.res.display.dpi")) {
                XposedBridge.hookAllMethods(Resources::class.java, "updateConfiguration", UpdateConfiguration(loadPackageParam, xposedPackageJson))
            }
        }
    }

    /**
     *
     */
    inner class MethodHookValue(private val value: Any) : XC_MethodHook() {
        override fun afterHookedMethod(methodHookParam: MethodHookParam) {
            methodHookParam.result = value
        }
    }

    inner class UpdateConfiguration(private val loadPackageParam: XC_LoadPackage.LoadPackageParam, private val xposedPackageJson: JSONObject) : XC_MethodHook() {
        @Suppress("DEPRECATION")
        override fun beforeHookedMethod(methodHookParam: MethodHookParam) {
            var configuration: Configuration? = null
            if (methodHookParam.args[0] != null) {
                configuration = Configuration(methodHookParam.args[0] as Configuration?)
            }
            if (configuration == null)
                return

            // 拦截语言
            if (xposedPackageJson.has("android.content.res.language")) {
                Log.d(TAG, loadPackageParam.packageName + ":language:" + xposedPackageJson.getString("android.content.res.language"))
                val localeParts = xposedPackageJson.getString("android.content.res.language").split("_")
                if (localeParts.size > 1) {
                    val language = localeParts[0]
                    val region   = if (localeParts.size >= 2) localeParts[1] else ""
                    val variant  = if (localeParts.size >= 3) localeParts[2] else ""

                    val locale = Locale(language, region, variant)
                    Locale.setDefault(locale)
                    configuration.locale = locale
                    if (Build.VERSION.SDK_INT >= 17) {
                        configuration.setLayoutDirection(locale)
                    }
                }
            }

            // 拦截DPI
            if (xposedPackageJson.has("android.content.res.display.dpi")) {
                val dpi = xposedPackageJson.getInt("android.content.res.display.dpi")
                if (dpi > 0) {
                    Log.d(TAG, loadPackageParam.packageName + ":dpi-1:" + dpi)
                    var displayMetrics: DisplayMetrics? = null
                    if (methodHookParam.args[1] != null) {
                        displayMetrics = DisplayMetrics()
                        displayMetrics.setTo(methodHookParam.args[1] as DisplayMetrics?)
                        methodHookParam.args[1] = displayMetrics;
                    }else{
                        displayMetrics = (methodHookParam.thisObject as Resources).displayMetrics
                    }
                    if (displayMetrics != null) {
                        displayMetrics.density = dpi / 160f
                        displayMetrics.densityDpi = dpi
                        if(Build.VERSION.SDK_INT >= 17) {
                            XposedHelpers.setIntField(configuration, "densityDpi", dpi)
                        }
                    }
                }
            }
            methodHookParam.args[0] = configuration;
        }
    }

}