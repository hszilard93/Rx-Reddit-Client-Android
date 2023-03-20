package com.b4kancs.rxredditdemo.domain.notification

import android.content.Context
import androidx.work.RxWorker
import androidx.work.WorkerParameters
import com.b4kancs.rxredditdemo.R
import com.b4kancs.rxredditdemo.data.networking.RedditJsonService
import com.b4kancs.rxredditdemo.data.utils.JsonPostsFeedHelper
import com.b4kancs.rxredditdemo.model.Post
import com.b4kancs.rxredditdemo.model.UserFeed
import com.b4kancs.rxredditdemo.repository.FollowsRepository
import io.reactivex.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.schedulers.Schedulers
import logcat.LogPriority
import logcat.logcat
import org.koin.java.KoinJavaComponent.inject
import io.reactivex.rxjava3.core.Single as Single3

class SubscriptionsNotificationWorker(val context: Context, workerParams: WorkerParameters) : RxWorker(context, workerParams) {

    private val jsonService: RedditJsonService by inject(RedditJsonService::class.java)
    private val followsRepository: FollowsRepository by inject(FollowsRepository::class.java)
    private val notificationManager: SubscriptionsNotificationManager by inject(SubscriptionsNotificationManager::class.java)

    override fun createWork(): Single<Result> {
        logcat { "createWork" }

        val subscribedFeeds = followsRepository.getAllSubscribedFeeds().blockingGet()
        if (subscribedFeeds.isEmpty()) {
            // We don't have any subscribed feeds, so we don't have to show a notification.
            logcat(LogPriority.INFO) { "There are no subscriptions. Calling Result.success()." }
            return Single.just(Result.success())
        }

        val workDisposables = CompositeDisposable()

        return Single.create { emitter ->
            downloadAllSubscribedFeedsAsMapSingle(subscribedFeeds)
                .doOnError { e ->
                    logcat(LogPriority.WARN) { "Error downloading user feeds. Message: ${e.message}" }
                    emitter.onSuccess(Result.retry())
                }
                .map { subscribedFeedPostMap ->
                    var feedsWithNewPostsCount = 0
                    subscribedFeedPostMap.forEach { (feed, posts) ->
                        val latestPost = posts.maxByOrNull { it.createdAt }
                        if (feed.lastPost != latestPost?.name) {
                            feedsWithNewPostsCount += 1
                        }
                    }
                    feedsWithNewPostsCount
                }
                .subscribe { feedsWithNewPostsCount ->
                    logcat(LogPriority.INFO) { "$feedsWithNewPostsCount subscribed users with new posts." }
                    if (feedsWithNewPostsCount > 0) {
                        val notificationMessage = context.getString(
                            R.string.follows_subscriptions_notification_message,
                            feedsWithNewPostsCount
                        )
                        notificationManager.showNotification(context, notificationMessage)
                    }
                    // Either way, the work's done.
                    emitter.onSuccess(Result.success())
                }.addTo(workDisposables)
        }.doFinally {
            logcat { "Disposing of workDisposables." }
            workDisposables.dispose()
        }
    }

    private fun downloadAllSubscribedFeedsAsMapSingle(feeds: List<UserFeed>): Single3<Map<UserFeed, List<Post>>> {
        logcat { "downloadAllSubscribedFeeds: feeds = ${feeds.map { it.name }}" }

        return Single3.just(
            feeds.map { feed ->
                // We query so many posts because some users have pinned posts that will be returned first.
                // So we need to sort a large number of posts by date to find the newest post.
                // Technically, we could encounter a user that has more pinned posts than the limit below, but as those
                // cannot be filtered out in the request, we'll deal with this issue if it ever becomes a problem.
                val jsonRequest = jsonService.getUsersPostsJson(feed.name, 50)
                    .subscribeOn(Schedulers.io())
                feed to JsonPostsFeedHelper
                    .fromGetUsersPostsJsonCallToListOfPostsAsSingle(jsonRequest)
                    .retry(5)
                    .blockingGet()
            }.associate { it -> it }
        )
    }
}