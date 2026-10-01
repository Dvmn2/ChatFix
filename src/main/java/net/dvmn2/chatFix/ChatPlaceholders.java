package net.dvmn2.chatFix;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.time.Duration;
import java.util.regex.Pattern;

/**
 * Ключевые слова в сообщениях чата:
 * <ul>
 *   <li>{@code [item]} — название предмета в основной руке; при наведении показывается
 *       стандартное описание предмета;</li>
 *   <li>{@code [inv]} — "Ник's inv"; по клику открывается сундук (6 рядов) со снимком
 *       инвентаря отправителя на момент отправки сообщения. Снимок только для просмотра.</li>
 * </ul>
 * Раскладка окна [inv]:
 * <pre>
 * ряд 1: ботинки | поножи | нагрудник | шлем | — | вторая рука | — | — | —
 * ряд 2: заглушки
 * ряды 3-5: основной инвентарь (верхняя, центральная, нижняя части)
 * ряд 6: хотбар
 * </pre>
 * Снимок делается на основном потоке ({@link #capture}), а на каждого получателя
 * компоненты собираются отдельно ({@link #apply}) — чтобы подсказки были на языке получателя.
 * Ссылка [inv] — callback Adventure с ограниченным временем жизни: по истечении срока
 * клик перестаёт работать, а снимок освобождается сборщиком мусора.
 */
public final class ChatPlaceholders implements Listener {

    private static final Pattern ITEM_PATTERN = Pattern.compile("\\[item\\]", Pattern.CASE_INSENSITIVE);
    private static final Pattern INV_PATTERN = Pattern.compile("\\[inv\\]", Pattern.CASE_INSENSITIVE);

    private static final String PERMISSION_ITEM = "chatmanager.item";
    private static final String PERMISSION_INV = "chatmanager.inv";

    /**
     * Сколько времени после отправки сообщения по [inv] можно кликнуть.
     */
    private static final Duration INV_LINK_LIFETIME = Duration.ofMinutes(30);

    // Раскладка 54-слотового окна.
    private static final int GUI_SIZE = 54;
    private static final int SLOT_BOOTS = 0;
    private static final int SLOT_LEGGINGS = 1;
    private static final int SLOT_CHESTPLATE = 2;
    private static final int SLOT_HELMET = 3;
    private static final int SLOT_OFFHAND = 5;
    private static final int[] TOP_ROW_FILLERS = {4, 6, 7, 8};
    private static final int SEPARATOR_ROW_START = 9;
    private static final int SEPARATOR_ROW_END = 18; // не включая
    private static final int MAIN_GUI_OFFSET = 9;    // слот игрока 9 -> слот окна 18
    private static final int HOTBAR_GUI_START = 45;  // слот игрока 0 -> слот окна 45

    private final LegacyComponentSerializer section = LegacyComponentSerializer.legacySection();
    private final ItemStack filler = createFiller();

    /**
     * Данные для подстановки, собранные один раз на сообщение.
     *
     * @param senderName  ник отправителя
     * @param itemEnabled нужно ли подставлять [item]
     * @param item        копия предмета в основной руке; {@code null} — рука пуста
     * @param invClick    ссылка для [inv]; {@code null} — [inv] подставлять не нужно
     */
    public record Snapshot(String senderName, boolean itemEnabled, ItemStack item, ClickEvent invClick) {
    }

    /**
     * Снимает данные отправителя, если в тексте есть разрешённые ему ключевые слова.
     * Вызывать только на основном потоке (читается инвентарь).
     *
     * @return снимок или {@code null}, если подставлять нечего
     */
    public Snapshot capture(Player sender, String text) {
        boolean wantsItem = sender.hasPermission(PERMISSION_ITEM) && ITEM_PATTERN.matcher(text).find();
        boolean wantsInv = sender.hasPermission(PERMISSION_INV) && INV_PATTERN.matcher(text).find();
        if (!wantsItem && !wantsInv) {
            return null;
        }

        ItemStack held = null;
        if (wantsItem) {
            held = copyOrNull(sender.getInventory().getItemInMainHand());
        }

        ClickEvent invClick = null;
        if (wantsInv) {
            invClick = createInventoryLink(sender.getName(), captureInventory(sender));
        }

        return new Snapshot(sender.getName(), wantsItem, held, invClick);
    }

    /**
     * Подставляет компоненты вместо [item] / [inv] для конкретного получателя.
     */
    public Component apply(Component message, Snapshot snapshot, CommandSender viewer) {
        if (snapshot == null) {
            return message;
        }

        Component result = message;
        if (snapshot.itemEnabled()) {
            result = result.replaceText(replacement(ITEM_PATTERN, itemComponent(snapshot, viewer)));
        }
        if (snapshot.invClick() != null) {
            result = result.replaceText(replacement(INV_PATTERN, inventoryComponent(snapshot, viewer)));
        }
        return result;
    }

    private static TextReplacementConfig replacement(Pattern pattern, Component with) {
        return TextReplacementConfig.builder()
                .match(pattern)
                .replacement(with)
                .build();
    }

    private Component itemComponent(Snapshot snapshot, CommandSender viewer) {
        ItemStack item = snapshot.item();
        if (item == null) {
            return section.deserialize(Lang.get(Lang.Key.EMPTY_HAND, viewer));
        }
        // displayName() — "[Название]" в цвете редкости; hoverEvent — стандартное описание предмета.
        return item.displayName().hoverEvent(item.asHoverEvent());
    }

    private Component inventoryComponent(Snapshot snapshot, CommandSender viewer) {
        Component hover = section.deserialize(Lang.get(Lang.Key.INV_HOVER, viewer));
        return Component.text(Lang.get(Lang.Key.INV_LABEL, viewer, snapshot.senderName()), NamedTextColor.AQUA)
                .hoverEvent(HoverEvent.showText(hover))
                .clickEvent(snapshot.invClick());
    }

    private ClickEvent createInventoryLink(String ownerName, ItemStack[] contents) {
        return ClickEvent.callback(
                audience -> {
                    if (audience instanceof Player viewer) {
                        openInventory(viewer, ownerName, contents);
                    }
                },
                ClickCallback.Options.builder()
                        .uses(ClickCallback.UNLIMITED_USES) // по ссылке кликают все получатели
                        .lifetime(INV_LINK_LIFETIME)
                        .build());
    }

    private void openInventory(Player viewer, String ownerName, ItemStack[] contents) {
        Component title = section.deserialize(Lang.get(Lang.Key.INV_TITLE, viewer, ownerName));

        PreviewHolder holder = new PreviewHolder();
        Inventory inventory = Bukkit.createInventory(holder, GUI_SIZE, title);
        holder.inventory = inventory;
        inventory.setContents(contents);

        viewer.openInventory(inventory);
    }

    /**
     * Раскладывает инвентарь игрока по 54 слотам окна. Пустые слоты экипировки/инвентаря — null.
     */
    private ItemStack[] captureInventory(Player sender) {
        PlayerInventory inv = sender.getInventory();
        ItemStack[] slots = new ItemStack[GUI_SIZE];

        slots[SLOT_BOOTS] = copyOrNull(inv.getBoots());
        slots[SLOT_LEGGINGS] = copyOrNull(inv.getLeggings());
        slots[SLOT_CHESTPLATE] = copyOrNull(inv.getChestplate());
        slots[SLOT_HELMET] = copyOrNull(inv.getHelmet());
        slots[SLOT_OFFHAND] = copyOrNull(inv.getItemInOffHand());

        for (int slot : TOP_ROW_FILLERS) {
            slots[slot] = filler;
        }
        for (int slot = SEPARATOR_ROW_START; slot < SEPARATOR_ROW_END; slot++) {
            slots[slot] = filler;
        }

        // Основной инвентарь: слоты игрока 9..35 -> ряды 3-5
        for (int i = 9; i <= 35; i++) {
            slots[i + MAIN_GUI_OFFSET] = copyOrNull(inv.getItem(i));
        }
        // Хотбар: слоты игрока 0..8 -> ряд 6
        for (int i = 0; i <= 8; i++) {
            slots[HOTBAR_GUI_START + i] = copyOrNull(inv.getItem(i));
        }
        return slots;
    }

    private static ItemStack copyOrNull(ItemStack item) {
        return (item == null || item.isEmpty()) ? null : item.clone();
    }

    private static ItemStack createFiller() {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        pane.editMeta(meta -> meta.displayName(Component.space()));
        return pane;
    }

    // --- Окно [inv] только для просмотра ---

    private static final class PreviewHolder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static boolean isPreview(Inventory top) {
        return top.getHolder(false) instanceof PreviewHolder;
    }

    // Отменяем любые клики (в т.ч. shift-клик из своего инвентаря в окно) и перетаскивание.
    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (isPreview(event.getView().getTopInventory())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (isPreview(event.getView().getTopInventory())) {
            event.setCancelled(true);
        }
    }
}