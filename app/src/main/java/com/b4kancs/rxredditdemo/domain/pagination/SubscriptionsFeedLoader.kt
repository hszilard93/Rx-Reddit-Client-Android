package com.b4kancs.rxredditdemo.domain.pagination

import com.b4kancs.rxredditdemo.repository.FollowsRepository
import org.koin.java.KoinJavaComponent

object SubscriptionsFeedLoader : AbstractCombinedFeedLoader() {

    private val followsRepository: FollowsRepository by KoinJavaComponent.inject(FollowsRepository::class.java)

    override fun getAllFeedsToBeDownloaded() =
        followsRepository.getAllSubscribedFeeds().blockingGet()
}