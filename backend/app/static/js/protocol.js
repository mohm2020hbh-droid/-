/**
 * Online wire protocol, mirroring backend/app/models/messages.py and
 * android/.../GameProtocol.kt.
 *
 * Text frames are JSON envelopes {type, payload}; recordings travel as binary
 * frames shaped [4 bytes big-endian header length][JSON header][audio bytes].
 */

export function encodeAudioFrame(roundNumber, audioBytes, mime) {
  const header = new TextEncoder().encode(
    JSON.stringify({ round_number: roundNumber, mime }),
  );
  const frame = new Uint8Array(4 + header.length + audioBytes.length);
  new DataView(frame.buffer).setUint32(0, header.length); // big-endian
  frame.set(header, 4);
  frame.set(audioBytes, 4 + header.length);
  return frame;
}

export function decodeAudioFrame(buffer) {
  const bytes = new Uint8Array(buffer);
  if (bytes.length < 4) return null;
  const headerLength = new DataView(bytes.buffer, bytes.byteOffset).getUint32(0);
  if (headerLength <= 0 || headerLength > 4096 || bytes.length < 4 + headerLength) return null;
  try {
    const header = JSON.parse(new TextDecoder().decode(bytes.subarray(4, 4 + headerLength)));
    return { header, audio: bytes.subarray(4 + headerLength) };
  } catch (e) {
    return null;
  }
}

export function serverUrl() {
  const scheme = location.protocol === "https:" ? "wss:" : "ws:";
  return `${scheme}//${location.host}/ws`;
}
