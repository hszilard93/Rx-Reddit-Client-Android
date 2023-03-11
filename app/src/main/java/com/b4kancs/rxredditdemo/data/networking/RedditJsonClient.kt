package com.b4kancs.rxredditdemo.data.networking

import com.b4kancs.rxredditdemo.model.Subreddit
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import logcat.LogPriority
import logcat.logcat
import org.koin.java.KoinJavaComponent.inject

object RedditJsonClient {
    private val service: RedditJsonService by inject(RedditJsonService::class.java)

    fun getPictureIdTypePairsFromGalleryPostAtUrl(url: String): Maybe<List<Pair<String, String>>> {
        logcat { "getPictureIdTypePairsFromGalleryPostAtUrl: url = $url" }
        return service
            .getGalleryJson("$url/.json")
            .subscribeOn(Schedulers.io())
            .map { response ->
                if (!response.isSuccessful) {
                    logcat(LogPriority.ERROR) { "Error getting gallery items for $url. Error: ${response.code()}" }
                    return@map emptyList<RedditPostListingModel.RedditPostDataChildDataMediaMetadataItem>()
                }
                response.body()?.first()?.data?.children?.first()?.data?.mediaMetadata?.map { it.value }
                    ?: emptyList()
            }
            .flatMapMaybe { items ->
                val idTypePairs = ArrayList<Pair<String, String>>()
                if (items.isEmpty()) {
                    Maybe.empty()
                } else {
                    items.forEach { idTypePairs.add(it.id to it.type) }
                    Maybe.just(idTypePairs)
                }
            }
    }

    fun getSubredditsByKeyword(keyword: String): Single<List<Subreddit>> {
        logcat { "getSubredditsByKeyword: keyword = $keyword" }
        return service.searchSubredditsByKeyword(keyword)
            .map { response -> response.body()!! }
            .map { subsModel ->
                subsModel.data.children
            }
            .map { listOfSubData ->
                val subreddits = ArrayList<Subreddit>()
                listOfSubData.forEach {
                    subreddits.add(Subreddit.fromSubredditJsonModel(it.data))
                }
                subreddits
            }
            .flatMap { Single.just(it.toList()) }
            .onErrorReturn {
                logcat(LogPriority.ERROR) { it.message.toString() }
                emptyList()
            }
    }
}