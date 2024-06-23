package dev.foxgirl.cminus

import com.google.common.collect.ImmutableMap
import dev.foxgirl.cminus.util.nbtCompoundOf
import dev.foxgirl.cminus.util.stackOf
import net.minecraft.component.DataComponentTypes
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

val specialItemDebugStick: ItemStack = stackOf(Items.DEBUG_STICK).apply {
    addSpecialItemID(this, "debug_stick")
    set(DataComponentTypes.CUSTOM_NAME, Text.literal("Debug Stick").formatted(Formatting.RED))
}

val specialItemKnockbackHoe: ItemStack = stackOf(Items.NETHERITE_HOE).apply {
    addSpecialItemID(this, "knockback_hoe")
    set(DataComponentTypes.CUSTOM_NAME, Text.literal("Knockback Hoe").formatted(Formatting.RED))
    addEnchantment(Enchantments.UNBREAKING, 3)
    addEnchantment(Enchantments.KNOCKBACK, 32)
}

val specialItemSupershotCrossbow: ItemStack = stackOf(Items.CROSSBOW).apply {
    addSpecialItemID(this, "supershot_crossbow")
    set(DataComponentTypes.CUSTOM_NAME, Text.literal("Supershot Crossbow").formatted(Formatting.RED))
    addEnchantment(Enchantments.UNBREAKING, 3)
    addEnchantment(Enchantments.MULTISHOT, 16)
}

val specialItemLightningTrident: ItemStack = stackOf(Items.TRIDENT).apply {
    addSpecialItemID(this, "lightning_trident")
    set(DataComponentTypes.CUSTOM_NAME, Text.literal("Lightning Trident").formatted(Formatting.RED))
    addEnchantment(Enchantments.UNBREAKING, 3)
    addEnchantment(Enchantments.LOYALTY, 3)
    addEnchantment(Enchantments.CHANNELING, 1)
}

val specialItemPumpkin: ItemStack = stackOf(Items.PUMPKIN).apply {
    addSpecialItemID(this, "pumpkin")
    set(DataComponentTypes.CUSTOM_NAME, Text.literal("Golem Pumpkin").formatted(Formatting.RED))
    addEnchantment(Enchantments.SHARPNESS, 1)
}

val specialItemBarrierBreaker: ItemStack = stackOf(Items.NETHERITE_SHOVEL).apply {
    addSpecialItemID(this, "barrier_breaker")
    set(DataComponentTypes.CUSTOM_NAME, Text.literal("Barrier Breaker").formatted(Formatting.RED))
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
