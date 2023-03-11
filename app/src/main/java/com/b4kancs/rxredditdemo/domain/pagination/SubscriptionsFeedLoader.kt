package com.b4kancs.rxredditdemo.domain.pagination

object SubscriptionsFeedLoader : AbstractCombinedFeedLoader() {

    override fun getAllFeedsToBeDownloaded() =
        followsRepository.getAllSubscribedFeeds().blockingGet()
}