/**
 * This package fixes a very specific issue with VillagerEntities somehow
 * crashing when attempting to cast our StandEntity to a PassiveEntity? I don't
 * understand why the crash occurs, it's almost certainly an issue with Lithium
 * making assumptions or another mod causing issues, as the original game code
 * never casts to PassiveEntity (as far as I can tell). Anyway, here's the
 * stack trace for the crash:
 *
 * <pre>
 * java.lang.ClassCastException: class dev.foxgirl.cminus.StandEntity cannot be cast to class net.minecraft.class_1296 (dev.foxgirl.cminus.StandEntity and net.minecraft.class_1296 are in unnamed module of loader net.fabricmc.loader.impl.launch.knot.KnotClassLoader @675d3402)
 * 	at net.minecraft.class_4106.method_46958(class_4106.java:23)
 * 	at net.minecraft.class_6670.method_38981(class_6670.java:76)
 * 	at net.minecraft.class_4106.method_46960(class_4106.java:33)
 * 	at net.minecraft.class_7898$1.trigger(class_7898.java:53)
 * 	at net.minecraft.class_7894.method_18922(class_7894.java:20)
 * 	at net.minecraft.class_4103$class_4216$1.method_46930(class_4103.java:118)
 * 	at java.base/java.util.stream.ReferencePipeline$2$1.accept(ReferencePipeline.java:178)
 * 	at java.base/java.util.stream.ReferencePipeline$2$1.accept(ReferencePipeline.java:179)
 * 	at java.base/java.util.stream.ReferencePipeline$3$1.accept(ReferencePipeline.java:197)
 * 	at java.base/java.util.ArrayList$ArrayListSpliterator.tryAdvance(ArrayList.java:1685)
 * 	at java.base/java.util.stream.ReferencePipeline.forEachWithCancel(ReferencePipeline.java:129)
 * 	at java.base/java.util.stream.AbstractPipeline.copyIntoWithCancel(AbstractPipeline.java:527)
 * 	at java.base/java.util.stream.AbstractPipeline.copyInto(AbstractPipeline.java:513)
 * 	at java.base/java.util.stream.AbstractPipeline.wrapAndCopyInto(AbstractPipeline.java:499)
 * 	at java.base/java.util.stream.FindOps$FindOp.evaluateSequential(FindOps.java:150)
 * 	at java.base/java.util.stream.AbstractPipeline.evaluate(AbstractPipeline.java:234)
 * 	at java.base/java.util.stream.ReferencePipeline.findFirst(ReferencePipeline.java:647)
 * 	at net.minecraft.class_4103$class_4216$1.method_19559(class_4103.java:119)
 * 	at net.minecraft.class_4103.method_18922(class_4103.java:61)
 * 	at net.minecraft.class_4095.method_18891(class_4095.java:736)
 * 	at net.minecraft.class_4095.method_19542(class_4095.java:492)
 * 	at net.minecraft.class_1646.method_5958(class_1646.java:279)
 * 	at net.minecraft.class_1308.method_6023(class_1308.java:847)
 * 	at net.minecraft.class_1309.method_6007(class_1309.java:2789)
 * 	at net.minecraft.class_1308.method_6007(class_1308.java:603)
 * 	at net.minecraft.class_1296.method_6007(class_1296.java:128)
 * 	at net.minecraft.class_1309.method_5773(class_1309.java:2529)
 * 	at net.minecraft.class_1308.method_5773(class_1308.java:377)
 * 	at net.minecraft.class_1646.method_5773(class_1646.java:320)
 * 	at net.minecraft.class_3218.method_18762(class_3218.java:769)
 * 	at net.minecraft.class_1937.method_18472(class_1937.java:492)
 * 	at net.minecraft.class_3218.method_31420(class_3218.java:407)
 * 	at net.minecraft.class_5574.method_31791(class_5574.java:54)
 * 	at net.minecraft.class_3218.method_18765(class_3218.java:371)
 * 	at net.minecraft.server.MinecraftServer.method_3813(MinecraftServer.java:998)
 * 	at net.minecraft.class_3176.method_3813(class_3176.java:294)
 * 	at net.minecraft.server.MinecraftServer.method_3748(MinecraftServer.java:889)
 * 	at net.minecraft.server.MinecraftServer.method_29741(MinecraftServer.java:691)
 * 	at net.minecraft.server.MinecraftServer.method_29739(MinecraftServer.java:275)
 * 	at java.base/java.lang.Thread.run(Thread.java:1583)
 * </pre>
 */
package dev.foxgirl.cminus.mixin.fixes.villager_find_entity_crash_fix;
