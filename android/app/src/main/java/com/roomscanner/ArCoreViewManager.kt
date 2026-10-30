
package com.roomscanner

import android.view.ViewGroup
import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.ThemedReactContext

class ArCoreViewManager : SimpleViewManager<ArCoreView>() {

    override fun getName() = "ArCoreView"

    override fun createViewInstance(
        reactContext: ThemedReactContext
    ): ArCoreView {
        return ArCoreView(reactContext)
    }

    override fun onDropViewInstance(view: ArCoreView) {
        view.closeSession()
        super.onDropViewInstance(view)
    }
}
