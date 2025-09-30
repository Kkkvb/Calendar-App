package zhang.myapplication.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CourseDao {
    @Query("SELECT * FROM Course")
    fun observeAll(): Flow<List<Course>>

    @Query("SELECT * FROM Course")
    suspend fun getAll(): List<Course>

    // NEW: fetch single course for ReminderReceiver chaining
    @Query("SELECT * FROM Course WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Course?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(course: Course): Long

    @Delete
    suspend fun delete(course: Course)
}
