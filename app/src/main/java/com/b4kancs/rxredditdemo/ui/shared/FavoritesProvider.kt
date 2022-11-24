package com.b4kancs.rxredditdemo.ui.shared

import com.b4kancs.rxredditdemo.database.FavoritesDbEntryPost
import io.reactivex.rxjava3.core.Single

interface FavoritesProvider {

    fun getFavoritePosts(): Single<List<FavoritesDbEntryPost>>
}