package com.b4kancs.rxredditdemo.ui.postviewer

import android.Manifest
import android.app.WallpaperManager
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
import com.b4kancs.rxredditdemo.data.database.PostFavoritesDbEntry
import com.b4kancs.rxredditdemo.model.Post
import com.b4kancs.rxredditdemo.repository.FavoritePostsRepository
import com.b4kancs.rxredditdemo.repository.PostsPropertiesRepository
import com.b4kancs.rxredditdemo.ui.shared.PostPagingDataObservableProvider
import com.b4kancs.rxredditdemo.utils.fromCompletable
import com.b4kancs.rxredditdemo.utils.toV3Observable
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.f2prateek.rx.preferences2.RxSharedPreferences
import com.tbruyelle.rxpermissions3.RxPermissions
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.PublishSubject
import logcat.LogPriority
import logcat.logcat
import org.koin.java.KoinJavaComponent.inject
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

class PostViewerViewModel(pagingDataObservableProvider: PostPagingDataObservableProvider) : ViewModel() {

    private val favoritePostsRepository: FavoritePostsRepository by inject(FavoritePostsRepository::class.java)
    private val rxSharedPreferences: RxSharedPreferences by inject(RxSharedPreferences::class.java)
    private val postsPropertiesRepository: PostsPropertiesRepository by inject(PostsPropertiesRepository::class.java)
    private val disposables = CompositeDisposable()

    val pagingDataObservable = pagingDataObservableProvider.getCachedPagingObservable()
    val navigateToFollowsActionTriggerSubject =
    // We need the username for FollowsFragment as well as a Subject that will pass back a
        // Completable which will signal the success of the navigation.
        PublishSubject.create<Pair<String, PublishSubject<Completable>>>()

    var hudTimeOutInSeconds = Int.MAX_VALUE
    var shouldBlurNsfwPosts = true

    init {
        logcat { "The paging data provider is $pagingDataObservableProvider" }

        rxSharedPreferences.getString("pref_list_hud").asObservable().toV3Observable()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { value ->   // Gets the current value or the default value immediately and reacts to preference changes.
                hudTimeOutInSeconds = when (value) {
                    "stay_on" -> Int.MAX_VALUE
                    else -> value.toInt()
                }
            }.addTo(disposables)

        rxSharedPreferences.getBoolean("pref_switch_unblur_nsfw").asObservable().toV3Observable()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { shouldBlurNsfwPosts = it.not() }
            .addTo(disposables)
    }

    fun getFavoritePosts(): Single<List<PostFavoritesDbEntry>> =
        favoritePostsRepository.getAllFavoritePostsFromDb()

    fun addPostToFavorites(post: Post): Completable =
        favoritePostsRepository.addFavoritePostToDb(post)

    fun removePostFromFavorites(post: Post): Completable =
        favoritePostsRepository.removeFavoritePostFromDb(post)


    fun openRedditLinkOfPost(post: Post, context: Context): Completable {
        logcat { "openRedditLinkOfPost: post = ${post.permalink}" }
        return Completable.create { emitter ->
            try {
                val link = "https://reddit.com${post.permalink}"
                val urlIntent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
                context.startActivity(urlIntent)
                emitter.onComplete()
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { "Could not open link! Message: ${e.message}" }
                emitter.onError(e)
            }
        }
    }

    fun goToUsersSubmissions(post: Post): Completable {
        logcat { "goToUsersSubmissions: post.name = ${post.name}, post.author = ${post.author}" }
        val userName = post.author
        val navigationResultSubject = PublishSubject.create<Completable>()
        navigateToFollowsActionTriggerSubject.onNext(Pair(userName, navigationResultSubject))
        return Completable.create { emitter ->
            navigationResultSubject
                .subscribe { completable ->
                    emitter.fromCompletable(completable)
                        .addTo(disposables)
                }
                .addTo(disposables)
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
                    // We have permission to write to storage.
                    getBitmapWithGlide(link, activity)
                        .subscribeBy(
                            onSuccess = { bitmap ->
                                val fileName = link.split('/').last()   // Get the filename from the URL
                                    .split('.').let {
                                        it.subList(
                                            0,
                                            it.lastIndex
                                        )              // Remove the extension from the filename (we will save it as a .jpg)
                                    }.reduce(String::plus)

                                saveImageToStorage(bitmap, fileName, activity)
                                    .subscribeOn(Schedulers.io())
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribeBy(
                                        onComplete = { emitter.onComplete() },
                                        onError = { emitter.onError(it) }
                                    )
                                    .addTo(disposables)
                            },
                            onError = { emitter.onError(it) }
                        )
                        .addTo(disposables)
                }.addTo(disposables)
        }
    }

    fun setImageAsBackground(link: String, activity: FragmentActivity): Completable {
        return Completable.create { emitter ->
            val permissions = RxPermissions(activity)
            permissions.request(Manifest.permission.SET_WALLPAPER)
                .subscribe { isPermissionGranted ->
                    if (!isPermissionGranted) {
                        logcat(LogPriority.WARN) { "Set wallpaper permission not granted." }
                        emitter.onError(Exception("Set wallpaper permission not granted."))
                    }
                    // We have permission to change the wallpaper.
                    getBitmapWithGlide(link, activity)
                        .subscribeBy(
                            onSuccess = { bitmap ->
                                createUriFromBitmap(bitmap, activity)
                                    .subscribeOn(Schedulers.io())
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .doOnError { e -> emitter.onError(e) }
                                    .subscribe { uri ->
                                        try {
                                            val wallpaperManager = WallpaperManager.getInstance(activity)
                                            val wallpaperIntent = wallpaperManager.getCropAndSetWallpaperIntent(uri)
                                            activity.startActivity(wallpaperIntent)

                                            wallpaperManager.setBitmap(bitmap)
                                            emitter.onComplete()
                                        } catch (e: Exception) {
                                            logcat(LogPriority.ERROR) { "Error attempting to set the wallpaper. Message = ${e.message}" }
                                            emitter.onError(e)
                                        }
                                    }
                                    .addTo(disposables)
                            },
                            onError = { e ->
                                emitter.onError(e)
                            }
                        ).addTo(disposables)
                }.addTo(disposables)
        }
    }


    fun shouldBlurThisPost(post: Post): Boolean {
        logcat { "shouldBlurThisPost: post = ${post.name}" }
        if (!shouldBlurNsfwPosts) return false

        return post.nsfw && !postsPropertiesRepository.isPostInDontBlurThesePostsSet(post)
    }

    fun dontBlurThisPostAnymore(post: Post) {
        logcat { "dontBlurThisPostAnymore: post = ${post.name}" }
        postsPropertiesRepository.addPostToDontBlurThesePostsSet(post)
    }


    private fun getBitmapWithGlide(link: String, context: Context): Single<Bitmap> {
        logcat { "downloadImageWithGlide: link = $link" }
        return Single.create { emitter ->
            try {
                Glide.with(context)
                    .load(link)
                    .into(object : CustomTarget<Drawable>() {
                        override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                            val bitmap = resource.toBitmap()
                            emitter.onSuccess(bitmap)
                        }

                        override fun onLoadCleared(placeholder: Drawable?) {}

                    })
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { "Error loading bitmap with Glide! Message = ${e.message}" }
                emitter.onError(e)
            }
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
                } catch (e: Exception) {
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
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR) { "Error saving image. Message: ${e.message}" }
                    emitter.onError(e)
                }
            }
        }
    }

    /* This function is based on https://stackoverflow.com/a/73524155/6663476 */
    private fun createUriFromBitmap(bitmap: Bitmap, context: Context): Single<Uri> {
        logcat { "createUriFromBitmap" }
        var uri: Uri? = null
        try {
            val fileName = System.nanoTime().toString() + ".png"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                else {
                    val directory =
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
                    val file = File(directory, fileName)
                    put(MediaStore.MediaColumns.DATA, file.absolutePath)
                }
            }

            uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            uri?.let {
                context.contentResolver.openOutputStream(it).use { output ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.apply {
                        clear()
                        put(MediaStore.Audio.Media.IS_PENDING, 0)
                    }
                    context.contentResolver.update(uri, values, null, null)
                }
            }
            return Single.just(uri!!)
        } catch (e: Exception) {
            if (uri != null) {
                logcat(LogPriority.ERROR) { "Error creating URI from Bitmap. Message = ${e.message}" }
                // Don't leave an orphan entry in the MediaStore
                context.contentResolver.delete(uri, null, null)
            }
            return Single.error(e)
        }
    }

    override fun onCleared() {
        logcat { "onCleared" }
        disposables.clear()
        super.onCleared()
    }
}