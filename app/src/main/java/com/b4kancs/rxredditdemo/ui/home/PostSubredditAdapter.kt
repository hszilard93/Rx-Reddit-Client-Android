package com.b4kancs.rxredditdemo.ui.home

import Post
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.b4kancs.rxredditdemo.R
import com.b4kancs.rxredditdemo.utils.Orientation
import com.b4kancs.rxredditdemo.utils.dpToPx
import com.bumptech.glide.Glide
import org.koin.java.KoinJavaComponent.inject
import java.util.*

class PostSubredditAdapter(private val posts: List<Post>) :
    RecyclerView.Adapter<PostSubredditAdapter.PostSubredditViewHolder>() {

    private lateinit var orientation: Orientation
    private val context: Context by inject(Context::class.java)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostSubredditViewHolder {
        orientation = Orientation.fromInt(context.resources.configuration.orientation)
        val view =
            when (orientation) {
                Orientation.PORTRAIT ->
                    LayoutInflater
                        .from(parent.context)
                        .inflate(R.layout.adapter_post_sub_row_large, parent, false)

                Orientation.LANDSCAPE ->
                    LayoutInflater
                        .from(parent.context)
                        .inflate(R.layout.adapter_post_sub_row, parent, false)
            }

        return PostSubredditViewHolder(view)
    }

    override fun onBindViewHolder(holder: PostSubredditViewHolder, position: Int) {
        val post = posts[position]
        holder.titleTextView.text = post.title

        val postAgeInMinutes =
            (Date().time - (post.createdAt * 1000L)) / (60 * 1000L) // time difference in ms divided by a minute's worth of ms
        val postAge = when (postAgeInMinutes) {
            in 0 until 60 -> postAgeInMinutes to "minute(s)"
            in 60 until 1440 -> postAgeInMinutes / 60 to "hour(s)"
            in 1440 until 525600 -> postAgeInMinutes / 1440 to "day(s)"
            in 525600 until Long.MAX_VALUE -> postAgeInMinutes / 525600 to "year(s)"
            else -> postAgeInMinutes to "ms"
        }
        holder.dateAuthorTextView.text = "posted ${postAge.first} ${postAge.second} ago by ${post.author}"

        holder.commentsTextView.text = "${post.numOfComments} comments"

        Glide.with(context).load(post.links?.first())
            .placeholder(R.drawable.ic_download)
            .error(R.drawable.not_found_24)
            .override(holder.imageView.width.dpToPx(context), 0)
            .into(holder.imageView)
            .view.adjustViewBounds = true
    }

    override fun getItemCount(): Int {
        return posts.size
    }

    class PostSubredditViewHolder(postView: View) : RecyclerView.ViewHolder(postView) {

        val titleTextView: TextView
        val imageView: ImageView
        val dateAuthorTextView: TextView
        val commentsTextView: TextView

        init {
            titleTextView = postView.findViewById(R.id.post_text_view)
            dateAuthorTextView = postView.findViewById(R.id.post_date_author_text_view)
            imageView = postView.findViewById(R.id.post_image_view)
            commentsTextView = postView.findViewById(R.id.post_comments_text)
        }
    }
}