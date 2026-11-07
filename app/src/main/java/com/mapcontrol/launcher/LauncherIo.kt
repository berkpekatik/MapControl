package com.mapcontrol.launcher

import java.util.ArrayDeque
import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Launcher'ın tüm arka plan G/Ç işi için tek paylaşımlı havuz: 2 daemon iş parçacığı ("launcher-io"),
 * boşta kalınca kendiliğinden kapanır. Havuz hiçbir zaman shutdown edilmez; widget'lar bırakılırken
 * yalnızca kendi [serial] kuyruğunu bırakır.
 */
object LauncherIo {
    private const val THREADS = 2
    private const val IDLE_SECONDS = 20L

    private val pool: ThreadPoolExecutor = ThreadPoolExecutor(
        THREADS, THREADS, IDLE_SECONDS, TimeUnit.SECONDS, LinkedBlockingQueue(),
    ) { r -> Thread(r, "launcher-io").apply { isDaemon = true } }.apply { allowCoreThreadTimeOut(true) }

    /** Widget başına sıralı kuyruk: işler gönderildiği sırayla, aynı anda biri koşar; iş parçacığı sahibi değildir. */
    fun serial(): Executor = SerialQueue(pool)

    private class SerialQueue(private val delegate: Executor) : Executor {
        private val tasks = ArrayDeque<Runnable>()
        private var active: Runnable? = null

        @Synchronized
        override fun execute(command: Runnable) {
            tasks.add(Runnable {
                try {
                    command.run()
                } finally {
                    next()
                }
            })
            if (active == null) next()
        }

        @Synchronized
        private fun next() {
            active = tasks.poll()
            active?.let { delegate.execute(it) }
        }
    }
}
