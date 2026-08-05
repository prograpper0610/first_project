package com.autobuy.core.data.repository

import com.autobuy.core.data.db.dao.ProfileDao
import com.autobuy.core.data.db.entity.ProfileEntity
import com.autobuy.core.data.model.ShopRecipe
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 쇼핑몰 자동화 프로필 (Recipe) 리포지토리.
 *
 * 내장 프로필 및 사용자가 직접 레코딩/추가한 프로필을 관리합니다.
 */
@Singleton
class ProfileRepository @Inject constructor(
    private val profileDao: ProfileDao
) {
    private val moshi = Moshi.Builder().build()
    private val recipeAdapter = moshi.adapter(ShopRecipe::class.java)

    /**
     * 모든 활성 프로필 관찰.
     */
    fun observeProfiles(): Flow<List<ProfileUiModel>> {
        return profileDao.observeAllProfiles().map { entities ->
            entities.mapNotNull { entity ->
                try {
                    val recipe = recipeAdapter.fromJson(entity.recipeJson)
                    recipe?.let {
                        ProfileUiModel(
                            id = entity.id,
                            shopId = entity.shopId,
                            shopName = entity.shopName,
                            version = entity.version,
                            isBuiltin = entity.isBuiltin,
                            recipe = it
                        )
                    }
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    suspend fun getProfileById(id: String): ProfileUiModel? = withContext(Dispatchers.IO) {
        val entity = profileDao.getProfileById(id) ?: return@withContext null
        val recipe = recipeAdapter.fromJson(entity.recipeJson) ?: return@withContext null
        ProfileUiModel(
            id = entity.id,
            shopId = entity.shopId,
            shopName = entity.shopName,
            version = entity.version,
            isBuiltin = entity.isBuiltin,
            recipe = recipe
        )
    }

    suspend fun getProfileByShopId(shopId: String): ProfileUiModel? = withContext(Dispatchers.IO) {
        val entity = profileDao.getProfileByShopId(shopId) ?: return@withContext null
        val recipe = recipeAdapter.fromJson(entity.recipeJson) ?: return@withContext null
        ProfileUiModel(
            id = entity.id,
            shopId = entity.shopId,
            shopName = entity.shopName,
            version = entity.version,
            isBuiltin = entity.isBuiltin,
            recipe = recipe
        )
    }

    /**
     * 새 프로필 (커스텀/레코딩) 저장.
     */
    suspend fun saveProfile(recipe: ShopRecipe, isBuiltin: Boolean = false): String = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val json = recipeAdapter.toJson(recipe)
        val entity = ProfileEntity(
            id = id,
            shopId = recipe.shopId,
            shopName = recipe.shopName,
            version = recipe.version,
            recipeJson = json,
            isBuiltin = isBuiltin,
            isActive = true,
            createdAt = Date(),
            updatedAt = Date()
        )
        profileDao.upsertProfile(entity)
        id
    }

    suspend fun deleteProfile(id: String) = withContext(Dispatchers.IO) {
        profileDao.softDeleteProfile(id)
    }
}

data class ProfileUiModel(
    val id: String,
    val shopId: String,
    val shopName: String,
    val version: String,
    val isBuiltin: Boolean,
    val recipe: ShopRecipe
)
