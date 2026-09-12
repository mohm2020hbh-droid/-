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

    def as_dict(self) -> dict:
        return {"id": self.id, "name": self.name, "emoji": self.emoji}


BATTLE_SOUND_LIBRARY: tuple[BattleSound, ...] = (
    ("ambulance", "صفارة إسعاف", "🚑"),
    ("police", "صفارة شرطة", "🚓"),
    ("car_horn", "منبه سيارة", "📢"),
    ("bee", "طنين نحلة", "🐝"),
    ("mosquito", "طنين بعوضة", "🦟"),
    ("snake", "فحيح أفعى", "🐍"),
    ("wind", "عصف رياح", "🌬️"),
    ("ocean", "موج بحر", "🌊"),
    ("rain", "تساقط مطر", "🌧️"),
    ("thunder", "دوي رعد", "⛈️"),
    ("frog", "نقيق ضفدع", "🐸"),
    ("cat", "مواء قطة", "🐱"),
    ("dog", "نباح كلب", "🐶"),
    ("cow", "خوار بقرة", "🐄"),
    ("sheep", "ثغاء خروف", "🐑"),
    ("rooster", "صياح ديك", "🐓"),
    ("bird", "زقزقة عصفور", "🐦"),
    ("owl", "نعيق بومة", "🦉"),
    ("train", "صفير قطار", "🚆"),
    ("phone", "رنين هاتف", "📞"),
    ("alarm", "منبه ساعة", "⏰"),
    ("doorbell", "جرس باب", "🔔"),
    ("helicopter", "مروحة هليكوبتر", "🚁"),
    ("motorcycle", "محرك دراجة نارية", "🏍️"),
    ("lion", "زئير أسد", "🦁"),
    ("elephant", "بوق فيل", "🐘"),
    ("cricket", "صرصور الليل", "🦗"),
    ("snore", "شخير نائم", "😴"),
    ("laugh", "ضحكة عالية", "😂"),
    ("guitar", "عزف جيتار", "🎸"),
    ("donkey", "نهيق حمار", "🫏"),
    ("horse", "صهيل حصان", "🐴"),
    ("chicken", "قَقَقَة دجاجة", "🐔"),
    ("duck", "بطة تصدر صوتًا", "🦆"),
    ("monkey", "صوت قرد", "🐒"),
    ("airplane", "طائرة", "✈️"),
    ("door", "صرير باب", "🚪"),
    ("bell", "رنين جرس", "🔔"),
    ("foghorn", "بوق ضباب", "📯"),
    ("fire", "طقطقة نار", "🔥"),
    ("monster", "زمجرة وحش", "👹"),
    ("robot", "صفير روبوت", "🤖"),
    ("whistle", "صافرة حادة", "📣"),
    ("drum", "دقات طبل", "🥁"),
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
