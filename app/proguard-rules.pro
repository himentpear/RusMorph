# The bundled database JSON is read by Gson reflection. R8 must preserve the
# importer DTO field names, including fields used only by nested JSON objects.
-keep class org.namchieh.rusmorph.data.local.Asset* { *; }
-keepattributes Signature,InnerClasses,EnclosingMethod
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken { *; }

