
package com.roomscanner

import android.content.Context
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.view.Surface
import android.view.WindowManager
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.LifecycleEventListener
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.events.RCTEventEmitter
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.NotYetAvailableException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class ArCoreView(context: Context) :
    GLSurfaceView(context),
    GLSurfaceView.Renderer,
    LifecycleEventListener {

    private val reactContext = context as ThemedReactContext

    @Volatile
    private var session: Session? = null

    @Volatile
    private var hostResumed = false

    @Volatile
    private var sessionReady = false

    @Volatile
    private var textureReady = false

    private var cameraTextureId = 0
    private var shaderProgram = 0

    private var lastReportedDepth = ""

    private val positions = floatArrayOf(
        -1f, -1f,
         1f, -1f,
        -1f,  1f,
         1f,  1f
    )

    private val inputUvs = floatArrayOf(
        0f, 1f,
        1f, 1f,
        0f, 0f,
        1f, 0f
    )

    private fun createBuffer(values: FloatArray): FloatBuffer {
        return ByteBuffer.allocateDirect(values.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(values)
                position(0)
            }
    }

    private val positionBuffer = createBuffer(positions)
    private val inputUvBuffer = createBuffer(inputUvs)
    private val transformedUvBuffer = createBuffer(inputUvs)

    init {
        setEGLContextClientVersion(2)
        setRenderer(this)
        renderMode = RENDERMODE_CONTINUOUSLY

        reactContext.addLifecycleEventListener(this)
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

        shaderProgram = createProgram()

        try {
            if (session == null) {
                val newSession = Session(context)
                val arConfig = Config(newSession)

                arConfig.depthMode = Config.DepthMode.AUTOMATIC

                newSession.configure(arConfig)
                session = newSession
            }

            session?.setCameraTextureNames(intArrayOf(cameraTextureId))

            sessionReady = true
            textureReady = true

            post {
                resumeArSession()
            }

        } catch (e: Exception) {
            sessionReady = false
            reportDepthStatus("ARCore error: ${e.message}")
            e.printStackTrace()
        }
    }

    override fun onSurfaceChanged(
        gl: GL10?,
        width: Int,
        height: Int
    ) {
        GLES20.glViewport(0, 0, width, height)

        @Suppress("DEPRECATION")
        val display = (context.getSystemService(
            Context.WINDOW_SERVICE
        ) as WindowManager).defaultDisplay

        session?.setDisplayGeometry(
            display.rotation,
            width,
            height
        )
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        if (!sessionReady) return

        try {
            val frame = session?.update() ?: return

            drawCamera(frame)

            if (frame.camera.trackingState == TrackingState.TRACKING) {
                acquireDepth(frame)
            } else {
                reportDepthStatus("Camera tracking...")
            }

        } catch (e: Exception) {
            reportDepthStatus("Scanner error: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun drawCamera(frame: Frame) {
        if (shaderProgram == 0) return

        frame.transformDisplayUvCoords(
            inputUvBuffer,
            transformedUvBuffer
        )

        GLES20.glUseProgram(shaderProgram)

        val positionHandle = GLES20.glGetAttribLocation(
            shaderProgram,
            "aPosition"
        )

        val textureHandle = GLES20.glGetAttribLocation(
            shaderProgram,
            "aTexCoord"
        )

        val samplerHandle = GLES20.glGetUniformLocation(
            shaderProgram,
            "uTexture"
        )

        positionBuffer.position(0)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(
            positionHandle,
            2,
            GLES20.GL_FLOAT,
            false,
            0,
            positionBuffer
        )

        transformedUvBuffer.position(0)
        GLES20.glEnableVertexAttribArray(textureHandle)
        GLES20.glVertexAttribPointer(
            textureHandle,
            2,
            GLES20.GL_FLOAT,
            false,
            0,
            transformedUvBuffer
        )

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)

        GLES20.glBindTexture(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            cameraTextureId
        )

        GLES20.glUniform1i(samplerHandle, 0)

        GLES20.glDrawArrays(
            GLES20.GL_TRIANGLE_STRIP,
            0,
            4
        )

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(textureHandle)
    }

    private fun acquireDepth(frame: Frame) {
        try {
            val depthImage = frame.acquireDepthImage16Bits()

            try {
                reportDepthStatus(
                    "Depth available: ${depthImage.width} x ${depthImage.height}"
                )
            } finally {
                depthImage.close()
            }

        } catch (e: NotYetAvailableException) {
            reportDepthStatus("Waiting for depth data")
        } catch (e: Exception) {
            reportDepthStatus("Depth error: ${e.message}")
        }
    }

    private fun reportDepthStatus(status: String) {
        if (status == lastReportedDepth) return

        lastReportedDepth = status

        reactContext.runOnUiQueueThread {
            val event = Arguments.createMap().apply {
                putString("status", status)
            }

            reactContext
                .getJSModule(RCTEventEmitter::class.java)
                .receiveEvent(id, "topDepthStatus", event)
        }
    }

    private fun createShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)

        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)

        val status = IntArray(1)

        GLES20.glGetShaderiv(
            shader,
            GLES20.GL_COMPILE_STATUS,
            status,
            0
        )

        if (status[0] == 0) {
            val error = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            throw RuntimeException(error)
        }

        return shader
    }

    private fun createProgram(): Int {
        val vertexShader = createShader(
            GLES20.GL_VERTEX_SHADER,
            """
            attribute vec2 aPosition;
            attribute vec2 aTexCoord;

            varying vec2 vTexCoord;

            void main() {
                gl_Position = vec4(aPosition, 0.0, 1.0);
                vTexCoord = aTexCoord;
            }
            """
        )

        val fragmentShader = createShader(
            GLES20.GL_FRAGMENT_SHADER,
            """
            #extension GL_OES_EGL_image_external : require

            precision mediump float;

            uniform samplerExternalOES uTexture;
            varying vec2 vTexCoord;

            void main() {
                gl_FragColor = texture2D(uTexture, vTexCoord);
            }
            """
        )

        val program = GLES20.glCreateProgram()

        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)

        val status = IntArray(1)

        GLES20.glGetProgramiv(
            program,
            GLES20.GL_LINK_STATUS,
            status,
            0
        )

        if (status[0] == 0) {
            val error = GLES20.glGetProgramInfoLog(program)
            GLES20.glDeleteProgram(program)
            throw RuntimeException(error)
        }

        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)

        return program
    }

    private fun resumeArSession() {
        if (!hostResumed || !sessionReady || !textureReady) return

        try {
            session?.resume()
        } catch (e: Exception) {
            reportDepthStatus("Unable to start camera: ${e.message}")
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
        closeSession()
    }

    fun closeSession() {
        hostResumed = false
        session?.close()
        session = null

        sessionReady = false
        textureReady = false

        reactContext.removeLifecycleEventListener(this)
    }
}
