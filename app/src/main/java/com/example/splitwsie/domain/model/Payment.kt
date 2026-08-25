package com.example.splitwsie.domain.model

import java.util.Date

enum class PaymentStatus {
    PENDING,
    PROCESSING,
    VERIFIED,
    COMPLETED,
    FAILED
}

data class Payment(
    val id: String,
    val payerId: String,
    val receiverId: String,
    val groupId: String?,
    val amount: Long, // in minor units (paise)
    val currency: String,
    val status: PaymentStatus,
    val razorpayOrderId: String?,
    val razorpayPaymentId: String?,
    val createdAt: Date,
    val updatedAt: Date
)
