package com.b4kancs.rxredditdemo.model

import android.content.res.AssetManager
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.b4kancs.rxredditdemo.data.networking.RedditSubredditsListingModel
import org.koin.java.KoinJavaComponent.inject
import org.w3c.dom.Element
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory

@Entity
data class Subreddit(
    val name: String,
    @PrimaryKey val address: String,
    val status: Status,
    val nsfw: Boolean = false
) {

    enum class Status { NOT_IN_DB, IN_DEFAULTS_LIST, IN_USER_LIST, FAVORITED }

    companion object {
        private val assets: AssetManager by inject(AssetManager::class.java)

        fun parseDefaultSubredditsFromXml(): List<Subreddit> {
            val subreddits = ArrayList<Subreddit>()

            val inStream: InputStream = assets.open("subreddits.xml")

            val builderFactory = DocumentBuilderFactory.newInstance()
            val docBuilder = builderFactory.newDocumentBuilder()
            val doc = docBuilder.parse(inStream)
            val nodeList = doc.getElementsByTagName("subreddit")

            for (i in 0 until nodeList.length) {
                val element = nodeList.item(i) as Element
                val name = element.getAttribute("name") ?: ""
                val address = element.textContent
                subreddits.add(Subreddit(name, address, Status.IN_DEFAULTS_LIST))
            }

            return subreddits
        }

        fun fromSubredditJsonModel(model: RedditSubredditsListingModel.RedditSubredditDataChildData) =
            Subreddit(
                name = model.name,
                address = model.url.drop(1).dropLast(1),   // turns '/r/pics/' into 'r/pics'
                status = Status.NOT_IN_DB,
                nsfw = model.nsfw
            )
    }
}