# Project setup

The repository intentionally does not contain the large sherpa-onnx AAR or
the shared eSpeak NG phoneme data.

After cloning the project, run:

```bash
python scripts/setup.py
```

The script:

1. Finds the latest official sherpa-onnx release.
2. Downloads its Android AAR.
3. Removes older sherpa-onnx AARs.
4. Writes the selected version to `sherpa-onnx.version`.
5. Downloads the current shared `espeak-ng-data`.
6. Installs the phoneme data into `app/src/main/assets/espeak-ng-data/`.

Gradle reads `sherpa-onnx.version`, so `app/build.gradle.kts` does not need
to be edited when a new sherpa-onnx version is released.

After setup, simply open the project in Android Studio and build it.
