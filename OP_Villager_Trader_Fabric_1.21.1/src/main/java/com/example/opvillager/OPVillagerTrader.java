package com.example.opvillager;

import com.mojang.brigadier.Command;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import net.minecraft.village.TradedItem;

public class OPVillagerTrader implements ModInitializer {
    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(CommandManager.literal("optrader")
                .requires(source -> source.hasPermissionLevel(2))
                .executes(context -> {
                    var source = context.getSource();
                    ServerWorld world = source.getWorld();
                    BlockPos pos = BlockPos.ofFloored(source.getPosition());

                    VillagerEntity villager = EntityType.VILLAGER.spawn(
                        world, pos, SpawnReason.COMMAND
                    );
                    if (villager == null) {
                        source.sendError(Text.literal("Couldn't spawn the trader here."));
                        return 0;
                    }

                    villager.setCustomName(Text.literal("OP Item Trader"));
                    villager.setCustomNameVisible(true);
                    villager.setPersistent();

                    TradeOfferList offers = new TradeOfferList();
                    int added = 0;

                    for (Item item : Registries.ITEM) {
                        if (item == Items.AIR || isExcluded(item)) continue;

                        ItemStack result = new ItemStack(item);
                        if (result.isEmpty()) continue;

                        addBestSafeEnchants(result);
                        offers.add(new TradeOffer(
                            new TradedItem(Items.DIRT, 1),
                            result,
                            1_000_000,
                            0,
                            0.0F
                        ));
                        added++;
                    }

                    villager.setOffers(offers);
                    final int offerCount = added;
                    source.sendFeedback(
                        () -> Text.literal("OP Trader spawned with " + offerCount
                            + " Dirt-priced offers. (Weapon items are intentionally excluded.)"),
                        false
                    );
                    return Command.SINGLE_SUCCESS;
                })
            )
        );
    }

    private static boolean isExcluded(Item item) {
        String path = Registries.ITEM.getId(item).getPath();
        return path.contains("sword")
            || path.contains("axe")
            || path.contains("bow")
            || path.contains("crossbow")
            || path.contains("trident")
            || path.contains("mace");
    }

    private static void addBestSafeEnchants(ItemStack stack) {
        if (!stack.isEnchantable()) return;

        String path = Registries.ITEM.getId(stack.getItem()).getPath();

        // Protective gear
        if (path.endsWith("_helmet") || path.endsWith("_chestplate")
                || path.endsWith("_leggings") || path.endsWith("_boots")
                || path.equals("elytra") || path.equals("turtle_helmet")) {
            add(stack, Enchantments.PROTECTION, 4);
            add(stack, Enchantments.UNBREAKING, 3);
            add(stack, Enchantments.MENDING, 1);
            return;
        }

        // Non-weapon utility tools
        if (path.endsWith("_pickaxe") || path.endsWith("_shovel")
                || path.endsWith("_hoe") || path.equals("shears")
                || path.equals("fishing_rod") || path.equals("flint_and_steel")) {
            add(stack, Enchantments.EFFICIENCY, 5);
            add(stack, Enchantments.UNBREAKING, 3);
            add(stack, Enchantments.MENDING, 1);
        }
    }

    private static void add(ItemStack stack,
            net.minecraft.registry.RegistryKey<net.minecraft.enchantment.Enchantment> key,
            int level) {
        Registries.ENCHANTMENT.getEntry(key).ifPresent(entry -> stack.addEnchantment(entry, level));
    }
}
