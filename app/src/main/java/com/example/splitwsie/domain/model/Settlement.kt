package com.example.splitwsie.domain.model

import java.util.Date

/**
 * Lifecycle of a settlement (a payment from one member to another).
 *
 * Only [VERIFIED] settlements reduce a balance. Everything else is visible to the user
 * so they do not pay twice, but is deliberately excluded from balance arithmetic.
 */
enum class SettlementStatus {
    /** Row written before handing off to the payment app. Reserves the debt against double-payment. */
    INITIATED,

    /** The payment app reported "submitted" (bank processing) — outcome not yet known. */
    PENDING_VERIFICATION,

    /** Confirmed. This is the only status that affects balances. */
    VERIFIED,

    /** The payment app or bank reported a failure. */
    FAILED,

    /** The user backed out before authorising, or the settlement was voided. */
    CANCELLED;

    val isTerminal: Boolean
        get() = this == VERIFIED || this == FAILED || this == CANCELLED

    /** In-flight settlements block a second payment for the same debt. */
    val isInFlight: Boolean
        get() = this == INITIATED || this == PENDING_VERIFICATION
}

enum class PaymentMethod {
    /** Real money movement through the user's UPI app (GPay / PhonePe / Paytm / BHIM). */
    UPI_INTENT,

    /** Money already changed hands outside the app; recorded manually. */
    CASH,

    /** Bank transfer made outside the app; recorded manually with a reference number. */
    BANK_TRANSFER,

    /** Simulated payment. Debug builds only — never moves money. */
    SANDBOX;

    val movesRealMoney: Boolean
        get() = this == UPI_INTENT

    val displayName: String
        get() = when (this) {
            UPI_INTENT -> "UPI"
            CASH -> "Cash"
            BANK_TRANSFER -> "Bank transfer"
            SANDBOX -> "Sandbox (test)"
        }
}

/**
 * A payment from [fromUserId] to [toUserId] settling part or all of a debt.
 *
 * [idempotencyKey] is unique in the database. It is derived from the debt being settled plus
 * the initiating attempt, so a retry after a lost callback reuses the same row instead of
 * creating a second payment.
 */
data class Settlement(
    val id: String,
    val groupId: String,
    val fromUserId: String,
    val toUserId: String,
    val amount: Double,
    val date: Date,
    val method: PaymentMethod,
    val status: SettlementStatus = SettlementStatus.INITIATED,
    val idempotencyKey: String,
    /** Transaction id reported by the UPI app / bank. Null until the payment completes. */
    val providerTxnId: String? = null,
    /** Our own reference passed to the payment app (`tr` in the UPI spec). */
    val providerRefId: String? = null,
    /** Set when the settlement targets one specific expense rather than the net debt. */
    val expenseId: String? = null,
    val note: String? = null,
    val verificationNotes: String? = null,
    val updatedAt: Date = date
) {
    /** Only verified settlements move balances. */
    val countsTowardsBalance: Boolean
        get() = status == SettlementStatus.VERIFIED
}
