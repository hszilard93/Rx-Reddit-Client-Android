package com.b4kancs.rxredditdemo.ui

import androidx.recyclerview.widget.DiffUtil
import com.b4kancs.rxredditdemo.model.Post

object PostComparator : DiffUtil.ItemCallback<Post>() {

    override fun areItemsTheSame(oldItem: Post, newItem: Post): Boolean = oldItem.name == newItem.name

    override fun areContentsTheSame(oldItem: Post, newItem: Post): Boolean = oldItem == newItem
}