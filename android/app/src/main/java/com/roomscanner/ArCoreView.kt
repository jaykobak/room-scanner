
package com.roomscanner

import android.content.Context
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.bridge.LifecycleEventListener
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class ArCoreView(context: Context) :
    GLSurfaceView(context),
    GLSurfaceView.Renderer,
    LifecycleEventListener {

    @Volatile
    private var session: Session? = null

    @Volatile
    private var sessionReady = false

    @Volatile
    private var hostResumed = false

    private var cameraTextureId = 0

    init {
        setEGLContextClientVersion(2)
        setRenderer(this)
        renderMode = RENDERMODE_CONTINUOUSLY

        (context as? ThemedReactContext)
            ?.addLifecycleEventListener(this)
    }

    private fun resumeArSession() {
        if (!hostResumed || !sessionReady) return

        try {
            session?.resume()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)

        val texture = IntArray(1)
        GLES20.glGenTextures(1, texture, 0)
        cameraTextureId = texture[0]

        GLES20.glBindTexture(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            cameraTextureId
        )

        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_LINEAR
        )

        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MAG_FILTER,
            GLES20.GL_LINEAR
        )

        try {
            val newSession = Session(context)

            val arConfig = Config(newSession)
            arConfig.depthMode = Config.DepthMode.AUTOMATIC
            newSession.configure(arConfig)

            newSession.setCameraTextureNames(
                intArrayOf(cameraTextureId)
            )

            session = newSession
            sessionReady = true
        } catch (e: Exception) {
            sessionReady = false
            e.printStackTrace()
        }

        resumeArSession()
    }

    override fun onSurfaceChanged(
        gl: GL10?,
        width: Int,
        height: Int
    ) {
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        if (!sessionReady) return

        try {
            val frame = session?.update() ?: return

            if (frame.camera.trackingState == TrackingState.TRACKING) {
                // Camera tracking is active.
                // We will add camera rendering and depth extraction next.
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onHostResume() {
        hostResumed = true
        onResume()
        resumeArSession()
    }

    override fun onHostPause() {
        hostResumed = false

        try {
            session?.pause()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        onPause()
    }

    override fun onHostDestroy() {
        session?.close()
        session = null
        sessionReady = false
    }

    fun closeSession() {
        session?.close()
        session = null
        sessionReady = false

        (context as? ThemedReactContext)
            ?.removeLifecycleEventListener(this)
    }
}
