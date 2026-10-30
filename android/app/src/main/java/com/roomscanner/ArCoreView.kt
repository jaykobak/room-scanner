
package com.roomscanner

import android.content.Context
import android.opengl.GLSurfaceView
import android.opengl.GLES20
import android.opengl.GLES11Ext
import com.google.ar.core.Session
import com.google.ar.core.Config
import com.google.ar.core.exceptions.CameraNotAvailableException
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class ArCoreView(context: Context) : GLSurfaceView(context), GLSurfaceView.Renderer {

    private var session: Session? = null

    init {
        setEGLContextClientVersion(2)
        setRenderer(this)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)

        try {
            session = Session(context).also {
                val arConfig = Config(it)
                arConfig.depthMode = Config.DepthMode.AUTOMATIC
                it.configure(arConfig)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        try {
            session?.let { arSession ->
                if (!arSession.isResumed) return

                val frame = arSession.update()
                val camera = frame.camera

                if (camera.trackingState == com.google.ar.core.TrackingState.TRACKING) {
                    // ARCore is actively tracking the camera.
                    // Depth acquisition will be added next.
                }
            }
        } catch (e: CameraNotAvailableException) {
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun resumeSession() {
        try {
            session?.resume()
            onResume()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun pauseSession() {
        session?.pause()
        onPause()
    }

    fun closeSession() {
        session?.close()
        session = null
    }
}
