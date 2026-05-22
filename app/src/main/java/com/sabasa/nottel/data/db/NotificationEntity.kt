package com.sabasa.nottel.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val appPackage: String,       // e.g. "com.whatsapp"
    val appLabel: String,         // e.g. "WhatsApp"
    val title: String,
    val message: String,
    val timestamp: Long,          // epoch ms — System.currentTimeMillis()

    val wasForwarded: Boolean = false,
    val forwardError: String? = null  // null = success or pending, message = failure reason
)