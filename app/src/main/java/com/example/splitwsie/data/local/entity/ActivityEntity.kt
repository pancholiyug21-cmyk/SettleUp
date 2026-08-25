package com.example.splitwsie.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

@Entity(
    tableName = "activities",
    indices = [
        Index(value = ["groupId"]),
        Index(value = ["userId"]),
        Index(value = ["timestamp"])
    ]
)
data class ActivityEntity(
    @PrimaryKey val id: String,
    val timestamp: Date,
    val type: String,
    val userId: String,
    val targetUserId: String? = null,
    val groupId: String? = null,
    val expenseId: String? = null,
    val settlementId: String? = null,
    val amount: Double? = null,
    val title: String? = null
)
