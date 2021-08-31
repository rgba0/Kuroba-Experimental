package com.github.k1rakishou.model.data.descriptor

sealed class ChanDescriptor {
  abstract fun isThreadDescriptor(): Boolean
  abstract fun isCatalogDescriptor(): Boolean

  abstract fun siteName(): String
  abstract fun boardCode(): String
  abstract fun threadDescriptorOrNull(): ThreadDescriptor?
  abstract fun catalogDescriptor(): CatalogDescriptor
  abstract fun threadNoOrNull(): Long?

  abstract fun boardDescriptor(): BoardDescriptor
  abstract fun siteDescriptor(): SiteDescriptor
  abstract fun serializeToString(): String
  abstract fun userReadableString(): String

  @Suppress("ReplaceCallWithBinaryOperator")
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is ChanDescriptor) return false
    if (this.javaClass != other.javaClass) return false

    return when (this) {
      // Do not remove the casts
      is ThreadDescriptor -> (this as ThreadDescriptor).equals(other as ThreadDescriptor)
      is CatalogDescriptor -> (this as CatalogDescriptor).equals(other as CatalogDescriptor)
      is CompositeCatalogDescriptor -> (this as CompositeCatalogDescriptor).equals(other as CompositeCatalogDescriptor)
    }
  }

  override fun hashCode(): Int {
    return when (this) {
      // Do not remove the casts
      is ThreadDescriptor -> (this as ThreadDescriptor).hashCode()
      is CatalogDescriptor -> (this as CatalogDescriptor).hashCode()
      is CompositeCatalogDescriptor -> (this as CompositeCatalogDescriptor).hashCode()
    }
  }

  @JvmOverloads
  fun toThreadDescriptor(threadNo: Long? = null): ThreadDescriptor {
    return when (this) {
      is ThreadDescriptor -> {
        check(this.threadNo == threadNo) {
          "Attempt to convert thread descriptor (${this.threadNo}) " +
                  "into another thread descriptor (${threadNo})"
        }

        this
      }
      is CatalogDescriptor -> {
        ThreadDescriptor.create(boardDescriptor, threadNo!!)
      }
      is CompositeCatalogDescriptor -> error("Can't convert into thread descriptor")
    }
  }

  class ThreadDescriptor private constructor(
    val boardDescriptor: BoardDescriptor,
    val threadNo: Long
  ) : ChanDescriptor() {

    override fun isThreadDescriptor(): Boolean = true
    override fun isCatalogDescriptor(): Boolean = false

    override fun siteName(): String = boardDescriptor.siteDescriptor.siteName
    override fun boardCode(): String = boardDescriptor.boardCode
    override fun threadDescriptorOrNull(): ThreadDescriptor? = this
    override fun catalogDescriptor(): CatalogDescriptor = CatalogDescriptor.create(boardDescriptor)
    override fun threadNoOrNull(): Long? = threadNo
    override fun siteDescriptor(): SiteDescriptor = boardDescriptor.siteDescriptor
    override fun boardDescriptor(): BoardDescriptor = boardDescriptor

    fun toOriginalPostDescriptor(): PostDescriptor {
      return PostDescriptor.create(this, threadNo)
    }

    override fun serializeToString(): String {
      return "TD_${boardDescriptor.siteName()}_${boardDescriptor.boardCode}_${threadNo}"
    }

    override fun userReadableString(): String {
      return "${boardDescriptor.siteDescriptor.siteName}/${boardDescriptor.boardCode}/$threadNo"
    }

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other !is ThreadDescriptor) return false

      if (boardDescriptor != other.boardDescriptor) return false
      if (threadNo != other.threadNo) return false

      return true
    }

    override fun hashCode(): Int {
      var result = boardDescriptor.hashCode()
      result = 31 * result + threadNo.hashCode()
      return result
    }

    override fun toString(): String {
      return "TD{${boardDescriptor.siteDescriptor.siteName}/${boardDescriptor.boardCode}/$threadNo}"
    }

    companion object {
      @JvmStatic
      fun create(siteName: String, boardCode: String, threadNo: Long): ThreadDescriptor {
        require(threadNo > 0) { "Bad threadId: $threadNo" }

        return create(BoardDescriptor.create(siteName, boardCode), threadNo)
      }

      @JvmStatic
      fun create(chanDescriptor: ChanDescriptor, threadNo: Long): ThreadDescriptor {
        return create(chanDescriptor.boardDescriptor(), threadNo)
      }

      @JvmStatic
      fun create(boardDescriptor: BoardDescriptor, threadNo: Long): ThreadDescriptor {
        require(threadNo > 0) { "Bad threadId: $threadNo" }

        return ThreadDescriptor(boardDescriptor, threadNo)
      }

      fun fromDescriptorParcelable(descriptorParcelable: DescriptorParcelable): ThreadDescriptor {
        require(descriptorParcelable.isThreadDescriptor()) { "Not a thread descriptor type" }
        require(descriptorParcelable is SingleDescriptorParcelable) { "Must be SingleDescriptorParcelable" }

        return create(
          descriptorParcelable.siteName,
          descriptorParcelable.boardCode,
          descriptorParcelable.threadNo!!
        )
      }
    }
  }

  // ICatalogDescriptor is a marker for all types of CatalogDescriptor (for now there are only two
  // of them: CatalogDescriptor and CompositeCatalogDescriptor). Very handy when type checking
  // ChanDescriptors to only allow/disallow catalog descriptors.
  sealed interface ICatalogDescriptor

  class CatalogDescriptor private constructor(
    val boardDescriptor: BoardDescriptor
  ) : ChanDescriptor(), ICatalogDescriptor {
    override fun isThreadDescriptor(): Boolean = false
    override fun isCatalogDescriptor(): Boolean = true

    override fun siteName(): String = boardDescriptor.siteDescriptor.siteName
    override fun boardCode(): String = boardDescriptor.boardCode
    override fun threadDescriptorOrNull(): ThreadDescriptor? = null
    override fun catalogDescriptor(): CatalogDescriptor = this
    override fun threadNoOrNull(): Long? = null
    override fun siteDescriptor(): SiteDescriptor = boardDescriptor.siteDescriptor
    override fun boardDescriptor(): BoardDescriptor = boardDescriptor

    override fun serializeToString(): String {
      return "CD_${boardDescriptor.siteName()}_${boardDescriptor.boardCode}"
    }

    override fun userReadableString(): String {
      return "${boardDescriptor.siteName()}/${boardDescriptor.boardCode}"
    }

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other !is CatalogDescriptor) return false

      if (boardDescriptor != other.boardDescriptor) return false

      return true
    }

    override fun hashCode(): Int {
      return boardDescriptor.hashCode()
    }

    override fun toString(): String {
      return "CD{${boardDescriptor.siteDescriptor.siteName}/${boardDescriptor.boardCode}}"
    }

    companion object {
      fun fromDescriptorParcelable(descriptorParcelable: DescriptorParcelable): CatalogDescriptor {
        require(!descriptorParcelable.isThreadDescriptor()) { "Not a catalog descriptor type" }
        require(descriptorParcelable is SingleDescriptorParcelable) { "Must be SingleDescriptorParcelable" }

        return create(
          descriptorParcelable.siteName,
          descriptorParcelable.boardCode
        )
      }

      @JvmStatic
      fun create(boardDescriptor: BoardDescriptor): CatalogDescriptor {
        return create(boardDescriptor.siteName(), boardDescriptor.boardCode)
      }

      @JvmStatic
      fun create(siteNameInput: String, boardCodeInput: String): CatalogDescriptor {
        val siteName = siteNameInput.intern()
        val boardCode = boardCodeInput.intern()

        return CatalogDescriptor(BoardDescriptor.create(siteName, boardCode))
      }
    }
  }

  class CompositeCatalogDescriptor(
    val catalogDescriptors: List<CatalogDescriptor>
  ) : ChanDescriptor(), ICatalogDescriptor {

    init {
      check(catalogDescriptors.isNotEmpty()) {
        "catalogDescriptors must not be empty!"
      }
      check(catalogDescriptors.size > 1) {
        "Use CatalogDescriptor for a single catalog!"
      }
      check(catalogDescriptors.size <= MAX_CATALOGS_COUNT) {
        "Composite descriptor has too many catalog descriptors! (count=${catalogDescriptors.size})"
      }
    }

    override fun isThreadDescriptor(): Boolean = false
    override fun isCatalogDescriptor(): Boolean = true

    override fun siteName(): String = error("Can't use site name")

    override fun boardCode(): String = error("Can't use board name")

    override fun threadDescriptorOrNull(): ThreadDescriptor? = null

    override fun catalogDescriptor(): CatalogDescriptor = error("Can't convert into CatalogDescriptor")

    override fun threadNoOrNull(): Long? = null

    override fun boardDescriptor(): BoardDescriptor = error("Can't use board descriptor")

    override fun siteDescriptor(): SiteDescriptor = error("Can't use site descriptor")

    override fun serializeToString(): String {
      // TODO(KurobaEx): CompositeCatalogDescriptor
      error("Can't serialize to string")
    }

    override fun userReadableString(): String {
      return catalogDescriptors.joinToString(
        separator = "+",
        transform = { catalogDescriptor -> catalogDescriptor.boardCode() }
      )
    }

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as CompositeCatalogDescriptor

      if (catalogDescriptors != other.catalogDescriptors) return false

      return true
    }

    override fun hashCode(): Int {
      return 31 * catalogDescriptors.hashCode()
    }

    override fun toString(): String {
      val joined = catalogDescriptors.joinToString(
        separator = "+",
        transform = { catalogDescriptor ->
          val boardDescriptor = catalogDescriptor.boardDescriptor
          return@joinToString "${boardDescriptor.siteDescriptor.siteName}/${boardDescriptor.boardCode}"
        }
      )

      return "CCD{$joined}"
    }

    companion object {
      const val MAX_CATALOGS_COUNT = 10

      fun fromDescriptorParcelable(descriptorParcelable: DescriptorParcelable): CompositeCatalogDescriptor {
        require(descriptorParcelable is CompositeDescriptorParcelable) { "Must be CompositeDescriptorParcelable" }

        return CompositeCatalogDescriptor(descriptorParcelable.toCatalogDescriptors())
      }
    }

  }
}