package app.securify.core

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import kotlin.concurrent.thread

class SubUpdateJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        thread { Subs.refreshDue(this); jobFinished(params, false) }
        return true
    }

    override fun onStopJob(params: JobParameters) = true

    companion object {
        private const val ID = 1

        fun schedule(ctx: Context, enabled: Boolean) {
            val js = ctx.getSystemService(JobScheduler::class.java)
            if (!enabled) return js.cancel(ID)
            if (js.getPendingJob(ID) != null) return
            js.schedule(
                JobInfo.Builder(ID, ComponentName(ctx, SubUpdateJob::class.java))
                    .setPeriodic(3_600_000L)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPersisted(true)
                    .build()
            )
        }
    }
}
