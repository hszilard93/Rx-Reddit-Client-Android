package com.b4kancs.rxredditdemo.model

import com.b4kancs.rxredditdemo.model.Subreddit

object DefaultSubredditObject {
    const val DEFAULT_SUBREDDIT_PREFERENCE_KEY = "default_subreddit"
    const val DEFAULT_SUBREDDIT_PREFERENCE_VALUE = "user/kjoneslol/m/sfwpornnetwork"
    var defaultSubreddit = Subreddit("SFWPornNetwork", "user/kjoneslol/m/sfwpornnetwork", Subreddit.Status.FAVORITED)
}