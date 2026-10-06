# Game Forensics — تحليل الألعاب والهندسة العكسية 🔍

مشروع لتحليل ملفات الألعاب والبرامج (APK / AAB / EXE / ELF / WASM / Unity / Godot …)
واستخراج بنيتها وثوابتها الفيزيائية **بالأدلة فقط**. مكتوب بـ Python 3 بدون أي مكتبات خارجية.

## القاعدة الذهبية
لا توجد قيمة بدون دليل. كل معلومة تُسجَّل في `evidence.jsonl` مع حالتها ومصدرها:

| الحالة | المعنى |
|---|---|
| `EXTRACTED` | موجودة حرفيًا في الملف (مع الملف + الموقع + نص الدليل) |
| `DERIVED` | محسوبة من سجلات أخرى بمعادلة معلنة |
| `INFERRED` | استنتاج من دليلين مستقلين أو أكثر |
| `ESTIMATED` | تقدير/تخمين — الثقة لا تتجاوز MEDIUM |
| `UNKNOWN` | غير معروفة — مع ما يلزم لحلّها |

حدود الثقة مفروضة في الكود (`fx/ledger.py`)، و`fx/validate.py` يعيد فتح كل ملف مصدر
ويتحقق من كل سجل `EXTRACTED`، وأي خطأ = لا يُنشر التقرير.

> ⚠️ حلّل فقط ما تملك حق تحليله. المشروع **لا** يتجاوز التشفير أو الحماية أو مضادات الغش؛
> المحتوى المشفّر يُسجَّل كـ `UNKNOWN`.

## البنية

```
game-forensics/
├── run.py              # تشغيل خط المعالجة كاملًا
├── fx/
│   ├── ledger.py       # سجل الأدلة + حدود الثقة
│   ├── probe.py        # المرحلة 0: كشف الأدوات المثبتة (jadx, apktool, Ghidra…)
│   ├── discover.py     # المرحلة 1: جرد الملفات، التوقيعات، sha256، الإنتروبيا، فحص الأرشيفات
│   └── validate.py     # إعادة التحقق من كل الأدلة
├── templates/          # قالب التقرير النهائي (22 قسمًا)
├── tests/              # اختبارات unittest
├── targets/            # ضع هنا الملفات المراد تحليلها (لا تُرفع إلى git)
├── captures/           # تسجيلات السلوك CSV: t,x,y[,vx,vy,collision] (لا تُرفع)
└── reports/            # المخرجات (لا تُرفع)
```

## الاستخدام

```bash
cd game-forensics
python3 run.py targets/my_game.apk --out reports/my_game
python3 -m unittest -v          # الاختبارات
```

المخرجات في `reports/<name>/`: `evidence.jsonl`، `work/tools.json`، `work/inventory.json`،
`validation_report.json`.

## الخطوات القادمة (Roadmap)

- [x] 0 — كشف الأدوات
- [x] 1 — الجرد والتصنيف وفحص الأرشيفات (zip-slip / zip-bomb / التشفير)
- [ ] 2 — كشف المحرك (Unity / Godot / Unreal / GameMaker / Web)
- [ ] 3-4 — استخراج الثوابت الرقمية (الجاذبية، الارتداد، الاحتكاك، الخطوة الزمنية…)
- [ ] 5 — البنية المعمارية (classes، signals، سلسلة Input→Physics→Render)
- [ ] 6-7 — المراحل والأصول (المشاهد، الصور، الأصوات)
- [ ] 8-9 — إعادة بناء الفيزياء من تسجيل سلوك (black-box fitting)
- [ ] 10-11 — المحاكاة والمقارنة (Observed vs Extracted vs Reconstructed)
- [ ] 13 — توليد `FINAL_REPORT.md`
