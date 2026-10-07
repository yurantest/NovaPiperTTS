# sherpa-onnx JNI entry points are loaded by the AAR itself.
# Keep the public Kotlin/Java API classes used by NovaPiperTTS.
-keep class com.k2fsa.sherpa.onnx.** { *; }
