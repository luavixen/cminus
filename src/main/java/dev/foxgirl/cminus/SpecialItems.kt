package dev.foxgirl.cminus

import com.google.common.collect.ImmutableMap
import dev.foxgirl.cminus.util.nbtCompoundOf
import dev.foxgirl.cminus.util.stackOf
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.LoreComponent
import net.minecraft.component.type.NbtComponent
import net.minecraft.enchantment.Enchantments
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.nbt.NbtString
import net.minecraft.text.Text
import net.minecraft.util.Formatting

fun addSpecialItemID(stack: ItemStack, specialItemID: String) {
    stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbtCompoundOf("cminus_special_item" to specialItemID)))
}
fun getSpecialItemID(stack: ItemStack): String? {
    if (stack.isEmpty) return null
    val customData = stack.get(DataComponentTypes.CUSTOM_DATA) ?: return null
    val specialItemIDElement = customData.nbt.get("cminus_special_item") as? NbtString ?: return null
    return specialItemIDElement.asString()
}

fun areSpecialItemsEqual(stack1: ItemStack, stack2: ItemStack): Boolean {
    return stack1.item == stack2.item
        && stack1.count == stack2.count
        && stack1.damage == stack2.damage
        && stack1.name == stack2.name
        && stack1.enchantments == stack2.enchantments
}

fun setCustomName(stack: ItemStack, name: Text) {
    stack.set(DataComponentTypes.CUSTOM_NAME, name)
}
fun setCustomLore(stack: ItemStack, lore: List<Text>) {
    stack.set(DataComponentTypes.LORE, LoreComponent(lore))
}

val specialItemDebugStick: ItemStack = stackOf(Items.DEBUG_STICK).apply {
    addSpecialItemID(this, "debug_stick")
    setCustomName(this, Text.literal("The Ancient Weapon SHIFTIS").formatted(Formatting.RED))
    setCustomLore(this, listOf(Text.of("transmutate any block into its alternative forms")))
}

val specialItemKnockbackHoe: ItemStack = stackOf(Items.NETHERITE_HOE).apply {
    addSpecialItemID(this, "knockback_hoe")
    setCustomName(this, Text.literal("The Ancient Weapon FLINGUS").formatted(Formatting.RED))
    setCustomLore(this, listOf(Text.of("banish your enemies into the stratosphere")))
    addEnchantment(Enchantments.UNBREAKING, 3)
    addEnchantment(Enchantments.KNOCKBACK, 32)
}

val specialItemSupershotCrossbow: ItemStack = stackOf(Items.CROSSBOW).apply {
    addSpecialItemID(this, "supershot_crossbow")
    setCustomName(this, Text.literal("The Ancient Weapon BULLETUS").formatted(Formatting.RED))
    setCustomLore(this, listOf(Text.of("shoot projectiles 360 degrees around you")))
    addEnchantment(Enchantments.UNBREAKING, 3)
    addEnchantment(Enchantments.MULTISHOT, 16)
}

val specialItemLightningTrident: ItemStack = stackOf(Items.TRIDENT).apply {
    addSpecialItemID(this, "lightning_trident")
    setCustomName(this, Text.literal("The Ancient Weapon SHOCKUS").formatted(Formatting.RED))
    setCustomLore(this, listOf(Text.of("harness the power of lightning in any weather")))
    addEnchantment(Enchantments.UNBREAKING, 3)
    addEnchantment(Enchantments.LOYALTY, 3)
    addEnchantment(Enchantments.CHANNELING, 1)
}

val specialItemPumpkin: ItemStack = stackOf(Items.JACK_O_LANTERN).apply {
    addSpecialItemID(this, "pumpkin")
    setCustomName(this, Text.literal("The Ancient Weapon SPAWNUS").formatted(Formatting.RED))
    setCustomLore(this, listOf(Text.of("summon iron golems at will wherever jack is placed")))
    addEnchantment(Enchantments.SHARPNESS, 1)
}

val specialItemBarrierBreaker: ItemStack = stackOf(Items.NETHERITE_SHOVEL).apply {
    addSpecialItemID(this, "barrier_breaker")
    setCustomName(this, Text.literal("The Barrier Breaker").formatted(Formatting.RED))
    setCustomLore(this, listOf(Text.of("you know where this needs to be used.")))
    addEnchantment(Enchantments.UNBREAKING, 3)
}

val specialItems: Map<String, ItemStack> = ImmutableMap
    .builder<String, ItemStack>()
    .put("debug_stick", specialItemDebugStick)
    .put("knockback_hoe", specialItemKnockbackHoe)
    .put("supershot_crossbow", specialItemSupershotCrossbow)
    .put("lightning_trident", specialItemLightningTrident)
    .put("pumpkin", specialItemPumpkin)
    .put("barrier_breaker", specialItemBarrierBreaker)
    .build()
