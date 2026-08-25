package com.example.splitwsie.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.splitwsie.data.local.entity.ActivityEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityDao {

    @Query("SELECT * FROM activities WHERE groupId = :groupId ORDER BY timestamp DESC")
    fun getActivitiesForGroup(groupId: String): Flow<List<ActivityEntity>>

    @Query("SELECT * FROM activities ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentActivities(limit: Int): Flow<List<ActivityEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(activity: ActivityEntity)
    
    @Query("DELETE FROM activities WHERE expenseId = :expenseId")
    suspend fun deleteByExpenseId(expenseId: String)
}
