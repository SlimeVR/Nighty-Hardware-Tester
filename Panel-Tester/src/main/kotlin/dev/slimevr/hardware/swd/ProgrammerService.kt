package dev.slimevr.hardware.swd

import dev.slimevr.testing.TestStatus
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class ProgrammerService(
    val name: String,
    val config: String
) {
    data class Task(
        val thread: Callable<TestStatus>,
        var future: Future<TestStatus>?
    )

    // Slow RPi3a is able to flash in 5 threads (total 10 targets)
    // RPi4b reliably can do only 4 cause of the datarace issue
    // NOTE: This limit seems USB bandwiths or USB controller speed relatable
    private val threadsCount: Int = 2
    private val runImmediately: Boolean = false
    val shaffle: Boolean = false
    private var exec = Executors.newFixedThreadPool(threadsCount)
    val tasks = mutableListOf<ProgrammerService.Task>()

    fun add(thread: Callable<TestStatus>) {
        val t = ProgrammerService.Task(thread, null)
        tasks.add(t)
        if (runImmediately)
            t.future = exec.submit(t.thread)
    }

    fun runAll() {
        if (!runImmediately) {
            tasks.forEach {
                it.future = exec.submit(it.thread)
            }
        }
    }

    fun await() {
        exec.shutdown()
        exec.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS)
    }

    fun reAddFailed(): Boolean {
        var taskLists = mutableListOf<ProgrammerService.Task>()
        for(t in tasks) {
            val s: TestStatus = t.future!!.get()
            if(s == TestStatus.ERROR)
                taskLists.add(t)
        }
        tasks.clear()
        if(taskLists.isNotEmpty()) {
            // Rerun tasks with fresh executor
            exec = Executors.newFixedThreadPool(threadsCount)
            if(shaffle)
                taskLists.shuffle()
            taskLists.forEach {
                this.add(it.thread)
            }
        }
        return taskLists.isNotEmpty()
    }

    fun clear() = tasks.clear()
}
