package com.example.splitwsie.data.repository

import android.database.sqlite.SQLiteConstraintException
import com.example.splitwsie.data.local.dao.SettlementDao
import com.example.splitwsie.data.local.entity.SettlementEntity
import com.example.splitwsie.domain.model.PaymentMethod
import com.example.splitwsie.domain.model.Settlement
import com.example.splitwsie.domain.model.SettlementStatus
import com.example.splitwsie.domain.repository.SettlementRepository
import com.example.splitwsie.domain.util.Money
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Date
import java.util.UUID
import javax.inject.Inject

import com.example.splitwsie.data.local.dao.ActivityDao
import com.example.splitwsie.data.local.entity.ActivityEntity

class SettlementRepositoryImpl @Inject constructor(
    private val settlementDao: SettlementDao,
    private val activityDao: ActivityDao
) : SettlementRepository {

    override fun observeForGroup(groupId: String): Flow<List<Settlement>> =
        settlementDao.observeForGroup(groupId).map { list -> list.map { it.toDomain() } }

    override fun observeAll(): Flow<List<Settlement>> =
        settlementDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeForUser(userId: String): Flow<List<Settlement>> =
        settlementDao.observeForUser(userId).map { list -> list.map { it.toDomain() } }

    override suspend fun getForGroup(groupId: String): List<Settlement> =
        settlementDao.getForGroup(groupId).map { it.toDomain() }

    override suspend fun getById(id: String): Settlement? =
        settlementDao.getById(id)?.toDomain()

    override suspend fun initiate(
        groupId: String,
        fromUserId: String,
        toUserId: String,
        amount: Double,
        method: PaymentMethod,
        idempotencyKey: String,
        providerRefId: String?,
        expenseId: String?,
        note: String?
    ): Settlement {
        // Fast path: the key was already used, so this is a retry of the same logical payment.
        settlementDao.getByIdempotencyKey(idempotencyKey)?.let { return it.toDomain() }

        val now = Date()
        val entity = SettlementEntity(
            id = UUID.randomUUID().toString(),
            groupId = groupId,
            fromUserId = fromUserId,
            toUserId = toUserId,
            amount = Money.round(amount),
            date = now,
            method = method.name,
            status = SettlementStatus.INITIATED.name,
            idempotencyKey = idempotencyKey,
            providerRefId = providerRefId,
            expenseId = expenseId,
            note = note,
            updatedAt = now
        )

        return try {
            settlementDao.insert(entity)
            entity.toDomain()
        } catch (e: SQLiteConstraintException) {
            // Lost a race against a concurrent initiate() with the same key — reuse that row.
            settlementDao.getByIdempotencyKey(idempotencyKey)?.toDomain()
                ?: throw e
        }
    }

    override suspend fun updateStatus(
        settlementId: String,
        status: SettlementStatus,
        providerTxnId: String?,
        verificationNotes: String?
    ): Settlement? {
        val existing = settlementDao.getById(settlementId) ?: return null

        // A terminal settlement is never re-opened. This is what makes a duplicated callback
        // (the same success delivered twice) a no-op instead of a second balance change.
        if (SettlementStatus.valueOf(existing.status).isTerminal) {
            return existing.toDomain()
        }

        val updated = existing.copy(
            status = status.name,
            providerTxnId = providerTxnId ?: existing.providerTxnId,
            verificationNotes = verificationNotes ?: existing.verificationNotes,
            updatedAt = Date()
        )
        settlementDao.update(updated)

        if (status == SettlementStatus.VERIFIED) {
            activityDao.insert(
                ActivityEntity(
                    id = UUID.randomUUID().toString(),
                    timestamp = Date(),
                    type = "SETTLED",
                    userId = existing.fromUserId,
                    targetUserId = existing.toUserId,
                    groupId = existing.groupId,
                    settlementId = existing.id,
                    amount = existing.amount
                )
            )
        }

        return updated.toDomain()
    }

    override suspend fun getInFlight(
        groupId: String,
        fromUserId: String,
        toUserId: String
    ): List<Settlement> =
        settlementDao.getInFlight(groupId, fromUserId, toUserId).map { it.toDomain() }

    override suspend fun getAllInFlight(): List<Settlement> =
        settlementDao.getAllInFlight().map { it.toDomain() }

    override suspend fun deleteForGroup(groupId: String) {
        settlementDao.deleteForGroup(groupId)
    }

    private fun SettlementEntity.toDomain() = Settlement(
        id = id,
        groupId = groupId,
        fromUserId = fromUserId,
        toUserId = toUserId,
        amount = amount,
        date = date,
        method = runCatching { PaymentMethod.valueOf(method) }.getOrDefault(PaymentMethod.CASH),
        status = runCatching { SettlementStatus.valueOf(status) }
            .getOrDefault(SettlementStatus.PENDING_VERIFICATION),
        idempotencyKey = idempotencyKey,
        providerTxnId = providerTxnId,
        providerRefId = providerRefId,
        expenseId = expenseId,
        note = note,
        verificationNotes = verificationNotes,
        updatedAt = updatedAt
    )
}
