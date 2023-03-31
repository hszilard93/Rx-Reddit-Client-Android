package com.b4kancs.rxredditdemo.repository.pagination

import androidx.paging.PagingSource
import com.b4kancs.rxredditdemo.data.networking.RedditJsonService
import com.b4kancs.rxredditdemo.data.utils.JsonPostsFeedHelper
import com.b4kancs.rxredditdemo.model.Post
import com.b4kancs.rxredditdemo.model.UserFeed
import com.b4kancs.rxredditdemo.repository.FollowsRepository
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.BehaviorSubject
import logcat.LogPriority
import logcat.logcat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

abstract class AbstractCombinedFeedLoader(
    protected val jsonService: RedditJsonService,
    protected val followsRepository: FollowsRepository
) {

    companion object {
        const val SINGLE_FEED_DOWNLOAD_SIZE = 20
    }

    protected val disposables = CompositeDisposable()
    protected var requiredFeedSize = 0
    protected val userNameToPostsSortedMap = ConcurrentHashMap<String, List<Post>>()
    protected val usersWithNoMorePostsSet = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    protected var lastServedPost: Post? = null

    // Determines the feeds to be downloaded, i.e. all followed feeds (combined) or just the subscribed feeds (used for notifications).
    abstract fun getAllFeedsToBeDownloaded(): List<UserFeed>


    // Start point of the logic, called by the PagingSource.
    fun loadCombinedFeed(pageSize: Int, after: String?): Single<PagingSource.LoadResult<String, Post>> {
        logcat { "loadCombinedFeed: pageSize = $pageSize, after = $after" }
        requiredFeedSize = pageSize
        val shouldContinueExistingFeed = after != null
        if (!shouldContinueExistingFeed) {
            logcat(LogPriority.INFO) { "Starting to download new combined feed." }
            clearState()
            val feedsToDownload = getAllFeedsToBeDownloaded()
            for (feed in feedsToDownload) {
                userNameToPostsSortedMap[feed.name] = emptyList()
            }
        }

        return Single.create { emitter ->
            downloadFeedsRecursivelyUntilDone()
                .subscribeBy(
                    onSuccess = { (resultPosts, lastServedPostName) ->
                        logcat(LogPriority.INFO) {
                            "onSuccess: resultPosts.size = ${resultPosts.size}, lastServedPostName = $lastServedPostName"
                        }
                        if (resultPosts.isEmpty()) {
                            logcat(LogPriority.ERROR) { "No more posts could be downloaded. Probably a network error!" }
                        }
                        emitter.onSuccess(
                            PagingSource.LoadResult.Page(
                                data = resultPosts,
                                prevKey = null,
                                nextKey = lastServedPostName
                            )
                        )
                        disposables.clear()
                    },
                    onError = {
                        emitter.onError(it)
                        disposables.clear()
                    }
                )
                .addTo(disposables)
        }

    }

    protected fun downloadFeedsRecursivelyUntilDone(): Single<Pair<List<Post>, String?>> {
        logcat { "continueDownloadingFeedsUntilDoneRecursively" }
        logcat(LogPriority.VERBOSE) {
            "userNameToPostsSortedMap = \n\t${
                userNameToPostsSortedMap.map { (user, posts) -> "$user: ${posts.size}" }
            }" + "\nusersWithNoMorePostsSet = ${usersWithNoMorePostsSet.size}"
        }

        val allPostsInFeedFlattened = userNameToPostsSortedMap.values.flatten().sortedByDescending { it.createdAt }
        // the earliest last post of all downloaded feeds (that have not run out).
        val earliestLastPost = userNameToPostsSortedMap
            .filterKeys { user -> !usersWithNoMorePostsSet.contains(user) }
            .filterValues { posts -> posts.isNotEmpty() }
            .maxByOrNull { (_, posts) -> posts.last().createdAt }
            ?.value?.last()

        // Return condition #1: Check if there are enough posts to serve.
        if (earliestLastPost != null) {
            val iOfEarliestLastPost = allPostsInFeedFlattened.indexOf(earliestLastPost)
            val iOfLastServedPost = allPostsInFeedFlattened.indexOf(lastServedPost)

            logcat {
                "allPostsInFeedFlattened.size = ${allPostsInFeedFlattened.size}, earliestLastPost = ${earliestLastPost.name}, " +
                        "iOfEarliestLastPost = $iOfEarliestLastPost, iOfLastServedPost = $iOfLastServedPost"
            }

            if (iOfEarliestLastPost - iOfLastServedPost >= requiredFeedSize) {
                logcat { "There are enough posts to serve." }
                val iOfLastToServe = iOfLastServedPost + requiredFeedSize
                lastServedPost = allPostsInFeedFlattened[iOfLastToServe]
                val resultList = allPostsInFeedFlattened.subList(iOfLastServedPost + 1, iOfLastToServe + 1)
                return Single.just(Pair(resultList, lastServedPost!!.name))
            }
        }

        // Return condition #2: Check if all feeds have run out.
        if (usersWithNoMorePostsSet.size == userNameToPostsSortedMap.size) {
            logcat { "All feeds have run out." }
            val iOfLastServedPost = allPostsInFeedFlattened.indexOf(lastServedPost)
            val iOfLastPostToServe = minOf(iOfLastServedPost + requiredFeedSize, allPostsInFeedFlattened.size - 1)
            val resultList = allPostsInFeedFlattened.subList(iOfLastServedPost + 1, iOfLastPostToServe + 1)
            val lastServedPost = if (resultList.size < requiredFeedSize) null else resultList.last()
            return Single.just(Pair(resultList, lastServedPost?.name))
        }

        // Download more posts.

        /*
        // One possible strategy is to only download the feed of the earliest last post, and see if we have enough posts to serve.
        // The downside of this is that it would be always downloading feeds in a sequential order, and the user would have to wait until
        // we have downloaded enough feeds to have a complete new page.

        val feedsToDownload: List<String> =
            // If we haven't downloaded any feeds yet, download all feeds.
            if (allPostsInFeedFlattened.isEmpty()) userNameToPostsSortedMap.keys().toList()
            // Else download only the feed of the earliest last post, and see if we have enough posts to serve.
            else listOf(earliestLastPost!!.author)

        // Instead, we will download a batch of all feeds (that have not run out) at once, in parallel, and see if we have a full page yet.
        // This will be more wasteful of data, but it's on the order of megabytes at most, and the user will not have to wait so much.
        */
        val feedsToDownload = userNameToPostsSortedMap.filterKeys { user -> !usersWithNoMorePostsSet.contains(user) }.keys.toList()
        val numberOfFeedsStillDownloadingBehaviorSubject = BehaviorSubject.createDefault(feedsToDownload.size)
        feedsToDownload
            .forEach { feedName ->
                downloadSingleFeedContinuingFromLastPost(feedName)
                    .subscribeBy(
                        onSuccess = { posts ->
                            logcat(LogPriority.VERBOSE) { "Successfully downloaded feed: $feedName. posts.size = ${posts.size}" }
                            userNameToPostsSortedMap.merge(feedName, posts.sortedByDescending { it.createdAt }) {
                                    old, new -> (old + new).distinctBy { it.name }
                            }
                            // Check if the feed has 'run out'.
                            if (posts.isEmpty()) {
                                usersWithNoMorePostsSet.add(feedName)
                            }
                            numberOfFeedsStillDownloadingBehaviorSubject.onNext(numberOfFeedsStillDownloadingBehaviorSubject.value!! - 1)
                        },
                        onError = { e ->
                            logcat(LogPriority.ERROR) { "Error downloading feed: $feedName. Message: ${e.message}" }
                            usersWithNoMorePostsSet.add(feedName)
                            numberOfFeedsStillDownloadingBehaviorSubject.onNext(numberOfFeedsStillDownloadingBehaviorSubject.value!! - 1)
                        }
                    )
                    .addTo(disposables)
            }
        // Continue processing after all the feeds have finished downloading.
        return Single.create { emitter ->
            numberOfFeedsStillDownloadingBehaviorSubject
                .filter { it == 0 }
                .firstOrError()
                .subscribe { _ ->
                    logcat { "Finished downloading a batch of feeds. Continuing processing." }
                    downloadFeedsRecursivelyUntilDone()
                        .subscribeBy(
                            onSuccess = { result -> emitter.onSuccess(result) },
                            onError = { emitter.onError(it) }
                        )
                        .addTo(disposables)
                }.addTo(disposables)
        }
    }

    protected fun downloadSingleFeedContinuingFromLastPost(name: String): Single<List<Post>> {
        logcat { "downloadSingleFeed: name = $name" }

        val lastDownloadedPostName = userNameToPostsSortedMap[name]?.lastOrNull()?.name
            ?.also {
                logcat(LogPriority.VERBOSE) { "\t\tafter = $it" }
            }

        val request = jsonService.getUsersPostsJson(name, SINGLE_FEED_DOWNLOAD_SIZE, lastDownloadedPostName)
            .subscribeOn(Schedulers.io())
            .observeOn(Schedulers.computation())
            .timeout(10, TimeUnit.SECONDS)
            .retry(3)

        return JsonPostsFeedHelper.fromGetUsersPostsJsonCallToListOfPostsAsSingle(request)
    }

    protected fun clearState() {
        userNameToPostsSortedMap.clear()
        usersWithNoMorePostsSet.clear()
        lastServedPost = null
    }
}