package com.roomscanner

import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Session

class ArCoreModule(
    reactContext: ReactApplicationContext
) : ReactContextBaseJavaModule(reactContext) {

    override fun getName() = "ArCoreModule"

    @ReactMethod
    fun checkAvailability(promise: Promise) {
        try {
            val availability = ArCoreApk.getInstance()
                .checkAvailability(reactApplicationContext)

            promise.resolve(availability.name)
        } catch (e: Exception) {
            promise.reject("ARCORE_CHECK_FAILED", e)
        }
    }

    @ReactMethod
    fun checkDepthSupport(promise: Promise) {
        var session: Session? = null

        try {
            session = Session(reactApplicationContext)

            val supported = session.isDepthModeSupported(
                Config.DepthMode.AUTOMATIC
            )

            promise.resolve(supported)
        } catch (e: Exception) {
            promise.reject("DEPTH_CHECK_FAILED", e)
        } finally {
            session?.close()
        }
    }
}