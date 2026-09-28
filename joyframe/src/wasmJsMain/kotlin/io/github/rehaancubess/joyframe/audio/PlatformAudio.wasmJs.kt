// SPDX-License-Identifier: Apache-2.0
package io.github.rehaancubess.joyframe.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.khronos.webgl.toInt8Array
import org.khronos.webgl.ArrayBuffer

internal actual fun createAudioBackend(bank: AudioBank): AudioBackend = WebAudio(bank)
private class WebAudio(private val bank: AudioBank) : AudioBackend {
    private val state = MutableStateFlow<AudioStatus>(AudioStatus.Loading)
    override val status = state.asStateFlow()
    private val effects = HashMap<SoundId,AudioBuffer>()
    private val voices = ArrayDeque<Pair<BufferSourceNode,GainNode>>()
    private var context: AudioContext? = null
    private var listeners: JsAny? = null
    private var engineBuffer: AudioBuffer? = null
    private var engine: Pair<BufferSourceNode,GainNode>? = null
    private var remaining = bank.effects.size + if(bank.loopBytes == null) 0 else 1
    private var activated = false
    private var wanted = false
    private var foreground = true
    private var visible = pageIsVisible()
    private var closed = false
    override fun prepare() {
        if(context != null || closed) return
        try {
            val ctx = newAudioContext(); context=ctx
            listeners = listen({
                resume(ctx,{
                    if(!closed) { activated=true; updateStatus(); syncEngine() }
                },{ fail("Browser audio could not resume") })
            },{ value -> visible=value; if(!value) stopEffects(); syncEngine() })
            fun decode(bytes: ByteArray, store: (AudioBuffer)->Unit) {
                ctx.decodeAudioData(bytes.toInt8Array().buffer,{ buffer ->
                    if(!closed) { store(buffer); remaining--; updateStatus(); syncEngine() }
                },{ fail("Browser could not decode WAV") })
            }
            bank.effects.forEach { (id,bytes) -> decode(bytes) { effects[id]=it } }
            bank.loopBytes?.let { decode(it) { engineBuffer=it } }
        } catch(failure: Throwable) { fail(failure.message ?: "Web Audio unavailable") }
    }
    private fun fail(message: String) { if(!closed) { state.value=AudioStatus.Failed(message); stop() } }
    private fun updateStatus() {
        if(closed || state.value is AudioStatus.Failed) return
        state.value = if(remaining>0) AudioStatus.Loading else if(activated) AudioStatus.Ready else AudioStatus.AwaitingGesture
    }
    override fun setForeground(value: Boolean) { foreground=value; if(!value) stopEffects(); syncEngine() }
    override fun setEngineEnabled(enabled: Boolean) { wanted=enabled; syncEngine() }
    private fun source(buffer: AudioBuffer, volume: Float, loop: Boolean): Pair<BufferSourceNode,GainNode> {
        val ctx = checkNotNull(context)
        val gain = ctx.createGain(); gain.gain.value=volume; gain.connect(ctx.destination)
        val source = ctx.createBufferSource(); source.buffer=buffer; source.loop=loop; source.connect(gain)
        return source to gain
    }
    override fun play(id: SoundId, volume: Float) {
        if(closed || !foreground || !visible || state.value != AudioStatus.Ready) return
        val buffer = effects[id] ?: return
        runCatching {
            if(voices.size==4) release(voices.removeFirst())
            val voice = source(buffer,volume,false)
            voices.addLast(voice)
            voice.first.onended = { voices.remove(voice); release(voice) }
            voice.first.start()
        }.onFailure { fail(it.message ?: "Audio playback failed") }
    }
    private fun syncEngine() {
        if(closed || !wanted || !foreground || !visible || state.value != AudioStatus.Ready) {
            engine?.let(::release); engine=null; return
        }
        if(engine != null) return
        val buffer = engineBuffer ?: return
        runCatching { engine=source(buffer,bank.loopVolume,true).also { it.first.start() } }
            .onFailure { fail(it.message ?: "Loop playback failed") }
    }
    private fun release(voice: Pair<BufferSourceNode,GainNode>) {
        voice.first.onended=null
        runCatching { voice.first.stop() }
        voice.first.disconnect(); voice.second.disconnect()
    }
    private fun stopEffects() { voices.toList().forEach(::release); voices.clear() }
    override fun stop() { wanted=false; engine?.let(::release); engine=null; stopEffects() }
    override fun close() {
        if(closed) return
        closed=true; stop(); listeners?.let(::unlisten); listeners=null
        context?.close(); context=null; effects.clear(); engineBuffer=null; state.value=AudioStatus.Closed
    }
}

private fun pageIsVisible(): Boolean = js("!document.hidden")
private fun listen(gesture: ()->Unit, visibility: (Boolean)->Unit): JsAny = js("""(function() {
    var key = function() { gesture(); };
    var view = function() { visibility(!document.hidden); };
    window.addEventListener('pointerdown',key);
    window.addEventListener('keydown',key);
    document.addEventListener('visibilitychange',view);
    return { key: key, view: view };
})()""")
private fun unlisten(handle: JsAny) { js("""(function() {
    window.removeEventListener('pointerdown',handle.key);
    window.removeEventListener('keydown',handle.key);
    document.removeEventListener('visibilitychange',handle.view);
})()""") }
private fun resume(ctx: AudioContext, success: ()->Unit, failure: ()->Unit) {
    js("ctx.resume().then(success).catch(failure)")
}
private fun newAudioContext(): AudioContext = js("new (window.AudioContext || window.webkitAudioContext)()")
internal external interface AudioParam : JsAny { var value: Float }
internal external interface AudioNode : JsAny { fun connect(destination: AudioNode); fun disconnect() }
internal external interface GainNode : AudioNode { val gain: AudioParam }
internal external interface AudioBuffer : JsAny
internal external interface BufferSourceNode : AudioNode {
    var buffer: AudioBuffer?
    var loop: Boolean
    var onended: (() -> Unit)?
    fun start()
    fun stop()
}
internal external interface AudioContext : JsAny {
    val destination: AudioNode
    fun close()
    fun createGain(): GainNode
    fun createBufferSource(): BufferSourceNode
    fun decodeAudioData(data: ArrayBuffer, onSuccess: (AudioBuffer)->Unit, onError: (JsAny)->Unit)
}
