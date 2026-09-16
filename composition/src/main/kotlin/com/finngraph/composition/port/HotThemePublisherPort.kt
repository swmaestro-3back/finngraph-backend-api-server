package com.finngraph.composition.port

import com.finngraph.composition.HotThemeSnapshot

interface HotThemePublisherPort {
    fun publish(snapshot: HotThemeSnapshot)
}
