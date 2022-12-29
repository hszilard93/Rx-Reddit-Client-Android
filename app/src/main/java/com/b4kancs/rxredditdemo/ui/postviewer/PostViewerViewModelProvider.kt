package com.b4kancs.rxredditdemo.ui.postviewer

import com.b4kancs.rxredditdemo.ui.PostPagingDataObservableProvider
import logcat.logcat

interface PostViewerViewModelProviderInterface {

    fun getViewModel(
            pagingDataObservableProvider: PostPagingDataObservableProvider? = null,
            fragmentBackStackId: String? = null
    ): PostViewerViewModel?

    fun persistViewModelForFragmentOnBackStack(fragmentBackStackId: String, viewModel: PostViewerViewModel) { }

    fun removePersistedViewModel(fragmentBackStackId: String)

    fun clearPersistedViewModels()
}


object PostViewerViewModelProvider : PostViewerViewModelProviderInterface {

    private val viewModelStoreMap = mutableMapOf<String, PostViewerViewModel>()

    override fun getViewModel(
            pagingDataObservableProvider: PostPagingDataObservableProvider?,
            fragmentBackStackId: String?
    ): PostViewerViewModel? {
        logcat { "getViewModel: pagingDataObservableProvider = $pagingDataObservableProvider, fragmentBackStackId = $fragmentBackStackId" }
        if (fragmentBackStackId != null) {
            return if (viewModelStoreMap.containsKey(fragmentBackStackId)) {
                val resultViewModel = viewModelStoreMap[fragmentBackStackId]!!
                logcat { "Returning stored instance for fragmentBackStackId = $fragmentBackStackId, viewModel = $resultViewModel" }
                resultViewModel
            }
            else {
                logcat { "Stored instance not found. Returning null." }
                null
            }
        }

        val resultViewModel = PostViewerViewModel(pagingDataObservableProvider!!)
        logcat { "Returning new PostViewerViewModel instance with pagingDataObservableProvider = $pagingDataObservableProvider, viewModel = $resultViewModel" }
        return resultViewModel
    }

    override fun persistViewModelForFragmentOnBackStack(fragmentBackStackId: String, viewModel: PostViewerViewModel) {
        logcat { "persistViewModelForFragmentOnBackStack: fragmentBackStackId = $fragmentBackStackId" }
        viewModelStoreMap[fragmentBackStackId] = viewModel
    }

    override fun removePersistedViewModel(fragmentBackStackId: String) {
        logcat { "removePersistedViewModel: fragmentBackStackId = $fragmentBackStackId" }
        viewModelStoreMap.remove(fragmentBackStackId)
    }

    override fun clearPersistedViewModels() {
        logcat { "clearPersistedViewModels" }
        viewModelStoreMap.clear()
    }
}