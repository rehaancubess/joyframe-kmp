package io.github.rehaancubess.joyframe

import android.content.Context
import io.github.rehaancubess.joyframe.audio.PlatformAudio

object JoyframeAndroid {
    /** Call from Application.onCreate before Audio.prepare. Only applicationContext is retained. */
    fun initialize(context: Context) = PlatformAudio.initialize(context)
}
