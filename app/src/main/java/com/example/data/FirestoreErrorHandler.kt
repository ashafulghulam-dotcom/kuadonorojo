package com.example.data

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType {
    CREATE,
    UPDATE,
    DELETE,
    LIST,
    GET,
    WRITE
}

fun handleFirestoreError(
    exception: Throwable,
    operationType: OperationType,
    path: String?
) {
    val user = Firebase.auth.currentUser
    val providerInfos = JSONArray()
    user?.providerData?.forEach { info ->
        providerInfos.put(
            JSONObject().apply {
                put("providerId", info.providerId)
                put("displayName", info.displayName ?: JSONObject.NULL)
                put("email", info.email ?: JSONObject.NULL)
                put("photoUrl", info.photoUrl?.toString() ?: JSONObject.NULL)
            }
        )
    }

    val payload = JSONObject().apply {
        put("error", exception.message ?: exception.toString())
        put("operationType", operationType.name.lowercase())
        put("path", path ?: JSONObject.NULL)
        put(
            "authInfo",
            JSONObject().apply {
                put("userId", user?.uid ?: JSONObject.NULL)
                put("email", user?.email ?: JSONObject.NULL)
                put("emailVerified", user?.isEmailVerified ?: false)
                put("isAnonymous", user?.isAnonymous ?: false)
                put("tenantId", user?.tenantId ?: JSONObject.NULL)
                put("providerInfo", providerInfos)
            }
        )
    }

    Log.e("FirestoreError", "Firestore Error: $payload", exception)
}
