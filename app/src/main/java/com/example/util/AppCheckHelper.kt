package com.example.util

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        val debugToken = intent?.getStringExtra("FIREBASE_APPCHECK_DEBUG_TOKEN")
        if (!debugToken.isNullOrBlank()) {
            System.setProperty("FIREBASE_APPCHECK_DEBUG_TOKEN", debugToken)
        }
        Firebase.appCheck.installAppCheckProviderFactory(
            DebugAppCheckProviderFactory.getInstance()
        )
    } catch (e: Exception) {
        Log.w("AppCheck", "AppCheck initialization note: ${e.message}")
    }
}
