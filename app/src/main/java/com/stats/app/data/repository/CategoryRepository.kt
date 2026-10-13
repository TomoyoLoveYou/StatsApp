package com.stats.app.data.repository

import com.stats.app.data.db.dao.PointsCategoryDao
import com.stats.app.data.db.entity.PointsCategoryEntity
import com.stats.app.util.AppLog
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepository @Inject constructor(
    private val categoryDao: PointsCategoryDao,
) {
    fun observeAll(): Flow<List<PointsCategoryEntity>> = categoryDao.observeAll()

    suspend fun getAll(): List<PointsCategoryEntity> = categoryDao.getAll()

    suspend fun create(name: String, color: Long): Long {
        val id = categoryDao.insert(PointsCategoryEntity(name = name, color = color))
        AppLog.i("CategoryRepo", "新建积分类别 #$id「$name」")
        return id
    }

    suspend fun update(category: PointsCategoryEntity) = categoryDao.update(category)

    suspend fun delete(category: PointsCategoryEntity) = categoryDao.delete(category)
}
