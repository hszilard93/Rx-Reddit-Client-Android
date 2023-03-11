package com.b4kancs.rxredditdemo.domain.pagination

object AggregateFeedLoader : AbstractCombinedFeedLoader() {

    override fun getAllFeedsToBeDownloaded() =
        followsRepository.getAllFollowsFromDb().blockingGet()
}