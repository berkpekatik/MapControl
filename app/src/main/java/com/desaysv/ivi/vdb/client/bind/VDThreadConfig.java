package com.desaysv.ivi.vdb.client.bind;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/* compiled from: r8-map-id-68001c32d3ba41aaeec8ea0ca25ce36b0b764eb40c8b7abbf6d2ae52bbacc2f9 */
public class VDThreadConfig {
    public int corePoolSize;
    public long keepAliveTime;
    public int maximumPoolSize;
    public TimeUnit timeUnit;
    public BlockingQueue<Runnable> workQueue;

    public VDThreadConfig(int i9, int i10, long j9, TimeUnit timeUnit2, BlockingQueue<Runnable> blockingQueue) {
        this.corePoolSize = i9;
        this.maximumPoolSize = i10;
        this.keepAliveTime = j9;
        this.timeUnit = timeUnit2;
        this.workQueue = blockingQueue;
    }
}
