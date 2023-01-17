package com.b4kancs.rxredditdemo.domain.pagination

import androidx.paging.PagingSource
import com.b4kancs.rxredditdemo.data.networking.RedditJsonService
import com.b4kancs.rxredditdemo.data.utils.JsonDataModelToPostTransformer
import com.b4kancs.rxredditdemo.model.Post
import com.b4kancs.rxredditdemo.repository.FollowsRepository
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.PublishSubject
import logcat.LogPriority
import logcat.logcat
import org.koin.java.KoinJavaComponent.inject
import retrofit2.HttpException
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

object AggregateFeedLoader {

    private class FeedsDownloadException : Exception()


    private val jsonService: RedditJsonService by inject(RedditJsonService::class.java)
    private val followsRepository: FollowsRepository by inject(FollowsRepository::class.java)
    private val disposables = CompositeDisposable()

    private val userNameToPostsMap = ConcurrentHashMap<String, List<Post>?>()
    private val usersWithNoMorePostsSet = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    private var requiredFeedSize = 0


    fun loadAggregateFeeds(pageSize: Int, after: String?): Single<PagingSource.LoadResult<String, Post>> {
        logcat { "loadAggregateFeeds: loadSize = $pageSize, after = $after" }

        // Request for a brand new aggregate feed.
        if (after == null) {
            logcat(LogPriority.INFO) { "Starting to load new aggregate feed." }
            // If we have existing feed data, reset them.
            if (userNameToPostsMap.values.isNotEmpty()) clearData()

            requiredFeedSize = pageSize
            return Single.create { emitter ->
                // Download initial batch of feeds
                // For the purposes of this function and others in this class, UserFeeds will be represented by their name properties,
                // and will be called users (user = UserFeed.name).
                downloadAllFeeds(pageSize / 2)
                    .observeOn(Schedulers.computation())
                    .subscribeBy(
                        onSuccess = { initialFeedsMap ->
                            logcat { "Initial feeds downloaded." }
                            processInitialBatchForResult(initialFeedsMap, pageSize)
                                .subscribe { result -> emitter.onSuccess(result) }
                                .addTo(disposables)
                        },
                        onComplete = {
                            logcat(LogPriority.INFO) { "There were no feeds to download. Returning empty list." }
                            emitter.onSuccess(PagingSource.LoadResult.Page(emptyList(), null, null))
                        },
                        onError = { e ->
                            logcat(LogPriority.ERROR) { "Could not download aggregate posts. Message: ${e.message}" }
                            emitter.onSuccess(PagingSource.LoadResult.Error(e))
                        }
                    )
                    .addTo(disposables)
            }
        }
        // Request to continue to load data into an existing aggregate feed.
        else {
            logcat(LogPriority.INFO) { "Continuing to load existing aggregate feed. after = $after" }
            requiredFeedSize += pageSize
            return recursivelyDownloadFeedsUntilDone(emptyMap(), pageSize)
        }
    }


    private fun downloadAllFeeds(loadSize: Int): Maybe<Map<String, List<Post>?>> {
        logcat { "downloadAllFeeds: loadSize = $loadSize" }


        val allUsers = followsRepository.getAllFollowsFromDb().blockingGet().map { user -> user.name }
        val feedsStillLoading = allUsers.toMutableSet()
        val feedsSuccessfullyLoaded = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
        val feedFinishedLoadingSubject = PublishSubject.create<String>()
        val userToPostsMap = ConcurrentHashMap<String, List<Post>?>()

        if (allUsers.isEmpty())
            return Maybe.empty()

        for (user in allUsers) {
            downloadSingleFeed(user, loadSize)
                .subscribeBy(
                    onSuccess = { posts ->
                        logcat { "Downloaded ${posts.size} posts from user $user." }
                        // It is safe to concurrently modify a regular hashmap as long as we aren't touching the same keys... I think!
                        userToPostsMap[user] = posts.sortedByDescending { it.createdAt }
                        feedsSuccessfullyLoaded.add(user)
                        feedFinishedLoadingSubject.onNext(user)
                    },
                    onError = { e ->
                        logcat(LogPriority.WARN) { "Error getting user feed ${user}. Message: ${e.message}" }
                        userToPostsMap[user] = null
                        feedFinishedLoadingSubject.onNext(user)
                    }
                ).addTo(disposables)
        }

        return Maybe.create { emitter ->
            feedFinishedLoadingSubject
                .observeOn(Schedulers.computation())
                .subscribeBy(
                    onNext = { user ->
                        logcat { "feedFinishedLoadingSubject.onNext" }
                        feedsStillLoading.remove(user)
                        if (feedsStillLoading.isEmpty())
                            feedFinishedLoadingSubject.onComplete()
                    },
                    onComplete = {
                        logcat { "feedFinishedLoadingSubject.onComplete" }
                        // We were able to download all/some of the feeds.
                        if (userToPostsMap.values.flatMap { it ?: emptyList() }.isNotEmpty())
                            emitter.onSuccess(userToPostsMap)
                        // Else we either have encountered errors or the feeds are simply empty.
                        else {
                            if (feedsSuccessfullyLoaded.isEmpty()) { // Likely a network error.
                                logcat(LogPriority.ERROR) { "'Tis a network error, m'lord. It took all of them!" }
                                emitter.onError(FeedsDownloadException())
                            }
                            else {   // The followed users could have been deleted etc. Unlikely but can happen.
                                logcat(LogPriority.WARN) { "There is nothing to see here. (All downloaded user feeds are empty.)" }
                                emitter.onSuccess(userToPostsMap)
                            }
                        }
                    },
                    onError = { _ ->
                        logcat(LogPriority.ERROR) { "feedFinishedLoadingSubject.onError" }
                        throw java.lang.IllegalStateException("This state should not be reached.")
                    }
                )
                .addTo(disposables)
        }
    }


    private fun processInitialBatchForResult(
        initialFeedsMap: Map<String, List<Post>?>,
        pageSize: Int
    ): Single<PagingSource.LoadResult<String, Post>> {
        logcat { "processInitialBatchForResult" }

        // If the batch is empty, we have no posts to download.
        if (initialFeedsMap.values.isEmpty()) {
            // The resulting list might be empty, if the feeds we follow don't have any posts, have been deleted, etc.
            logcat(LogPriority.INFO) { "Initial batch is empty. Returning empty feed." }
            val resultingPostsList = userNameToPostsMap
                .flatMap { (_, v) -> v ?: emptyList() }
                .sortedByDescending { it.createdAt }

            return Single.just(
                PagingSource.LoadResult.Page(
                    resultingPostsList,
                    prevKey = null,
                    nextKey = resultingPostsList.lastOrNull()?.name
                )
            )
        }

        return recursivelyDownloadFeedsUntilDone(initialFeedsMap, pageSize)
    }


    private fun recursivelyDownloadFeedsUntilDone(
        newFeedsMap: Map<String, List<Post>?>,
        pageSize: Int
    ): Single<PagingSource.LoadResult<String, Post>> {
        logcat { "recursivelyDownloadFeedsUntilDone: newFeedsMap.size = ${newFeedsMap.size}, pageSize = $pageSize" }

        // We have some processing to do.
        // Our goal is to construct a feed of posts of size >= loadSize in chronological order,
        // where we can know that no user's posts have been left out of the order.
        // For this, the user with the youngest oldest post's oldest post
        // must fit into the list of all posts at an index >= loadSize (we are done).
        // If a user has no more posts, his oldest post is no longer taken into account in this calculation.
        // If all user's have run out of posts, we are also done.

        val userNameToOldestPostMap = HashMap<String, Post>()
        newFeedsMap.forEach { (user, posts) ->
            if (!posts.isNullOrEmpty()) {
                logcat(LogPriority.WARN) { "Evaluating posts from $user on thread ${Thread.currentThread()}" }
                posts.mapIndexed { i, p -> logcat(LogPriority.WARN) { "\t$i\t${p.name}, ${p.title}}" } }

                logcat { "Adding ${posts.size} posts to userNameToPostsMap[$user] (size = ${userNameToPostsMap[user]?.size})." }
                val usersPreviousPosts = userNameToPostsMap[user] ?: arrayListOf()
                // We store the posts in chronological order.
                val usersUpdatedPosts = (usersPreviousPosts + posts).sortedByDescending { post -> post.createdAt }
                userNameToPostsMap[user] = usersUpdatedPosts
            }
            else {
                logcat { "$user has run out of posts." }
                usersWithNoMorePostsSet.add(user)
            }
            // Store the oldest post of the user that we've downloaded so far. Null in case of a download error.
            userNameToPostsMap[user]?.let { updatedPosts ->
                logcat(LogPriority.VERBOSE) { "updatedPosts:" }
                updatedPosts.mapIndexed { i, it -> logcat(LogPriority.VERBOSE) { "\t$i.\t${it.name}, ${it.title}, ${it.createdAt}\n" } }
                userNameToOldestPostMap[user] = updatedPosts.last()
            }
        }

        logcat {
            "userNameToOldestPostMap.size = ${userNameToOldestPostMap.size}, " +
                    "values = ${userNameToOldestPostMap.values.map { it.name }}"
        }

        val (youngestOldestUser, youngestOldestPost) = userNameToOldestPostMap
            .map { (user, post) -> user to post }
            .filter { (user, _) -> user !in usersWithNoMorePostsSet }
            .fold(Pair<String?, Post?>(null, null)) { (youngestUser, youngestPost), (user, post) ->
                logcat { "evaluating $user, ${post.name} against $youngestUser, ${youngestPost?.name}" }
                if (youngestPost == null) user to post
                else if (post.createdAt > youngestPost.createdAt) user to post
                else youngestUser to youngestPost
            }

        logcat { "youngestOldestUser = $youngestOldestUser, youngestOldestPost = $youngestOldestPost" }

        val allPostsInFeedFlattened = userNameToPostsMap
            .flatMap { (_, posts) -> posts ?: emptyList() }
            .sortedByDescending { post -> post.createdAt }
        val youngestOldestPostsIndexInFeed = allPostsInFeedFlattened.indexOf(youngestOldestPost)

        if (youngestOldestPost == null || youngestOldestPostsIndexInFeed >= requiredFeedSize - 1) {   // We are done.
            val resultList = allPostsInFeedFlattened.subList(requiredFeedSize - pageSize, allPostsInFeedFlattened.size)
            val nextKey = if (resultList.size == pageSize) resultList.last().name else null
            logcat(LogPriority.INFO) {
                "Returning page of last ${resultList.size} elements of aggregate feed with size ${allPostsInFeedFlattened.size}. " +
                        "nextKey = $nextKey"
            }

            return Single.just(
                PagingSource.LoadResult.Page(
                    resultList,
                    prevKey = null,
                    nextKey = nextKey
                )
            )
        }
        else {  // Download the feed with the youngestOldestPost and start processing again.
            return Single.create { emitter ->
                logcat { "Continuing by downloading feed $youngestOldestUser from ${youngestOldestPost.name}" }
                downloadSingleFeed(
                    name = youngestOldestUser!!,
                    loadSize = pageSize / 2
                )
                    .subscribeBy(
                        onSuccess = { posts ->
                            recursivelyDownloadFeedsUntilDone(mapOf(youngestOldestUser to posts), pageSize)
                                .subscribe { result ->
                                    emitter.onSuccess(result)
                                }
                                .addTo(disposables)
                        },
                        onError = { e ->
                            logcat(LogPriority.ERROR) { "Could not download aggregate posts. Message: ${e.message}" }
                            emitter.onSuccess(PagingSource.LoadResult.Error(e))
                        }
                    ).addTo(disposables)
            }
        }
    }


    private fun downloadSingleFeed(name: String, loadSize: Int): Single<List<Post>> {
        logcat { "downloadSingleFeed: name = $name, loadSize = $loadSize" }

        val lastDownloadedPostName = userNameToPostsMap[name]?.last()?.name?.also {
            logcat { "after = $it" }
        }

        return jsonService.getUsersPostsJson(name, loadSize, lastDownloadedPostName)
            .observeOn(Schedulers.computation())
            .map { response ->
                if (response.isSuccessful)
                    response.body()!!.data.children
                else
                    throw HttpException(response)
            }
            .map { postsModels ->
                postsModels
                    .map { JsonDataModelToPostTransformer.fromJsonPostDataModel(it.data) }
                    .filter { it.links != null }        // The 'links' of all posts that are not picture or gallery posts is null
            }
            .retry(5)
    }


    private fun clearData() {
        logcat { "clearData" }
        disposables.clear()
        userNameToPostsMap.clear()
        usersWithNoMorePostsSet.clear()
    }
}