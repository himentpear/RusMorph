false = False
true = True
null = None
# -*- coding: utf-8 -*-
import json
import re
import sys

# Data for Lesson 6
lesson_6_pages = [
    # Page 61 (printed 48)
    {
        "pdf_page": 61,
        "printed_page": 48,
        "lesson": 6,
        "sections": [
            {
                "type": "OTHER",
                "heading": "УРОК 6 第六课",
                "index_items": [
                    "语音",
                    "书写",
                    "语调",
                    "礼貌用语",
                    "语法",
                    "文化国情",
                    "言语训练"
                ]
            },
            {
                "type": "OTHER",
                "heading": "语音 Фонетика",
                "index_items": [
                    "软辅音[р'], [п'], [б'], [ф'], [в']"
                ]
            },
            {
                "type": "OTHER",
                "heading": "软辅音[р'], [п'], [б'], [ф'], [в']说明"
            }
        ],
        "unclear": [],
        "qa": {
            "ocr_status": "TRANSCRIBED",
            "requires_review": False
        }
    },
    # Page 62 (printed 49)
    {
        "pdf_page": 62,
        "printed_page": 49,
        "lesson": 6,
        "sections": [
            {
                "type": "EXERCISE",
                "exercise_number": 1,
                "instruction": "听录音，模仿，注意口形。",
                "items": [
                    {
                        "number": None,
                        "text": "ра – ря    ря – рё – рю – ре – ри"
                    },
                    {
                        "number": None,
                        "text": "ру – рю    рья – рьё – рью – рье – рьи"
                    },
                    {
                        "number": None,
                        "text": "ро – рё    арь – ерь"
                    },
                    {
                        "number": None,
                        "text": "ры – ри"
                    },
                    {
                        "number": None,
                        "text": "рэ – ре"
                    }
                ],
                "example": None,
                "raw_text": "Упражнение 1 听录音，模仿，注意口形。\nра – ря    ря – рё – рю – ре – ри\nру – рю    рья – рьё – рью – рье – рьи\nро – рё    арь – ерь\nры – ри\nрэ – ре",
                "status": "CONFIDENT"
            },
            {
                "type": "EXERCISE",
                "exercise_number": 2,
                "instruction": "用调型1与调型3朗读单词。",
                "items": [
                    {
                        "number": None,
                        "text": "слова́рь, словарём, рис, а́дрес, мо́ре, моря́, вре́мя, река́, среда́, говори́ть, смотре́ть, три, рю́мка"
                    }
                ],
                "example": None,
                "raw_text": "Упражнение 2 用调型1与调型3朗读单词。\nслова́рь, словарём, рис, а́дрес, мо́ре, моря́, вре́мя, река́, среда́, говори́ть, смотре́ть, три, рю́мка",
                "status": "CONFIDENT"
            },
            {
                "type": "OTHER",
                "heading": "辅音[п'], [б'], [ф'], [в']说明"
            },
            {
                "type": "EXERCISE",
                "exercise_number": 3,
                "instruction": "听录音，模仿，注意口形。",
                "items": [
                    {
                        "number": None,
                        "text": "пя – пё – пю – пе – пи    па – пя    по – пё    пу – пю    пэ – пе"
                    },
                    {
                        "number": None,
                        "text": "п – пь    пья – пьё – пью – пье – пьи"
                    },
                    {
                        "number": None,
                        "text": "бя – бё – бю – бе – би    ба – бя    бо – бё    бу – бю    бэ – бе"
                    },
                    {
                        "number": None,
                        "text": "б – бь    бья – бьё – бью – бье – бьи"
                    },
                    {
                        "number": None,
                        "text": "фя – фё – фю – фе – фи    ф – фь    фья – фьё – фью – фье – фьи"
                    },
                    {
                        "number": None,
                        "text": "вя – вё – вю – ве – ви    в – вь    вья – вьё – вью – вье – вьи"
                    },
                    {
                        "number": None,
                        "text": "вя – фя    вё – фё    вю – фю    ве – фе    ви – фи"
                    }
                ],
                "example": None,
                "raw_text": "Упражнение 3 听录音，模仿，注意口形。\nпя – пё – пю – пе – пи    па – пя    по – пё    пу – пю    пэ – пе\nп – пь    пья – пьё – пью – пье – пьи\nбя – бё – бю – бе – би    ба – бя    бо – бё    бу – бю    бэ – бе\nб – бь    бья – бьё – бью – бье – бьи\nфя – фё – фю – фе – фи    ф – фь    фья – фьё – фью – фье – фьи\nвя – вё – вю – ве – ви    в – вь    вья – вьё – вью – вье – вьи\nвя – фя    вё – фё    вю – фю    ве – фе    ви – фи",
                "status": "CONFIDENT"
            },
            {
                "type": "EXERCISE",
                "exercise_number": 4,
                "instruction": "用调型1与调型3朗读单词。",
                "items": [
                    {
                        "number": None,
                        "text": "пи́во, проспе́кт, переры́в, Пётр, пять, компью́тер, Фёдор, шофёр, буфе́т, конфе́та, о́бувь, обе́д, тебя́, обе́дать, обе́дают, Ве́ра, ве́чером, четве́рг, весна́, весно́й, Ви́ктор, Ви́тя, живём, живёт, сего́дня = се[во]дня"
                    }
                ],
                "example": None,
                "raw_text": "Упражнение 4 用调型1与调型3朗读单词。\nпи́во, проспе́кт, переры́в, Пётр, пять, компью́тер, Фёдор, шофёр, буфе́т, конфе́та, о́бувь, обе́д, тебя́, обе́дать, обе́дают, Ве́ра, ве́чером, четве́рг, весна́, весно́й, Ви́ктор, Ви́тя, живём, живёт, сего́дня = се[во]дня",
                "status": "CONFIDENT"
            },
            {
                "type": "EXERCISE",
                "exercise_number": 5,
                "instruction": "听录音，模仿。",
                "items": [
                    {
                        "number": None,
                        "text": "– Сего́дня среда́(суббо́та)?\n– Да.\n– Среда́(суббо́та)?\n– Да, да, сего́дня среда́(суббо́та)."
                    },
                    {
                        "number": None,
                        "text": "– Сего́дня ?\n– Да, сего́дня.\n– Сего́дня ве́чером?\n– Да, сего́дня ве́чером."
                    }
                ],
                "example": None,
                "raw_text": "Упражнение 5 听录音，模仿。\n– Сего́дня среда́(суббо́та)?    – Сего́дня ?\n– Да.    – Да, сего́дня.\n– Среда́(суббо́та)?    – Сего́дня ве́чером?\n– Да, да, сего́дня среда́(суббо́та).    – Да, сего́дня ве́чером.",
                "status": "CONFIDENT"
            }
        ],
        "unclear": [],
        "qa": {
            "ocr_status": "TRANSCRIBED",
            "requires_review": False
        }
    },
    # Page 63 (printed 50)
    {
        "pdf_page": 63,
        "printed_page": 50,
        "lesson": 6,
        "sections": [
            {
                "type": "EXERCISE",
                "exercise_number": 5,
                "instruction": "听录音，模仿。",
                "items": [
                    {
                        "number": None,
                        "text": "– Са́ша, что ты де́лаешь?\n– Я обе́даю.\n– Где ты обе́даешь?\n– Я обе́даю в буфе́те(в рестора́не).\nА моя́ жена́ обе́дает до́ма."
                    },
                    {
                        "number": None,
                        "text": "– Са́ша, где ты сейча́с живёшь?\n– Я живу́ в Москве́.\n– А твой брат Ви́тя? Где он живёт?\n– Он живёт в Са́нкт-Петербу́рге."
                    },
                    {
                        "number": None,
                        "text": "– Анна, что ты де́лаешь?\n– Я смотрю́ футбо́л.\n– Кто игра́ет?\n– На́ши и ру́сские."
                    }
                ],
                "example": None,
                "raw_text": "– Са́ша, что ты де́лаешь?\n– Я обе́даю.\n– Где ты обе́даешь?\n– Я обе́даю в буфе́те(в рестора́не).\nА моя́ жена́ обе́дает до́ма.\n\n– Са́ша, где ты сейча́с живёшь?\n– Я живу́ в Москве́.\n– А твой брат Ви́тя? Где он живёт?\n– Он живёт в Са́нкт-Петербу́рге.\n\n– Анна, что ты де́лаешь?\n– Я смотрю́ футбо́л.\n– Кто игра́ет?\n– На́ши и ру́сские.",
                "status": "CONFIDENT"
            },
            {
                "type": "OTHER",
                "heading": "语调 ИНТОНАЦИЯ / 调型4 А тебя́?"
            },
            {
                "type": "EXERCISE",
                "exercise_number": 6,
                "instruction": "听录音，跟读，体会调心上音调的变化。",
                "items": [
                    {
                        "number": "1",
                        "text": "А тебя́? А меня́? А вас? А нас? А его́? А её? А их?"
                    },
                    {
                        "number": "2",
                        "text": "А тебя́ как? А вас как? А его́ как? А её как?"
                    },
                    {
                        "number": "3",
                        "text": "А тебя́ как зову́т? А вас как зову́т? А его́ как зову́т? А её как зову́т? А их как зову́т?"
                    }
                ],
                "example": None,
                "raw_text": "Упражнение 6 听录音，跟读，体会调心上音调的变化。\n(1) А тебя́? А меня́? А вас? А нас? А его́? А её? А их?\n(2) А тебя́ как? А вас как? А его́ как? А её как?\n(3) А тебя́ как зову́т? А вас как зову́т? А его́ как зову́т? А её как зову́т? А их как зову́т?",
                "status": "CONFIDENT"
            },
            {
                "type": "EXERCISE",
                "exercise_number": 7,
                "instruction": "听录音，读对话，体会调心上音调的变化。然后做替换练习。",
                "items": [
                    {
                        "number": None,
                        "text": "– Кто э́то?\n– Это Ни́на.\n– А э́то кто?\n– Это Анто́н."
                    },
                    {
                        "number": None,
                        "text": "– Что они́ де́лают?\n– Они́ чита́ют уче́бник.\n– А где они́ чита́ют?\n– В ко́мнате."
                    },
                    {
                        "number": None,
                        "text": "– Что э́то?\n– Это стол.\n– А э́то что?\n– Это стул."
                    },
                    {
                        "number": None,
                        "text": "Слова́ для заме́ны:\n(Оле́г, Ми́ша, Анна, Ира, Фома́, Эмма, Ка́тя, Са́ша, Ма́ша, муж, жена́...)\n(шкаф, окно́, слова́рь, кни́га, сад, парк, плащ, дом, общежи́тие ...)"
                    }
                ],
                "example": None,
                "raw_text": "Упражнение 7 听录音，读对话，体会调心上音调的变化。然后做替换练习。\n– Кто э́то?    – Что они́ де́лают?    – Что э́то?\n– Это Ни́на.    – Они́ чита́ют уче́бник.    – Это стол.\n– А э́то кто?    – А где они́ чита́ют?    – А э́то что?\n– Это Анто́н.    – В ко́мнате.    – Это стул.\nСлова́ для заме́ны:\n(Оле́г, Ми́ша, Анна, Ира, Фома́, Эмма, Ка́тя, Са́ша, Ма́ша, муж, жена́...)\n(шкаф, окно́, слова́рь, кни́га, сад, парк, плащ, дом, общежи́тие ...)",
                "status": "CONFIDENT"
            }
        ],
        "unclear": [],
        "qa": {
            "ocr_status": "TRANSCRIBED",
            "requires_review": False
        }
    },
    # Page 64 (printed 51)
    {
        "pdf_page": 64,
        "printed_page": 51,
        "lesson": 6,
        "sections": [
            {
                "type": "OTHER",
                "heading": "语法 ГРАММАТИКА",
                "index_items": [
                    "1. 名词的性(2)",
                    "2. 名词的数(3)",
                    "3. 名词单数第六格",
                    "4. 人称代词第六格",
                    "5. 动词第二变位法"
                ]
            },
            {
                "type": "OTHER",
                "heading": "1 名词的性(2)"
            },
            {
                "type": "EXERCISE",
                "exercise_number": 8,
                "instruction": "将下列名词按阴性、阳性、中性分类。",
                "items": [
                    {
                        "number": None,
                        "text": "пи́во, проспе́кт, переры́в, компью́тер, шофёр, буфе́т, конфе́та, о́бувь, четве́рг, весна́, я́блоко, общежи́тие, слова́рь, тетра́дь, общежи́тие, вре́мя"
                    }
                ],
                "example": None,
                "raw_text": "Упражнение 8 将下列名词按阴性、阳性、中性分类。\nпи́во, проспе́кт, переры́в, компью́тер, шофёр, буфе́т, конфе́та, о́бувь, четве́рг, весна́, я́блоко, общежи́тие, слова́рь, тетра́дь, общежи́тие, вре́мя",
                "status": "CONFIDENT"
            },
            {
                "type": "OTHER",
                "heading": "2 名词的数(3)"
            },
            {
                "type": "EXERCISE",
                "exercise_number": 9,
                "instruction": "写出下列名词的复数形式。",
                "items": [
                    {
                        "number": None,
                        "text": "проспе́кт, компью́тер, буфе́т, конфе́та, обе́д, по́чта, ру́чка, до́чка, река́, кни́га, сад, парк"
                    }
                ],
                "example": None,
                "raw_text": "Упражнение 9 写出下列名词的复数形式。\nпроспе́кт, компью́тер, буфе́т, конфе́та, обе́д, по́чта, ру́чка, до́чка, река́, кни́га, сад, парк",
                "status": "CONFIDENT"
            }
        ],
        "unclear": [],
        "qa": {
            "ocr_status": "TRANSCRIBED",
            "requires_review": False
        }
    },
    # Page 65 (printed 52)
    {
        "pdf_page": 65,
        "printed_page": 52,
        "lesson": 6,
        "sections": [
            {
                "type": "OTHER",
                "heading": "3 名词单数第六格"
            },
            {
                "type": "EXERCISE",
                "exercise_number": 10,
                "instruction": "写出下列名词的第六格形式。",
                "items": [
                    {
                        "number": None,
                        "text": "проспе́кт, Пётр, компью́тер, Фёдор, шофёр, буфе́т, заво́д, фильм, университе́т, весна́, сайт, Интерне́т, дом, сад, парк"
                    }
                ],
                "example": None,
                "raw_text": "Упражнение 10 写出下列名词的第六格形式。\nпроспе́кт, Пётр, компью́тер, Фёдор, шофёр, буфе́т, заво́д, фильм, университе́т, весна́, сайт, Интерне́т, дом, сад, парк",
                "status": "CONFIDENT"
            },
            {
                "type": "EXERCISE",
                "exercise_number": 11,
                "instruction": "听录音，模仿，体会语音语调。",
                "items": [
                    {
                        "number": None,
                        "text": "– Где вы рабо́таете?\n– Я рабо́таю в институ́те."
                    },
                    {
                        "number": None,
                        "text": "– Где ты у́чишься?\n– Я учу́сь в университе́те."
                    },
                    {
                        "number": None,
                        "text": "Слова́ для заме́ны:\n(на заво́де, в шко́ле, на фи́рме, в кафе́, в Росси́и, в Москве́... )"
                    }
                ],
                "example": None,
                "raw_text": "Упражнение 11 听录音，模仿，体会语音语调。\n– Где вы рабо́таете?    – Где ты у́чишься?\n– Я рабо́таю в институ́те.    – Я учу́сь в университе́те.\nСлова́ для заме́ны:\n(на заво́де, в шко́ле, на фи́рме, в кафе́, в Росси́и, в Москве́... )",
                "status": "CONFIDENT"
            },
            {
                "type": "OTHER",
                "heading": "4 人称代词第六格"
            },
            {
                "type": "EXERCISE",
                "exercise_number": 12,
                "instruction": "听录音，模仿，体会语音语调。",
                "items": [
                    {
                        "number": None,
                        "text": "Вы зна́ете обо мне."
                    },
                    {
                        "number": None,
                        "text": "Это Москва́. О ней мы ча́сто говори́м."
                    },
                    {
                        "number": None,
                        "text": "Ма́ма, э́то мой друзья́. О них ты зна́ешь."
                    }
                ],
                "example": None,
                "raw_text": "Упражнение 12 听录音，模仿，体会语音语调。\nВы зна́ете обо мне.\nЭто Москва́. О ней мы ча́сто говори́м.\nМа́ма, э́то мой друзья́. О них ты зна́ешь.",
                "status": "CONFIDENT"
            },
            {
                "type": "OTHER",
                "heading": "5 动词第二变位法"
            }
        ],
        "unclear": [],
        "qa": {
            "ocr_status": "TRANSCRIBED",
            "requires_review": False
        }
    },
    # Page 66 (printed 53)
    {
        "pdf_page": 66,
        "printed_page": 53,
        "lesson": 6,
        "sections": [
            {
                "type": "OTHER",
                "heading": "动词第二变位法说明"
            },
            {
                "type": "EXERCISE",
                "exercise_number": 13,
                "instruction": "听录音，模仿，体会语音语调，背诵动词变位。",
                "items": [
                    {
                        "number": "1",
                        "text": "– Что ты у́чишь?\n– Я учу́ слова́. А ты?\n– А я учу́ диало́г и текст."
                    },
                    {
                        "number": "2",
                        "text": "– Вы говори́те по-ру́сски?\n– Да, говорю́."
                    },
                    {
                        "number": "3",
                        "text": "– Ты рабо́таешь?\n– Нет, я учу́сь.\n– Где ты у́чишься?\n– В институ́те ру́сского языка́.\n– Как ты у́чишься?\n– Непло́хо ."
                    },
                    {
                        "number": "4",
                        "text": "– Что вы смо́трите?\n– Я смотрю́ телеви́зор."
                    },
                    {
                        "number": "5",
                        "text": "– Кото́рый час ?\n– Два часа́. (час, три часа́, четы́ре часа́, пять часо́в, шесть часо́в, семь часо́в, де́вять часо́в)"
                    },
                    {
                        "number": "6",
                        "text": "– Что вы хоти́те сейча́с де́лать?\n– Я хочу́ обе́дать."
                    }
                ],
                "example": None,
                "raw_text": "Упражнение13 听录音，模仿，体会语音语调，背诵动词变位。\n(1) – Что ты у́чишь?\n– Я учу́ слова́. А ты?\n– А я учу́ диало́г и текст.\n(2) – Вы говори́те по-ру́сски?\n– Да, говорю́.\n(3) – Ты рабо́таешь?\n– Нет, я учу́сь.\n– Где ты у́чишься?\n– В институ́те ру́сского языка́.\n– Как ты у́чишься?\n– Непло́хо .\n(4) – Что вы смо́трите?\n– Я смотрю́ телеви́зор.\n(5) – Кото́рый час ?\n– Два часа́. (час, три часа́, четы́ре часа́, пять часо́в, шесть часо́в, семь часо́в, де́вять часо́в)\n(6) – Что вы хоти́те сейча́с де́лать?\n– Я хочу́ обе́дать.",
                "status": "CONFIDENT"
            },
            {
                "type": "OTHER",
                "heading": "言语训练 РАЗВИТИЕ РЕЧИ",
                "index_items": [
                    "1. 句型",
                    "2. 对话",
                    "3. 课文",
                    "4. 生词"
                ]
            },
            {
                "type": "OTHER",
                "heading": "1 句型 Речевые образцы"
            },
            {
                "type": "DIALOGUE",
                "heading": "2 对话 Диалог",
                "turns": [
                    {
                        "speaker": None,
                        "text": "– Здра́вствуй, Ни́на!"
                    },
                    {
                        "speaker": None,
                        "text": "– Здра́вствуй, Ира! Кто э́то?"
                    },
                    {
                        "speaker": None,
                        "text": "– Это мой брат."
                    },
                    {
                        "speaker": None,
                        "text": "– Как его́ зову́т?"
                    },
                    {
                        "speaker": None,
                        "text": "– Его́ зову́т Анто́н."
                    },
                    {
                        "speaker": None,
                        "text": "– Он рабо́тает и́ли у́чится?"
                    },
                    {
                        "speaker": None,
                        "text": "– Он у́чится в шко́ле."
                    }
                ],
                "raw_text": "2 对话 Диалог\n– Здра́вствуй, Ни́на!\n– Здра́вствуй, Ира! Кто э́то?\n– Это мой брат.\n– Как его́ зову́т?\n– Его́ зову́т Анто́н.\n– Он рабо́тает и́ли у́чится?\n– Он у́чится в шко́ле.",
                "status": "CONFIDENT"
            }
        ],
        "unclear": [],
        "qa": {
            "ocr_status": "TRANSCRIBED",
            "requires_review": False
        }
    },
    # Page 67 (printed 54)
    {
        "pdf_page": 67,
        "printed_page": 54,
        "lesson": 6,
        "sections": [
            {
                "type": "DIALOGUE",
                "heading": "2 对话 Диалог（续）",
                "turns": [
                    {
                        "speaker": None,
                        "text": "– Как он у́чится?"
                    },
                    {
                        "speaker": None,
                        "text": "– Хорошо́."
                    }
                ],
                "raw_text": "– Как он у́чится?\n– Хорошо́.",
                "status": "CONFIDENT"
            },
            {
                "type": "TEXT",
                "heading": "3 课文 Текст",
                "title": None,
                "paragraphs": [
                    "Мой брат Анто́н живёт в Москве́, у́чится в шко́ле. Он у́чится хорошо́. Анто́н хо́чет учи́ться в институ́те, поэ́тому он о́чень мно́го рабо́тает: мно́го чита́ет, мно́го пи́шет."
                ],
                "raw_text": "3 课文 Текст\nМой брат Анто́н живёт в Москве́, у́чится в шко́ле. Он у́чится хорошо́. Анто́н хо́чет учи́ться в институ́те, поэ́тому он о́чень мно́го рабо́тает: мно́го чита́ет, мно́го пи́шет.",
                "status": "CONFIDENT"
            },
            {
                "type": "EXERCISE",
                "exercise_number": 14,
                "instruction": "听录音，模仿，背诵对话及短文。",
                "items": [],
                "example": None,
                "raw_text": "Упражнение 14 听录音，模仿，背诵对话及短文。",
                "status": "CONFIDENT"
            },
            {
                "type": "EXERCISE",
                "exercise_number": 15,
                "instruction": "演练对话。",
                "items": [],
                "example": None,
                "raw_text": "Упражнение 15 演练对话。",
                "status": "CONFIDENT"
            },
            {
                "type": "EXERCISE",
                "exercise_number": 16,
                "instruction": "回答课文的问题。",
                "items": [
                    {
                        "number": "1",
                        "text": "Ваш брат рабо́тает и́ли у́чится?"
                    },
                    {
                        "number": "2",
                        "text": "Где он у́чится?"
                    },
                    {
                        "number": "3",
                        "text": "Как он у́чится?"
                    },
                    {
                        "number": "4",
                        "text": "Как его́ зову́т?"
                    },
                    {
                        "number": "5",
                        "text": "Почему́ он мно́го рабо́тает?"
                    }
                ],
                "example": None,
                "raw_text": "Упражнение 16 回答课文的问题。\n(1) Ваш брат рабо́тает и́ли у́чится?\n(2) Где он у́чится?\n(3) Как он у́чится?\n(4) Как его́ зову́т?\n(5) Почему́ он мно́го рабо́тает?",
                "status": "CONFIDENT"
            },
            {
                "type": "EXERCISE",
                "exercise_number": 17,
                "instruction": "用单数第一人称转述课文内容。",
                "items": [],
                "example": None,
                "raw_text": "Упражнение 17 用单数第一人称转述课文内容。",
                "status": "CONFIDENT"
            },
            {
                "type": "EXERCISE",
                "exercise_number": 18,
                "instruction": "听录音，读句子，体会动词хоте́ть的用法。",
                "items": [
                    {
                        "number": None,
                        "text": "Я хочу́ учи́ться. Вы хоти́те рабо́тать? Он не хо́чет отдыха́ть. Я не хочу́ чита́ть. Мы хоти́м обе́дать. Они́ хотя́т игра́ть в футбо́л. Где вы хоти́те жить?"
                    }
                ],
                "example": None,
                "raw_text": "Упражнение 18 听录音，读句子，体会动词хоте́ть的用法。\nЯ хочу́ учи́ться. Вы хоти́те рабо́тать? Он не хо́чет отдыха́ть. Я не хочу́ чита́ть. Мы хоти́м обе́дать. Они́ хотя́т игра́ть в футбо́л. Где вы хоти́те жить?",
                "status": "CONFIDENT"
            },
            {
                "type": "EXERCISE",
                "exercise_number": 19,
                "instruction": "听录音，读句子，体会动词жить的用法。",
                "items": [
                    {
                        "number": None,
                        "text": "Я живу́ в Москве́. Ты живёшь в Петербу́рге. Он (она́) живёт до́ма. Мы живём в институ́те. Вы живёте на мо́ре. Они́ живу́т в го́роде. Где вы живёте?"
                    }
                ],
                "example": None,
                "raw_text": "Упражнение 19 听录音，读句子，体会动词жить的用法。\nЯ живу́ в Москве́. Ты живёшь в Петербу́рге. Он (она́) живёт до́ма. Мы живём в институ́те. Вы живёте на мо́ре. Они́ живу́т в го́роде. Где вы живёте?",
                "status": "CONFIDENT"
            },
            {
                "type": "EXERCISE",
                "exercise_number": 20,
                "instruction": "听录音，读句子，体会连接词и́ли的用法。",
                "items": [
                    {
                        "number": "1",
                        "text": "Он у́чится и́ли рабо́тает?"
                    },
                    {
                        "number": "2",
                        "text": "Она́ живёт в Москве́ и́ли в Баку́?"
                    },
                    {
                        "number": "3",
                        "text": "Ты у́чишься в шко́ле и́ли в институ́те?"
                    },
                    {
                        "number": "4",
                        "text": "Это слова́рь и́ли уче́бник?"
                    },
                    {
                        "number": "5",
                        "text": "Сего́дня и́ли за́втра?"
                    },
                    {
                        "number": "6",
                        "text": "Это ваш сын и́ли его́?"
                    }
                ],
                "example": None,
                "raw_text": "Упражнение 20 听录音，读句子，体会连接词и́ли的用法。\n(1) Он у́чится и́ли рабо́тает?\n(2) Она́ живёт в Москве́ и́ли в Баку́?\n(3) Ты у́чишься в шко́ле и́ли в институ́те?\n(4) Это слова́рь и́ли уче́бник?\n(5) Сего́дня и́ли за́втра?\n(6) Это ваш сын и́ли его́?",
                "status": "CONFIDENT"
            }
        ],
        "unclear": [],
        "qa": {
            "ocr_status": "TRANSCRIBED",
            "requires_review": False
        }
    },
    # Page 68 (printed 55)
    {
        "pdf_page": 68,
        "printed_page": 55,
        "lesson": 6,
        "sections": [
            {
                "type": "EXERCISE",
                "exercise_number": 21,
                "instruction": "将下列句子译成俄语。",
                "items": [
                    {
                        "number": "1",
                        "text": "“你在做什么？”\n“我在背单词。”"
                    },
                    {
                        "number": "2",
                        "text": "你在上学还是工作？"
                    },
                    {
                        "number": "3",
                        "text": "安东在工厂工作。"
                    },
                    {
                        "number": "4",
                        "text": "我想在大学学习。"
                    }
                ],
                "example": None,
                "raw_text": "Упражнение 21 将下列句子译成俄语。\n(1) “你在做什么？”\n“我在背单词。”\n(2) 你在上学还是工作？\n(3) 安东在工厂工作。\n(4) 我想在大学学习。",
                "status": "CONFIDENT"
            },
            {
                "type": "OTHER",
                "heading": "4 生词 Новые слова"
            }
        ],
        "unclear": [],
        "qa": {
            "ocr_status": "TRANSCRIBED",
            "requires_review": False
        }
    },
    # Page 69 (printed 56)
    {
        "pdf_page": 69,
        "printed_page": 56,
        "lesson": 6,
        "sections": [
            {
                "type": "OTHER",
                "heading": "书写 ПИСЬМО"
            },
            {
                "type": "EXERCISE",
                "exercise_number": 22,
                "instruction": "按照范例抄写下列单词。",
                "items": [
                    {
                        "number": None,
                        "text": "хорошо тебя живут класс"
                    }
                ],
                "example": None,
                "raw_text": "Упражнение 22 按照范例抄写下列单词。\nхорошо тебя живут класс",
                "status": "CONFIDENT"
            },
            {
                "type": "EXERCISE",
                "exercise_number": 23,
                "instruction": "填空并标重音。",
                "items": [
                    {
                        "number": None,
                        "text": "учи́ть: я □, ты □, он(она́) □, мы □, вы □, они́ □"
                    },
                    {
                        "number": None,
                        "text": "учи́ться: я □, ты □, он(она́) □, мы □, вы □, они́ □"
                    },
                    {
                        "number": None,
                        "text": "говори́ть: я □, ты □, он(она́) □, мы □, вы □, они́ □"
                    },
                    {
                        "number": None,
                        "text": "смотре́ть: я □, ты □, он(она́) □, мы □, вы □, они́ □"
                    },
                    {
                        "number": None,
                        "text": "жить: я □, ты □, он(она́) □, мы □, вы □, они́ □"
                    }
                ],
                "example": None,
                "raw_text": "Упражнение 23 填空并标重音。\nучи́ть: я □, ты □, он(она́) □, мы □, вы □, они́ □\nучи́ться: я □, ты □, он(она́) □, мы □, вы □, они́ □\nговори́ть: я □, ты □, он(она́) □, мы □, вы □, они́ □\nсмотре́ть: я □, ты □, он(она́) □, мы □, вы □, они́ □\nжить: я □, ты □, он(она́) □, мы □, вы □, они́ □",
                "status": "CONFIDENT"
            },
            {
                "type": "EXERCISE",
                "exercise_number": 24,
                "instruction": "填空并标重音。",
                "items": [
                    {
                        "number": None,
                        "text": "Как он у́чится? □, □, □ (加副词)"
                    },
                    {
                        "number": None,
                        "text": "Я смотрю́ (телеви́зор). □, □, □ (加补语)"
                    },
                    {
                        "number": None,
                        "text": "Я учу́ (слова́). □, □, □ (加补语)"
                    },
                    {
                        "number": None,
                        "text": "Я хочу́ (рабо́тать). □, □, □ (加动词)"
                    }
                ],
                "example": None,
                "raw_text": "Упражнение 24 填空并标重音。\nКак он у́чится? □, □, □ (加副词)\nЯ смотрю́ (телеви́зор). □, □, □ (加补语)\nЯ учу́ (слова́). □, □, □ (加补语)\nЯ хочу́ (рабо́тать). □, □, □ (加动词)",
                "status": "CONFIDENT"
            },
            {
                "type": "OTHER",
                "heading": "礼貌用语 Речевой этикет"
            }
        ],
        "unclear": [],
        "qa": {
            "ocr_status": "TRANSCRIBED",
            "requires_review": False
        }
    },
    # Page 70 (printed 57)
    {
        "pdf_page": 70,
        "printed_page": 57,
        "lesson": 6,
        "sections": [
            {
                "type": "OTHER",
                "heading": "文化国情 О России"
            }
        ],
        "unclear": [],
        "qa": {
            "ocr_status": "TRANSCRIBED",
            "requires_review": False
        }
    }
]

# Validation
print(f"Total pages: {len(lesson_6_pages)}")
assert len(lesson_6_pages) == 10

# Check Cyrillic vs Latin mixups
mixed_pattern = re.compile(r'([\u0400-\u04FF][a-zA-Z]|[a-zA-Z][\u0400-\u04FF])')

def check_text(txt, loc):
    if not isinstance(txt, str):
        return
    matches = mixed_pattern.findall(txt)
    if matches:
        print(f"WARNING: Mixed Cyrillic/Latin found at {loc}: {matches} in '{txt}'")

for p in lesson_6_pages:
    p_num = p['printed_page']
    for s_idx, s in enumerate(p.get('sections', [])):
        loc = f"p.{p_num} s.{s_idx} ({s.get('type')})"
        for k, v in s.items():
            if isinstance(v, str):
                check_text(v, f"{loc}.{k}")
            elif isinstance(v, list):
                for i_idx, item in enumerate(v):
                    if isinstance(item, str):
                        check_text(item, f"{loc}.{k}[{i_idx}]")
                    elif isinstance(item, dict):
                        for ik, iv in item.items():
                            if isinstance(iv, str):
                                check_text(iv, f"{loc}.{k}[{i_idx}].{ik}")

# Check all exercise numbers
ex_nums = []
for p in lesson_6_pages:
    for s in p.get('sections', []):
        if s.get('type') == 'EXERCISE':
            ex_nums.append((p['printed_page'], s.get('exercise_number')))

print("Exercises found:", ex_nums)

# Save to destination
dest_path = r"C:\Users\jisub\Documents\RusMorph\data-source\transcriptions\urok_06.json"
with open(dest_path, 'w', encoding='utf-8') as f:
    json.dump(lesson_6_pages, f, ensure_ascii=False, indent=2)

print(f"Successfully saved to {dest_path}")
