package com.example.splitwsie.domain.usecase

import com.example.splitwsie.domain.model.Expense
import com.example.splitwsie.domain.model.PaymentMethod
import com.example.splitwsie.domain.model.Settlement
import com.example.splitwsie.domain.model.SettlementStatus
import com.example.splitwsie.domain.model.Split
import com.example.splitwsie.domain.model.SplitType
import com.example.splitwsie.domain.util.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class BalanceCalculatorTest {

    private val calculator = BalanceCalculator()

    private fun expense(
        id: String,
        paidBy: String,
        amount: Double,
        splits: List<Pair<String, Double>>
    ) = Expense(
        id = id,
        groupId = "g1",
        title = id,
        amount = amount,
        category = "GENERAL",
        date = Date(),
        paidById = paidBy,
        splits = splits.map { Split(it.first, it.second, SplitType.EXACT) }
    )

    private fun settlement(
        from: String,
        to: String,
        amount: Double,
        status: SettlementStatus = SettlementStatus.VERIFIED
    ) = Settlement(
        id = "s-$from-$to-$amount-$status",
        groupId = "g1",
        fromUserId = from,
        toUserId = to,
        amount = amount,
        date = Date(),
        method = PaymentMethod.UPI_INTENT,
        status = status,
        idempotencyKey = "k-$from-$to-$amount-$status"
    )

    @Test
    fun `equal split leaves each non-payer owing their share`() {
        // A pays 300 for A, B and C.
        val e = expense("e1", "A", 300.0, listOf("A" to 100.0, "B" to 100.0, "C" to 100.0))

        val debts = calculator.calculateBalances(listOf(e))

        assertEquals(2, debts.size)
        assertEquals(100.0, debts.first { it.fromUserId == "B" }.amount, 0.001)
        assertEquals("A", debts.first { it.fromUserId == "B" }.toUserId)
        assertEquals(100.0, debts.first { it.fromUserId == "C" }.amount, 0.001)
    }

    @Test
    fun `opposing expenses net down to a single transaction`() {
        val e1 = expense("e1", "A", 100.0, listOf("B" to 100.0))
        val e2 = expense("e2", "B", 50.0, listOf("A" to 50.0))

        val debts = calculator.calculateBalances(listOf(e1, e2))

        assertEquals(1, debts.size)
        assertEquals("B", debts[0].fromUserId)
        assertEquals("A", debts[0].toUserId)
        assertEquals(50.0, debts[0].amount, 0.001)
    }

    @Test
    fun `a verified settlement reduces the debt`() {
        val e = expense("e1", "A", 100.0, listOf("B" to 100.0))

        val debts = calculator.calculateBalances(listOf(e), listOf(settlement("B", "A", 40.0)))

        assertEquals(1, debts.size)
        assertEquals(60.0, debts[0].amount, 0.001)
    }

    @Test
    fun `paying the full amount clears the debt entirely`() {
        val e = expense("e1", "A", 100.0, listOf("B" to 100.0))

        val debts = calculator.calculateBalances(listOf(e), listOf(settlement("B", "A", 100.0)))

        assertTrue("expected no outstanding debts, got $debts", debts.isEmpty())
    }

    @Test
    fun `repeated partial settlements accumulate`() {
        val e = expense("e1", "A", 100.0, listOf("B" to 100.0))
        val settlements = listOf(
            settlement("B", "A", 30.0).copy(id = "s1", idempotencyKey = "k1"),
            settlement("B", "A", 45.0).copy(id = "s2", idempotencyKey = "k2")
        )

        val debts = calculator.calculateBalances(listOf(e), settlements)

        assertEquals(25.0, debts.single().amount, 0.001)
    }

    @Test
    fun `settlements that are not verified do not move the balance`() {
        val e = expense("e1", "A", 100.0, listOf("B" to 100.0))

        for (status in listOf(
            SettlementStatus.INITIATED,
            SettlementStatus.PENDING_VERIFICATION,
            SettlementStatus.FAILED,
            SettlementStatus.CANCELLED
        )) {
            val debts = calculator.calculateBalances(
                listOf(e),
                listOf(settlement("B", "A", 100.0, status))
            )
            assertEquals(
                "$status must not change the balance",
                100.0,
                debts.single().amount,
                0.001
            )
        }
    }

    @Test
    fun `equal split of an amount that does not divide evenly leaves no residue`() {
        // 100 / 3 -- the payer is credited 100 and the three shares must debit exactly 100.
        val shares = Money.splitEqually(Money.toPaise(100.0), 3).map { Money.fromPaise(it) }
        val e = expense(
            "e1", "A", 100.0,
            listOf("A" to shares[0], "B" to shares[1], "C" to shares[2])
        )

        val debts = calculator.calculateBalances(listOf(e))
        val totalOwedToA = debts.filter { it.toUserId == "A" }.sumOf { it.amount }

        assertEquals(100.0 - shares[0], totalOwedToA, 0.001)
        // And after everyone pays, nothing is left over.
        val after = calculator.calculateBalances(
            listOf(e),
            debts.map { settlement(it.fromUserId, it.toUserId, it.amount) }
        )
        assertTrue("expected fully settled, got $after", after.isEmpty())
    }

    @Test
    fun `outstandingBetween reports what one user owes another`() {
        val e = expense("e1", "A", 100.0, listOf("B" to 100.0))
        val settlements = listOf(settlement("B", "A", 25.0))

        assertEquals(75.0, calculator.outstandingBetween(listOf(e), settlements, "B", "A"), 0.001)
        // Nothing flows the other way.
        assertEquals(0.0, calculator.outstandingBetween(listOf(e), settlements, "A", "B"), 0.001)
    }

    @Test
    fun `net balance is positive for a lender and negative for a borrower`() {
        val e = expense("e1", "A", 90.0, listOf("A" to 30.0, "B" to 30.0, "C" to 30.0))

        assertEquals(60.0, calculator.netBalanceFor(listOf(e), emptyList(), "A"), 0.001)
        assertEquals(-30.0, calculator.netBalanceFor(listOf(e), emptyList(), "B"), 0.001)
        assertEquals(0.0, calculator.netBalanceFor(listOf(e), emptyList(), "D"), 0.001)
    }

    @Test
    fun `every debtor and creditor is fully matched across many users`() {
        val expenses = listOf(
            expense("e1", "A", 120.0, listOf("A" to 30.0, "B" to 30.0, "C" to 30.0, "D" to 30.0)),
            expense("e2", "B", 80.0, listOf("A" to 20.0, "B" to 20.0, "C" to 20.0, "D" to 20.0)),
            expense("e3", "C", 40.0, listOf("C" to 20.0, "D" to 20.0))
        )

        val debts = calculator.calculateBalances(expenses)

        // The plan must fully settle everyone: applying it leaves nothing outstanding.
        val applied = debts.map { settlement(it.fromUserId, it.toUserId, it.amount) }
        assertTrue(
            "expected all balances cleared, got ${calculator.calculateBalances(expenses, applied)}",
            calculator.calculateBalances(expenses, applied).isEmpty()
        )
        // And no more transfers than the classic n-1 bound.
        assertTrue("expected at most 3 transfers, got ${debts.size}", debts.size <= 3)
    }

    @Test
    fun `an empty group has no debts`() {
        assertTrue(calculator.calculateBalances(emptyList()).isEmpty())
    }

    @Test
    fun `a single-person expense produces no debt`() {
        val e = expense("e1", "A", 50.0, listOf("A" to 50.0))
        assertTrue(calculator.calculateBalances(listOf(e)).isEmpty())
    }

    @Test
    fun `deleting an expense removes its effect on balances`() {
        val e1 = expense("e1", "A", 100.0, listOf("B" to 100.0))
        val e2 = expense("e2", "A", 60.0, listOf("B" to 60.0))

        assertEquals(160.0, calculator.calculateBalances(listOf(e1, e2)).single().amount, 0.001)
        // Same call with e2 removed, as the repository would return after a delete.
        assertEquals(100.0, calculator.calculateBalances(listOf(e1)).single().amount, 0.001)
    }

    @Test
    fun `large amounts stay exact`() {
        val e = expense("e1", "A", 9_999_999.99, listOf("B" to 9_999_999.99))
        assertEquals(9_999_999.99, calculator.calculateBalances(listOf(e)).single().amount, 0.001)
    }
}
