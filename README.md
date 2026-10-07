# NovaPiperTTS

Офлайн-движок синтеза речи для Android на голосах [Piper](https://github.com/OHF-voice/piper1-gpl)
и библиотеке [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx).

- Работает как системный движок синтеза речи: подходит для любых читалок и приложений специальных возможностей.
- Каждый голос Piper регистрируется в Android отдельным голосом, в списке есть значки ♀/♂.
- Голоса скачиваются в приложении по требованию (загрузка в фоне, с докачкой и отменой).
- Данные eSpeak NG встроены в приложение, для синтеза интернет не нужен.
- Архитектуры: arm64-v8a, armeabi-v7a, x86, x86_64 (в том числе для Waydroid).
- Android 8.0+ (minSdk 26), поддерживаются Android TV приставки.
## Скриншоты 
<img width="1074" height="2330" alt="photo_2026-10-07_13-25-45" src="https://github.com/user-attachments/assets/c4103704-e6ee-47f7-b519-5764bfec3dfe" />

<img width="1080" height="2311" alt="photo_2026-10-07_13-25-34" src="https://github.com/user-attachments/assets/2bce5046-f2a5-4c2d-a025-f88c17c9bb9c" />


## Лицензии
В приложение встроены данные eSpeak NG (GPL-3.0-or-later); sherpa-onnx распространяется под Apache-2.0.
У каждого голоса Piper своя лицензия, перед распространением проверьте карточку модели.
