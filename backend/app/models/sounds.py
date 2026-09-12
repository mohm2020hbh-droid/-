"""The fixed sound library (R6).

Every entry is a *target sound* the performer has to imitate with their voice.
Both players see the name and the emoji, because the rater needs to know the
target to judge the imitation at all.
"""

from __future__ import annotations

import random
from dataclasses import dataclass
from typing import Iterable, Sequence


@dataclass(frozen=True)
class Sound:
    """One target sound."""

    id: str
    name: str
    emoji: str

    def as_dict(self) -> dict:
        return {"id": self.id, "name": self.name, "emoji": self.emoji}


SOUND_LIBRARY: tuple[Sound, ...] = (
    Sound("cat", "مواء قطة", "🐱"),
    Sound("dog", "نباح كلب", "🐶"),
    Sound("rooster", "صياح ديك", "🐓"),
    Sound("lion", "زئير أسد", "🦁"),
    Sound("cow", "خوار بقرة", "🐄"),
    Sound("sheep", "ثغاء خروف", "🐑"),
    Sound("horse", "صهيل حصان", "🐴"),
    Sound("elephant", "بوق فيل", "🐘"),
    Sound("frog", "نقيق ضفدع", "🐸"),
    Sound("bird", "زقزقة عصفور", "🐦"),
    Sound("bee", "طنين نحلة", "🐝"),
    Sound("snake", "فحيح أفعى", "🐍"),
    Sound("ambulance", "صفارة إسعاف", "🚑"),
    Sound("car_horn", "منبه سيارة", "📢"),
    Sound("motorcycle", "محرك دراجة نارية", "🏍️"),
    Sound("helicopter", "مروحة هليكوبتر", "🚁"),
    Sound("train", "صفير قطار", "🚆"),
    Sound("door_creak", "صرير باب", "🚪"),
    Sound("phone_ring", "رنين هاتف", "📞"),
    Sound("alarm_clock", "منبه ساعة", "⏰"),
    Sound("thunder", "دوي رعد", "⛈️"),
    Sound("rain", "تساقط مطر", "🌧️"),
    Sound("wind", "عصف رياح", "🌬️"),
    Sound("ocean_wave", "موج بحر", "🌊"),
    Sound("baby_cry", "بكاء طفل", "👶"),
    Sound("laugh", "ضحكة عالية", "😂"),
    Sound("snore", "شخير نائم", "😴"),
    Sound("sneeze", "عطسة", "🤧"),
    Sound("crowd_cheer", "هتاف جمهور", "🏟️"),
    Sound("guitar", "عزف جيتار", "🎸"),
)

SOUNDS_BY_ID: dict[str, Sound] = {sound.id: sound for sound in SOUND_LIBRARY}


def pick_sound(exclude_ids: Iterable[str] = (), rng: random.Random | None = None) -> Sound:
    """Pick a random sound, avoiding the ones already used in this match.

    Falls back to the full library once every sound has been used, so a match
    longer than the library never runs dry.
    """

    chooser = rng or random
    excluded = set(exclude_ids)
    candidates: Sequence[Sound] = tuple(s for s in SOUND_LIBRARY if s.id not in excluded)
    if not candidates:
        candidates = SOUND_LIBRARY
    return chooser.choice(candidates)
