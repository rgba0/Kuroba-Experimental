package com.github.k1rakishou.chan.core.site.loader

import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor

sealed class ThreadLoadResult {
  data class Error(val exception: ChanLoaderException) : ThreadLoadResult() {
    override fun toString(): String = "ThreadLoadResult.Error{exception=${exception.errorMessageOrClassName()}}"
  }

  data class Loaded(val chanDescriptor: ChanDescriptor) : ThreadLoadResult() {
    override fun toString(): String = "ThreadLoadResult.Loaded{chanDescriptor=${chanDescriptor}}"
  }
}