package com.pocketsloth.app.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.pocketsloth.app.data.local.db.dao.DatasetDao
import com.pocketsloth.app.data.local.db.dao.ExampleDao
import com.pocketsloth.app.data.local.db.dao.TrainingRunDao
import com.pocketsloth.app.data.local.db.entity.DatasetEntity
import com.pocketsloth.app.data.local.db.entity.ExampleEntity
import com.pocketsloth.app.data.local.db.entity.TrainingRunEntity

@Database(
    entities = [
        DatasetEntity::class,
        ExampleEntity::class,
        TrainingRunEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun datasetDao(): DatasetDao
    abstract fun exampleDao(): ExampleDao
    abstract fun trainingRunDao(): TrainingRunDao

    companion object {
        const val NAME = "pocket_sloth.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                .fallbackToDestructiveMigration()
                .build()
    }
}
