# The bundled database JSON is read by Gson reflection. R8 must preserve the
# importer DTO field names, including fields used only by nested JSON objects.
-keep class org.namchieh.rusmorph.data.local.Asset* { *; }
-keepattributes Signature,InnerClasses,EnclosingMethod
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken { *; }

# The tutor request, correction, and saved messages use Gson field names as
# their wire and saved-state format in the minified production build.
-keep class org.namchieh.rusmorph.data.repository.ConversationHistory { *; }
-keep class org.namchieh.rusmorph.data.repository.ConversationRequest { *; }
-keep class org.namchieh.rusmorph.data.repository.ConversationCorrection { *; }
-keep class org.namchieh.rusmorph.ui.conversation.ConversationMessage { *; }

