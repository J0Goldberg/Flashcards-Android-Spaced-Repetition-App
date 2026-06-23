package com.flashcards.di

import android.content.Context
import com.flashcards.data.local.*
import com.flashcards.data.repository.FlashcardsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): FlashcardsDatabase =
        FlashcardsDatabase.getInstance(ctx)

    @Provides fun provideDeckDao(db: FlashcardsDatabase): DeckDao = db.deckDao()
    @Provides fun provideCardDao(db: FlashcardsDatabase): CardDao = db.cardDao()
    @Provides fun provideCardStateDao(db: FlashcardsDatabase): CardStateDao = db.cardStateDao()
    @Provides fun provideReviewEventDao(db: FlashcardsDatabase): ReviewEventDao = db.reviewEventDao()

    @Provides @Singleton
    fun provideRepository(
        deckDao: DeckDao,
        cardDao: CardDao,
        cardStateDao: CardStateDao,
        reviewEventDao: ReviewEventDao
    ): FlashcardsRepository = FlashcardsRepository(deckDao, cardDao, cardStateDao, reviewEventDao)
}
