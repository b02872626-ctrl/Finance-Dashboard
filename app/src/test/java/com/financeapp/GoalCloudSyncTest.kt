package com.financeapp

import com.financeapp.data.model.GoalStatus
import com.financeapp.data.model.GoalType
import com.financeapp.remote.GoalCloudSync
import com.financeapp.remote.GoalCloudSync.CloudContribution
import com.financeapp.remote.GoalCloudSync.CloudGoal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * Goals in the cloud (i_goals / i_goal_contributions): what a sync pass
 * does with the phone's rows and the cloud's rows. Made-up names, amounts
 * and account numbers.
 */
class GoalCloudSyncTest {

    private val zone = TimeZone.getTimeZone("Africa/Addis_Ababa")
    private val t0 = 1_760_000_000_000L

    private fun cloudGoal(id: String = "g1", name: String = "Laptop") = CloudGoal(
        id = id, name = name, emoji = "laptop", type = GoalType.DATE_BOUND,
        targetAmount = 60_000.0, targetMonths = null, deadline = "2026-06-30",
        priority = 1, note = null, status = GoalStatus.ACTIVE,
        linkedAccountKey = "Commercial Bank of Ethiopia#4321",
        createdAt = t0, updatedAt = t0, achievedAt = null,
    )

    private fun localGoal(id: String = "g1", name: String = "Laptop", synced: Boolean = true) =
        GoalCloudSync.toLocal(cloudGoal(id, name), zone).copy(isSynced = synced)

    private fun cloudSave(id: String = "s1", goalId: String = "g1", amount: Double = 500.0) =
        CloudContribution(id = id, goalId = goalId, amount = amount, contributedAt = t0 + 1, note = "cash", createdAt = t0 + 1)

    private fun localSave(id: String = "s1", goalId: String = "g1", synced: Boolean = true) =
        GoalCloudSync.toLocal(cloudSave(id, goalId)).copy(isSynced = synced)

    @Test
    fun `reinstall gets every goal and save back, marked synced`() {
        val plan = GoalCloudSync.plan(
            localGoals = emptyList(), localContributions = emptyList(),
            cloudGoals = listOf(cloudGoal("g1"), cloudGoal("g2", "Safety net")),
            cloudContributions = listOf(cloudSave("s1", "g1"), cloudSave("s2", "g2"), cloudSave("s3", "g2")),
            zone = zone,
        )
        assertEquals(listOf("g1", "g2"), plan.goalsToSave.map { it.id })
        assertEquals(listOf("s1", "s2", "s3"), plan.contributionsToAdd.map { it.id })
        assertTrue(plan.goalsToSave.all { it.isSynced } && plan.contributionsToAdd.all { it.isSynced })
        assertTrue(plan.goalIdsToDelete.isEmpty() && plan.contributionIdsToDelete.isEmpty())
        val laptop = plan.goalsToSave.first()
        assertEquals("Commercial Bank of Ethiopia#4321", laptop.linkedAccountKey)
        assertEquals(t0, laptop.createdAt)
        assertEquals("2026-06-30", GoalCloudSync.deadlineToDate(laptop.deadline!!, zone))
    }

    @Test
    fun `a goal made here and not uploaded yet is kept`() {
        val plan = GoalCloudSync.plan(listOf(localGoal(synced = false)), emptyList(), emptyList(), emptyList(), zone = zone)
        assertTrue(plan.isEmpty)
    }

    @Test
    fun `a synced goal missing from the cloud was deleted on the web, so it goes here too`() {
        val plan = GoalCloudSync.plan(
            listOf(localGoal("g1"), localGoal("g2", synced = false)),
            listOf(localSave("s1", "g1"), localSave("s2", "g2")),
            emptyList(), emptyList(), zone = zone,
        )
        assertEquals(listOf("g1"), plan.goalIdsToDelete)
        // s1 goes with its goal (cascade); s2 belongs to a goal still waiting to upload.
        assertTrue(plan.contributionIdsToDelete.isEmpty())
    }

    @Test
    fun `a synced goal the web changed takes the cloud version`() {
        val plan = GoalCloudSync.plan(listOf(localGoal(name = "Laptop")), emptyList(), listOf(cloudGoal(name = "New laptop")), emptyList(), zone = zone)
        assertEquals(listOf("New laptop"), plan.goalsToSave.map { it.name })
        assertTrue(plan.goalsToSave.single().isSynced)
    }

    @Test
    fun `a synced goal equal to the cloud is left alone`() {
        val plan = GoalCloudSync.plan(listOf(localGoal()), listOf(localSave()), listOf(cloudGoal()), listOf(cloudSave()), zone = zone)
        assertTrue(plan.isEmpty)
    }

    @Test
    fun `a goal edited here keeps its edit over the cloud until it uploads`() {
        val plan = GoalCloudSync.plan(listOf(localGoal(name = "Laptop", synced = false)), emptyList(), listOf(cloudGoal(name = "Old name")), emptyList(), zone = zone)
        assertTrue(plan.isEmpty)
    }

    @Test
    fun `a goal deleted here is not brought back by the pull`() {
        val plan = GoalCloudSync.plan(
            emptyList(), emptyList(),
            listOf(cloudGoal("g1")), listOf(cloudSave("s1", "g1")),
            pendingDeletionIds = setOf("g1"), zone = zone,
        )
        assertTrue(plan.isEmpty)
    }

    @Test
    fun `saves follow the same rules and never point at a gone goal`() {
        val plan = GoalCloudSync.plan(
            localGoals = listOf(localGoal("g1")),
            localContributions = listOf(localSave("s1", "g1"), localSave("s2", "g1", synced = false)),
            cloudGoals = listOf(cloudGoal("g1")),
            cloudContributions = listOf(cloudSave("s1", "g1"), cloudSave("s3", "g1"), cloudSave("s4", "g1"), cloudSave("s5", "g9")),
            pendingDeletionIds = setOf("s3"),
            zone = zone,
        )
        // s4 is new from the cloud; s3 was removed here and is on its way out;
        // s5 belongs to a goal the phone doesn't have.
        assertEquals(listOf("s4"), plan.contributionsToAdd.map { it.id })
        // s2 is waiting to upload.
        assertTrue(plan.contributionIdsToDelete.isEmpty())

        val removedOnWeb = GoalCloudSync.plan(
            listOf(localGoal("g1")), listOf(localSave("s1", "g1")), listOf(cloudGoal("g1")), emptyList(), zone = zone,
        )
        assertEquals(listOf("s1"), removedOnWeb.contributionIdsToDelete)
    }

    @Test
    fun `deadline round-trips as a date and lands on the end of that day`() {
        val endOfDay = GoalCloudSync.dateToDeadline("2026-06-30", zone)!!
        assertEquals("2026-06-30", GoalCloudSync.deadlineToDate(endOfDay, zone))
        assertEquals("2026-07-01", GoalCloudSync.deadlineToDate(endOfDay + 1, zone))
        val midnight = Calendar.getInstance(zone).apply { clear(); set(2026, Calendar.JUNE, 30) }.timeInMillis
        assertEquals(midnight + 86_400_000L - 1, endOfDay)
        assertNull(GoalCloudSync.dateToDeadline("30/06/2026", zone))
    }

    @Test
    fun `timestamps as Postgres sends them parse to the same moment`() {
        val expected = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear(); set(2026, Calendar.NOVEMBER, 13, 7, 58, 21); set(Calendar.MILLISECOND, 123)
        }.timeInMillis
        assertEquals(expected, GoalCloudSync.parseIsoMs("2026-11-13T07:58:21.123456+00:00"))
        assertEquals(expected, GoalCloudSync.parseIsoMs("2026-11-13T10:58:21.123+03:00"))
        assertEquals(expected - 123, GoalCloudSync.parseIsoMs("2026-11-13T07:58:21Z"))
        assertEquals(expected - 123, GoalCloudSync.parseIsoMs("2026-11-13T07:58:21+00"))
        assertEquals(expected - 123 + 500, GoalCloudSync.parseIsoMs("2026-11-13T07:58:21.5+00:00"))
        assertNull(GoalCloudSync.parseIsoMs("yesterday"))
        assertNull(GoalCloudSync.parseIsoMs(null))
    }

    @Test
    fun `tiny rounding in amounts and blank text are not changes`() {
        val local = localGoal().copy(targetAmount = 60_000.004, note = "", emoji = " ")
        val cloud = GoalCloudSync.toLocal(cloudGoal().copy(targetAmount = 60_000.0, note = null, emoji = null), zone)
        assertTrue(GoalCloudSync.sameGoal(local, cloud, zone))
        assertFalse(GoalCloudSync.sameGoal(local, cloud.copy(targetAmount = 60_001.0), zone))
        assertFalse(GoalCloudSync.sameGoal(local, cloud.copy(status = GoalStatus.ACHIEVED), zone))
    }

    @Test
    fun `the cloud row carries what the phone needs back`() {
        val g = localGoal().copy(isSynced = false, note = "for school")
        val cloud = GoalCloudSync.toCloud(g, zone)
        assertEquals("2026-06-30", cloud.deadline)
        assertEquals("for school", cloud.note)
        assertTrue(GoalCloudSync.sameGoal(g, GoalCloudSync.toLocal(cloud, zone), zone))
    }
}
