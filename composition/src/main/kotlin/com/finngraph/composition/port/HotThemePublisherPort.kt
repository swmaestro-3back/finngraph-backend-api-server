package com.finngraph.composition.port

import com.finngraph.composition.hottheme.HotThemeSnapshot

interface HotThemePublisherPort {
    fun publish(snapshot: HotThemeSnapshot)
}
