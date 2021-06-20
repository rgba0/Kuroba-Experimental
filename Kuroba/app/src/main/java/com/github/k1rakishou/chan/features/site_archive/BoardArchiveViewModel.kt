package com.github.k1rakishou.chan.features.site_archive

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.github.k1rakishou.chan.core.base.BaseViewModel
import com.github.k1rakishou.chan.core.compose.AsyncData
import com.github.k1rakishou.chan.core.di.component.viewmodel.ViewModelComponent
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.sites.archive.NativeArchivePost
import com.github.k1rakishou.common.BadStatusResponseException
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

class BoardArchiveViewModel(
  private val catalogDescriptor: ChanDescriptor.CatalogDescriptor
) : BaseViewModel() {

  @Inject
  lateinit var siteManager: SiteManager
  @Inject
  lateinit var themeEngine: ThemeEngine

  private val _state = MutableStateFlow(ViewModelState())
  private var _searchQuery = mutableStateOf("")
  private var _archiveThreadsAsync: AsyncData<List<ArchiveThread>> = AsyncData.NotInitialized

  val state: StateFlow<ViewModelState>
    get() = _state.asStateFlow()
  val searchQuery: State<String>
    get() = _searchQuery

  override fun injectDependencies(component: ViewModelComponent) {
    component.inject(this)
  }

  override suspend fun onViewModelReady() {
    _state.updateState { copy(archiveThreadsAsync = AsyncData.Loading) }

    val nativeArchivePostListResult = siteManager.bySiteDescriptor(catalogDescriptor.siteDescriptor())
      ?.actions()
      ?.archive(catalogDescriptor.boardDescriptor)

    if (nativeArchivePostListResult == null) {
      val exception = ArchiveNotSupportedException(catalogDescriptor.boardCode())
      _state.updateState { copy(archiveThreadsAsync = AsyncData.Error(exception)) }
      return
    }

    val nativeArchivePostList = if (nativeArchivePostListResult is ModularResult.Error) {
      val error = nativeArchivePostListResult.error

      if (error is BadStatusResponseException && error.status == 404) {
        val exception = ArchiveNotSupportedException(catalogDescriptor.boardCode())
        _state.updateState { copy(archiveThreadsAsync = AsyncData.Error(exception)) }
        return
      }

      _state.updateState { copy(archiveThreadsAsync = AsyncData.Error(error)) }
      return
    } else {
      nativeArchivePostListResult.valueOrNull()!!
    }

    val archiveThreads = nativeArchivePostList.map { nativeArchivePost ->
      when (nativeArchivePost) {
        is NativeArchivePost.Chan4NativeArchivePost -> {
          return@map ArchiveThread(
            threadNo = nativeArchivePost.threadNo,
            comment = nativeArchivePost.comment.toString()
          )
        }
      }
    }

    _archiveThreadsAsync = AsyncData.Data(archiveThreads)
    _state.updateState { copy(archiveThreadsAsync = AsyncData.Data(archiveThreads)) }
  }

  fun updateSearchQuery(query: String) {
    if (query.isEmpty()) {
      if (_archiveThreadsAsync is AsyncData.Data) {
        _state.updateState { copy(archiveThreadsAsync = _archiveThreadsAsync) }
      }

      _searchQuery.value = ""
      return
    }

    val archiveThreadsAsync = _archiveThreadsAsync
    if (archiveThreadsAsync !is AsyncData.Data) {
      _searchQuery.value = ""
      return
    }

    val filteredArchiveThreads = archiveThreadsAsync.data.filter { archiveThread ->
      val threadNoStr = archiveThread.threadNo.toString()
      if (threadNoStr.contains(query, ignoreCase = true)) {
        return@filter true
      }

      if (archiveThread.comment.contains(query, ignoreCase = true)) {
        return@filter true
      }

      return@filter false
    }

    _searchQuery.value = query
    _state.updateState { copy(archiveThreadsAsync = AsyncData.Data(filteredArchiveThreads)) }
  }

  data class ViewModelState(
    val archiveThreadsAsync: AsyncData<List<ArchiveThread>> = AsyncData.NotInitialized
  )

  data class ArchiveThread(
    val threadNo: Long,
    val comment: String
  )

  class ArchiveNotSupportedException(boardCode: String) : Exception("Board '/$boardCode/' has no archive")
}