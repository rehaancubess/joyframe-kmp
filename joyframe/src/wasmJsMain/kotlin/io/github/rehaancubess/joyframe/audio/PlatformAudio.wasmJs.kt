// SPDX-License-Identifier: Apache-2.0
package io.github.rehaancubess.joyframe.audio

import org.khronos.webgl.toInt8Array
import org.khronos.webgl.ArrayBuffer


internal actual object PlatformAudio {
    private val effects = HashMap<SoundId, AudioBuffer>()
    private val sources = ArrayDeque<BufferSourceNode>()
    private var context: AudioContext? = null
    private var preparing = false
    private var engineBuffer: AudioBuffer? = null
    private var engineSource: BufferSourceNode? = null
    private var engineWanted = false
    private var foreground = true
    private var activated = false

    actual fun setForeground(value: Boolean) { foreground = value; if (!value) stopEffects(); syncEngine() }
    actual fun prepare() {
        if (preparing) return
        val ctx = runCatching { newAudioContext() }.getOrNull() ?: return
        context = ctx
        preparing = true
        foreground = pageIsVisible()
        onFirstGesture { activated = true; runCatching { ctx.resume() }; syncEngine() }
        onVisibilityChange { visible ->
            foreground = visible
            if (!visible) stopEffects()
            else if (activated) runCatching { ctx.resume() }
            syncEngine()
        }
        for ((id, wav) in AudioBank.effects) {
            runCatching {
                ctx.decodeAudioData(wav.toInt8Array().buffer,
                    { buffer -> effects[id] = buffer }, { _ -> Unit })
            }
        }
        runCatching {
            ctx.decodeAudioData(AudioBank.loopBytes.toInt8Array().buffer,
                { buffer -> engineBuffer = buffer; syncEngine() }, { _ -> Unit })
        }
    }

    actual fun play(id: SoundId, volume: Float) {
        if (!foreground || !activated) return
        val ctx = context ?: return
        val buffer = effects[id] ?: return
        runCatching {
            val gain = ctx.createGain()
            gain.gain.value = volume.coerceIn(0f, 1f)
            gain.connect(ctx.destination)
            val source = ctx.createBufferSource()
            source.buffer = buffer
            source.connect(gain)
            if (sources.size == 4) runCatching { sources.removeFirst().stop() }
            sources.addLast(source)
            source.start()
        }
    }

    actual fun setEngineEnabled(enabled: Boolean) {
        engineWanted = enabled
        syncEngine()
    }
    private fun syncEngine() {
        if (!engineWanted || !foreground) {
            runCatching { engineSource?.stop() }
            engineSource = null
            return
        }
        if (engineSource != null) return
        val ctx = context ?: return
        val buffer = engineBuffer ?: return
        runCatching {
            val gain = ctx.createGain()
            gain.gain.value = AudioBank.loopVolume
            gain.connect(ctx.destination)
            engineSource = ctx.createBufferSource().also {
                it.buffer = buffer
                it.loop = true
                it.connect(gain)
                it.start()
            }
        }
    }
    private fun stopEffects() {
        sources.forEach { runCatching { it.stop() } }
        sources.clear()
    }
    actual fun stop() {
        setEngineEnabled(false)
        stopEffects()
    }
}

private fun onVisibilityChange(action: (Boolean) -> Unit) {
    js("document.addEventListener('visibilitychange', function() { action(!document.hidden); })")
}
private fun pageIsVisible(): Boolean = js("!document.hidden")

internal external interface AudioParam : JsAny {
    var value: Float
}

internal external interface AudioNode : JsAny {
    fun connect(destination: AudioNode)
}

internal external interface GainNode : AudioNode {
    val gain: AudioParam
}

internal external interface AudioBuffer : JsAny

internal external interface BufferSourceNode : AudioNode {
    var buffer: AudioBuffer?
    var loop: Boolean
    val playbackRate: AudioParam
    fun start()
    fun stop()
}

internal external interface AudioContext : JsAny {
    val destination: AudioNode
    fun resume()
    fun createGain(): GainNode
    fun createBufferSource(): BufferSourceNode
    fun decodeAudioData(data: ArrayBuffer, onSuccess: (AudioBuffer) -> Unit, onError: (JsAny) -> Unit)
}


private fun newAudioContext(): AudioContext =
    js("new (window.AudioContext || window.webkitAudioContext)()")


private fun onFirstGesture(action: () -> Unit) {
    js(
        """(function() {
            var fired = false;
            var run = function() { if (!fired) { fired = true; action(); } };
            window.addEventListener('pointerdown', run, { once: true });
            window.addEventListener('keydown', run, { once: true });
            window.addEventListener('touchstart', run, { once: true });
        })()"""
    )
}
