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
    name_en: str

    def as_dict(self) -> dict:
        return {
            "id": self.id,
            "name": self.name,
            "name_en": self.name_en,
            "emoji": self.emoji,
        }


SOUND_LIBRARY: tuple[Sound, ...] = (
    Sound("cat", "مواء قطة", "🐱", "Cat"),
    Sound("dog", "نباح كلب", "🐶", "Dog"),
    Sound("rooster", "صياح ديك", "🐓", "Rooster"),
    Sound("lion", "زئير أسد", "🦁", "Lion"),
    Sound("cow", "خوار بقرة", "🐄", "Cow"),
    Sound("sheep", "ثغاء خروف", "🐑", "Sheep"),
    Sound("horse", "صهيل حصان", "🐴", "Horse"),
    Sound("elephant", "بوق فيل", "🐘", "Elephant"),
    Sound("frog", "نقيق ضفدع", "🐸", "Frog"),
    Sound("bird", "زقزقة عصفور", "🐦", "Bird"),
    Sound("bee", "طنين نحلة", "🐝", "Bee"),
    Sound("snake", "فحيح أفعى", "🐍", "Snake"),
    Sound("ambulance", "صفارة إسعاف", "🚑", "Ambulance Siren"),
    Sound("car_horn", "منبه سيارة", "📢", "Car Horn"),
    Sound("motorcycle", "محرك دراجة نارية", "🏍️", "Motorcycle Engine"),
    Sound("helicopter", "مروحة هليكوبتر", "🚁", "Helicopter"),
    Sound("train", "صفير قطار", "🚆", "Train Whistle"),
    Sound("door_creak", "صرير باب", "🚪", "Creaking Door"),
    Sound("phone_ring", "رنين هاتف", "📞", "Phone Ring"),
    Sound("alarm_clock", "منبه ساعة", "⏰", "Alarm Clock"),
    Sound("thunder", "دوي رعد", "⛈️", "Thunder"),
    Sound("rain", "تساقط مطر", "🌧️", "Rain"),
    Sound("wind", "عصف رياح", "🌬️", "Wind"),
    Sound("ocean_wave", "موج بحر", "🌊", "Ocean Waves"),
    Sound("baby_cry", "بكاء طفل", "👶", "Crying Baby"),
    Sound("laugh", "ضحكة عالية", "😂", "Loud Laughter"),
    Sound("snore", "شخير نائم", "😴", "Snoring"),
    Sound("sneeze", "عطسة", "🤧", "Sneeze"),
    Sound("crowd_cheer", "هتاف جمهور", "🏟️", "Cheering Crowd"),
    Sound("guitar", "عزف جيتار", "🎸", "Guitar"),
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
