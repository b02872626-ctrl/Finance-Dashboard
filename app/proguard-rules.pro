# Add project specific ProGuard rules here.
-keep class com.financeapp.data.** { *; }
-keep class com.financeapp.parsing.** { *; }

# Credential Manager (Google sign-in): the Play Services provider is loaded
# reflectively; without this R8 strips it and sign-in fails with
# "no provider dependencies found". Per developer.android.com/identity/sign-in/credential-manager
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** {
  *;
}

# Keep line numbers so crash stack traces from release builds are readable
# (mapping.txt is kept per build in app/build/outputs/mapping/release/).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
