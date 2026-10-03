# ANDROID_REQUIREMENTS.md

## 1) المنصة والاتجاه
| البند | القيمة | سبب |
|---|---|---|
| المنصة | Android Phone فقط | الطلب |
| الاتجاه | `sensorLandscape` (يسمح بالقلب بين الأفقيين، لا Portrait أبدًا) | الطلب: LANDSCAPE فقط أثناء اللعب |
| minSdk | 24 (Android 7.0) | WebView حديث + تغطية ≈ 98% |
| targetSdk | 35 (Gradle) / 34 (مسار apt) | Google Play يشترط ≥ 35 لتطبيقات جديدة اعتبارًا من 2025 — يُرفع في مسار Gradle |
| الصلاحيات | `VIBRATE` فقط | **لا INTERNET** ⇒ لا جمع بيانات، Data safety نظيف |
| Application ID | `app.pogosummit.game` | **قرار مؤقت** — غيّره قبل الرفع إن لزم |
| Version | `versionName 0.1.0` · `versionCode 1` | |
| الشاشة | immersive sticky، keep-screen-on، Cutout = shortEdges، safe-area عبر CSS `env()` + جسر JS احتياطي | Notch/ثقوب الكاميرا |
| الأيقونة/Splash | أيقونة إجرائية (adaptive-ready PNG) + شاشة تحميل HTML بشعار اللعبة | |

## 2) ميزانيات الأداء (Phase 17)
| الميزانية | الهدف | كيف نضمنه |
|---|---|---|
| FPS | 60 على الأجهزة المتوسطة | استيفاء فيزياء 120Hz، دقة ديناميكية، ظل واحد، دفعات instanced |
| Draw calls | < 250 (مقاس بعد الترقية البصرية: 101–124) | دمج هندسة المنصات، InstancedMesh للأشجار/الصخور/الأعشاب، culling بالمسافة |
| مثلثات | < 150k نشطة (مقاس: 103k–142k مع ممر الظل) | LOD: عناصر الخلفية منخفضة التفاصيل، تقليل عند Simplified |
| ذاكرة النسيج | < 48MB | خامات إجرائية صغيرة (≤ 512²)، Atlas واحد للجسيمات والواجهة |
| الجسيمات | ≤ 256 نشط، مجمّعة (Object Pool) | لا `new` أثناء اللعب |
| حجم APK | < 12MB | لا أصول ثنائية؛ three.js مضغوط — **الفعلي 454 KB** (يشمل خطّين woff2 ≈ 80 KB) |
| زمن البدء | < 3 ثوانٍ | شاشة تحميل + بناء مرحلة واحدة |
| البطارية | سقف 60 fps (خيار 30) + إيقاف الحلقة عند `visibilitychange` | |

مستويات الرسم: **High / Default / Simplified** (مثل خيارات الأصل في PDF ص11): تتحكم في pixelRatio، الظل، كثافة الجسيمات، كثافة الأعشاب/الأشجار.

## 3) سلسلة البناء
| الأداة | الحالة هنا | ملاحظة |
|---|---|---|
| Node 22 + esbuild + three | ✔ | `npm run build` ⇒ `android/app/src/main/assets/www/` |
| `aapt` `dalvik-exchange` `apksigner` `zipalign` + android-23.jar (apt) | ✔ | `tools/build-apk.sh` ⇒ APK debug موقّع |
| Gradle 8.14 + AGP + compileSdk 35 | ✘ **محجوب** | يتطلب `dl.google.com`؛ مشروع Gradle مكتوب لكن **غير مبني هنا** |
| إنتاج AAB للنشر | ✘ | يحتاج AGP؛ تشغيل `./gradlew bundleRelease` على جهاز المطوّر |
| محاكي/جهاز Android | ✘ | لا KVM ولا جهاز في هذه البيئة |

> **للمستخدم:** لتفعيل بناء AAB هنا أضف `dl.google.com` (و`maven.google.com`) ضمن Allowed domains في إعدادات البيئة السحابية (Network access → Custom)، ثم أعد التشغيل. الخطوات: https://code.claude.com/docs/en/cloud-environments#network-access

## 4) مصفوفة الاختبار المطلوبة قبل الإطلاق (غير منفَّذة بالكامل هنا)
| اختبار | هنا | على جهاز حقيقي |
|---|---|---|
| منطق الفيزياء + الحفظ + اللمس (vitest، 78 اختبارًا) | ✔ | — |
| منطق غلاف Android (JUnit عبر Gradle محلي، 10 اختبارات) | ✔ | — |
| عرض WebGL2 + لقطات (Chromium/SwiftShader) | ✔ | — |
| لمس متعدد/إلغاء (Playwright touch emulation) | ✔ | يجب تأكيده |
| APK يُبنى ويُوقَّع ويمرّ `apksigner verify` + `aapt dump badging` | ✔ | — |
| تشغيل الـAPK، Immersive، Cutout، اهتزاز فعلي، FPS حقيقي | ✘ | **مطلوب** |
| أجهزة متوسطة (Mali-G52 / Adreno 610) | ✘ | **مطلوب** |
| Data safety / Content rating / تواقيع Play App Signing | ✘ | عند الرفع |

## 5) قائمة تحقق Google Play (للإطلاق)
- [ ] targetSdk 35 + AAB موقّع بمفتاح الإطلاق (لا مفتاح debug).
- [ ] أيقونة 512² + رسوم ميزة + لقطات شاشة (أفقية).
- [ ] نموذج Data safety: «لا جمع بيانات».
- [ ] تصنيف المحتوى (IARC). سياسة خصوصية (لا بيانات).
- [ ] اختبار على 3 أجهزة على الأقل (ضعيف/متوسط/قوي) + تحقق من اهتزاز وتخزين.
