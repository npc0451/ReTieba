package dev.infinityf4p.tiebapure.core.media

import android.content.Context

object ReaderCachePreferences {
    val imageLimitsMb = listOf(64, 128, 256, 512)
    fun imageLimitMb(context: Context): Int = context.getSharedPreferences("reader-cache", Context.MODE_PRIVATE)
        .getInt("image-limit-mb", 256).takeIf { it in imageLimitsMb } ?: 256

    fun setImageLimitMb(context: Context, value: Int) {
        require(value in imageLimitsMb)
        check(context.getSharedPreferences("reader-cache", Context.MODE_PRIVATE).edit()
            .putInt("image-limit-mb", value).commit()) { "无法保存缓存设置，请重试。" }
    }
}
