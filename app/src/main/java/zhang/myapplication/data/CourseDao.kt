package zhang.myapplication.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CourseDao {
    @Query("SELECT * FROM Course")
    fun observeAll(): Flow<List<Course>>

    @Query("SELECT * FROM Course")
    suspend fun getAll(): List<Course>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(course: Course): Long

    @Delete
    suspend fun delete(course: Course)
}
