package com.b4kancs.rxredditdemo.model

import android.content.res.AssetManager
import androidx.room.Entity
import androidx.room.PrimaryKey
import org.koin.java.KoinJavaComponent.inject
import org.w3c.dom.Element
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory

@Entity
data class Subreddit(
    val name: String,
    @PrimaryKey val address: String,
    val isFavorite: Boolean = false,
    val isInDefaultList: Boolean = false
) {
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
                subreddits.add(Subreddit(name, address, isInDefaultList = true))
            }

            return subreddits
        }
    }
}