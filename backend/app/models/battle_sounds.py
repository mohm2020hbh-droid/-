"""The Voice Battle sound catalog.

Voice Battle needs the server to *pick* a sound id and broadcast it, and both
clients then synthesise the identical target locally from that id (see
``backend/app/static/js/sounds.js``). So — unlike the Duel mode's
:mod:`app.models.sounds`, which is a text-and-emoji prompt a human reads and
never needs to match anything client-side — this list's ids, names and emoji
must stay a byte-for-byte mirror of the JS ``SOUNDS`` array. If a sound is
added to one side, add it to the other, in the same order, or a Voice Battle
round can name a sound a client cannot render.
"""

from __future__ import annotations

import random
from dataclasses import dataclass
from typing import Iterable, Sequence


@dataclass(frozen=True)
class BattleSound:
    """One synthesisable target, mirroring one JS ``sounds.js`` entry."""

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


BATTLE_SOUND_LIBRARY: tuple[BattleSound, ...] = (
    ("ambulance", "صفارة إسعاف", "🚑", "Ambulance Siren"),
    ("police", "صفارة شرطة", "🚓", "Police Siren"),
    ("car_horn", "منبه سيارة", "📢", "Car Horn"),
    ("bee", "طنين نحلة", "🐝", "Bee"),
    ("mosquito", "طنين بعوضة", "🦟", "Mosquito"),
    ("snake", "فحيح أفعى", "🐍", "Snake"),
    ("wind", "عصف رياح", "🌬️", "Wind"),
    ("ocean", "موج بحر", "🌊", "Ocean Waves"),
    ("rain", "تساقط مطر", "🌧️", "Rain"),
    ("thunder", "دوي رعد", "⛈️", "Thunder"),
    ("frog", "نقيق ضفدع", "🐸", "Frog"),
    ("cat", "مواء قطة", "🐱", "Cat"),
    ("dog", "نباح كلب", "🐶", "Dog"),
    ("cow", "خوار بقرة", "🐄", "Cow"),
    ("sheep", "ثغاء خروف", "🐑", "Sheep"),
    ("rooster", "صياح ديك", "🐓", "Rooster"),
    ("bird", "زقزقة عصفور", "🐦", "Bird"),
    ("owl", "نعيق بومة", "🦉", "Owl"),
    ("train", "صفير قطار", "🚆", "Train Whistle"),
    ("phone", "رنين هاتف", "📞", "Phone Ring"),
    ("alarm", "منبه ساعة", "⏰", "Alarm Clock"),
    ("doorbell", "جرس باب", "🔔", "Doorbell"),
    ("helicopter", "مروحة هليكوبتر", "🚁", "Helicopter"),
    ("motorcycle", "محرك دراجة نارية", "🏍️", "Motorcycle"),
    ("lion", "زئير أسد", "🦁", "Lion"),
    ("elephant", "بوق فيل", "🐘", "Elephant"),
    ("cricket", "صرصور الليل", "🦗", "Cricket"),
    ("snore", "شخير نائم", "😴", "Snoring"),
    ("laugh", "ضحكة عالية", "😂", "Laughter"),
    ("guitar", "عزف جيتار", "🎸", "Guitar"),
    ("donkey", "نهيق حمار", "🫏", "Donkey"),
    ("horse", "صهيل حصان", "🐴", "Horse"),
    ("chicken", "قَقَقَة دجاجة", "🐔", "Chicken"),
    ("duck", "بطة تصدر صوتًا", "🦆", "Duck"),
    ("monkey", "صوت قرد", "🐒", "Monkey"),
    ("airplane", "طائرة", "✈️", "Airplane"),
    ("door", "صرير باب", "🚪", "Creaking Door"),
    ("bell", "رنين جرس", "🔔", "Bell"),
    ("foghorn", "بوق ضباب", "📯", "Foghorn"),
    ("fire", "طقطقة نار", "🔥", "Crackling Fire"),
    ("monster", "زمجرة وحش", "👹", "Monster"),
    ("robot", "صفير روبوت", "🤖", "Robot"),
    ("whistle", "صافرة حادة", "📣", "Whistle"),
    ("drum", "دقات طبل", "🥁", "Drum"),
)
BATTLE_SOUND_LIBRARY = tuple(BattleSound(*entry) for entry in BATTLE_SOUND_LIBRARY)

BATTLE_SOUNDS_BY_ID: dict[str, BattleSound] = {s.id: s for s in BATTLE_SOUND_LIBRARY}


def pick_battle_sound(
    exclude_ids: Iterable[str] = (), rng: "random.Random | None" = None
) -> BattleSound:
    """Pick a random battle sound, avoiding ids already used in this room."""

    chooser = rng or random
    excluded = set(exclude_ids)
    candidates: Sequence[BattleSound] = tuple(
        s for s in BATTLE_SOUND_LIBRARY if s.id not in excluded
    )
    if not candidates:
        candidates = BATTLE_SOUND_LIBRARY
    return chooser.choice(candidates)
