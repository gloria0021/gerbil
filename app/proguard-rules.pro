# ML Kit GenAI（Gemini Nano）は beta で、部品（ComponentRegistrar）をマニフェストに書いたクラス名から探し、
# 内部の protobuf をリフレクションで読む。R8 で名前が変わったり消えたりすると、Generation.getClient() で
# 依存が見つからず NullPointerException で落ちるため、ML Kit と部品の登録の仕組みをまとめて残す。
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_** { *; }
-keep class * implements com.google.firebase.components.ComponentRegistrar { *; }
-keep class com.google.firebase.components.** { *; }
