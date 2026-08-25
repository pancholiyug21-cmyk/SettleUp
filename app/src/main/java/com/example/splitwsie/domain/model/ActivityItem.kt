package com.example.splitwsie.domain.model

import java.util.Date

data class ActivityItem(
    val id: String,
    val timestamp: Date,
    val type: ActivityType,
    val userId: String,
    val targetUserId: String? = null,
    val groupId: String? = null,
    val expenseId: String? = null,
    val settlementId: String? = null,
    val amount: Double? = null,
    val title: String? = null
)

enum class ActivityType {
    EXPENSE_ADDED,
    EXPENSE_EDITED,
    SETTLED,
    GROUP_CREATED,
    JOINED_GROUP;

    companion object {
        fun fromName(value: String): ActivityType =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: EXPENSE_ADDED
    }
}

fun com.example.splitwsie.data.local.entity.ActivityEntity.toDomain() = ActivityItem(
    id = id,
    timestamp = timestamp,
    type = ActivityType.fromName(type),
    userId = userId,
    targetUserId = targetUserId,
    groupId = groupId,
    expenseId = expenseId,
    settlementId = settlementId,
    amount = amount,
    title = title
)
