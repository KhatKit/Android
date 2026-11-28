package heizige.kk.khatkit.app.feature.favorite.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import heizige.kk.khatkit.app.core.data.db.dao.FavoriteDAO
import heizige.kk.khatkit.app.feature.favorite.data.FavoriteRepository
import javax.inject.Singleton

/** 收藏单 feature 的仓库装配；DAO/Entity/schema 仍留 core。 */
@Module
@InstallIn(SingletonComponent::class)
object FavoriteHiltModule {
    @Provides
    @Singleton
    fun provideFavoriteRepository(favoriteDAO: FavoriteDAO): FavoriteRepository =
        FavoriteRepository(favoriteDAO)
}
