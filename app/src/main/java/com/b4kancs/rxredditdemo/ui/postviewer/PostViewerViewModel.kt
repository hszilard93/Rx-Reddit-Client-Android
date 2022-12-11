package com.b4kancs.rxredditdemo.ui.postviewer

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.graphics.drawable.toBitmap
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import com.b4kancs.rxredditdemo.data.database.FavoritesDbEntryPost
import com.b4kancs.rxredditdemo.model.Post
import com.b4kancs.rxredditdemo.repository.FavoritePostsRepository
import com.b4kancs.rxredditdemo.ui.PostPagingDataObservableProvider
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.tbruyelle.rxpermissions3.RxPermissions
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.schedulers.Schedulers
import logcat.LogPriority
import logcat.logcat
import org.koin.java.KoinJavaComponent.inject
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

class PostViewerViewModel(pagingDataObservableProvider: PostPagingDataObservableProvider) : ViewModel() {

    private val favoritePostsRepository: FavoritePostsRepository by inject(FavoritePostsRepository::class.java)
    private val disposables = CompositeDisposable()

    val pagingDataObservable = pagingDataObservableProvider.cachedPagingObservable()

    fun openRedditLinkOfPost(post: Post, context: Context): Completable {
        logcat { "openRedditLinkOfPost: post = ${post.permalink}" }
        return Completable.create { emitter ->
            try {
                val link = "https://reddit.com${post.permalink}"
                val urlIntent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
                context.startActivity(urlIntent)
                emitter.onComplete()
            }
            catch (e: Exception) {
                logcat(LogPriority.ERROR) { "Could not open link! Message: ${e.message}" }
                emitter.onError(e)
            }
        }
    }

    fun downloadImage(link: String, activity: FragmentActivity): Completable {
        logcat { "downloadImage: link = $link" }
        return Completable.create { emitter ->
            val permissions = RxPermissions(activity)
            permissions.request(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .subscribe { isPermissionGranted ->
                    if (!isPermissionGranted) {
                        logcat(LogPriority.WARN) { "Write permission not granted." }
                        emitter.onError(Exception("Write permission not granted."))
                    }

                    downloadImageWithGlide(link, activity)
                        .subscribeBy(
                            onComplete = { emitter.onComplete() },
                            onError = { emitter.onError(it) }
                        )
                        .addTo(disposables)
                }
        }
    }

    private fun downloadImageWithGlide(link: String, context: Context): Completable {
        logcat { "downloadImageWithGlide: link = $link" }

        return Completable.create { emitter ->
            Glide.with(context)
                .load(link)
                .into(object : CustomTarget<Drawable>() {
                    override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                        val bitmap = resource.toBitmap()
                        val fileName = link.split('/').last()   // Get the filename from the URL
                            .split('.').let {
                                it.subList(0, it.lastIndex)              // Remove the extension from the filename
                            }.reduce(String::plus)
                        saveImageToStorage(bitmap, fileName, context)
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribeBy(
                                onComplete = { emitter.onComplete() },
                                onError = { emitter.onError(it) }
                            )
                            .addTo(disposables)
                    }

                    override fun onLoadCleared(placeholder: Drawable?) {}

                })
        }
    }

    private fun saveImageToStorage(bitmap: Bitmap, fileName: String, context: Context): Completable {
        logcat { "saveImageToStorage: fileName = $fileName" }

        return Completable.create { emitter ->
            val directory = Environment.DIRECTORY_PICTURES
            val outputStream: OutputStream
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, directory)
                }

                try {
                    with(context.contentResolver) {
                        val uri = insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                        outputStream = openOutputStream(uri!!)!!
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                    }
                    logcat(LogPriority.INFO) { "Image saved to device." }
                    emitter.onComplete()
                }
                catch (e: Exception) {
                    logcat(LogPriority.ERROR) { "Error saving image. Message: ${e.message}" }
                    emitter.onError(e)
                }
            } // SDK_INT <= 28
            else {
                try {
                    val path = Environment.getExternalStoragePublicDirectory(directory).let {
                        if (it.exists()) {
                            logcat(LogPriority.INFO) { "Saving to $it}" }; it
                        }
                        else throw NoSuchFileException(it)
                    }
                        .absolutePath

                    var newFile = File(path, "$fileName.jpg")

                    var i = 1
                    while (newFile.exists()) {      // If a file with the same 'name' already exists, create a new file with 'name (1)' etc.
                        newFile = File(path, "$fileName (${i++}).jpg")
                    }

                    outputStream = FileOutputStream(newFile)
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                    logcat(LogPriority.INFO) { "Image saved to device." }
                    emitter.onComplete()
                }
                catch (e: Exception) {
                    logcat(LogPriority.ERROR) { "Error saving image. Message: ${e.message}" }
                    emitter.onError(e)
                }
            }
        }
    }

    fun getFavoritePosts(): Single<List<FavoritesDbEntryPost>> =
        favoritePostsRepository.getAllFavoritePostsFromDb()

    fun addPostToFavorites(post: Post): Completable =
        favoritePostsRepository.addFavoritePostToDb(post)

    fun removePostFromFavorites(post: Post): Completable =
        favoritePostsRepository.removeFavoritePostFromDb(post)
}