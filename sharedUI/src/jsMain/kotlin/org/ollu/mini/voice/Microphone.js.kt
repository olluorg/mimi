package org.ollu.mini.voice

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberMicrophoneLevels(): MicrophoneLevels = remember { WebMicrophone() }

/**
 * Loudness from `getUserMedia` through an analyser node. The browser asks the
 * user for the microphone itself; echo cancellation is requested so the page's
 * own voice is mostly removed before it is measured.
 */
private class WebMicrophone : MicrophoneLevels {
    override val isSupported = true
    private var handle: dynamic = null

    override fun start(onFrame: (FloatArray, Int) -> Unit, onUnavailable: () -> Unit) {
        if (handle != null) return
        handle = startMicrophone({ samples: dynamic, rate: Int ->
            val n = (samples.length as Int)
            onFrame(FloatArray(n) { (samples[it] as Double).toFloat() }, rate)
        }, onUnavailable)
    }

    override fun stop() {
        val h = handle ?: return
        handle = null
        h.stop()
    }
}

private fun startMicrophone(onFrame: (dynamic, Int) -> Unit, onFail: () -> Unit): dynamic = js(
    """(function(){
        var h = { stopped: false, timer: null, stream: null, ctx: null };
        h.stop = function(){
            h.stopped = true;
            if (h.timer) clearInterval(h.timer);
            if (h.stream) h.stream.getTracks().forEach(function(t){ t.stop(); });
            if (h.ctx) h.ctx.close();
        };
        if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) { onFail(); return h; }
        navigator.mediaDevices.getUserMedia({ audio: { echoCancellation: true, noiseSuppression: true, autoGainControl: false } })
            .then(function(stream){
                if (h.stopped) { stream.getTracks().forEach(function(t){ t.stop(); }); return; }
                h.stream = stream;
                var Ctx = window.AudioContext || window.webkitAudioContext;
                h.ctx = new Ctx();
                var analyser = h.ctx.createAnalyser();
                analyser.fftSize = 2048;
                h.ctx.createMediaStreamSource(stream).connect(analyser);
                var buf = new Float32Array(analyser.fftSize);
                // Down to about 16 kHz, averaging each group so nothing above
                // the new Nyquist folds back in; that is plenty for a voice.
                var step = Math.max(1, Math.round(h.ctx.sampleRate / 16000));
                var rate = Math.round(h.ctx.sampleRate / step);
                h.timer = setInterval(function(){
                    analyser.getFloatTimeDomainData(buf);
                    var out = [];
                    for (var i = 0; i + step <= buf.length; i += step) {
                        var s = 0;
                        for (var j = 0; j < step; j++) s += buf[i + j];
                        out.push(s / step);
                    }
                    onFrame(out, rate);
                }, 50);
            })
            .catch(function(){ onFail(); });
        return h;
    })()"""
)
