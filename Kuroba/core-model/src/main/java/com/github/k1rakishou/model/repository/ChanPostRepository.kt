package com.github.k1rakishou.model.repository

import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.ModularResult.Companion.Try
import com.github.k1rakishou.common.MurmurHashUtils
import com.github.k1rakishou.common.SuspendableInitializer
import com.github.k1rakishou.common.linkedMapWithCap
import com.github.k1rakishou.common.mutableMapWithCap
import com.github.k1rakishou.common.myAsync
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.KurobaDatabase
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanOriginalPost
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.source.cache.ChanCacheOptions
import com.github.k1rakishou.model.source.cache.thread.ChanThreadsCache
import com.github.k1rakishou.model.source.local.ChanPostLocalSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

class ChanPostRepository(
  database: KurobaDatabase,
  private val isDevFlavor: Boolean,
  private val applicationScope: CoroutineScope,
  private val appConstants: AppConstants,
  private val localSource: ChanPostLocalSource,
  private val chanThreadsCache: ChanThreadsCache
) : AbstractRepository(database) {
  private val TAG = "ChanPostRepository"
  private val suspendableInitializer = SuspendableInitializer<Unit>("ChanPostRepository")

  init {
    applicationScope.launch(Dispatchers.Default) {
      // We need to first delete the posts, so that the threads are only left with the OP
      val postDeleteResult = deleteOldPostsIfNeeded().mapValue { Unit }
      if (postDeleteResult is ModularResult.Error) {
        suspendableInitializer.initWithModularResult(postDeleteResult)
        return@launch
      }

      // Then we can delete the threads themselves
      val threadDeleteResult = deleteOldThreadsIfNeeded().mapValue { Unit }
      suspendableInitializer.initWithModularResult(threadDeleteResult)
    }
  }

  suspend fun awaitUntilInitialized() = suspendableInitializer.awaitUntilInitialized()

  suspend fun getTotalCachedPostsCount(): Int {
    check(suspendableInitializer.isInitialized()) { "ChanPostRepository is not initialized yet!" }

    return applicationScope.myAsync {
      return@myAsync chanThreadsCache.getTotalCachedPostsCount()
    }
  }

  suspend fun createEmptyThreadIfNotExists(descriptor: ChanDescriptor.ThreadDescriptor): ModularResult<Long?> {
    check(suspendableInitializer.isInitialized()) { "ChanPostRepository is not initialized yet!" }

    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        return@tryWithTransaction localSource.insertEmptyThread(descriptor)
      }
    }
  }

  /**
   * Returns a list of posts that differ from the cached ones and which we want to parse again and
   * show the user (otherwise show cached posts)
   * */
  @Suppress("UNCHECKED_CAST")
  suspend fun insertOrUpdateMany(
    posts: List<ChanPost>,
    cacheOptions: ChanCacheOptions,
    isCatalog: Boolean
  ): ModularResult<List<Long>> {
    check(suspendableInitializer.isInitialized()) { "ChanPostRepository is not initialized yet!" }

    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        if (isCatalog) {
          require(posts.all { post -> post is ChanOriginalPost }) {
            "Not all posts are original posts"
          }

          return@tryWithTransaction insertOrUpdateCatalogOriginalPosts(
            posts as List<ChanOriginalPost>,
            cacheOptions
          )
        } else {
          return@tryWithTransaction insertOrUpdateThreadPosts(
            posts,
            cacheOptions
          )
        }
      }
    }
  }

  fun getCachedThreadPostsNos(threadDescriptor: ChanDescriptor.ThreadDescriptor): Set<Long> {
    check(suspendableInitializer.isInitialized()) { "ChanPostRepository is not initialized yet!" }

    return chanThreadsCache.getThreadPostNoSet(threadDescriptor)
  }

  fun getCachedPost(postDescriptor: PostDescriptor, isOP: Boolean): ChanPost? {
    check(suspendableInitializer.isInitialized()) { "ChanPostRepository is not initialized yet!" }

    check(postDescriptor.isOP() == isOP) {
      "isOP flags differ for ($postDescriptor), " +
        "postDescriptor.isOP: ${postDescriptor.isOP()}, isOP: $isOP"
    }

    if (isOP) {
      return chanThreadsCache.getOriginalPostFromCache(postDescriptor)
    } else {
      return chanThreadsCache.getPostFromCache(postDescriptor)
    }
  }

  fun putPostHash(postDescriptor: PostDescriptor, hash: MurmurHashUtils.Murmur3Hash) {
    check(suspendableInitializer.isInitialized()) { "ChanPostRepository is not initialized yet!" }

    chanThreadsCache.putPostHash(postDescriptor, hash)
  }

  fun getPostHash(postDescriptor: PostDescriptor): MurmurHashUtils.Murmur3Hash? {
    check(suspendableInitializer.isInitialized()) { "ChanPostRepository is not initialized yet!" }

    return chanThreadsCache.getPostHash(postDescriptor)
  }

  fun markThreadAsDeleted(threadDescriptor: ChanDescriptor.ThreadDescriptor, deleted: Boolean) {
    chanThreadsCache.markThreadAsDeleted(threadDescriptor, deleted)
  }

  suspend fun getCatalogOriginalPosts(
    descriptor: ChanDescriptor.CatalogDescriptor,
    count: Int
  ): ModularResult<List<ChanPost>> {
    check(suspendableInitializer.isInitialized()) { "ChanPostRepository is not initialized yet!" }
    require(count > 0) { "Bad count param: $count" }

    Logger.d(TAG, "getCatalogOriginalPosts(descriptor=$descriptor, count=$count)")

    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        val catalogPosts = localSource.getCatalogOriginalPosts(
          descriptor,
          count
        )

        if (catalogPosts.isNotEmpty()) {
          chanThreadsCache.putManyCatalogPostsIntoCache(catalogPosts)
        }

        return@tryWithTransaction catalogPosts
          // Sort in descending order by threads' lastModified value because that's the BUMP ordering
          .sortedByDescending { chanPost -> chanPost.lastModified }
      }
    }
  }

  suspend fun getCatalogOriginalPosts(
    descriptor: ChanDescriptor.CatalogDescriptor,
    threadNoList: Collection<Long>
  ): ModularResult<List<ChanPost>> {
    check(suspendableInitializer.isInitialized()) { "ChanPostRepository is not initialized yet!" }

    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        val originalPostsFromCache = threadNoList.mapNotNull { threadNo ->
          chanThreadsCache.getOriginalPostFromCache(descriptor.toThreadDescriptor(threadNo))
        }

        val originalPostNoFromCacheSet = originalPostsFromCache.map { post ->
          post.postDescriptor.postNo
        }.toSet()

        val originalPostNoListToGetFromDatabase = threadNoList.filter { threadNo ->
          threadNo !in originalPostNoFromCacheSet
        }

        if (originalPostNoListToGetFromDatabase.isEmpty()) {
          // All posts were found in the cache
          Logger.d(TAG, "getCatalogOriginalPosts() found all posts in the cache " +
            "(count=${originalPostsFromCache.size})")
          return@tryWithTransaction originalPostsFromCache
        }

        val catalogPostsFromDatabase = localSource.getCatalogOriginalPosts(
          descriptor,
          originalPostNoListToGetFromDatabase
        )

        if (catalogPostsFromDatabase.isNotEmpty()) {
          chanThreadsCache.putManyCatalogPostsIntoCache(catalogPostsFromDatabase)
        }

        Logger.d(TAG, "getCatalogOriginalPosts() found ${originalPostsFromCache.size} posts in " +
          "the cache and the rest (${catalogPostsFromDatabase.size}) taken from the database")
        return@tryWithTransaction originalPostsFromCache + catalogPostsFromDatabase
      }
    }
  }

  /**
   * Returns LinkedHashMap of OP posts associated with thread descriptors sorted in the order of [threadDescriptors]
   * */
  suspend fun getCatalogOriginalPosts(
    threadDescriptors: Collection<ChanDescriptor.ThreadDescriptor>
  ): ModularResult<LinkedHashMap<ChanDescriptor.ThreadDescriptor, ChanOriginalPost>> {
    check(suspendableInitializer.isInitialized()) { "ChanPostRepository is not initialized yet!" }

    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        val originalPostsFromCache = chanThreadsCache.getCatalogPostsFromCache(threadDescriptors)

        val notCachedOriginalPostThreadDescriptors = threadDescriptors.filter { threadDescriptor ->
          !originalPostsFromCache.containsKey(threadDescriptor)
        }

        if (notCachedOriginalPostThreadDescriptors.isEmpty()) {
          // All posts were found in the cache
          Logger.d(TAG, "getCatalogOriginalPosts() found all posts in the cache " +
            "(count=${originalPostsFromCache.size})")
          return@tryWithTransaction originalPostsFromCache
        }

        val catalogPostsFromDatabase = localSource.getCatalogOriginalPosts(
          notCachedOriginalPostThreadDescriptors
        )

        if (catalogPostsFromDatabase.isNotEmpty()) {
          chanThreadsCache.putManyCatalogPostsIntoCache(catalogPostsFromDatabase.values.toList())
        }

        val tempMap = mutableMapWithCap<ChanDescriptor.ThreadDescriptor, ChanOriginalPost>(
          originalPostsFromCache.size + catalogPostsFromDatabase.size
        )

        Logger.d(TAG, "getCatalogOriginalPosts() found ${originalPostsFromCache.size} posts in " +
          "the cache and the rest (${catalogPostsFromDatabase.size}) taken from the database")

        tempMap.putAll(originalPostsFromCache)
        tempMap.putAll(catalogPostsFromDatabase)

        val resultMap = linkedMapWithCap<ChanDescriptor.ThreadDescriptor, ChanOriginalPost>(tempMap.size)

        threadDescriptors.forEach { threadDescriptor ->
          resultMap[threadDescriptor] = requireNotNull(tempMap[threadDescriptor])
        }

        return@tryWithTransaction resultMap
      }
    }
  }

  @OptIn(ExperimentalTime::class)
  suspend fun preloadForThread(threadDescriptor: ChanDescriptor.ThreadDescriptor): ModularResult<Unit> {
    check(suspendableInitializer.isInitialized()) { "ChanPostRepository is not initialized yet!" }

    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        Logger.d(TAG, "preloadForThread($threadDescriptor) begin")

        val time = measureTime {
          val postsFromDatabase = localSource.getThreadPosts(threadDescriptor)
          Logger.d(TAG, "preloadForThread($threadDescriptor) got ${postsFromDatabase.size} from DB")

          if (postsFromDatabase.isNotEmpty()) {
            chanThreadsCache.putManyThreadPostsIntoCache(
              postsFromDatabase,
              ChanCacheOptions.StoreInMemory
            )
          }
        }

        Logger.d(TAG, "preloadForThread($threadDescriptor) end, took $time")
      }
    }
  }

  suspend fun getThreadPosts(
    descriptor: ChanDescriptor.ThreadDescriptor
  ): ModularResult<List<ChanPost>> {
    check(suspendableInitializer.isInitialized()) { "ChanPostRepository is not initialized yet!" }
    Logger.d(TAG, "getThreadPosts(descriptor=$descriptor)")

    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        val postsFromCache = chanThreadsCache.getThreadPosts(descriptor)
        if (postsFromCache.isNotEmpty()) {
          return@tryWithTransaction postsFromCache
        }

        val postsFromDatabase = localSource.getThreadPosts(descriptor)
        if (postsFromDatabase.isEmpty()) {
          return@tryWithTransaction emptyList()
        }

        chanThreadsCache.putManyThreadPostsIntoCache(
          postsFromDatabase,
          ChanCacheOptions.StoreInMemory
        )

        return@tryWithTransaction postsFromDatabase
      }
    }
  }

  suspend fun deleteAll(): ModularResult<Int> {
    check(suspendableInitializer.isInitialized()) { "ChanPostRepository is not initialized yet!" }

    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        val result = localSource.deleteAll()
        chanThreadsCache.deleteAll()

        return@tryWithTransaction result
      }
    }
  }

  suspend fun deleteThread(threadDescriptor: ChanDescriptor.ThreadDescriptor): ModularResult<Unit> {
    check(suspendableInitializer.isInitialized()) { "ChanPostRepository is not initialized yet!" }

    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        val result = localSource.deleteThread(threadDescriptor)
        chanThreadsCache.deleteThread(threadDescriptor)

        return@tryWithTransaction result
      }
    }
  }

  suspend fun deleteCatalog(catalogDescriptor: ChanDescriptor.CatalogDescriptor): ModularResult<Unit> {
    check(suspendableInitializer.isInitialized()) { "ChanPostRepository is not initialized yet!" }

    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        val threadDescriptors = chanThreadsCache.getCatalog(catalogDescriptor)
          ?.mapPostsOrdered { chanOriginalPost -> chanOriginalPost.postDescriptor.threadDescriptor() }
          ?.distinct()

        if (threadDescriptors != null) {
          localSource.deleteCatalog(threadDescriptors)
        }

        return@tryWithTransaction
      }
    }
  }

  suspend fun deletePost(postDescriptor: PostDescriptor): ModularResult<Unit> {
    check(suspendableInitializer.isInitialized()) { "ChanPostRepository is not initialized yet!" }

    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        localSource.deletePost(postDescriptor)
        chanThreadsCache.deletePost(postDescriptor)

        return@tryWithTransaction
      }
    }
  }

  suspend fun totalPostsCount(): ModularResult<Int> {
    check(suspendableInitializer.isInitialized()) { "ChanPostRepository is not initialized yet!" }

    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        return@tryWithTransaction localSource.countTotalAmountOfPosts()
      }
    }
  }

  suspend fun totalThreadsCount(): ModularResult<Int> {
    check(suspendableInitializer.isInitialized()) { "ChanPostRepository is not initialized yet!" }

    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        return@tryWithTransaction localSource.countTotalAmountOfThreads()
      }
    }
  }

  private suspend fun insertOrUpdateCatalogOriginalPosts(
    posts: List<ChanOriginalPost>,
    cacheOptions: ChanCacheOptions
  ): List<Long> {
    if (posts.isEmpty()) {
      return emptyList()
    }

    localSource.insertManyOriginalPosts(posts, cacheOptions)

    if (posts.isNotEmpty()) {
      chanThreadsCache.putManyCatalogPostsIntoCache(posts)
    }

    return posts.map { it.postDescriptor.postNo }
  }

  private suspend fun insertOrUpdateThreadPosts(
    posts: List<ChanPost>,
    cacheOptions: ChanCacheOptions
  ): List<Long> {
    var originalPost: ChanOriginalPost? = null
    val postsThatDifferWithCache = ArrayList<ChanPost>()

    // Figure out what posts differ from the cache that we want to update in the
    // database
    posts.forEach { chanPost ->
      val differsFromCached = postDiffersFromCached(chanPost)
      if (differsFromCached) {
        if (chanPost is ChanOriginalPost) {
          if (originalPost != null) {
            throw IllegalStateException("More than one OP found!")
          }

          originalPost = chanPost
        }

        postsThatDifferWithCache += chanPost
      }
    }

    if (originalPost == null) {
      Logger.e(TAG, "Posts have no original post")
      return emptyList()
    }

    if (postsThatDifferWithCache.isEmpty()) {
      Logger.d(TAG, "postsThatDifferWithCache is empty")
      return emptyList()
    }

    Logger.d(TAG, "insertOrUpdateThreadPosts() ${postsThatDifferWithCache.size} posts differ from " +
      "the cache (total posts=${posts.size})")

    val chanThreadId = localSource.getThreadIdByPostDescriptor(originalPost!!.postDescriptor)
    if (chanThreadId == null) {
      return originalPost?.postDescriptor?.postNo
        ?.let { post -> listOf(post) }
        ?: emptyList()
    }

    localSource.insertPosts(postsThatDifferWithCache, cacheOptions)
    chanThreadsCache.putManyThreadPostsIntoCache(postsThatDifferWithCache, cacheOptions)

    return postsThatDifferWithCache.map { it.postDescriptor.postNo }
  }

  suspend fun cleanupPostsInRollingStickyThread(
    threadDescriptor: ChanDescriptor.ThreadDescriptor,
    threadCap: Int
  ): ModularResult<Unit> {
    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        val chanThread = chanThreadsCache.getThread(threadDescriptor)
          ?: return@tryWithTransaction

        val postDescriptors = chanThread.cleanupPostsInRollingStickyThread(threadCap)
        localSource.deletePosts(postDescriptors)

        Logger.d(TAG, "cleanupPostsInRollingStickyThread() deleted ${postDescriptors.size} " +
          "posts from cache and database")
      }
    }
  }

  @OptIn(ExperimentalTime::class)
  suspend fun deleteOldPostsIfNeeded(forced: Boolean = false): ModularResult<ChanPostLocalSource.DeleteResult> {
    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        val totalAmountOfPostsInDatabase = localSource.countTotalAmountOfPosts()
        if (totalAmountOfPostsInDatabase <= 0) {
          Logger.d(TAG, "deleteOldPostsIfNeeded database is empty")
          return@tryWithTransaction ChanPostLocalSource.DeleteResult()
        }

        val maxPostsAmount = appConstants.maxAmountOfPostsInDatabase

        if (!forced && totalAmountOfPostsInDatabase < maxPostsAmount) {
          Logger.d(TAG, "Not enough posts to start deleting, " +
            "posts in database amount: $totalAmountOfPostsInDatabase, " +
            "max allowed posts amount: $maxPostsAmount")
          return@tryWithTransaction ChanPostLocalSource.DeleteResult()
        }

        val toDeleteCount = if (forced) {
          totalAmountOfPostsInDatabase / 2
        } else {
          // Delete half of the posts in the database
          max(totalAmountOfPostsInDatabase, maxPostsAmount) / 2
        }

        if (toDeleteCount <= 0) {
          return@tryWithTransaction ChanPostLocalSource.DeleteResult()
        }

        Logger.d(TAG, "Starting deleting $toDeleteCount posts " +
          "(totalAmountOfPostsInDatabase = $totalAmountOfPostsInDatabase, " +
          "maxPostsAmount = $maxPostsAmount)")

        val (deleteMRResult, time) = measureTimedValue { Try { localSource.deleteOldPosts(toDeleteCount) } }
        val deleteResult = if (deleteMRResult is ModularResult.Error) {
          Logger.d(TAG, "Error while trying to delete old posts", deleteMRResult.error)
          throw deleteMRResult.error
        } else {
          (deleteMRResult as ModularResult.Value).value
        }

        val newAmount = localSource.countTotalAmountOfPosts()
        Logger.d(TAG, "Deleted ${deleteResult.deletedTotal} posts, " +
          "skipped ${deleteResult.skippedTotal} posts, $newAmount posts left, took $time")

        return@tryWithTransaction deleteResult
      }
    }
  }

  @OptIn(ExperimentalTime::class)
  suspend fun deleteOldThreadsIfNeeded(forced: Boolean = false): ModularResult<ChanPostLocalSource.DeleteResult> {
    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        val totalAmountOfThreadsInDatabase = localSource.countTotalAmountOfThreads()
        if (totalAmountOfThreadsInDatabase <= 0) {
          Logger.d(TAG, "deleteOldThreadsIfNeeded database is empty")
          return@tryWithTransaction ChanPostLocalSource.DeleteResult()
        }

        val maxThreadsAmount = appConstants.maxAmountOfThreadsInDatabase

        if (!forced && totalAmountOfThreadsInDatabase < maxThreadsAmount) {
          Logger.d(TAG, "Not enough threads to start deleting, " +
            "threads in database amount: $totalAmountOfThreadsInDatabase, " +
            "max allowed threads amount: $maxThreadsAmount")
          return@tryWithTransaction ChanPostLocalSource.DeleteResult()
        }

        val toDeleteCount = if (forced) {
          totalAmountOfThreadsInDatabase / 2
        } else {
          // Delete half of the posts in the database
          max(totalAmountOfThreadsInDatabase, maxThreadsAmount) / 2
        }

        if (toDeleteCount <= 0) {
          return@tryWithTransaction ChanPostLocalSource.DeleteResult()
        }

        Logger.d(TAG, "Starting deleting $toDeleteCount threads " +
          "(totalAmountOfThreadsInDatabase = $totalAmountOfThreadsInDatabase, " +
          "maxThreadsAmount = $maxThreadsAmount)")

        val (deleteMRResult, time) = measureTimedValue { Try { localSource.deleteOldThreads(toDeleteCount) } }
        val deleteResult = if (deleteMRResult is ModularResult.Error) {
          Logger.d(TAG, "Error while trying to delete old threads", deleteMRResult.error)
          throw deleteMRResult.error
        } else {
          (deleteMRResult as ModularResult.Value).value
        }

        val newAmount = localSource.countTotalAmountOfThreads()
        Logger.d(TAG, "Deleted ${deleteResult.deletedTotal} threads, " +
          "skipped ${deleteResult.skippedTotal} threads, $newAmount threads left, took $time")

        return@tryWithTransaction deleteResult
      }
    }
  }

  private fun postDiffersFromCached(chanPost: ChanPost): Boolean {
    val fromCache = if (chanPost is ChanOriginalPost) {
      chanThreadsCache.getOriginalPostFromCache(chanPost.postDescriptor)
    } else {
      chanThreadsCache.getPostFromCache(chanPost.postDescriptor)
    }

    if (fromCache == null) {
      // Post is not cached yet - update
      return true
    }

    if (fromCache is ChanOriginalPost) {
      // Cached post is an original post - always update
      return true
    }

    if (fromCache != chanPost) {
      // Cached post is not the same as the fresh post - update
      return true
    }

    return false
  }

}