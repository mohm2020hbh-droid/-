# Drop-in target recordings

Every target sound in Voice Duel is synthesised on the device, so the game
works offline with nothing to download. If you would rather use a real
recording for a sound, put the file here:

    backend/app/static/audio/<sound_id>.mp3      (.ogg and .wav also work)

Use the sound's id exactly as it appears in `app/static/js/sounds.js` — the
same id the game uses for scoring and for the Arabic/English names, e.g.

    donkey.mp3      lion.wav       train.ogg
    cat.mp3         rooster.mp3    doorbell.ogg

That is all. The next time the sound is played the file is picked up
automatically: no code change, no list to edit, no restart beyond reloading
the page. The same file is then used both for playback and for scoring, so a
player is always scored against exactly the audio they heard.

Notes:

* The id must match exactly, including case. `Donkey.mp3` will not be found.
* Any sample rate and channel count is fine; the file is converted to mono at
  the engine's analysis rate when it loads.
* Keep clips short — roughly 0.5 to 3 seconds, like the built-in targets. A
  long clip makes the imitation round drag.
* Trim the silence at the start and end, or the player will hear a pause
  before the sound.
* A missing, corrupt, or unsupported file is ignored and the synthesised
  target is used instead, so a bad file can never stop the game from running.

The full list of ids is in `app/static/js/sounds.js`.
