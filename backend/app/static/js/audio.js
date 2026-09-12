/**
 * Microphone capture and playback.
 *
 * Recording goes through MediaRecorder (so the online mode can relay the
 * encoded bytes untouched) and is additionally decoded to raw PCM, which is
 * what the scoring engine needs. Nothing leaves the device in single player.
 */

let context = null;

/** Browsers only allow audio after a gesture, so the context is made lazily. */
export function audioContext() {
  if (!context) {
    const Ctor = window.AudioContext || window.webkitAudioContext;
    context = new Ctor();
  }
  if (context.state === "suspended") context.resume();
  return context;
}

export function microphoneSupported() {
  return !!(navigator.mediaDevices && navigator.mediaDevices.getUserMedia && window.MediaRecorder);
}

/** Play raw samples and resolve when playback ends. */
export function playSamples(samples, sampleRate) {
  return new Promise((resolve) => {
    const ctx = audioContext();
    const buffer = ctx.createBuffer(1, samples.length, sampleRate);
    buffer.copyToChannel ? buffer.copyToChannel(samples, 0)
                         : buffer.getChannelData(0).set(samples);
    const source = ctx.createBufferSource();
    source.buffer = buffer;
    source.connect(ctx.destination);
    source.onended = resolve;
    source.start();
  });
}

/** Play encoded bytes that arrived from the other player. */
export function playEncoded(bytes, mime) {
  return new Promise((resolve) => {
    const url = URL.createObjectURL(new Blob([bytes], { type: mime || "audio/webm" }));
    const element = new Audio(url);
    const done = () => { URL.revokeObjectURL(url); resolve(); };
    element.onended = done;
    element.onerror = done;
    element.play().catch(done);
  });
}

/** Decode encoded audio to mono PCM for analysis. */
export async function decodeToPcm(arrayBuffer) {
  const ctx = audioContext();
  const buffer = await ctx.decodeAudioData(arrayBuffer.slice(0));
  const channel = buffer.getChannelData(0);
  return { samples: Float32Array.from(channel), sampleRate: buffer.sampleRate };
}

/**
 * Record for a fixed number of seconds.
 *
 * @param seconds  how long to record
 * @param onTick   called each second with the time remaining
 * @param signal   `{ stopEarly: fn }` is attached so the caller can cut it short
 */
export function recordClip(seconds, onTick, control = {}) {
  return new Promise(async (resolve, reject) => {
    if (!microphoneSupported()) {
      reject(new Error("unsupported"));
      return;
    }

    let stream;
    try {
      stream = await navigator.mediaDevices.getUserMedia({ audio: true });
    } catch (error) {
      reject(error);
      return;
    }

    const mime = MediaRecorder.isTypeSupported("audio/webm;codecs=opus")
      ? "audio/webm;codecs=opus"
      : "audio/webm";
    const recorder = new MediaRecorder(stream, { mimeType: mime });
    const chunks = [];
    let timer = null;

    const cleanUp = () => {
      if (timer) clearInterval(timer);
      stream.getTracks().forEach((track) => track.stop());
    };

    recorder.ondataavailable = (event) => { if (event.data.size) chunks.push(event.data); };

    recorder.onstop = async () => {
      cleanUp();
      const blob = new Blob(chunks, { type: mime });
      if (!blob.size) { reject(new Error("empty")); return; }
      const bytes = new Uint8Array(await blob.arrayBuffer());
      try {
        const pcm = await decodeToPcm(bytes.buffer);
        resolve({ bytes, mime, ...pcm });
      } catch (error) {
        // The bytes are still usable for the online relay even if this
        // browser cannot decode them back for analysis.
        resolve({ bytes, mime, samples: null, sampleRate: 0 });
      }
    };

    const stop = () => { if (recorder.state === "recording") recorder.stop(); };
    control.stopEarly = stop;

    recorder.start();
    let remaining = seconds;
    onTick?.(remaining);
    timer = setInterval(() => {
      remaining -= 1;
      onTick?.(Math.max(0, remaining));
      if (remaining <= 0) stop();
    }, 1000);
  });
}

/** Encode mono samples as a 16-bit PCM WAV, playable anywhere with no codec. */
export function encodeWav(samples, sampleRate) {
  const buffer = new ArrayBuffer(44 + samples.length * 2);
  const view = new DataView(buffer);
  const ascii = (offset, text) => {
    for (let i = 0; i < text.length; i++) view.setUint8(offset + i, text.charCodeAt(i));
  };
  ascii(0, "RIFF"); view.setUint32(4, 36 + samples.length * 2, true); ascii(8, "WAVE");
  ascii(12, "fmt "); view.setUint32(16, 16, true); view.setUint16(20, 1, true);
  view.setUint16(22, 1, true); view.setUint32(24, sampleRate, true);
  view.setUint32(28, sampleRate * 2, true); view.setUint16(32, 2, true); view.setUint16(34, 16, true);
  ascii(36, "data"); view.setUint32(40, samples.length * 2, true);
  for (let i = 0; i < samples.length; i++) {
    view.setInt16(44 + i * 2, Math.max(-1, Math.min(1, samples[i])) * 0x7fff, true);
  }
  return new Uint8Array(buffer);
}
