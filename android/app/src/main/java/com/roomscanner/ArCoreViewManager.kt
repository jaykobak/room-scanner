
package com.roomscanner

import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.annotations.ReactProp
import com.facebook.react.uimanager.events.Event
import com.facebook.react.uimanager.events.RCTEventEmitter

class ArCoreViewManager : SimpleViewManager<ArCoreView>() {

    override fun getName() = "ArCoreView"

    override fun createViewInstance(
        reactContext: ThemedReactContext
    ): ArCoreView {
        return ArCoreView(reactContext)
    }

    override fun getExportedCustomDirectEventTypeConstants(): Map<String, Any> {
        return mapOf(
            "topDepthStatus" to mapOf("registrationName" to "onDepthStatus")
        )
    }

    override fun onDropViewInstance(view: ArCoreView) {
        view.closeSession()
        super.onDropViewInstance(view)
    }
}
