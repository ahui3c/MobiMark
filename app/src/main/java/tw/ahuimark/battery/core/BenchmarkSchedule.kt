package tw.ahuimark.battery.core

import tw.ahuimark.battery.model.LOOP_DURATION_MS
import tw.ahuimark.battery.model.WEB_WORKLOAD_DURATION_MS
import tw.ahuimark.battery.model.WORKLOAD_DURATION_MS
import tw.ahuimark.battery.model.ACTIVE_WORKLOADS
import tw.ahuimark.battery.model.WorkloadType

data class WorkloadPosition(
    val workload: WorkloadType,
    val workloadIndex: Int,
    val elapsedInWorkloadMs: Long,
    val workloadDurationMs: Long,
    val completedLoops: Int
)

object BenchmarkSchedule {
    fun positionAt(elapsedMs: Long): WorkloadPosition {
        val safeElapsed = elapsedMs.coerceAtLeast(0L)
        val elapsedInLoop = safeElapsed % LOOP_DURATION_MS
        var cursor = 0L
        val index = ACTIVE_WORKLOADS.indexOfFirst { workload ->
            val end = cursor + durationFor(workload)
            val active = elapsedInLoop < end
            cursor = end
            active
        }.coerceAtLeast(0)
        val workload = ACTIVE_WORKLOADS[index]
        val workloadStart = ACTIVE_WORKLOADS.take(index).sumOf(::durationFor)
        return WorkloadPosition(
            workload = workload,
            workloadIndex = index,
            elapsedInWorkloadMs = elapsedInLoop - workloadStart,
            workloadDurationMs = durationFor(workload),
            completedLoops = (safeElapsed / LOOP_DURATION_MS).toInt()
        )
    }

    fun durationFor(workload: WorkloadType): Long =
        if (workload == WorkloadType.WEB) WEB_WORKLOAD_DURATION_MS else WORKLOAD_DURATION_MS
}
