package org.droidplanner.android.fragments.widget

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import org.droidplanner.android.R
import org.droidplanner.android.fragments.widget.video.BaseUVCVideoWidget

class MiniWidgetUVCLinkVideo : BaseUVCVideoWidget() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        aspectRatio = ASPECT_RATIO_16_9
        return inflater.inflate(R.layout.fragment_mini_widget_uvc_video, container, false)
    }
}
