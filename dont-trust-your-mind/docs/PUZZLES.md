# Puzzle catalogue

Generated from `content/puzzles/*.json`. Solution targets are shown so the
table doubles as a spoiler sheet for testing; they are never shown in-game
before a stage ends.

## Chapter 1 — What You See · ما تراه

| # | id | type | Arabic instruction | answer (ar) | English instruction | answer (en) |
|---|---|---|---|---|---|---|
| 1 | `p001_red` | visual | اضغط على الأحمر | `w_red` | Tap the red one | `w_red` |
| 2 | `p002_bigger` | visual | اضغط على أكبر رقم | `n11` | Tap the biggest number | `n11` |
| 3 | `p003_dont_tap` | reverse | لا تضغط أي شيء | wait 5s, touching nothing | Don't tap anything | wait 5s, touching nothing |
| 4 | `p004_first_letter` | language | اضغط على أول حرف | `char:word:0` | Tap the first letter | `char:word:0` |
| 5 | `p005_third_square` | position | اضغط على المربع الثالث | `sq3` | Tap the third square | `sq3` |
| 6 | `p006_this_sentence` | ui | اضغط على هذه الجملة | `ui:instruction` | Tap this sentence | `ui:instruction` |
| 7 | `p007_stroop` | visual | اضغط على الكلمة المكتوبة باللون الأخضر | `w3` | Tap the word written in green | `w3` |
| 8 | `p008_unseen` | visual | اضغط على الشيء الذي لا تراه | `empty` | Tap the thing you cannot see | `empty` |
| 9 | `p009_two_steps` | multi_step | اضغط الأزرق، ثم الأصفر | `c_blue`→ `c_yellow` | Tap blue, then yellow | `c_blue`→ `c_yellow` |
| 10 | `p010_liar` | meta | كل تعليمة في هذه المرحلة كاذبة. اضغط على الدائرة. | `square` | Every instruction in this stage is false. Tap the circle. | `square` |

## Chapter 2 — What You Read · ما تقرأه

| # | id | type | Arabic instruction | answer (ar) | English instruction | answer (en) |
|---|---|---|---|---|---|---|
| 11 | `p011_last_letter` | language | اضغط على آخر حرف | `char:word:4` | Tap the last letter | `char:word:2` |
| 12 | `p012_four_letters` | language | اضغط على الكلمة التي فيها أربعة حروف | `w_book` | Tap the word with four letters | `w_star` |
| 13 | `p013_right` | language | اضغط على يمين | `w_right` | Tap right | `w_right` |
| 14 | `p014_foreign_word` | language | اضغط على الكلمة التي ليست عربية | `w3` | Tap the word that is not English | `w3` |
| 15 | `p015_spell_it` | multi_step | اضغط حروف كلمة «باب» بالترتيب | `char:pool:1`→ `char:pool:0`→ `char:pool:2` | Tap the letters that spell DOG, in order | `char:pool:1`→ `char:pool:0`→ `char:pool:2` |
| 16 | `p016_palindrome` | language | اضغط على الكلمة التي تُقرأ نفسها من الجهتين | `w2` | Tap the word that reads the same both ways | `w2` |
| 17 | `p017_repeated_letter` | language | اضغط على الحرف الذي يتكرر | `char:word:0` (or `char:word:4`) | Tap the letter that repeats | `char:word:1` (or `char:word:2`) |
| 18 | `p018_word_in_word` | language | اضغط على الكلمة التي تخبّئ كلمة أخرى بداخلها | `w2` | Tap the word hiding another word inside it | `w2` |
| 19 | `p019_second_from_left` | language | اضغط على الحرف الثاني من اليسار | `char:word:3` | Tap the second letter from the left | `char:word:1` |
| 20 | `p020_count_the_instruction` | meta | كم كلمة في هذه الجملة؟ | `n5` | How many words are in this sentence? | `n7` |

## Chapter 3 — What You Remember · ما تتذكره

| # | id | type | Arabic instruction | answer (ar) | English instruction | answer (en) |
|---|---|---|---|---|---|---|
| 21 | `p021_where_was_red` | memory | احفظ الألوان، ثم اضغط مكان الأحمر | `z_tr` | Memorise the colours, then tap where red was | `z_tr` |
| 22 | `p022_red_again` | meta | اضغط على الأحمر | `c_red` | Tap the red one | `c_red` |
| 23 | `p023_order_from_memory` | memory | احفظ الأرقام، ثم اضغط الأشكال بترتيبها | `s_c`→ `s_a`→ `s_b` | Memorise the numbers, then tap the shapes in that order | `s_c`→ `s_a`→ `s_b` |
| 24 | `p024_not_the_red` | reverse | لا تضغط على الأحمر | `board` | Do not tap the red one | `board` |
| 25 | `p025_missing_colour` | memory | احفظ الألوان، ثم اضغط على اللون الذي لم يظهر | `b_red` | Memorise the colours, then tap the one that did not appear | `b_red` |
| 26 | `p026_count_flashes` | memory | كم مرة ومض المربع؟ | `n3` | How many times did the square flash? | `n3` |
| 27 | `p027_descending` | reverse | اضغط الأرقام بترتيب تنازلي | `n4`→ `n3`→ `n2`→ `n1` | Tap the numbers in descending order | `n4`→ `n3`→ `n2`→ `n1` |
| 28 | `p028_truth_this_time` | meta | كل تعليمة في هذه المرحلة صادقة. اضغط على المربع. | `square` | Every instruction in this stage is true. Tap the square. | `square` |
| 29 | `p029_do_the_opposite` | reverse | افعل عكس التعليمة التالية | `small` | Do the opposite of the instruction below | `small` |
| 30 | `p030_who_came_first` | memory | اضغط على الشكل الذي ظهر أولًا | `s_mid` | Tap the shape that appeared first | `s_mid` |

## Chapter 4 — What You Touch · ما تلمسه

| # | id | type | Arabic instruction | answer (ar) | English instruction | answer (en) |
|---|---|---|---|---|---|---|
| 31 | `p031_stage_number` | ui | اضغط على رقم هذه المرحلة | `ui:stage` | Tap this stage's number | `ui:stage` |
| 32 | `p032_hold` | timing | اضغط مع الاستمرار | hold `pad` 2.0s | Press and hold | hold `pad` 2.0s |
| 33 | `p033_wait_three` | timing | اضغط على الزر بعد ثلاث ثوانٍ | `b` (after 3s) | Tap the button after three seconds | `b` (after 3s) |
| 34 | `p034_before_it_goes` | timing | اضغط عليه قبل أن يختفي | `b` (before 1.8s) | Tap it before it disappears | `b` (before 1.8s) |
| 35 | `p035_hint_button` | ui | الحل ليس داخل اللغز | `ui:hint` | The answer is not inside the puzzle | `ui:hint` |
| 36 | `p036_background` | ui | اضغط على ما بين الأشكال | `board` | Tap what is between the shapes | `board` |
| 37 | `p037_odd_one_fast` | visual | اضغط على الشكل المختلف قبل انتهاء الوقت | `d` | Tap the odd one out before time runs out | `d` |
| 38 | `p038_skip_is_the_answer` | ui | لا يمكنك حل هذه المرحلة | `ui:skip` | You cannot solve this stage | `ui:skip` |
| 39 | `p039_window` | timing | اضغط والعدّاد بين ٧ و ٥ | `b` (after 3s, before 5.0s) | Tap while the counter reads between 7 and 5 | `b` (after 3s, before 5.0s) |
| 40 | `p040_leaving` | ui | الخروج هو الحل | `ui:back` | Leaving is the answer | `ui:back` |

## Chapter 5 — What You Believe · ما تصدّقه

| # | id | type | Arabic instruction | answer (ar) | English instruction | answer (en) |
|---|---|---|---|---|---|---|
| 41 | `p041_no_trick` | meta | لا توجد خدعة هنا. اضغط على الدائرة. | `circle` | There is no trick here. Tap the circle. | `circle` |
| 42 | `p042_lying_hint` | meta | اضغط على الشكل الذي لا يذكره التلميح | `tr_red` | Tap the shape the hints do not mention | `tr_red` |
| 43 | `p043_size_is_all` | meta | اضغط على أكبر رقم | `b` | Tap the biggest number | `b` |
| 44 | `p044_instruction_changes` | meta | اضغط على الدائرة | `update` | Tap the circle | `update` |
| 45 | `p045_two_answers` | meta | هناك حلّان صحيحان. اختر أيّهما شئت. | `t1` (or `t2`) | There are two correct answers. Pick either. | `t1` (or `t2`) |
| 46 | `p046_double_negative` | meta | لا تفعل عكس التعليمة التالية | `square` | Do not do the opposite of the instruction below | `square` |
| 47 | `p047_everything_you_learned` | multi_step | أول حرف، ثم الشكل الأحمر، ثم رقم المرحلة | `char:word:0`→ `s_red`→ `ui:stage` | First letter, then the red shape, then the stage number | `char:word:0`→ `s_red`→ `ui:stage` |
| 48 | `p048_the_timer` | ui | الوقت ينفد. والوقت هو الحل. | `ui:timer` | Time is running out. Time is the answer. | `ui:timer` |
| 49 | `p049_impostor` | meta | اضغط على الدائرة | `circle` | Tap the circle | `circle` |
| 50 | `p050_no_solution` | meta | هذه آخر مرحلة. لا يوجد حل. | wait 6s, touching nothing | This is the last stage. There is no solution. | wait 6s, touching nothing |

## Daily pool

Selected deterministically from the date, so every device agrees offline.

| id | type | Arabic instruction | answer (ar) | English instruction | answer (en) |
|---|---|---|---|---|---|
| `d01_no_middle` | position | اضغط على الشكل الأوسط | `board` | Tap the middle shape | `board` |
| `d02_still_one` | visual | اضغط على الشكل الساكن | `c` | Tap the shape that is not moving | `c` |
| `d03_shared_letter` | language | اضغط على الحرف الموجود في الكلمتين | `char:word:1` | Tap the letter that is in both words | `char:word:3` |
| `d04_hold_smallest` | timing | اضغط مع الاستمرار على أصغر دائرة | hold `small` 1.8s | Press and hold the smallest circle | hold `small` 1.8s |
| `d05_sides_match` | semantic | اضغط على الشكل الذي عدد أضلاعه مثل عدد حروف كلمة «شمس» | `tri` | Tap the shape with as many sides as the word FOUR has letters | `sq` (or `di`) |
| `d06_second_largest` | visual | اضغط على ثاني أكبر مربع | `s3` | Tap the second largest square | `s3` |
| `d07_vanished` | memory | اضغط على مكان الشكل الذي اختفى | `z_b` | Tap where the shape that vanished used to be | `z_b` |
| `d08_last_word` | semantic | اضغط على اللون الذي تنتهي به هذه الجملة: الأخضر | `c_green` | Tap the colour this sentence ends with: green | `c_green` |
| `d09_more_than_shown` | visual | كم دائرة على هذه الشاشة؟ | `n4` | How many circles are on this screen? | `n4` |
| `d10_patience` | reverse | الزر الصحيح سيظهر بعد قليل | `real` | The right button will appear shortly | `real` |

## Coverage

- Campaign stages: **50**
- Daily puzzles: **10**
- Stages whose answer resolves to a **different target**: **5** (stages 11, 12, 17, 19, 20)
- Stages where the **thing you must tap is different** in the two languages (a different target, or the same slot holding a different word): **19** (stages 1, 4, 7, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 25, 33, 34, 39, 44, 47)
