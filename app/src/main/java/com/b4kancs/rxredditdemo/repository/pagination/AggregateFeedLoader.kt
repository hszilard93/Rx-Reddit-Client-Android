package com.b4kancs.rxredditdemo.repository.pagination

import com.b4kancs.rxredditdemo.data.networking.RedditJsonService
import com.b4kancs.rxredditdemo.repository.FollowsRepository

class AggregateFeedLoader(jsonService: RedditJsonService, followsRepository: FollowsRepository) :
        AbstractCombinedFeedLoader(jsonService, followsRepository) {

    override fun getAllFeedsToBeDownloaded() =
        followsRepository.getAllFollowsFromDb().blockingGet()
}