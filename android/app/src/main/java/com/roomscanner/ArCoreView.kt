
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
import kotlin.math.roundToInt
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
    private val accumulatedWorldPoints = LinkedHashMap<String, FloatArray>()
    private var totalDepthSamples = 0L

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
                val plane = depthImage.planes[0]
                val buffer = plane.buffer
                    .duplicate()
                    .order(ByteOrder.LITTLE_ENDIAN)

                val width = depthImage.width
                val height = depthImage.height

                val intrinsics = frame.camera.imageIntrinsics
                val focalLength = intrinsics.focalLength
                val principalPoint = intrinsics.principalPoint
                val imageDimensions = intrinsics.imageDimensions

                val scaleX = width.toFloat() / imageDimensions[0]
                val scaleY = height.toFloat() / imageDimensions[1]

                val fx = focalLength[0] * scaleX
                val fy = focalLength[1] * scaleY
                val cx = principalPoint[0] * scaleX
                val cy = principalPoint[1] * scaleY

                val grid = mutableListOf<String>()
                val cameraPose = frame.camera.pose

                var centerDepthMm = 0

                for (row in 0 until 3) {
                    val rowValues = mutableListOf<String>()

                    for (col in 0 until 3) {
                        val pixelX = ((col + 0.5f) * width / 3).toInt()
                        val pixelY = ((row + 0.5f) * height / 3).toInt()

                        val measurements = mutableListOf<Int>()

                        for (dy in -1..1) {
                            for (dx in -1..1) {
                                val x = pixelX + dx
                                val y = pixelY + dy

                                if (x in 0 until width && y in 0 until height) {
                                    val offset =
                                        y * plane.rowStride +
                                        x * plane.pixelStride

                                    if (offset + 1 < buffer.limit()) {
                                        val packedDepth =
                                            buffer.getShort(offset).toInt()

                                        val depthMm = packedDepth and 0x1FFF

                                        if (depthMm > 0) {
                                            measurements.add(depthMm)
                                            totalDepthSamples++
                                        }
                                    }
                                }
                            }
                        }

                        if (measurements.isEmpty()) {
                            rowValues.add("--")
                        } else {
                            measurements.sort()

                            val medianMm =
                                measurements[measurements.size / 2]

                            rowValues.add("${medianMm / 10}")

                            // Save the centre region's measurement.
                            if (row == 1 && col == 1) {
                                centerDepthMm = medianMm
                            }

                            // Convert this depth sample into camera coordinates.
                            val z = medianMm / 1000f
                            val x = (pixelX - cx) * z / fx
                            val y = (cy - pixelY) * z / fy

                            // ARCore cameras look along their local negative Z axis.
                            val cameraPoint = floatArrayOf(x, y, -z)

                            // Transform the point into ARCore world coordinates.
                            val worldPoint =
                                cameraPose.transformPoint(cameraPoint)

                            // Group nearby points into 5 cm spatial cells.
                            val key = listOf(
                                (worldPoint[0] / 0.05f).roundToInt(),
                                (worldPoint[1] / 0.05f).roundToInt(),
                                (worldPoint[2] / 0.05f).roundToInt()
                            ).joinToString(",")

                            if (!accumulatedWorldPoints.containsKey(key)) {
                                accumulatedWorldPoints[key] = worldPoint
                            }

                            // Keep memory usage bounded.
                            if (accumulatedWorldPoints.size > 1500) {
                                val oldestKey =
                                    accumulatedWorldPoints.keys.first()

                                accumulatedWorldPoints.remove(oldestKey)
                            }
                        }
                    }

                    grid.add(rowValues.joinToString("  "))
                }

                val coordinateText = if (centerDepthMm > 0) {
                    val z = centerDepthMm / 1000f
                    val x = (width / 2f - cx) * z / fx
                    val y = (cy - height / 2f) * z / fy

                    val worldPoint = cameraPose.transformPoint(
                        floatArrayOf(x, y, -z)
                    )

                    "Centre point (world space):\n" +
                        "X: ${"%.2f".format(java.util.Locale.US, worldPoint[0])} m\n" +
                        "Y: ${"%.2f".format(java.util.Locale.US, worldPoint[1])} m\n" +
                        "Z: ${"%.2f".format(java.util.Locale.US, worldPoint[2])} m"
                } else {
                    "Centre point: No valid depth"
                }

                reportDepthStatus(
                    "Depth map (cm)\n" +
                        grid.joinToString("\n") +
                        "\n\n$coordinateText" +
                        "\n\nStored 3D points: ${accumulatedWorldPoints.size}" +
                        "\nDepth samples processed: $totalDepthSamples"
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
