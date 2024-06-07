/**
 * This package wraps Minecraft's default NBT string reader in some extra
 * exception handling to prevent evil clients from crashing the server by
 * sending NBT data that is too nested. It's possible to generate a
 * StackOverflowError by sending specifically crafted NBT strings.
 */
package dev.foxgirl.cminus.mixin.fixes.nbt_crash_fix;
